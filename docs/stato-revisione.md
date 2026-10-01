# Stato salvato per lo studio del codice

Questo snapshot raccoglie il backend Spring Boot/Security, Docker con MySQL e
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

## Difetto noto da correggere prima del rilascio

`TicketService.list` usa `null` per i ticket non visibili. Il repository JPA
`readAll` non elimina questi valori, mentre i repository precedenti lo facevano.
Il controller può quindi fallire presentando la lista quando contiene ticket
non visibili. Serve filtrare i valori null e aggiungere un test con ticket
visibili e non visibili sul repository JPA. La correzione non è inclusa nello
snapshot: è conservato lo stato discusso durante la revisione.

## Verifiche già eseguite

- Dieci test JUnit passati: dominio, codec, sicurezza e HTTP servlet.
- Test con MySQL/Redis e due contesti Spring: migrazione, rollback, nanosecondi,
  archivio, cache, doppia accettazione e gare inizio/riassegnazione.
- Test HTTP sul container aggiornato: login persistente, CSRF, permessi, ciclo e logout.
- Migrazione locale dei nove ticket preesistenti, con backup e archivio conservati.

Queste verifiche non coprivano il difetto delle liste descritto sopra.

## Tempo reale: discusso, non implementato

La proposta è SSE per notificare il browser, mantenendo i POST per le azioni.
Una outbox transazionale potrebbe registrare le notifiche insieme alle modifiche,
per poi pubblicarle dopo il commit e recuperare eventuali crash.
Restano da progettare riconnessione, recupero degli eventi, duplicati, ordine,
più istanze, revoca delle sessioni e riassegnazioni. Redis attualmente è una cache,
non un sistema di notifiche. Non sono presenti endpoint SSE, WebSocket o outbox.
