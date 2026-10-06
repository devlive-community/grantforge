---
title: Agente NameNode de HDFS
description: Aplica dentro del NameNode de Hadoop 3.5.0 las políticas de rutas de GrantForge e informa de la auditoría de accesos.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in del servidor `grantforge-plugin-hdfs` define los recursos y la configuración de conexión; `grantforge-agent-hdfs` se instala dentro del NameNode y comprueba los accesos mediante `INodeAttributeProvider` y `AccessControlEnforcer` de Hadoop, reutilizando el núcleo de agentes de GrantForge para descargar políticas firmadas, guardar la instantánea local e informar de la auditoría por lotes. El agente actual se compila para **Hadoop 3.5.0, Java 17 o superior**; otras versiones de Hadoop necesitan adaptación y validación para la versión correspondiente.

En el código fuente, el plug-in del servidor está en `plugins/grantforge-plugin-hdfs`, el agente NameNode está en `agents/grantforge-agent-hdfs` y la infraestructura compartida de protocolo, caché de instantáneas e informe de auditoría está en `core/grantforge-agent-core`.

## Relación con los permisos

El agente ejecuta primero la comprobación de permisos nativa de HDFS y después las políticas de GrantForge: el usuario tiene que cumplir al mismo tiempo los requisitos de los permisos nativos y de las políticas. Las políticas de permiso de GrantForge no sortean los permisos POSIX, las ACL, la comprobación del propietario ni el sticky bit; las políticas de denegación siempre deniegan. Los ajustes de permisos nativos se mantienen con las herramientas de administración de Hadoop.

Por defecto `grantforge.hdfs.native.fallback=false`: cuando no hay una instantánea de políticas local, no hay una política coincidente o el agente aún no se ha iniciado, se deniega el acceso a los datos. Si se pone a `true`, los accesos que ninguna política decide usan los permisos nativos; las políticas de denegación explícita siguen vigentes. Cuando el servidor no está alcanzable de forma temporal, se sigue usando la última instantánea local que pasó la verificación de firma.

Las proyecciones de ancestros, destino, subárbol y rutas de instantáneas de una misma llamada de retorno de autorización usan la misma versión de la instantánea de políticas; las políticas actualizadas entran en vigor en la siguiente llamada, para no combinar reglas de permiso de versiones distintas. La auditoría de accesos registra la versión de política que realmente se usó.

El agente comprueba los permisos `read`, `write` y `execute` que un usuario normal necesita para acceder al destino, y también los directorios padre, los directorios ancestros y los subdirectorios que hay que validar de forma recursiva. Operaciones como crear, borrar o renombrar afectan a varias rutas, así que las políticas de permiso deben cubrirlas todas. En modo estricto, no basta con la política `read` sobre el archivo de destino: hay que configurar al usuario la política `execute` sobre los directorios ancestros, por ejemplo permitir `execute` en `/` y marcar recursivo, y luego configurar permisos de lectura y escritura en el directorio de datos real.

Las rutas de instantáneas se comprueban tanto en la ruta realmente solicitada como en la ruta original sin `.snapshot/<nombre-de-la-instantánea>`, por ejemplo `/data/.snapshot/s1/secret` comprueba también `/data/secret`. Por eso la política de denegación sobre la ruta original también restringe la instantánea; además se pueden definir restricciones más estrictas para la ruta de instantánea explícita. Las consultas de metadatos mantienen la semántica de permisos de recorrido de directorios de HDFS.

Una autorización recursiva comprueba como máximo `100000` inodos; al superar el límite se deniega la operación, para no asignar memoria sin límite dentro del NameNode. Las rutas muy largas se evalúan con la ruta completa; la visualización del recurso en la auditoría se limita a `1000` caracteres, y en el detalle de la solicitud se registran la longitud original y el resumen SHA-256.

El superusuario de HDFS sigue administrándolo Hadoop. Las llamadas de retorno de superusuario con ruta pasan primero por la comprobación de superusuario de Hadoop y después se evalúan según los nombres de operación que proporciona Hadoop 3.5.0: la lectura de archivos y las consultas de metadatos piden `read`, la enumeración de directorios pide `read` + `execute` y las operaciones de modificación conocidas piden `write`. Las operaciones desconocidas, ausentes o que no se pueden deducir con exactitud (por ejemplo `checkAccess` y `concat`) piden de forma conservadora los tres permisos.

Las llamadas de retorno de superusuario no tienen el contexto completo de inodos y subárbol; las llamadas de administración del cluster sin ruta mantienen la comprobación nativa; no es posible restringir todas las operaciones recursivas del superusuario con políticas de subdirectorio. Quienes usan los datos deben hacerlo con usuarios Hadoop normales.

## Métricas

El agente informa de las métricas a través del sistema Metrics2 de Hadoop, con los mismos sinks que las métricas dfs del propio NameNode, y en el JMX del NameNode aparece como `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (el exportador JMX de Prometheus puede capturarlo directamente). Cada métrica lleva las etiquetas `instance` (nombre de la instancia del servicio de datos) y `agentVersion`, así que los dos NameNode de una HA se pueden consultar por separado. Si falla el registro de métricas solo se pierden las métricas: el agente registra un aviso y sigue ejecutando autorizaciones sin métricas.

| Métrica | Explicación |
| --- | --- |
| `Callbacks` | Número de llamadas de retorno de autorización que ejecuta el agente |
| `SuperuserCallbacks` | Número de llamadas de retorno de superusuario que ejecuta el agente |
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
3. Copia el `grantforge-agent-hdfs-<versión>.jar` correspondiente a la versión publicada actual, de `agents/hdfs/` del paquete publicado, al classpath del NameNode, por ejemplo `$HADOOP_HOME/share/hadoop/hdfs/lib/`. El jar del agente ya incluye su propio motor de políticas, Jackson y las bibliotecas de firma; las clases de Hadoop las proporciona el NameNode; la versión del agente en el latido se genera a partir de los metadatos de compilación.
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

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs,core/grantforge-plugin-host -am test
```

El artefacto del agente está en `agents/grantforge-agent-hdfs/target/grantforge-agent-hdfs-<versión>.jar`. Las pruebas unitarias cubren las llamadas de retorno de autorización del NameNode, la configuración, los metadatos de versión y las decisiones de políticas; las pruebas de WebHDFS y Kerberos arrancan servicios temporales locales. Antes de pasar a producción, además hay que verificar en el cluster de destino la lectura y escritura, la creación, el renombrado, el borrado recursivo, la conmutación de HA y el comportamiento de la caché tras perder la red.

La verificación de integración se ejecuta con `verify` mediante Testcontainers (las pruebas unitarias no arrancan un cluster; `verify` necesita un Docker daemon disponible):

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am verify
# usa el mismo punto de entrada que el nightly
bash script/ci/hdfs_integration.sh
```

Las pruebas usan una imagen de contenedor de Apache Hadoop de versión fija y copian el jar del agente realmente empaquetado al classpath del NameNode. Testcontainers crea una red aislada y gestiona el ciclo de vida de NameNode y DataNode, y verifica la lectura y escritura, la creación, el añadido, el renombrado, el borrado, la denegación recursiva y de instantáneas, los permisos nativos y la auditoría, la actualización de políticas, el reinicio del NameNode usando la caché firmada tras desconectar el servidor de políticas, y el modo estricto y el repliegue a permisos nativos cuando no hay instantánea.

Hace falta un Docker daemon en ejecución y que se permita descargar las imágenes de prueba. Si Docker no está disponible, las pruebas fallan, no se omiten en silencio. El cliente de sistema de archivos se ejecuta dentro del contenedor de Hadoop, y el servicio HTTP de políticas usa el reenvío de puertos del host de Testcontainers; no hace falta un cluster Hadoop externo. Al terminar las pruebas se limpian los contenedores y la red de prueba, y los logs se guardan en `agents/grantforge-agent-hdfs/target/hdfs-testcontainers`.

Las pruebas de HA arrancan dos NameNode, un DataNode y un JournalNode, y configuran nombres de instancia y directorios de caché independientes para los dos agentes. Usan un cliente HDFS lógico para conmutar manualmente el nodo activo y verifican la lectura, la escritura y las políticas de denegación después de la conmutación; el único JournalNode es solo para las pruebas: no verifica la tolerancia a fallos por mayoría ni implica la conmutación automática por fallo con ZooKeeper.

El código fuente de las pruebas y sus dependencias van directamente en el `src/test` y el ámbito de test del `agents/grantforge-agent-hdfs` existente, sin crear un proyecto de pruebas Maven aparte; Testcontainers no entra en el paquete publicado del agente. El nightly ejecuta el mismo punto de entrada de pruebas en Java 17 y 21, y guarda los informes y los logs de los contenedores.

Los puntos de entrada de extensión de Hadoop y la semántica de permisos se describen en [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) y en [HDFS Permissions Guide](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
