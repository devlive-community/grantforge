---
title: Plug-in e tipi di servizio
description: Estendere con i plug-in di tipo di servizio la gestione delle policy di GrantForge ai sistemi di dati esterni, dal contratto alla pacchettizzazione, all’isolamento e alla distribuzione.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Un plug-in di tipo di servizio descrive un sistema esterno: quali livelli di risorse ha, quali tipi di accesso conosce, se supporta mascheramento e filtro delle righe, e quale configurazione richiede una connessione. Su questa base GrantForge offre per questo tipo di sistema servizi di dati, un editor di policy generico, istantanee delle policy e audit degli accessi (vedi [Servizi di dati e policy](/it/external/data-services/)).

## Dipendenze

Un plug-in dipende solo da `grantforge-plugin-api` (che a sua volta dipende solo dal JDK e da JSpecify) e viene inserito con scope `provided`:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## Implementare ServiceTypeProvider

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
        // restituisce i valori candidati sotto il livello request.resource() che iniziano con request.userInput(), al massimo request.limit()
        return List.of();
    }
}
```

Quanto sopra è tratto dal plug-in di esempio `plugins/grantforge-plugin-example` e si può copiare direttamente come template.

La definizione viene validata in un solo passaggio al momento della costruzione e segnala tutti i problemi insieme: padri sconosciuti o ciclici, nomi duplicati, riferimenti a tipi di accesso o risorse non dichiarati, ecc. I nomi devono corrispondere a `[a-z][a-z0-9_-]{0,63}`.

| Componente | Spiegazione |
| --- | --- |
| Risorse | Gerarchia, tipo di confronto (esatto, con caratteri jolly, per percorso, con espressione regolare), se distingue maiuscole e minuscole, se è obbligatorio, se supporta esclusioni e ricorsione (solo per i percorsi), se supporta la ricerca, se può essere foglia |
| Tipi di accesso | Nome, nome visualizzato, gli altri tipi di accesso che implica (per esempio `all` implica `select`); può essere limitato a risorse |
| Mascheramento, filtro delle righe | Dichiara quali risorse li supportano e quali forme di mascheramento esistono; l’esecuzione avviene nel sistema di destinazione |
| Condizioni | Le condizioni che si possono allegare a una policy (per esempio un intervallo IP), valutate dallo SPI delle condizioni del motore delle policy |
| Campi di configurazione | Stringa, testo lungo, intero, booleano, segreto, enumerazione; i campi segreto vengono salvati cifrati e non possono avere un valore predefinito |

Il provider deve avere un costruttore pubblico senza parametri ed essere thread-safe. `validateConfig`, `testConnection` e `lookup` hanno implementazioni predefinite: sovrascrivile quando serve.

## Descrittore e pacchettizzazione

Metti `grantforge-plugin.yaml` nella directory radice del plug-in:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.0"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

Un plug-in può essere:

- un jar, con il descrittore nella directory radice del jar;
- una directory o uno zip: `grantforge-plugin.yaml`, `classes/` e `lib/*.jar`.

Mettilo in `grantforge.plugins.directory` (per impostazione predefinita `plugins`) e fai clic su “Ripeti scansione” nella pagina “Plug-in” della console; non serve riavviare.

Quando il plug-in ha dipendenze, impacchettalo con assembly in uno zip con classificazione `plugin`, come `plugins/grantforge-plugin-hdfs` (descrittore al primo livello, `classes/`, `lib/`), e copia nella fase `generate-resources` le dipendenze di runtime in `target/plugin-lib`:

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

## All’avvio dal codice sorgente

Quando avvii `org.devlive.grantforge.server.GrantForge` direttamente nell’IDE, le classi del server provengono dai `target/classes` dei vari moduli; in quel momento, se `grantforge.plugins.directory` non è configurato e nella directory di lavoro non esiste una directory `plugins`, viene usata la directory `plugins/` del repository: i moduli plug-in già compilati che contiene (quelli che hanno il descrittore in `target/classes` e per cui la compilazione ha generato `target/plugin-lib`) vengono caricati direttamente come plug-in, con le classi da `target/classes` e le dipendenze da `target/plugin-lib`; i moduli che non generano `plugin-lib` (come il plug-in di esempio usato nei test) non vengono caricati. Dopo aver modificato il codice di un plug-in, lascia che sia l’IDE a ricompilarlo e fai clic su “Ripeti scansione” nella pagina “Plug-in” della console per renderlo effettivo. Prima di usare un modulo plug-in per la prima volta, compilalo una volta con Maven per copiare le dipendenze:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Compatibilità

`apiVersion` dichiara la versione del contratto di cui il plug-in ha bisogno. L’host fornisce attualmente `1.0.0`; viene caricato solo un plug-in la cui versione principale coincida e che non sia inferiore alla versione richiesta, altrimenti viene contrassegnato come “incompatibile”. Ogni cambiamento del contratto incrementa la versione e la CI la confronta con japicmp rispetto all’ultima versione pubblicata (`script/ci/check_plugin_api_compat.py`); le modifiche incompatibili devono incrementare la versione principale.

## Isolamento

- Ogni plug-in ha il proprio class loader, il cui padre è il class loader della piattaforma, e solo `org.devlive.grantforge.plugin.api.` e `org.jspecify.annotations.` vengono delegati all’host; un plug-in non vede né le classi di Spring né quelle del server e può portare con sé dipendenze di qualsiasi versione.
- Un fallimento di lettura, una versione incompatibile, un duplicato, un’eccezione in fase di costruzione o un timeout fanno solo contrassegnare quel plug-in come fallito e registrano il motivo; il servizio continua a funzionare normalmente.
- Ogni chiamata a un plug-in ha un timeout (`grantforge.plugins.call-timeout`, 10 secondi per impostazione predefinita).

## Agenti e istantanee

Il codice sorgente dei plug-in di tipo di servizio si trova in `plugins/` e viene caricato dal server di GrantForge; il codice sorgente degli agenti concreti si trova in `agents/` e, una volta impacchettato, viene distribuito nei sistemi di destinazione, per esempio `agents/grantforge-agent-hdfs-*` si distribuisce sull’NameNode di HDFS. L’infrastruttura degli agenti condivisa si trova in `core/grantforge-agent-core`.

Gli agenti nei sistemi di destinazione accedono a `/api/v1/agent/**` con un token agente:

| Interfaccia | Funzione |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Segnala lo stato dell’agente e la versione attuale dell’istantanea |
| `GET /api/v1/agent/policies` | Scarica l’istantanea delle policy; restituisce 304 se non è cambiata; l’intestazione della risposta porta la firma Ed25519 |
| `GET /api/v1/agent/signing-key` | La chiave pubblica per verificare la firma |
| `POST /api/v1/agent/access-events` | Segnala in blocco gli eventi di accesso, che confluiscono nell’audit degli accessi |

Gli agenti valutano localmente con `grantforge-policy-engine` (un’API Java 8 che si può integrare in sistemi più vecchi), senza dover chiamare GrantForge a ogni accesso.

Gli agenti non devono implementare questi protocolli da soli: `core/grantforge-agent-core` (Java 8) li incapsula già:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // valutatori di condizioni, per nome

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Invia i battiti con l’intervallo richiesto dal server; quando la versione delle policy cambia, scarica l’istantanea (304 se l’ETag non è cambiato) e ne verifica la firma con la chiave pubblica Ed25519 del server (la chiave pubblica si può fissare nelle impostazioni, altrimenti viene ottenuta dal server al primo contatto e conservata); sostituisce la copia e la salva nella directory di cache solo se la verifica ha successo; se il server non è raggiungibile all’avvio, continua a usare l’ultima istantanea.
- L’istantanea espande ruoli e gruppi fino agli utenti e `decide` aggiunge alla richiesta i ruoli e i gruppi che l’utente ha nell’istantanea. Quando il servizio è disattivato o non c’è ancora un’istantanea, il risultato è `NOT_DETERMINED` e tocca all’agente decidere se ripiegare sui controlli propri del sistema o se negare.
- Gli eventi di accesso entrano in una coda limitata (quando è piena, vengono scartati e contati, ma non bloccano mai il sistema) e vengono inviati a blocchi; quando il server non è raggiungibile, vengono scritti in `audit-spool/`, nella directory di cache, e reinviati al recupero, scartando i più vecchi oltre il limite.
- Dipende da Jackson 2 e da Bouncy Castle (Ed25519 non esiste prima del JDK 15); poiché il sistema di destinazione porta altre versioni di queste librerie, al momento del pacchettizzazione dell’agente occorre riposizionarle con shade.

## Esempio

`plugins/grantforge-plugin-example` è un plug-in completo: il tipo `example` (database → table → column e path), i tipi di accesso select, update e all, il mascheramento delle colonne, il filtro delle righe sulle tabelle, una condizione di intervallo IP, e la configurazione url, timeout, password (il test di connessione ha successo quando la password è `example`); permette inoltre di cercare database e tabelle di esempio. Il test end-to-end full-stack lo usa per percorrere tutto il ciclo “aggiungi servizio → scrivi policy → emetti token → l’agente scarica → audit degli accessi”.
