# Stato salvato per lo studio del codice

Il backend su `main` integra lo snapshot Spring Boot/Security, Docker con MySQL e
Redis, e la migrazione JPA/Hibernate. Lo sviluppo di nuove funzionalità è sospeso
finché il proprietario non avrà studiato e compreso il codice.

## Percorso di lettura

1. `domain/Ticket.java`: stati, permessi del dominio e storico.
2. `application/TicketService.java`: casi d'uso e visibilità dei ticket.
3. `application/TicketRepository.java`: contratto della persistenza.
4. `infrastructure/jpa/JpaTicketRepository.java`: transazioni, lock e cache.
5. `infrastructure/jpa/TicketRowMapper.java`: conversione e verifica dello storico.
6. `security/SecurityConfiguration.java`: login, sessioni, CSRF e ruoli.
7. `db/migration` nelle risorse e nel codice Java: schema e importazione legacy.
8. Test e `docs/jpa.md`: esempi, concorrenza e spiegazione degli approcci.

I percorsi Java sono relativi a `src/main/java/it/fabio/transport`, tranne la
migrazione Java in `src/main/java/db/migration`.

## Correzione delle liste JPA

`TicketService.list` usa `null` per i ticket non visibili. Il repository JPA
`readAll` ora elimina questi valori nella stessa transazione di lettura, prima
che la lista raggiunga il presenter del controller.

La regressione in `JpaPersistenceCheck` crea ticket persistenti di reparti e
operatori differenti. Verifica che la CUT li veda entrambi, che reparto e
operatore ricevano soltanto i propri ticket, che le liste non contengano valori
null e che il controller le presenti correttamente.

## Verifiche già eseguite

- Dieci test JUnit passati: dominio, codec, sicurezza e HTTP servlet.
- Test con MySQL/Redis e due contesti Spring: migrazione, rollback, nanosecondi,
  archivio, cache, doppia accettazione e gare inizio/riassegnazione.
- Test HTTP sul container aggiornato: login persistente, CSRF, permessi, ciclo e logout.
- Migrazione locale dei nove ticket preesistenti, con backup e archivio conservati.

Il controllo delle liste descritto sopra estende la copertura dello snapshot
precedente con la regressione su visibilità e presentazione JPA.

## Tempo reale: discusso, non implementato

La proposta è SSE per notificare il browser, mantenendo i POST per le azioni.
Una outbox transazionale potrebbe registrare le notifiche insieme alle modifiche,
per poi pubblicarle dopo il commit e recuperare eventuali crash.
Restano da progettare riconnessione, recupero degli eventi, duplicati, ordine,
più istanze, revoca delle sessioni e riassegnazioni. Redis attualmente è una cache,
non un sistema di notifiche. Non sono presenti endpoint SSE, WebSocket o outbox.
