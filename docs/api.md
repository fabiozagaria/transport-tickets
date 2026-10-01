# API locale dei ticket

Tutti gli endpoint richiedono `Authorization: Bearer <credenziale-demo>`.
Le credenziali pubbliche qui sotto servono esclusivamente per la demo locale.
Ruoli e ID vengono risolti sul server; non vengono accettati dal corpo delle richieste.

| Credenziale | Ruolo | ID utente |
| --- | --- | --- |
| `department-demo` | DEPARTMENT | `00000000-0000-0000-0000-000000000001` |
| `cut-demo` | CUT | `00000000-0000-0000-0000-000000000002` |
| `operator-demo` | OPERATOR | `00000000-0000-0000-0000-000000000003` |
| `operator2-demo` | OPERATOR | `00000000-0000-0000-0000-000000000004` |

I reparti demo sono `00000000-0000-0000-0000-000000000001` (Reparto demo)
e `00000000-0000-0000-0000-000000000002` (Radiologia demo).

| Metodo | Endpoint | Corpo form / risultato |
| --- | --- | --- |
| GET | `/api/departments` | Elenco reparti |
| GET | `/api/operators` | Elenco operatori, riservato alla CUT |
| POST | `/api/tickets` | `patientCode`, `originId`, `destinationId`, `priority`; opzionale `scheduledAt` |
| GET | `/api/tickets` | Ticket visibili all'utente |
| GET | `/api/tickets/{id}` | Ticket, storico e scadenze |
| POST | `/api/tickets/{id}/assign` | `operatorId`; CUT, anche per riassegnare |
| POST | `/api/tickets/{id}/accept` | Vuoto; operatore assegnato |
| POST | `/api/tickets/{id}/start` | Vuoto; operatore assegnato |
| POST | `/api/tickets/{id}/identify` | `patientCode`; operatore assegnato |
| POST | `/api/tickets/{id}/depart` | Vuoto; operatore assegnato |
| POST | `/api/tickets/{id}/arrive` | Vuoto; operatore assegnato |
| POST | `/api/tickets/{id}/complete` | Vuoto; operatore assegnato |

`priority` accetta `NORMAL` o `URGENT`. `scheduledAt` è un istante ISO-8601 con
fuso, ad esempio `2027-01-01T10:00:00Z`, non precedente alla creazione.
Le risposte ticket comprendono ID, codice paziente fittizio, ID dei reparti,
creatore, operatore, priorità, stato, orari, scadenze e storico degli eventi.
I campi non ancora disponibili sono `null`. I campi sconosciuti o duplicati
sono respinti e il corpo è limitato a 8 KiB.

## Esempio

Creare un ticket:

```sh
curl -i http://127.0.0.1:8080/api/tickets \
  -H 'Authorization: Bearer department-demo' \
  --data-urlencode 'patientCode=FAKE-001' \
  -d 'originId=00000000-0000-0000-0000-000000000001' \
  -d 'destinationId=00000000-0000-0000-0000-000000000002' \
  -d 'priority=URGENT'
```

La risposta è `201 Created` con `Location` e ticket JSON. Copiare il suo `id`:

```sh
TICKET_ID='inserire-id-della-risposta'
curl "http://127.0.0.1:8080/api/tickets/$TICKET_ID/assign" \
  -H 'Authorization: Bearer cut-demo' \
  -d 'operatorId=00000000-0000-0000-0000-000000000003'

curl "http://127.0.0.1:8080/api/tickets/$TICKET_ID/accept" \
  -H 'Authorization: Bearer operator-demo' -X POST
curl "http://127.0.0.1:8080/api/tickets/$TICKET_ID/start" \
  -H 'Authorization: Bearer operator-demo' -X POST
curl "http://127.0.0.1:8080/api/tickets/$TICKET_ID/identify" \
  -H 'Authorization: Bearer operator-demo' --data-urlencode 'patientCode=FAKE-001'

for action in depart arrive complete; do
  curl "http://127.0.0.1:8080/api/tickets/$TICKET_ID/$action" \
    -H 'Authorization: Bearer operator-demo' -X POST
done
```

## Errori

Formato: `{"error":"descrizione"}`. Una richiesta respinta non aggiunge eventi.

| Stato HTTP | Significato |
| --- | --- |
| 400 | Campi, UUID, priorità, orario o codice paziente non validi |
| 401 | Credenziale assente o sconosciuta |
| 403 | Ruolo non autorizzato |
| 404 | Endpoint, ticket inesistente o ticket non visibile |
| 405 | Metodo non consentito, con header `Allow` |
| 409 | Transizione non consentita o riassegnazione dopo l'inizio |
| 413 | Corpo oltre 8 KiB |
| 415 | Corpo non vuoto con formato diverso da form URL-encoded |

Il codice paziente compare solo nelle risposte autorizzate; le risposte hanno
`Cache-Control: no-store`. Le credenziali demo non sono adatte a un servizio pubblico.
