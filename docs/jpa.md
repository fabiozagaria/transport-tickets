# Come funziona JPA nel progetto

Il percorso resta: controller → `TicketService` → `JpaTicketRepository` → dominio
`Ticket` → database. Hibernate gestisce la persistenza; le regole restano nel dominio.

## Entità e snapshot

`TicketRow` e `TicketEventRow` descrivono tabelle e relazioni. Non vengono esposte
nell'API: `TicketView` è uno snapshot immutabile. `TicketRowMapper.read` riproduce
gli eventi e confronta il risultato con le colonne del ticket, rifiutando storici
incompleti o incoerenti. I nomi storici restano indipendenti dal catalogo corrente.

Le relazioni `LAZY` vengono risolte nei mapper dentro la transazione. Open Session
in View è disabilitato: dopo la transazione circolano solo oggetti indipendenti da JPA.

## Transazioni e concorrenza

`TransactionTemplate.execute` racchiude caricamento, controllo dei permessi,
transizione e aggiornamento. Un'eccezione runtime annulla stato e storico.
`saveAndFlush` salva un nuovo ticket. Sulle modifiche, Hibernate rileva i cambiamenti
dell'entità già gestita. `flush` invia SQL, ma non conclude la transazione: un errore
successivo può ancora provocare rollback.

`findForUpdate` usa `PESSIMISTIC_WRITE`: richieste sullo stesso ticket si attendono
anche fra API separate. Non blocca tutti i ticket come `synchronized (tickets)`.
Il service ricontrolla i permessi nel callback dopo il lock: una riassegnazione
precedente impedisce al vecchio operatore di avanzare il ticket.

`@Version` include la revisione nella condizione di aggiornamento e la incrementa.
`event_count` cambia anche se una nuova assegnazione lascia identici stato e operatore:
ciascun evento genera quindi una revisione. Il conflitto ottimistico produce 409;
guasti e timeout dell'archivio producono 503.

Un'alternativa è usare solo locking ottimistico: meno attesa preventiva, ma una
richiesta può fallire al commit e deve ricaricare il ticket. Qui il lock pessimista
mantiene il comportamento delle gare già testate. Non ritentiamo automaticamente
azioni utente che potrebbero non essere più consentite.

## Cache e migrazioni

La cache contiene ID e revisione nella chiave. Una modifica rende inutilizzate le
chiavi precedenti; queste scadono dopo cinque minuti. La revisione viene prima
letta in MySQL e Redis viene consultato fuori dalle transazioni. Su un miss, ticket
e storico sono caricati in uno snapshot REPEATABLE READ.

Cancellare una chiave fissa dopo il commit sarebbe più semplice, ma una lettura
concorrente potrebbe reinserire dati vecchi dopo la cancellazione. Le chiavi
versionate evitano questa gara. MySQL resta necessario anche su un cache hit.

Flyway aggiorna lo schema prima della validazione Hibernate. `ddl-auto=update`
non fornirebbe un'importazione verificabile dei payload binari. V3 importa lo
storico validato in una transazione; V4 rinomina la tabella originale come archivio.
Il repository JDBC precedente resta solo fra le fixture di test per generare dati
legacy. I repository dell'applicazione sono JPA.

I test storage avviano due contesti Spring distinti su MySQL temporaneo e verificano
migrazione fallita senza importazioni parziali, nanosecondi, archivio, riavvio,
rollback, cache guasta, doppia accettazione e inizio/riassegnazione nei due ordini.
