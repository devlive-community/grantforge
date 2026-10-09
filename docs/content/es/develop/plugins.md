---
title: Plug-ins y tipos de servicio
description: "Extiende con plug-ins de tipo de servicio la gestión de políticas de GrantForge a los sistemas de datos externos: contrato, empaquetado, aislamiento y distribución."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Un plug-in de tipo de servicio describe un sistema externo: qué niveles de recursos tiene, qué tipos de acceso conoce, si admite enmascaramiento y filtrado de filas, y qué configuración necesita una conexión. A partir de ahí, GrantForge ofrece para este tipo de sistema servicios de datos, un editor de políticas general, instantáneas de políticas y auditoría de accesos (ver [Servicios de datos, políticas y agentes](/es/external/data-services/)).

## Dependencias

El plug-in solo depende de `grantforge-plugin-api` (que a su vez solo depende del JDK y de JSpecify) y se incorpora con el ámbito `provided`:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## Implementar ServiceTypeProvider

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // Devuelve los valores candidatos por debajo del nivel request.resource() que empiecen por request.userInput(), como máximo request.limit()
        return List.of();
    }
}
```

Lo anterior está tomado del plug-in de ejemplo `plugins/grantforge-plugin-example` y se puede copiar directamente como plantilla.

La definición se valida de una sola vez al construirla e informa de todos los problemas a la vez: padres desconocidos o cíclicos, nombres repetidos, referencias a tipos de acceso o recursos no declarados, etc. Los nombres tienen que encajar con `[a-z][a-z0-9_-]{0,63}`.

| Componente | Explicación |
| --- | --- |
| Recursos | Jerarquía, tipo de comparación (exacta, con comodines, por ruta, por expresión regular), si distingue mayúsculas de minúsculas, si es obligatorio, si admite exclusiones y recursión (solo en rutas), si admite búsqueda, si puede ser hoja |
| Tipos de acceso | Nombre, nombre visible, los otros tipos de acceso que implica (por ejemplo `all` implica `select`); puede limitarse a recursos |
| Enmascaramiento, filtrado de filas | Declara qué recursos los admiten y qué formas de enmascaramiento hay; la ejecución se hace en el sistema de destino |
| Condiciones | Condiciones que se pueden adjuntar a una política (por ejemplo un rango de IP), evaluadas por el SPI de condiciones del motor de políticas |
| Campos de configuración | Cadena, texto largo, entero, booleano, secreto, enumeración; los campos de secreto se guardan cifrados y no pueden tener valor por defecto |

El proveedor necesita un constructor público sin parámetros y tiene que ser seguro para hilos (thread-safe). `validateConfig`, `testConnection` y `lookup` tienen implementaciones por defecto: sobrescríbelas cuando haga falta.

### Cuando una búsqueda falla

Cuando `lookup` falla, lance una `LookupException` con un motivo: la consola muestra el motivo y permite reintentar, en lugar de mostrar que no hay valores. Use una sola línea y nunca incluya secretos; el servidor además oculta en ella la configuración secreta del servicio.

- `NOT_FOUND`: el lugar donde buscar no existe, como el directorio de búsqueda configurado
- `ACCESS_DENIED`: el sistema de destino rechazó al usuario de búsqueda
- `UNREACHABLE`: no se puede conectar con el sistema de destino
- `AUTHENTICATION_FAILED`: falló el inicio de sesión en el sistema de destino
- `LIMIT_EXCEEDED`: hay demasiados valores; hay que acotar la búsqueda
- `INVALID_INPUT`: la entrada no se puede buscar, como una ruta fuera del directorio permitido
- `FAILED`: cualquier otro fallo

Cualquier otra excepción que lance un plug-in se trata como `FAILED`, así que los plug-ins creados para la API 1.0 no necesitan cambios. `LookupException` está disponible desde la API 1.1.0.

### Exploración de directorios

Un nivel de rutas puede permitir que los administradores elijan valores explorando directorios: declare `browseSupported(true)` en el nivel (solo con el comparador `PATH`) e implemente `browse(BrowseRequest)`, que devuelve una `BrowsePage`: el directorio inicial `root`, el directorio listado, sus entradas y el cursor `nextCursor` de la página siguiente (`null` en la última). Cada `BrowseEntry` tiene su nombre, el valor que usa una política, si es un directorio y, opcionalmente, propietario, grupo, permisos, tamaño y hora de modificación.

El cursor es del propio plug-in; el servidor lo devuelve sin cambios. Mantenga el mismo orden de una página a otra, sin omitir ni repetir entradas. Una página tiene como máximo 500 entradas, y el servidor trata una página mayor que la pedida como un fallo del plug-in. Si el sistema de destino no puede paginar y un directorio supera el límite de exploración, lance `LIMIT_EXCEEDED` en lugar de devolver una parte. Los fallos de exploración usan `LookupException`, igual que las búsquedas.

## Descriptor y empaquetado

Pon `grantforge-plugin.yaml` en el directorio raíz del plug-in:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

Un plug-in puede ser:

- un jar, con el descriptor en el directorio raíz del jar;
- un directorio o un zip: `grantforge-plugin.yaml`, `classes/` y `lib/*.jar`.

Ponlo en `grantforge.plugins.directory` (por defecto `plugins`) y pulsa "Volver a buscar" en la página "Plug-ins" de la consola; no hace falta reiniciar.

Si el plug-in tiene dependencias, empaquétalo con assembly como un zip de la clasificación `plugin`, igual que `plugins/grantforge-plugin-hdfs` (descriptor en el nivel superior, `classes/`, `lib/`), y copia en la fase `generate-resources` las dependencias de tiempo de ejecución a `target/plugin-lib`:

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Al arrancar desde el código fuente

Cuando arrancas `org.devlive.grantforge.server.GrantForge` directamente en el IDE, las clases del servidor vienen de los `target/classes` de los distintos módulos. Si en ese momento no hay configurado `grantforge.plugins.directory` ni existe un directorio `plugins` en el directorio de trabajo, se usa el directorio `plugins/` del repositorio: los módulos de plug-in ya compilados que contiene (los que tienen un descriptor en `target/classes` y un `target/plugin-lib` generado por la compilación) se cargan directamente como plug-ins, con las clases de `target/classes` y las dependencias de `target/plugin-lib`; los módulos que no generan `plugin-lib` (como el plug-in de ejemplo para las pruebas) no se cargan. Después de modificar el código de un plug-in, deja que el IDE lo recompile y pulsa "Volver a buscar" en la página "Plug-ins" de la consola para que surta efecto. Antes de usar un módulo de plug-in por primera vez, compílalo una vez con Maven para copiar las dependencias:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Compatibilidad

`apiVersion` declara la versión del contrato que necesita el plug-in. El host proporciona actualmente `1.1.0`; solo se carga un plug-in cuya versión principal coincida y cuya versión disponible no sea inferior a la requerida; si no, se marca como "incompatible". Cada cambio del contrato aumenta la versión, y CI la compara con japicmp contra la última versión publicada (`script/ci/check_plugin_api_compat.py`); los cambios incompatibles tienen que aumentar la versión principal.

GrantForge 2026.1.0 proporciona la API de plug-ins 1.1.0. Un plug-in que declara `apiVersion: "1.1"` necesita un host 2026.1.0 o posterior; uno que declara `1.0` funciona sin cambios en el nuevo host. La versión del producto y la de la API de plug-ins son independientes: la de la API solo sube cuando cambia el contrato.

## Aislamiento

- Cada plug-in tiene su propio cargador de clases, cuyo padre es el cargador de clases de plataforma, y solo se delegan al host `org.devlive.grantforge.plugin.api.` y `org.jspecify.annotations.`; un plug-in no ve ni las clases de Spring ni las del servidor, y puede traer dependencias en cualquier versión.
- Un fallo de lectura, una versión incompatible, un duplicado, una excepción de construcción o un tiempo de espera agotado solo marca ese plug-in como fallido y deja constancia del motivo; el servidor sigue funcionando con normalidad.
- Cada llamada a un plug-in tiene tiempo de espera (`grantforge.plugins.call-timeout`, 10 segundos por defecto).

## Agentes e instantáneas

El código fuente de los plug-ins de tipo de servicio está en `plugins/` y lo carga el servidor de GrantForge; el código fuente de los agentes concretos está en `agents/` y, tras empaquetarlo, se despliega en los sistemas de destino, por ejemplo `agents/grantforge-agent-hdfs-*` en el NameNode de HDFS. La infraestructura de agentes compartida está en `core/grantforge-agent-core`.

Los agentes de los sistemas de destino acceden a `/api/v1/agent/**` con un token de agente:

| Interfaz | Función |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Informa del estado del agente y de la versión actual de la instantánea |
| `GET /api/v1/agent/policies` | Descarga la instantánea de políticas; devuelve 304 si no ha cambiado; la respuesta lleva una firma Ed25519 |
| `GET /api/v1/agent/signing-key` | La clave pública para verificar la firma |
| `POST /api/v1/agent/access-events` | Informa por lotes de eventos de acceso, que pasan a la auditoría de accesos |

Los agentes evalúan localmente con `grantforge-policy-engine` (una API de Java 8 que se puede integrar en sistemas más antiguos) y no tienen que llamar a GrantForge en cada acceso.

Los agentes no tienen que implementar estos protocolos por su cuenta: `core/grantforge-agent-core` (Java 8) ya los encapsula:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // evaluadores de condiciones, por nombre

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Envía latidos en el intervalo que exige el servidor; cuando cambia la versión de políticas, descarga la instantánea (304 si el ETag no ha cambiado) y verifica la firma con la clave pública Ed25519 del servidor (la clave pública se puede fijar en los ajustes; si no, se obtiene del servidor al primer contacto y se conserva). Solo si la verificación pasa se reemplaza la copia y se guarda en el directorio de caché; si el servidor no está alcanzable al arrancar, se sigue usando la última instantánea.
- La instantánea despliega los roles y grupos hasta los usuarios, y `decide` añade a la solicitud los roles y grupos del usuario en la instantánea. Si el servicio está desactivado o todavía no hay instantánea, el resultado es `NOT_DETERMINED` y el agente decide si repliega a las comprobaciones propias del sistema o si deniega.
- Los eventos de acceso pasan a una cola acotada (cuando está llena, se descartan y se cuentan, pero nunca se bloquea el sistema) y se envían por lotes; si el servidor no está alcanzable, se escriben en `audit-spool/`, dentro del directorio de caché, y se reenvían al recuperarse, descartando los más antiguos por encima del límite.
- Depende de Jackson 2 y de Bouncy Castle (Ed25519 no existe antes del JDK 15); como el sistema de destino trae otras versiones de estas bibliotecas, hay que reubicarlas con shade al empaquetar el agente.

## Ejemplo

`plugins/grantforge-plugin-example` es un plug-in completo: el tipo `example` (database → table → column y path), los tipos de acceso select, update y all, enmascaramiento de columnas, filtrado de filas de tabla, una condición de rango de IP y la configuración url, timeout y password (la prueba de conexión tiene éxito cuando la contraseña es `example`); además puede buscar bases de datos y tablas de ejemplo. La prueba end-to-end full-stack recorre con él todo el camino: "añadir servicio → escribir política → emitir token → el agente descarga → auditoría de accesos".
