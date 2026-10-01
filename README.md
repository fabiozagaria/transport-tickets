# Transport Tickets — backend Spring Boot

Progetto personale di studio, nato dall’uso quotidiano di un software per la
gestione dei trasporti ospedalieri nel mio lavoro. L’obiettivo è provare a
ricrearne i flussi principali in un’implementazione indipendente, per capire
come strutturare il codice e approfondire le tecnologie utilizzate.

Il software che utilizzo al lavoro è una fonte di ispirazione funzionale;
questo progetto non è affiliato a TapMyLife. Usare esclusivamente dati fittizi.

## Avvio

Con Docker e Compose:

```sh
docker compose up --build -d
docker compose logs api
docker compose down
```

L'API è disponibile su `http://127.0.0.1:8080`. Compose avvia MySQL 8.4 e Redis 7.4;
solo la porta dell'API viene pubblicata, sul computer locale. MySQL conserva i dati
nel volume `mysql-data`. Non usare `docker compose down -v` se vuoi conservarli.

Compose abilita esplicitamente gli account demo, creati in MySQL solo se assenti:
`department-demo`, `department2-demo`, `radiology-demo`, `cut-demo`, `operator-demo`
e `operator2-demo`. Password locale: `local-demo-change-me`. Sostituirla tramite
`DEMO_PASSWORD` nel file `.env` ignorato da Git. Modificare questa variabile non
resetta le password di account già esistenti.

Fuori da Compose il bootstrap demo è disabilitato. Servono `MYSQL_URL`,
`MYSQL_USER`, `MYSQL_PASSWORD` e account provisionati in MySQL; non esiste ancora
una schermata o API di amministrazione utenti. L'avvio richiede Java 17+ e Maven:

```sh
mvn package
java -jar target/transport-tickets-0.1.0-SNAPSHOT.jar
```

## Autenticazione

Spring Security usa sessioni server, login e logout. Le vecchie credenziali
`Authorization: Bearer ...` non sono più accettate. Le password sono salvate con
hash BCrypt; i dati dell'account contengono ruolo, stato attivo e reparto.
Vedi [contratto API e login](docs/api.md).

Richiedere `GET /api/auth/csrf`, conservare il cookie di sessione e inviare il token
nell'header `X-CSRF-TOKEN` per ogni POST, incluso login e logout. Dopo il login
richiedere un nuovo token: Spring ruota l'ID di sessione e invalida il token precedente.
La sessione scade dopo 30 minuti di inattività. Il cookie è HttpOnly e SameSite=Strict;
con HTTPS configurare `SESSION_COOKIE_SECURE=true`.

Gli account vengono ricontrollati a ogni richiesta autenticata. Disabilitazione,
modifica password, ruolo o reparto invalidano la sessione alla richiesta successiva.
Le sessioni sono locali alla singola API, perse al riavvio e non condivise tramite
Redis: per più istanze servirà gestione distribuita delle sessioni o affinità del traffico.

## Regole e responsabilità

Percorso: UNASSIGNED → ASSIGNED → ACCEPTED → STARTED → PATIENT_IDENTIFIED →
IN_TRANSIT → ARRIVED → COMPLETED.

Il reparto crea soltanto con origine uguale al proprio reparto e vede i ticket
con quell'origine, anche creati da colleghi. La CUT vede tutti i ticket e assegna
o riassegna prima dell'inizio. Solo l'operatore assegnato avanza il ticket.
Una riassegnazione richiede nuova accettazione e conserva la prima assegnazione.
Il codice paziente errato non modifica stato o storico.

- `TransportApplication`: avvio Spring Boot.
- `api/TicketController`, `AuthController`, `ApiErrors`: HTTP e rappresentazioni.
- `security/SecurityConfiguration`: autenticazione, sessioni, CSRF e ruoli degli endpoint.
- `application/TicketService`: accesso al singolo ticket e coordinamento delle operazioni.
- `application/TransportDirectory`: catalogo utenti e reparti, indipendente da HTTP.
- `infrastructure/jpa/JpaUserDirectory`: account persistenti e reparti; bootstrap demo opzionale.
- `domain/Ticket`: transizioni, scadenze e storico.
- `application/TicketView`: snapshot immutabile.
- `infrastructure/jpa/JpaTicketRepository`: transazioni JPA e lock sulle righe.
- `infrastructure/RedisTicketCache`: cache opzionale delle letture singole.

Spring Security controlla il ruolo; il service ricontrolla accesso e assegnazione
nella stessa transazione della modifica. Il dominio resta indipendente da Spring.
L'orario degli eventi e gli ID sono generati dal server. La programmazione è
conservata ma non vincola il trasporto. Per URGENT le scadenze sono 20 minuti dalla
creazione per assegnare e 20 dalla prima assegnazione per completare: ipotesi
 didattica da verificare. Il ritardo non blocca il flusso.

Vedi anche [spiegazione degli approcci JPA](docs/jpa.md).

## Persistenza e cache

JPA/Hibernate salva i ticket in `ticket_records` e gli eventi ordinati in
`ticket_events`. Le entità sono separate dal dominio: il mapper ricostruisce il
ticket applicando e verificando le transizioni. Nomi storici di reparti, creatore
e operatore corrente sono snapshot; gli eventi conservano gli ID degli attori.
Secondi e nanosecondi sono colonne separate per preservare esattamente gli `Instant`.

Flyway applica migrazioni versionate; Hibernate verifica lo schema senza modificarlo.
Su un database esistente, la baseline 0 consente di partire dalle tabelle JDBC.
La migrazione V3 legge i payload v1/v2/v3, verifica lo storico e importa tutti i
ticket in una transazione. Un payload corrotto interrompe l'importazione e annulla
le righe importate. MySQL non annulla il DDL delle migrazioni precedenti: lo schema
può quindi esistere anche dopo un errore. Correggere la causa prima di eseguire un
`repair` Flyway controllato e riprovare; l'applicazione non ripara automaticamente.
Il vecchio `tickets` viene rinominato `legacy_tickets_archive`: nessun repository
dell’applicazione scrive nell’archivio. Il suo contenuto rimane disponibile per verifiche.
La versione originale è conservata in `legacy_version`; la revisione JPA parte da 0.

Per aggiornare un'installazione esistente: fare un backup del database, fermare
tutte le vecchie API (`docker compose stop api`), costruire e avviare la nuova API.
È una migrazione con fermo: non mescolare istanze JDBC e JPA. Tornare alla versione
precedente richiede ripristinare il backup, perché l'archivio non riceve nuove modifiche.

Le modifiche usano un lock pessimista sul singolo ticket, con controllo accessi,
transizione e aggiunta degli eventi nella stessa transazione. `@Version` aggiunge
il controllo della revisione. Errori del callback annullano stato e storico.
Hikari gestisce un pool di massimo 10 connessioni; Open Session in View è disabilitato.

La cache usa `tickets:jpa:v1:<id>:<revision>`, con TTL 300 secondi. Le letture
controllano prima la revisione in MySQL; una cache vecchia diventa irraggiungibile
senza cancellazioni esplicite. Il valore contiene anche la revisione e l'ID viene
verificato. Redis viene consultato fuori dalle transazioni e dai lock del database.
Su un miss, ticket e storico sono letti nello stesso snapshot REPEATABLE READ.
Una lettura concorrente può osservare lo stato precedente a una modifica, come
una normale lettura del database; le azioni ricontrollano sempre il dato sotto lock.
Cache assente, corrotta o indisponibile causa fallback; un guasto MySQL resta bloccante.

Restano paginazione e query mirate per le liste, riduzione delle letture dello storico,
osservabilità e gestione strutturata dei guasti. La cache è considerata affidabile:
un payload valido alterato con lo stesso ID e revisione non viene confrontato
integralmente con MySQL. L'importazione carica l'archivio in memoria: per archivi
grandi servirà una migrazione a blocchi progettata con la stessa atomicità.

## Configurazione e dipendenze

- `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`: connessione di ticket e account.
- `TICKET_STORAGE`: `mysql` predefinito, oppure `memory` per ticket temporanei; gli account restano in MySQL.
- `REDIS_HOST`, `REDIS_PORT`: cache opzionale, porta predefinita 6379.
- `DEMO_USERS_ENABLED`, `DEMO_PASSWORD`: bootstrap demo esplicito.
- `BIND_ADDRESS`, `PORT`: predefiniti `127.0.0.1` e 8080; Docker ascolta su `0.0.0.0`.

Spring Web sostituisce il server HTTP JDK. Spring Security gestisce password,
autenticazione, sessioni e CSRF. Spring Test e Security Test verificano controller
e filtri. Spring Data JPA fornisce repository e transazioni; Hibernate esegue il
mapping relazionale e Hikari riusa le connessioni. Flyway Core e il modulo MySQL
rendono lo schema e l'importazione ripetibili e verificabili. Il driver MySQL
resta necessario per la connessione. Non viene aggiunto H2: i test storage usano MySQL reale.

Docker usa una build Maven/JDK e un runtime JRE, con processo non root. MySQL usa
credenziali demo e connessione senza TLS nella rete interna locale; Redis non ha
ancora autenticazione o TLS. Il backend non separa organizzazioni e non ha ancora
limitazione dei tentativi di login o recupero password.

## Test

```sh
mvn test
docker compose up --build -d
python3 scripts/test_http.py
docker build --target build -t transport-tickets-checks .
sh scripts/test_storage.sh
```

JUnit verifica login, CSRF, logout, sessione invalidata, ruoli, appartenenza al
reparto, ciclo completo, riassegnazione, errori dell'archivio e regressioni del codec.
I test HTTP reali usano le sessioni e gli account MySQL del container.
I test storage usano un MySQL temporaneo separato dai dati dell’app e verificano
importazione, rollback su archivio corrotto, precisione dei tempi, conservazione dell’archivio,
riavvio/idempotenza, BCrypt e utenti persistenti, rollback, cache, ciclo,
doppia accettazione e gara inizio/riassegnazione forzando entrambi gli ordini.
