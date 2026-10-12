---
title: Agente Apache ZooKeeper
description: Despliega el agente en cada nodo de ZooKeeper para aplicar las políticas de GrantForge mediante ACL de marcador.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El plug-in de servidor `grantforge-plugin-zookeeper` define el tipo de servicio `zookeeper`, sus recursos y la configuración de conexión. Aplicar las políticas requiere además una segunda pieza que se ejecuta dentro del propio ZooKeeper: el agente, que participa en las decisiones de ACL de los servidores como proveedor de autenticación SASL. Se encuentra en `agents/grantforge-agent-zookeeper`; el comando `mvn -pl agents/grantforge-agent-zookeeper -am package` produce un único JAR shaded compatible con Java 8, con sus dependencias, que la distribución incluye en `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. La implementación está orientada a las API de ZooKeeper 3.6–3.9; la compatibilidad con las versiones reales aún requiere validación. Por tanto, ni la matriz completa de versiones está superada ni la aceptación para producción está concluida.

## Despliegue

1. Instala el JAR en el directorio `lib` de **cada servidor de ZooKeeper**, incluidos todos los nodos que puedan aceptar solicitudes de clientes. Crea **credenciales de Agent** en el servicio de datos `zookeeper` correspondiente de GrantForge. Guarda el token en un archivo independiente que solo pueda leer la cuenta que ejecuta ZooKeeper; no escribas el token en `zoo.cfg`.
2. Añade estos ajustes al `zoo.cfg` de cada servidor y sustituye la dirección, las rutas y el identificador de instancia del ejemplo. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, un `grantforge.cache.dir` local y `grantforge.instance` son obligatorios. La cuenta que ejecuta ZooKeeper debe poder escribir en el directorio de caché.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. Este proveedor **sustituye al proveedor SASL nativo**; no conserves otro proveedor registrado para el mismo esquema `sasl` en ninguna otra entrada `authProvider.N`. Es obligatorio establecer `enableEagerACLCheck=false`: si la comprobación está activada, se rechaza el registro del proveedor y el marcador no permite acceso. Conserva y verifica la autenticación normal de ZooKeeper con SASL, JAAS y Kerberos; el agente no sustituye la autenticación. Reinicia los servidores siguiendo el procedimiento de mantenimiento del ensemble.
4. Opcionalmente, configura `grantforge.signing.key.file` con la ruta del archivo que contiene la clave pública de firma fijada como confiable. `grantforge.principal.mapping=short` es el valor predeterminado y convierte `kafka/host@REALM` en `kafka`; `full` conserva el principal completo. Los usuarios de las políticas deben coincidir con las identidades resultantes.

## ACL y limitaciones

- Solo la ACL de marcador `sasl:grantforge` delega en las políticas de GrantForge, por ejemplo `sasl:grantforge:cdrwa`, donde `c=create`, `d=delete`, `r=read`, `w=write` y `a=admin`. Las demás ACL nativas, incluidas otras identidades SASL, funcionan como siempre.
- El marcador deniega el acceso si el agente no está en ejecución, no hay una instantánea de políticas utilizable, su `serviceType` no es `zookeeper` o ninguna política coincide/permite el acceso. **No es una denegación global**: otras ACL, `world:anyone` o una identidad `super` aún pueden permitir la solicitud.
- Las políticas comparan la ruta absoluta de destino en el servidor. Para `create`/`delete`, es el znode que se va a crear/eliminar, no el padre que aporta la ACL. Las solicitudes con chroot necesitan el prefijo chroot real en la ruta de la política: `/brokers` bajo el chroot `/kafka` corresponde a `/kafka/brokers`. El `create` secuencial comprueba el prefijo absoluto de la solicitud en el servidor antes de añadir el número de secuencia; los `read`/`delete` posteriores comprueban el nombre completo del nodo generado.
- Las ACL nativas no se migran automáticamente y las ACL no se heredan de forma recursiva; los znodes nuevos necesitan una ACL de marcador explícita. Antes de usar `setACL`, verifica los accesos permitidos y denegados en un ámbito reducido y conserva un canal de administración independiente. No apliques el marcador al nodo raíz sin una validación cuidadosa, ya que podrías bloquear el acceso.
- Un servidor sin agente interpreta `sasl:grantforge` como la identidad SASL nativa `grantforge`, en lugar de evaluar políticas. Por ello es necesario desplegarlo en todos los nodos; un despliegue parcial no protege el ensemble.
- Las escrituras reenviadas de un follower/observer al leader pueden no tener conexión de cliente en esta interfaz del proveedor, por lo que el contexto `clientAddress` puede estar vacío. No se garantizan políticas de extremo a extremo en el ensemble basadas en la IP del cliente.
- Este proveedor no puede conocer el resultado final de la solicitud, por lo que no envía eventos de auditoría finales `AccessEvent` ni ofrece una auditoría completa de acceso.
