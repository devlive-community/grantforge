---
title: Agente de OpenSearch
description: Ejecutar el agente de OpenSearch junto al clúster, para que las políticas de GrantForge se conviertan en los propios roles del plug-in de seguridad, con seguridad a nivel de campo, campos enmascarados y seguridad a nivel de documento, los patrones de índice sigan siendo patrones para que los índices nuevos sean accesibles al instante, y los accesos se informen desde el registro de auditoría.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

El agente de OpenSearch es un servicio junto al clúster. Mantiene el control de acceso propio del plug-in de seguridad al día con las políticas firmadas de un servicio de datos `opensearch`: un rol por usuario que contiene exactamente lo que las políticas le dan, asignado solo a ese usuario, con seguridad a nivel de campo, campos enmascarados y seguridad a nivel de documento. OpenSearch comprueba entonces cada petición por sí mismo, y el agente nunca se interpone entre los clientes y el clúster. La distribución lo incluye como JAR ejecutable, `agents/opensearch/grantforge-agent-opensearch-<version>.jar`, que requiere Java 17 o posterior.

Las pruebas de integración con Testcontainers del repositorio ejecutan el agente, y el JAR tal como se distribuye, contra OpenSearch `2.19`, `3.3`, `3.6` y `3.9` con el plug-in de seguridad. Esto no certifica todas las demás versiones; el agente rechaza clústeres anteriores a `2.19` y clústeres sin plug-in de seguridad. Para el plug-in de servicio, consulte [OpenSearch](/es/plugins/opensearch/).

## Funcionamiento

- **Un rol por usuario contiene lo que da GrantForge.** Para cada usuario al que las políticas dan algo, el agente mantiene un rol con su nombre, `grantforge_alice` para `alice`, asignado solo a ese usuario. Su descripción lo marca como del agente, y el agente no toma ningún rol sin esa marca por uno propio, de modo que no cambia ningún otro rol ni asignación.
- **Los patrones de índice siguen siendo patrones.** Un patrón de índice de una política, como `logs-*`, se concede como patrón allí donde todas las políticas deciden igual en toda su extensión: toda política de cualquier tipo cuyos índices se solapan con el patrón lo cubre por completo. El agente lo decide con exactitud, con autómatas finitos de los patrones. Los índices creados después, como los registros de mañana, son entonces accesibles al instante, también cuando el primer documento escrito los crea. Donde una política decide otra cosa dentro de un patrón, como una denegación de `logs-secret` dentro de `logs-*`, el agente concede por nombre cada índice y flujo de datos que tiene el clúster, y los nuevos en la siguiente sincronización.
- **Seguridad a nivel de campo solo donde algo por debajo se decide de otro modo.** Un índice que el usuario puede leer entero, sin política que pueda quitarle un campo, se lee sin seguridad a nivel de campo, de modo que los campos añadidos después se leen al instante. Si no, el usuario lee los campos que permite una política, de los que tienen sus documentos y los que nombran las políticas; los multicampos como `name.keyword` siguen a su campo.
- **Máscaras y filtros de filas son los del plug-in de seguridad.** Una máscara se convierte en un campo enmascarado de la entrada, y un filtro de filas en su consulta de seguridad a nivel de documento. La API validate de OpenSearch comprueba cada consulta antes de concederla; una consulta que OpenSearch no ejecutaría no muestra ningún documento al usuario, y se registra. Una máscara que el plug-in de seguridad no conoce oculta el campo en su lugar.
- **Una entrada por conjunto de permisos.** Los índices y patrones con los mismos permisos comparten una entrada; las entradas nunca se solapan, ya que el plug-in de seguridad combina la seguridad a nivel de campo y de documento de las entradas que se solapan.
- **Las operaciones compuestas del clúster vienen con la lectura y la escritura.** La lectura trae `cluster_composite_ops_ro`, para multi-search, multi-get y desplazamiento, y la escritura `cluster_composite_ops`, para bulk.
- **Los cambios son un parche.** El agente escribe sus roles y asignaciones como parches JSON, que el plug-in de seguridad aplica de una vez en todo el clúster: los roles antes de las asignaciones que los nombran, las asignaciones antes de los roles que nombran y que se eliminan. Un parche que el plug-in de seguridad rechaza se aplica cambio a cambio, de modo que un rol defectuoso falla solo.
- **Las políticas del momento presente.** Cada sincronización planifica según las políticas tal como se aplican en ese momento, de modo que los periodos de validez empiezan y terminan a tiempo.
- **Los roles de backend son grupos.** Una política para un grupo se aplica a los usuarios internos que tienen un rol de backend con ese nombre, junto con los grupos en los que GrantForge pone a los usuarios.
- **Los cambios hechos a mano se deshacen.** El agente compara en cada sincronización sus roles y asignaciones con lo que guarda el plug-in de seguridad; lo que difiere se vuelve a escribir. Cuando nada ha cambiado, el agente no envía nada.
- **Se informa de los roles fuera de GrantForge.** OpenSearch concede lo que da cualquiera de los roles de un usuario, por lo que el agente no puede quitar lo que da otro rol asignado al usuario. Avisa una vez de cada rol así que da lo que deniega una política, nombrando el rol, el usuario, el acceso, el índice y la política, y de los usuarios asignados a `all_access`, a los que ningún rol limita.
- **Los accesos vienen del registro de auditoría.** El agente lee el registro de auditoría del plug-in de seguridad de los índices donde lo escribe, `security-auditlog-*`: lo que rechazó, como `MISSING_PRIVILEGES`, y lo que permitió, como `GRANTED_PRIVILEGES` cuando la configuración de auditoría los registra. Cada evento nombra al usuario, el tipo de acceso, cada índice o flujo de datos alcanzado, o el clúster, y dice si lo decidió GrantForge o lo permitió un rol fuera de GrantForge.

| Acción | Evento de acceso |
| --- | --- |
| `indices:data/read/*`: búsqueda, lectura, recuento | `read` en cada índice o flujo de datos |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| Otros `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` y `repository/*` | `cluster_monitor`, `manage_snapshots` en el clúster |
| Plantillas de índice y de componentes, canalizaciones de ingesta | `manage_templates`, `manage_pipelines` en el clúster |

## Despliegue

1. Añada un servicio de datos `opensearch` y configure sus políticas. Abra **Permisos sobre los datos → Agentes**, seleccione el servicio y emita un token de agente. Solo se muestra una vez; guárdelo en un archivo que solo pueda leer la cuenta del agente.
2. Dé al agente un usuario que pueda cambiar la configuración del plug-in de seguridad: un usuario asignado a `all_access` mientras `plugins.security.restapi.roles_enabled` lo incluya, o el certificado de administración que nombra `plugins.security.authcz.admin_dn`. Las pruebas ejecutan el agente como el `admin` de la configuración de demostración, y con su certificado de administración.
3. Para informar de los accesos, mantenga el registro de auditoría en el clúster (`plugins.security.audit.type: internal_opensearch`). Para lo que OpenSearch permitió, quite además `GRANTED_PRIVILEGES` de las categorías desactivadas de la configuración de auditoría; las denegaciones se registran de forma predeterminada.
4. Escriba el archivo de ajustes, sustituyendo las direcciones, rutas y nombres de ejemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   Estas rutas están en la máquina del agente, y el directorio de caché debe admitir escritura. Los archivos de contraseña se leen como UTF-8 y se recortan.
5. Inicie el agente, por ejemplo como servicio de systemd:

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   Sustituya `<version>` por la versión de la distribución. Los ajustes indicados como `java -Dgrantforge.…=… -jar …` prevalecen sobre el archivo. El agente se detiene limpiamente con SIGTERM y envía o guarda en cola los eventos pendientes; OpenSearch conserva lo que se le dio.
6. **Pasar los roles existentes a políticas:** los roles asignados a usuarios fuera de GrantForge siguen aplicándose junto a los del agente. Cree las políticas correspondientes y después elimine esas asignaciones; los avisos del agente nombran los roles que dan lo que deniega una política.

| Ajuste | Propósito |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatorios: el servidor de GrantForge, el archivo del token de agente, un nombre para este agente y un directorio con escritura para la instantánea firmada, los eventos en cola y la posición de lectura del registro de auditoría |
| `grantforge.signing.key.file` | Archivo opcional que fija la clave de firma de las instantáneas; copie la clave pública X.509 Ed25519 codificada en Base64 desde la página Agentes. Si se omite, la clave se obtiene del servidor en el primer contacto |
| `grantforge.opensearch.hosts` | Obligatorio: los nodos como URL, probados por turno cuando uno no está accesible; sin esquema un nodo es `https`, y el puerto predeterminado es `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | El usuario del agente y el archivo de su contraseña |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | En su lugar: certificado de cliente PEM y su clave privada, juntos, para iniciar sesión con un certificado; `grantforge.opensearch.tls.key.passphrase.file` contiene la frase de contraseña de una clave cifrada |
| `grantforge.opensearch.ssl.verification.mode` | `full` (predeterminado) comprueba los certificados y los nombres de los nodos, `certificate` solo sus certificados, `none` nada |
| `grantforge.opensearch.tls.ca` | Certificados de CA PEM que verifican los nodos; vacío confía en lo que confía Java |
| `grantforge.opensearch.timeout.ms` | Cuánto pueden tardar la conexión y una petición, predeterminado `30000`, rango `1..600000` milisegundos |
| `grantforge.opensearch.role.prefix` | El comienzo de los nombres de los roles del agente, predeterminado `grantforge_`; letras minúsculas, dígitos, guiones y guiones bajos, termina en guion bajo, como máximo 32 caracteres. Al agente le pertenecen los roles que marcó cuyos nombres empiezan por él |
| `grantforge.opensearch.unmanaged.users` | Usuarios que el agente deja en paz, separados por comas, como cuentas de servicio; siempre deja en paz a su propio usuario y a los usuarios asignados a `all_access` |
| `grantforge.sync.interval.ms` | Con qué frecuencia el agente pone OpenSearch al día, predeterminado `10000`, rango `1000..3600000` milisegundos |
| `grantforge.audit.log` | Predeterminado `true`; `false` no lee ningún registro de auditoría ni informa de accesos |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | Los índices del registro de auditoría, predeterminado `security-auditlog-*`; con qué frecuencia se lee, predeterminado `5000` milisegundos; y el máximo de documentos por lectura, predeterminado `1000`, de modo que un atraso se lea en varias vueltas |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tiempos de espera de conexión y lectura de GrantForge, predeterminados `5000` / `8000` milisegundos |
| `grantforge.refresh.interval.ms` | Con qué frecuencia el agente pide políticas nuevas, predeterminado `30000`, al menos `1000` milisegundos |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventos de acceso: tamaño de lote (predeterminado `500`), tamaño de cola (predeterminado `10000`), intervalo de envío (predeterminado `5000` milisegundos) y límite de cola en disco (predeterminado 64 MiB) |
| `grantforge.audit.fold.window.ms` | Durante cuánto tiempo los accesos permitidos idénticos se agrupan en un evento, predeterminado `10000`, hasta `3600000` milisegundos; `0` envía cada uno |

## Usuarios, grupos y roles

Las políticas nombran a los usuarios de OpenSearch tal como los conoce el plug-in de seguridad. El agente mantiene roles para los usuarios internos, para los usuarios que nombran las políticas, que pueden iniciar sesión mediante un directorio como LDAP o SAML, y para los usuarios para los que ya mantenía roles; los usuarios reservados y ocultos del plug-in de seguridad, como `kibanaserver`, se dejan en paz. Los grupos son los roles de backend de un usuario interno junto con los grupos en los que GrantForge pone al usuario; el agente no conoce los roles de backend que da un directorio al iniciar sesión, así que dé a esos usuarios sus grupos en GrantForge. Los roles son los que GrantForge da al usuario.

## Decisiones y limitaciones

- Sin instantánea el agente no cambia nada. Un servicio que no está en uso, o un token vinculado a un servicio cuyo tipo no es `opensearch`, no concede nada: el agente elimina sus roles y asignaciones, y se aplican los roles propios del plug-in de seguridad.
- Mientras el agente no se ejecuta, OpenSearch conserva lo último que se le dio; los cambios de políticas lo esperan. Un cambio llega a OpenSearch dentro del intervalo de actualización y el de sincronización.
- Los índices concedidos por nombre, y no mediante un patrón, reciben sus permisos en la siguiente sincronización.
- Los alias alcanzan los índices que agrupan: buscar en un alias requiere permisos sobre cada índice que contiene, por eso las políticas nombran índices y flujos de datos, no alias.
- Un filtro de filas solo puede comprobar campos que el usuario puede leer, y una máscara se aplica a valores de campos text y keyword.
- Los índices cuyo nombre empieza por un punto solo se alcanzan mediante patrones de política que empiezan por un punto.
- El agente no gestiona los tenants del plug-in de seguridad para OpenSearch Dashboards.

## Guías relacionadas

- [OpenSearch](/es/plugins/opensearch/)
- [Servicios de datos, políticas y agentes](/es/external/data-services/)
