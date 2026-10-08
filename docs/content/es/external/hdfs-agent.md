---
title: Agente NameNode de HDFS
description: Aplica políticas de rutas GrantForge con el agente NameNode correspondiente a la versión Hadoop y registra auditorías.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de servidor `grantforge-plugin-hdfs` define recursos y conexiones. Los agentes NameNode numerados para Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 y 3.5 verifican los accesos mediante la SPI de su versión y comparten instantáneas firmadas, evaluación de políticas y envío de auditoría.

La lógica HDFS compartida está en `agents/grantforge-agent-hdfs-common` y los adaptadores por versión en `agents/grantforge-agent-hdfs-<line>`. Los adaptadores nativos compartidos forman el módulo Maven de producción `agents/grantforge-agent-hdfs-native`, con base Java 8 / Hadoop 2.7.7. Cada módulo numerado consume su dependencia Maven binaria y conserva sus puntos de entrada y callbacks específicos, sin recompilar las fuentes de producción compartidas. `core/grantforge-agent-core` sigue siendo la biblioteca común de protocolo y ejecución.

El plug-in del servidor conserva un solo tipo de servicio `hdfs` y una versión de cliente independiente de los agentes. Para Hadoop 2.x, usa `webhdfs://namenode:50070` o la dirección `swebhdfs://` correspondiente con HTTPS; Hadoop 3.x admite RPC `hdfs://` o WebHDFS. Las pruebas en contenedores cubren WebHDFS en 2.x y ambos protocolos en 3.x. RPC en 2.x no está certificado y el protocolo configurado no cambia automáticamente.

Con las extensiones de atributos inode activadas, Apache Hadoop 2.7.7 genera un error nativo de puntero nulo cuando un usuario normal consulta `/`, antes del callback del agente. Usa directorios de datos como `/data` para las operaciones y `lookup.path` en esta versión. La prueba en contenedores verifica explícitamente esta limitación.

## Versiones objetivo de los agentes HDFS

| Base de Hadoop | Java del contenedor | Directorio del agente |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8 (imagen amd64) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

Selecciona `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` de `agents/hdfs/<line>/` para la línea Hadoop del clúster. La lógica compartida apunta a Java 8 y el adaptador 3.5 a Java 17.

Hadoop 2.7, 2.10, 3.2 y 3.3 no ofrecen el callback de autorización de superusuario que usa el agente. Hadoop conserva el control de esos accesos; utiliza usuarios normales para acceder a datos sujetos a políticas de GrantForge.

## Relación con los permisos

El comportamiento siguiente describe los accesos de usuarios normales. Las limitaciones de superusuario se indican arriba y en el apartado de callbacks de superusuario.

El agente ejecuta primero la comprobación de permisos nativa de HDFS y después las políticas de GrantForge: el usuario tiene que cumplir al mismo tiempo los requisitos de los permisos nativos y de las políticas. Las políticas de permiso de GrantForge no sortean los permisos POSIX, las ACL, la comprobación del propietario ni el sticky bit; las políticas de denegación siempre deniegan. Los ajustes de permisos nativos se mantienen con las herramientas de administración de Hadoop.

Por defecto `grantforge.hdfs.native.fallback=false`: cuando no hay una instantánea de políticas local, no hay una política coincidente o el agente aún no se ha iniciado, se deniega el acceso a los datos. Si se pone a `true`, los accesos que ninguna política decide usan los permisos nativos; las políticas de denegación explícita siguen vigentes. Cuando el servidor no está alcanzable de forma temporal, se sigue usando la última instantánea local que pasó la verificación de firma.

Las proyecciones de ancestros, destino, subárbol y rutas de instantáneas de una misma llamada de retorno de autorización usan la misma versión de la instantánea de políticas; las políticas actualizadas entran en vigor en la siguiente llamada, para no combinar reglas de permiso de versiones distintas. La auditoría de accesos registra la versión de política que realmente se usó.

El agente comprueba los permisos `read`, `write` y `execute` que un usuario normal necesita para acceder al destino, y también los directorios padre, los directorios ancestros y los subdirectorios que hay que validar de forma recursiva. Operaciones como crear, borrar o renombrar afectan a varias rutas, así que las políticas de permiso deben cubrirlas todas. En modo estricto, no basta con la política `read` sobre el archivo de destino: hay que configurar al usuario la política `execute` sobre los directorios ancestros, por ejemplo permitir `execute` en `/` y marcar recursivo, y luego configurar permisos de lectura y escritura en el directorio de datos real.

Las rutas de instantáneas se comprueban tanto en la ruta realmente solicitada como en la ruta original sin `.snapshot/<nombre-de-la-instantánea>`, por ejemplo `/data/.snapshot/s1/secret` comprueba también `/data/secret`. Por eso la política de denegación sobre la ruta original también restringe la instantánea; además se pueden definir restricciones más estrictas para la ruta de instantánea explícita. Las consultas de metadatos mantienen la semántica de permisos de recorrido de directorios de HDFS.

Una autorización recursiva comprueba como máximo `100000` inodos; al superar el límite se deniega la operación, para no asignar memoria sin límite dentro del NameNode. Las rutas muy largas se evalúan con la ruta completa; la visualización del recurso en la auditoría se limita a `1000` caracteres, y en el detalle de la solicitud se registran la longitud original y el resumen SHA-256.

Los callbacks de superusuario siguientes corresponden a Hadoop 3.4 y 3.5. El superusuario de HDFS sigue administrándolo Hadoop. Las llamadas de retorno de superusuario con ruta pasan primero por la comprobación de superusuario de Hadoop y después se evalúan según los nombres de operación que proporciona Hadoop 3.4 / 3.5: la lectura de archivos y las consultas de metadatos piden `read`, la enumeración de directorios pide `read` + `execute` y las operaciones de modificación conocidas piden `write`. Las operaciones desconocidas, ausentes o que no se pueden deducir con exactitud (por ejemplo `checkAccess` y `concat`) piden de forma conservadora los tres permisos.

Las llamadas de retorno de superusuario no tienen el contexto completo de inodos y subárbol; las llamadas de administración del cluster sin ruta mantienen la comprobación nativa; no es posible restringir todas las operaciones recursivas del superusuario con políticas de subdirectorio. Quienes usan los datos deben hacerlo con usuarios Hadoop normales.

## Métricas

El agente informa de las métricas a través del sistema Metrics2 de Hadoop, con los mismos sinks que las métricas dfs del propio NameNode, y en el JMX del NameNode aparece como `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (el exportador JMX de Prometheus puede capturarlo directamente). Cada métrica lleva las etiquetas `instance` (nombre de la instancia del servicio de datos) y `agentVersion`, así que los dos NameNode de una HA se pueden consultar por separado. Si falla el registro de métricas solo se pierden las métricas: el agente registra un aviso y sigue ejecutando autorizaciones sin métricas.

| Métrica | Explicación |
| --- | --- |
| `Callbacks` | Número de llamadas de retorno de autorización que ejecuta el agente |
| `SuperuserCallbacks` | Número de llamadas de retorno de superusuario que ejecuta el agente (3.4 / 3.5) |
| `NativeDenies` | Número de accesos que Hadoop deniega antes de llegar al agente |
| `EvaluationFailures` | Número de llamadas de retorno que fallan cerradas por una excepción al evaluar las políticas |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Número de permisos que las políticas deciden permitir / denegar / dejar sin decidir; lo no decidido también se deniega en modo estricto |
| `MissingSnapshots` | Número de llamadas de retorno atendidas sin una instantánea de políticas verificada |
| `SnapshotVersion` | Versión de la instantánea de políticas en uso; 0 significa que no hay |
| `QueuedEvents` / `DroppedEvents` | Número de eventos de auditoría pendientes de envío en memoria / número de eventos descartados por estar llenas la cola o el búfer de disco |
| `ServerReachable` | Si el último acceso al servidor de políticas tuvo éxito (1/0) |

## Despliegue

1. Añade un servicio `hdfs` en los servicios de datos de GrantForge, guarda la configuración y prueba la conexión; configura políticas de rutas para los nombres de usuario cortos, grupos de usuarios o roles reales de Hadoop.
2. En "Permisos sobre los datos → Agentes" emite un token para este servicio. Escribe el token original en un archivo local de cada NameNode, por ejemplo `/etc/hadoop/grantforge/token`, legible por el usuario con el que se ejecuta el NameNode.
3. Selecciona la línea Hadoop del clúster y copia `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` de `agents/hdfs/<line>/` al classpath del NameNode, por ejemplo `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Instala un único adaptador compatible. El jar incluye la lógica compartida, Jackson y la biblioteca de firma; el NameNode aporta Hadoop.
4. Configura las siguientes propiedades en el `hdfs-site.xml` de cada NameNode; los dos NameNode de una HA usan `instance` distintos y sus propios directorios de caché locales.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. Comprueba que `dfs.permissions.enabled=true` y que `dfs.namenode.inode.attributes.provider.bypass.users` está vacío; al arrancar, el agente rechaza las configuraciones que permitan sortear las llamadas de retorno de autorización. Reinicia el NameNode y después comprueba el latido y la versión de políticas en la página de agentes de GrantForge. El agente lee la configuración existente del NameNode; no modifica los atributos nativos de los inodos.

Para un primer despliegue puedes empezar con `native.fallback=true`, confirmar que la instantánea de políticas ya está sincronizada y completar los permisos de los directorios ancestros, y después pasar al modo estricto. El tipo de servicio vinculado al token tiene que ser `hdfs`; una configuración errónea o vinculada a otro tipo de servicio deniega el acceso.

## Ajustes opcionales

| Propiedad | Valor por defecto | Uso |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Tiempo de espera para conectar con GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Tiempo de espera para leer la respuesta |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervalo de actualización de políticas cuando el servidor no está alcanzable, como mínimo `1000`; el latido normal usa el intervalo que aconseja el servidor |
| `grantforge.hdfs.signing.key.file` | sin definir | Archivo opcional con la clave pública de firma, con la clave pública X.509 Base64 que proporciona la consola; una vez configurado solo se aceptan firmas de esa clave pública |
| `grantforge.hdfs.audit.batch.size` | `500` | Número máximo de eventos por envío, rango `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Capacidad de la cola de auditoría en memoria, rango `1..1000000`, como mínimo capaz de albergar un lote; cuando la cola está llena, cuenta y descarta los eventos nuevos |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervalo de vaciado de la auditoría, entero positivo, máximo `2147483647`; reducirlo baja la latencia de envío |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Límite del búfer de disco cuando el servidor no está alcanzable, entero no negativo; `0` desactiva el búfer de disco |

Cuando el acceso llega a rachas muy altas, puedes aumentar la cola de auditoría para reducir los desbordamientos de cola; acortar el intervalo de vaciado baja la latencia de la auditoría y también aumenta la frecuencia de envío. El límite del búfer de disco controla el espacio de disco que se usa durante cortes prolongados; si el búfer está desactivado o agotado, se pueden perder eventos. El envío de auditoría se ejecuta en segundo plano y no espera la respuesta del servidor de políticas.

Si no se configura una clave pública de firma, el agente la obtiene del servidor la primera vez y la guarda junto con la instantánea. El agente usa el nombre de usuario corto y los grupos que le pasa Hadoop; los roles y grupos adicionales vienen de la instantánea firmada; la asignación del nombre corto del principal de Kerberos la decide el `hadoop.security.auth_to_local` del cluster.

## Compilar y verificar desde el código fuente

Los comandos muestran 3.5; sustituye la línea por 2.7, 2.10, 3.2, 3.3 o 3.4 para el adaptador deseado.

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

El artefacto del agente está en `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<versión>.jar`. Las pruebas unitarias cubren las llamadas de retorno de autorización del NameNode, la configuración, los metadatos de versión y las decisiones de políticas; las pruebas de WebHDFS y Kerberos arrancan servicios temporales locales. Antes de pasar a producción, además hay que verificar en el cluster de destino la lectura y escritura, la creación, el renombrado, el borrado recursivo, la conmutación de HA y el comportamiento de la caché tras perder la red.

La verificación de integración se ejecuta con `verify` mediante Testcontainers (las pruebas unitarias no arrancan un cluster; `verify` necesita un Docker daemon disponible):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# usa el mismo punto de entrada que el nightly
bash script/ci/hdfs_integration.sh 3.5
# Una línea o la matriz completa
bash script/ci/hdfs_integration.sh all
```

Las pruebas usan una imagen de contenedor de Apache Hadoop de versión fija y copian el jar del agente realmente empaquetado al classpath del NameNode. Testcontainers crea una red aislada y gestiona el ciclo de vida de NameNode y DataNode, y verifica la lectura y escritura, la creación, el añadido, el renombrado, el borrado, la denegación recursiva y de instantáneas, los permisos nativos y la auditoría, la actualización de políticas, el reinicio del NameNode usando la caché firmada tras desconectar el servidor de políticas, y el modo estricto y el repliegue a permisos nativos cuando no hay instantánea.

Hace falta un Docker daemon en ejecución y que se permita descargar las imágenes de prueba. Si Docker no está disponible, las pruebas fallan, no se omiten en silencio. El cliente de sistema de archivos se ejecuta dentro del contenedor de Hadoop, y el servicio HTTP de políticas usa el reenvío de puertos del host de Testcontainers; no hace falta un cluster Hadoop externo. Al terminar las pruebas se limpian los contenedores y la red de prueba, y los logs se guardan en `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

Las pruebas de HA arrancan dos NameNode, un DataNode y un JournalNode, y configuran nombres de instancia y directorios de caché independientes para los dos agentes. Usan un cliente HDFS lógico para conmutar manualmente el nodo activo y verifican la lectura, la escritura y las políticas de denegación después de la conmutación; el único JournalNode es solo para las pruebas: no verifica la tolerancia a fallos por mayoría ni implica la conmutación automática por fallo con ZooKeeper.

Las pruebas unitarias nativas compartidas se ejecutan en `agents/grantforge-agent-hdfs-native/src/test`, sin compilarse en los seis adaptadores. Las pruebas de callbacks específicos permanecen en sus módulos numerados. Las fuentes compartidas de pruebas de contenedores en `agents/grantforge-agent-hdfs-common/src/test/shared` siguen compilándose en los módulos de producción numerados. No existe un proyecto Maven independiente para pruebas; Testcontainers sigue siendo una dependencia de test. Nightly prueba las seis versiones Hadoop con hosts Java 17/21; la JVM de cada contenedor sigue la matriz anterior. Se conservan informes y logs.

Los puntos de entrada de extensión de Hadoop y la semántica de permisos se describen en [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) y en [HDFS Permissions Guide](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
