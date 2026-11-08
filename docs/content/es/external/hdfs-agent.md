---
title: Agente NameNode de Apache Hadoop HDFS
description: Aplica políticas de rutas GrantForge con el agente NameNode correspondiente a la versión Hadoop y registra auditorías.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plugin de servidor `grantforge-plugin-hdfs` ofrece de forma unificada el tipo de servicio `hdfs`, sus recursos y la configuración de conexión. Los agentes de NameNode se construyen por separado para cada versión de Hadoop, comprueban los accesos con `INodeAttributeProvider` y `AccessControlEnforcer` de su versión y comparten la lógica de políticas firmadas, caché, evaluación de políticas y auditoría.

En el código fuente, el plugin de servidor está en `plugins/grantforge-plugin-hdfs`, los agentes de versión en `agents/grantforge-agent-hdfs-<line>` y la lógica de producción común sin dependencia de Hadoop en `agents/grantforge-agent-hdfs-common`. La adaptación nativa compartida está en el módulo de producción de Maven `agents/grantforge-agent-hdfs-native`, con Java 8 / Hadoop 2.7.7 como base; cada módulo numerado la usa como dependencia binaria de Maven, conserva sus puntos de entrada y devoluciones de llamada propios y ya no vuelve a compilar el código de producción compartido. El protocolo, la caché de instantáneas y la infraestructura de auditoría siguen en `core/grantforge-agent-core`.

El plugin de servidor mantiene un único tipo `hdfs`, y la versión del cliente es independiente de la del agente. La conexión y la búsqueda de rutas en Hadoop 2.x usan `webhdfs://namenode:50070` (con HTTPS, la dirección `swebhdfs://` correspondiente); Hadoop 3.x admite RPC `hdfs://` o WebHDFS. La matriz de contenedores verifica WebHDFS en 2.x, y RPC y WebHDFS en 3.x; el RPC de 2.x no está certificado y el protocolo de conexión nunca cambia automáticamente.

Con la extensión de atributos de inode activada, Apache Hadoop 2.7.7 provoca en su código nativo un error de puntero nulo cuando un usuario normal consulta directamente la ruta raíz `/`, antes incluso de la devolución de llamada del agente. En esta versión, usa directorios de datos reales como `/data` para las operaciones de datos y para el `lookup.path` del servicio; las pruebas mantienen una aserción propia para el fallo en la raíz.

## Elegir la versión

| Sufijo del módulo del agente | Apache Hadoop verificado | JVM del contenedor | Interfaz de autorización | Devolución de llamada de rutas de superusuario |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | por parámetros | no |
| `2.10` | 2.10.2 | Java 8 | por parámetros | no |
| `3.2` | 3.2.4 | Java 8 | por parámetros | no |
| `3.3` | 3.3.6 | Java 8 | por contexto | no |
| `3.4` | 3.4.3 | Java 11 | por contexto, devoluciones de llamada de superusuario y de denegación | sí |
| `3.5` | 3.5.0 | Java 17 | por contexto, devoluciones de llamada de superusuario y de denegación | sí |

Por ejemplo, para Hadoop 2.10.2 se instala `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`. En cada NameNode se instala un único agente de versión; al actualizar se quita el jar anterior y el nombre de clase del provider en la configuración no cambia. Al arrancar, el agente comprueba la versión mayor y menor de Hadoop y las devoluciones de llamada necesarias, y no arranca si la versión no coincide; otros parches de la misma versión y las distribuciones de proveedores siguen necesitando una verificación propia.

El código principal de los agentes `common` y `2.7` a `3.4` usa bytecode de Java 8, y `3.5` usa Java 17. La tabla indica las JVM con las que se ejecutan realmente las pruebas de integración; no significa que 3.4.3 esté verificado con Java 8. La imagen de prueba oficial de 3.3.6 solo existe para amd64, así que en hosts ARM se ejecuta explícitamente con emulación amd64.

## Relación con los permisos nativos

El agente ejecuta primero las comprobaciones de permisos nativas de HDFS y después las políticas de GrantForge: el usuario debe cumplir tanto los permisos nativos como las políticas. Las políticas de permiso de GrantForge no se saltan los permisos POSIX, las ACL, las comprobaciones de propietario ni el sticky bit; las políticas de denegación siempre deniegan. Los permisos nativos se siguen gestionando con las herramientas de administración de Hadoop.

De forma predeterminada, `grantforge.hdfs.native.fallback=false`: sin instantánea local de políticas, sin política coincidente o mientras el agente no ha arrancado, se deniega el acceso a los datos. Con `true`, los accesos que ninguna política decide usan los permisos nativos; las denegaciones explícitas siguen vigentes. Si el servidor no está disponible temporalmente, se sigue usando la última instantánea local con firma verificada.

Las proyecciones de ancestros, destino, subárboles y rutas de instantánea dentro de una misma devolución de llamada de autorización usan la misma versión de la instantánea de políticas; las políticas actualizadas se aplican desde la siguiente devolución de llamada, de modo que no se mezclan reglas de permiso de versiones distintas. La auditoría de accesos registra la versión de políticas usada realmente.

El agente comprueba los permisos `read`, `write` y `execute` que un usuario normal necesita sobre el destino, y también el directorio padre, los directorios ancestros y los subdirectorios que requieren comprobación recursiva. Crear, eliminar, renombrar y otras operaciones afectan a varias rutas, y las políticas de permiso deben cubrirlas todas. En modo estricto no basta una política `read` sobre el archivo de destino: el usuario necesita también políticas `execute` sobre los directorios ancestros, por ejemplo permitir `execute` en `/` con recursión marcada, y después permisos de lectura y escritura sobre los directorios de datos reales.

Las rutas de instantánea se comprueban tanto con la ruta solicitada como con la ruta original sin `.snapshot/<nombre de la instantánea>`; por ejemplo, `/data/.snapshot/s1/secret` comprueba también `/data/secret`. Por eso las políticas de denegación de la ruta original también restringen las instantáneas; además se pueden poner restricciones más estrictas en rutas de instantánea explícitas. Las consultas de metadatos siguen la semántica de recorrido de directorios de HDFS.

Una autorización recursiva examina como máximo `100000` inodes; por encima de ese límite se deniega la operación para que el NameNode no reserve memoria sin límite. Las rutas muy largas se evalúan con la ruta completa; la visualización del recurso en la auditoría se limita a `1000` caracteres y los detalles de la solicitud registran la longitud original y un resumen SHA-256.

Hadoop 2.7, 2.10, 3.2 y 3.3 dejan pasar a los superusuarios antes de llamar al agente, que no puede controlar ni auditar esos accesos. En Hadoop 3.4 y 3.5, las devoluciones de llamada de superusuario con ruta pasan primero la comprobación nativa y después se comprueban las políticas según el nombre de la operación: leer archivos y consultar metadatos exige `read`, listar directorios `read` + `execute` y las operaciones de modificación conocidas `write`. Las operaciones desconocidas, ausentes o imposibles de deducir con precisión (como `checkAccess` y `concat`) exigen por precaución los tres permisos.

Las devoluciones de llamada de superusuario no tienen el contexto completo de inodes y subárboles; las llamadas de administración del clúster sin ruta mantienen la comprobación nativa, y las políticas de subdirectorios no pueden limitar todas las operaciones recursivas de un superusuario. Quienes usan los datos deben usar usuarios normales de Hadoop.

## Métricas

El agente publica métricas con el sistema Metrics2 de Hadoop, con los mismos sinks que las métricas dfs del propio NameNode; en el JMX del NameNode aparecen como `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (el exportador JMX de Prometheus puede recogerlas directamente). Cada métrica lleva las etiquetas `instance` (nombre de la instancia del servicio de datos) y `agentVersion`, de modo que los dos NameNode de un par HA se pueden ver por separado. Si falla el registro de métricas, solo se pierden las métricas: el agente registra una advertencia y sigue autorizando sin ellas.

| Métrica | Descripción |
| --- | --- |
| `Callbacks` | Devoluciones de llamada de autorización ejecutadas por el agente |
| `SuperuserCallbacks` | Devoluciones de llamada de superusuario ejecutadas por el agente |
| `NativeDenies` | Accesos que Hadoop denegó antes de llegar al agente |
| `EvaluationFailures` | Devoluciones de llamada cerradas por fallo debido a una excepción al evaluar políticas |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Permisos que las políticas permitieron / denegaron / dejaron sin decidir; los no decididos también se deniegan en modo estricto |
| `MissingSnapshots` | Devoluciones de llamada atendidas sin instantánea de políticas verificada |
| `SnapshotVersion` | Versión de la instantánea de políticas en uso; 0 si no hay ninguna |
| `QueuedEvents` / `DroppedEvents` | Eventos de auditoría pendientes de envío en memoria / descartados porque la cola o el búfer de disco estaban llenos |
| `ServerReachable` | Si el último acceso al servidor de políticas tuvo éxito (1/0) |

## Despliegue

1. Añade un servicio `hdfs` en los servicios de datos de GrantForge, guarda la configuración y prueba la conexión; configura políticas de ruta para los nombres cortos reales de los usuarios de Hadoop, sus grupos o sus roles.
2. En "Permisos de datos → Agentes", emite un token para este servicio. Escribe el texto del token en un archivo local de cada NameNode, por ejemplo `/etc/hadoop/grantforge/token`, legible por el usuario que ejecuta el NameNode.
3. Según la tabla, elige el jar de agente correspondiente en `agents/hdfs/<línea de Hadoop>/` del paquete de distribución y colócalo en el classpath del NameNode, por ejemplo en `$HADOOP_HOME/share/hadoop/hdfs/lib/`. El jar del agente incluye su propio motor de políticas, Jackson y la biblioteca de firma; las clases de Hadoop las aporta el NameNode. La versión del agente en el heartbeat incluye la versión del producto y la versión de Hadoop con la que se compiló.
4. Configura las siguientes propiedades en el `hdfs-site.xml` de cada NameNode; los dos NameNode de un par HA usan `instance` distintos y cada uno su propio directorio de caché local.

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

5. Comprueba que `dfs.permissions.enabled=true` y que `dfs.namenode.inode.attributes.provider.bypass.users` está vacío; el agente no arranca con una configuración que permita saltarse la devolución de llamada de autorización. Reinicia el NameNode y revisa el heartbeat y la versión de políticas en la página de agentes de GrantForge. El agente lee la configuración existente del NameNode y no modifica los atributos nativos de los inodes.

En el primer despliegue puedes empezar con `native.fallback=true`, comprobar que la instantánea de políticas está sincronizada y completar los permisos de los directorios ancestros, y después pasar al modo estricto. El token debe estar vinculado al tipo de servicio `hdfs`; una configuración errónea o un vínculo con otro tipo de servicio provoca la denegación.

## Configuración opcional

| Propiedad | Valor predeterminado | Uso |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Tiempo de espera de conexión con GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Tiempo de espera de lectura de la respuesta |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervalo de actualización de políticas mientras el servidor no está disponible, al menos `1000`; el heartbeat normal usa el intervalo que recomienda el servidor |
| `grantforge.hdfs.signing.key.file` | sin definir | Archivo opcional de clave pública de firma, con la clave pública X.509 en Base64 que ofrece la consola; si se define, solo se aceptan firmas de esa clave |
| `grantforge.hdfs.audit.batch.size` | `500` | Número máximo de eventos por envío, rango `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Capacidad de la cola de auditoría en memoria, rango `1..1000000`, al menos un lote; cuando se llena, los eventos nuevos se cuentan y se descartan |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervalo de vaciado de la auditoría, entero positivo de como máximo `2147483647`; un valor menor reduce el retraso del envío |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Límite del búfer de disco mientras el servidor no está disponible, entero no negativo; `0` desactiva el búfer de disco |

Ante picos de acceso, aumenta la cola de auditoría para que se desborde menos; acortar el intervalo de vaciado reduce el retraso de la auditoría, pero aumenta la frecuencia de los envíos. El límite del búfer de disco acota el espacio usado durante cortes largos; con el búfer desactivado o agotado se pueden perder eventos. La auditoría se envía en segundo plano y no espera la respuesta del servidor de políticas.

Si no se configura una clave pública de firma, el agente la obtiene del servidor la primera vez y la guarda con la instantánea. El agente usa el nombre de usuario corto y los grupos que le pasa Hadoop; los roles y los grupos adicionales proceden de la instantánea firmada. La correspondencia entre principals de Kerberos y nombres cortos la determina `hadoop.security.auth_to_local` del clúster.

## Compilar desde el código fuente y verificar

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

El artefacto del agente está en `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`. Las pruebas unitarias cubren las devoluciones de llamada de autorización del NameNode, la configuración, los metadatos de versión y las decisiones de las políticas; las pruebas de WebHDFS y Kerberos arrancan servicios locales temporales. Antes de pasar a producción, verifica también en el clúster de destino la lectura y escritura, la creación, el renombrado, el borrado recursivo, la conmutación HA y el comportamiento de la caché tras un corte.

La verificación de integración se ejecuta con Testcontainers en la fase `verify` (las pruebas unitarias no arrancan ningún clúster; `verify` necesita un demonio de Docker disponible):

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# el mismo punto de entrada que nightly
bash script/ci/hdfs_integration.sh
# verificar solo la línea de versiones indicada
bash script/ci/hdfs_integration.sh 2.10
```

Las pruebas usan imágenes de Apache Hadoop de versión fija; las imágenes antiguas se construyen a partir de una imagen base de Java 8 con resumen fijo y una distribución de Apache verificada con SHA-512. El jar real del agente de la versión correspondiente se coloca en el classpath del NameNode y se comprueban las versiones reales de Hadoop y de la JVM. Testcontainers crea una red aislada y gestiona el ciclo de vida del NameNode y del DataNode; se verifican la lectura y escritura, la creación, la adición, el renombrado, el borrado, las denegaciones recursivas y en instantáneas, los permisos nativos y la auditoría, la actualización de políticas, el reinicio del NameNode con la caché firmada tras desconectar el servidor de políticas, y el modo estricto y el recurso a los permisos nativos sin instantánea.

Hace falta un demonio de Docker en ejecución y permiso para descargar las imágenes de prueba. Sin Docker, las pruebas fallan en lugar de omitirse en silencio. El cliente del sistema de archivos se ejecuta dentro del contenedor de Hadoop, y el servicio HTTP de políticas usa el reenvío de puertos del host de Testcontainers; no se necesita un clúster de Hadoop externo. Al terminar se eliminan los contenedores y la red de prueba, y los registros se guardan en `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

La prueba de HA arranca dos NameNode, un DataNode y un JournalNode, y da a los dos agentes nombres de instancia y directorios de caché distintos. Conmuta a mano el nodo activo con un cliente HDFS lógico y verifica después la lectura, la escritura y las políticas de denegación; un único JournalNode solo sirve para la prueba, así que no se verifica la tolerancia a fallos por mayoría ni la conmutación automática con ZooKeeper.

La prueba de HA automática (Hadoop 2.10 a 3.5, Java 17 y 21) arranca ZooKeeper, tres JournalNode, dos NameNode con su propio ZKFC y un DataNode. Verifica que, al detener directamente el NameNode activo, ZooKeeper elige otro nodo, el nuevo nodo activo sigue aplicando las políticas y los eventos de denegación pertenecen a su instancia; que las políticas nuevas publicadas mientras un nodo estaba fuera se aplican cuando vuelve como Standby, y que tras volver a conmutar se sigue aplicando la versión nueva en lugar de la antigua; que, mientras el servicio de políticas no está disponible, el nodo que toma el relevo sigue con la instantánea ya cargada y reenvía la auditoría cuando el servicio se recupera; y que con uno de los tres JournalNode detenido las escrituras siguen funcionando, mientras que con dos fallan y el NameNode activo se detiene en lugar de continuar sin mayoría. También hay una variante con Kerberos (solo Hadoop 3.5.0): los JournalNode también inician sesión con keytab y solo abren HTTPS, los ZKFC se autentican ante ZooKeeper con SASL y solo el principal del NameNode puede acceder al nodo de elección (un cliente de ZooKeeper no autenticado no puede ni leer); la prueba verifica que, al detener el NameNode activo, otro nodo toma el relevo y sigue aplicando las políticas a los usuarios de Kerberos, que los clientes sin ticket siempre se rechazan y que se puede volver a conmutar cuando regresa el nodo original. Los JournalNode se ejecutan con un principal del mismo nombre corto que el del NameNode, porque un JournalNode solo entrega los edits al principal completo del NameNode o a solicitantes con su mismo nombre corto, y la autenticación HTTP solo proporciona el nombre corto. La HA automática de Hadoop 2.7 no está verificada: su cliente solo conmuta si puede resolver los nombres de host de todos los NameNode; el nombre del contenedor detenido en la prueba deja de resolverse, así que el cliente informa directamente de una UnknownHostException sin probar el otro nodo.

La prueba de Kerberos se ejecuta en las seis líneas de versiones (Java 17 y 21): el KDC se ejecuta dentro de la JVM de prueba, el NameNode y el DataNode arrancan en modo seguro con su propio keytab, la transferencia de datos del DataNode exige SASL y solo abre HTTPS, y WebHDFS usa SPNEGO. La prueba verifica que, tras asignar el principal a su nombre corto, las políticas de GrantForge se aplican a la lectura y la escritura, que los usuarios sin política se rechazan, que las denegaciones se auditan con el nombre corto, que los clientes sin ticket se rechazan sin recurrir a la autenticación simple y que el NameNode vuelve a iniciar sesión tras reiniciarse y sigue aplicando las políticas.

El código fuente de las pruebas está en `src/test` de los módulos de producción reales. Las pruebas unitarias comunes de políticas se ejecutan en common y las pruebas unitarias nativas compartidas en `agents/grantforge-agent-hdfs-native/src/test`; ya no se compilan en los seis agentes de versión. Las pruebas de devolución de llamada de cada versión siguen en su módulo numerado, y el código compartido de pruebas de contenedores `agents/grantforge-agent-hdfs-common/src/test/shared` se sigue compilando en cada módulo numerado; no hay un proyecto de pruebas de Maven aparte. Testcontainers solo está en el ámbito test. Nightly verifica las seis versiones de Hadoop en hosts con Java 17 y 21, y los contenedores de Hadoop usan las JVM de la tabla. Hoy los contenedores cubren la autenticación Simple, la HA manual, Kerberos y HTTPS en las seis líneas de versiones, la HA automática de 2.10 a 3.5 y la HA automática con Kerberos en Hadoop 3.5.0; la HA automática de 2.7, la HA automática con Kerberos en otras versiones y los parches de los proveedores siguen necesitando verificación en el entorno de destino.

Los puntos de extensión de Hadoop y la semántica de permisos se describen en la [API de Apache Hadoop 3.5.0](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) y en la [guía de permisos de HDFS](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
