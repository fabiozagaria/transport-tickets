# Transport Tickets — API MVP locale

Progetto didattico indipendente, ispirato al flusso dei trasporti ospedalieri. Non è il codice di TapMyLife e non è un prodotto affiliato. Usare esclusivamente dati fittizi.

## Versioni del repository

Questo README descrive **`main`: API Java con server HTTP JDK e archivio in memoria**.

Il backend Spring Boot è nel branch [study/backend-jpa-snapshot](https://github.com/fabiozagaria/transport-tickets/tree/study/backend-jpa-snapshot), con [README e istruzioni propri](https://github.com/fabiozagaria/transport-tickets/blob/study/backend-jpa-snapshot/README.md): JPA/MySQL, Flyway, Redis, sessioni, CSRF, ruoli e transazioni con lock.

I comandi, le credenziali demo e i contratti dei due branch sono differenti. Per esplorare la persistenza e la sicurezza usare il branch di studio; non attribuire tali funzionalità alla versione in memoria.

## Scopo

Laboratorio backend per regole di dominio, storico dei trasporti, accesso alle risorse e concorrenza. Il prossimo traguardo del branch di studio è comprendere e verificare il flusso già presente prima di aggiungere frontend o nuove integrazioni.

## Uso dell'AI

Il progetto include codice sviluppato con strumenti AI e viene usato per studio, lettura critica e verifiche pratiche. La presenza di una funzionalità non implica che ogni parte sia già stata consolidata autonomamente.

## Classi e motivazioni

- `UserRole`: separa reparto, CUT e operatore.
- `User`: identità e ruolo, senza password. Autenticazione e hash delle password saranno aggiunti nel livello di sicurezza.
- `Department`: identifica origine e destinazione evitando stringhe libere ripetute. È un record immutabile.
- `TicketStatus`: insieme degli stati validi; l'enum non controlla da solo le transizioni.
- `TicketPriority`: separa urgenza e avanzamento del lavoro.
- `TicketEvent`: storico immutabile dell'attore, dell'azione e dell'operatore assegnato.
- `Ticket`: dati e comportamento del singolo trasporto. Nessun setter pubblico dello stato: le modifiche passano da azioni esplicite che verificano ruolo, assegnazione e stato. Le regole sono qui per avere un modello verificabile senza Spring; `application/TicketService` orchestra già accesso e azioni tramite il repository; questa versione in memoria non offre transazioni database.

## Regole implementate

Il reparto crea; la CUT assegna o riassegna prima dell'inizio. La riassegnazione richiede una nuova accettazione e conserva la prima assegnazione. Solo l'operatore assegnato avanza il ticket. Il codice paziente errato non modifica lo stato. Più ticket possono essere accettati e iniziati: ogni ticket è indipendente.

Percorso: UNASSIGNED → ASSIGNED → ACCEPTED → STARTED → PATIENT_IDENTIFIED → IN_TRANSIT → ARRIVED → COMPLETED.

Per gli urgenti, le scadenze sono calcolate in 20 minuti dalla creazione per l'assegnazione e 20 minuti dalla prima assegnazione per il completamento. Questa è un'ipotesi didattica da verificare, non una regola ufficiale. Il ritardo non blocca l'esecuzione.

## Esecuzione

Richiede JDK 17+ (incluso `javac`). Le API usano il server HTTP del JDK, senza dipendenze esterne. Compilare con `mvn compile` oppure:

```sh
mkdir -p target/classes
javac --release 17 -d target/classes $(find src/main/java -name '*.java')
java -cp target/classes it.fabio.transport.api.TicketApi
```

Il server ascolta solo su `127.0.0.1:8080`; un'altra porta si passa come argomento. Le richieste POST usano `application/x-www-form-urlencoded`, le risposte sono JSON. Vedi [contratto ed esempi API](docs/api.md).

Il database è in memoria: al riavvio si perdono i ticket. Le credenziali sono demo, senza login reale; questa versione serve per esercitazioni locali con dati fittizi. Mancano database persistente, login e frontend. L'orario programmato è conservato, ma non applica vincoli finché non viene chiarito se indica prelievo o arrivo.

Per eseguire i test HTTP (richiede anche Python 3):

```sh
python3 scripts/test_api.py
```

## Concorrenza e sicurezza da completare

L'API ricava ruolo e identità da credenziali demo definite sul server e usa `Clock` per l'orario. Le richieste non possono impostare attore, stato o data degli eventi. La CUT vede tutti i ticket, il reparto vede quelli creati dalla propria identità e l'operatore soltanto quelli assegnati a lui. Questa demo non implementa una separazione delle organizzazioni.

L'API serializza modifiche e letture per produrre snapshot coerenti, evitando gare tra inizio e riassegnazione nella singola istanza. La classe di dominio rimane priva di sincronizzazione propria. Il futuro database dovrà usare transazioni e controllo della versione o lock, anche tra più istanze.

## Fuori dalla prima versione

Ritorni collegati, scanner, modalità di trasporto, integrazioni, notifiche e separazione delle organizzazioni. Per il ritorno è già concordato che lo sblocco manuale richiede l'andata completata.

## Struttura delle responsabilità

- `api/TicketApi`: controller HTTP e avvio del server; legge richieste e invia risposte.
- `api/TicketPresenter` e `Json`: trasformano i risultati in risposte JSON.
- `application/TicketService`: coordina creazione, lettura e azioni; verifica ruolo e visibilità senza dipendere da HTTP.
- `application/CreateTicket`: dati della richiesta di creazione già convertiti in tipi Java.
- `application/TicketView`: copia immutabile del ticket e dello storico.
- `application/TicketRepository`: contratto delle operazioni atomiche sull'archivio.
- `infrastructure/InMemoryTicketRepository`: archivio in memoria con un lock per istanza.
- `application/DemoDirectory`: identità e reparti della demo.
- `domain/Ticket`: regole, transizioni, scadenze e storico del trasporto.

Percorso di un'azione: richiesta HTTP → controller → service → repository → `Ticket`.
Il repository mantiene il lock durante controllo dell'accesso, modifica e copia dei dati.
Il controller legge il corpo della richiesta e invia la risposta fuori dal lock.
Il service ricontrolla l'accesso durante la modifica: una riassegnazione potrebbe
avvenire dopo la prima lettura del controller.

Rimane un lock condiviso fra tutti i ticket della singola istanza. L'interfaccia
repository richiede che i callback restituiscano dati separati dall'oggetto mutabile;
il service restituisce esclusivamente `TicketView`. Il lock non implementa rollback
né coordina più server: la futura persistenza richiederà vere transazioni.

I controlli Java in `src/test/java` verificano il service senza HTTP, inclusa
l'immutabilità delle copie e la perdita dell'accesso dopo una riassegnazione.
Lo script Python esegue anche i test HTTP del contratto esistente.
