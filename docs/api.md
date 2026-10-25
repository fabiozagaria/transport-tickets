# API e sessioni

Il backend usa Spring Security con sessioni. Le credenziali Bearer demo precedenti
non funzionano più. Conservare il cookie `JSESSIONID` fra le richieste.

## Login con curl

Richiede Python 3 per estrarre il token JSON:

```sh
CSRF=$(curl -sS -c /tmp/tickets-cookies http://127.0.0.1:8080/api/auth/csrf |
  python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -sS -b /tmp/tickets-cookies -c /tmp/tickets-cookies \
  -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode 'username=department-demo' \
  --data-urlencode 'password=local-demo-change-me' \
  http://127.0.0.1:8080/api/auth/login
CSRF=$(curl -sS -b /tmp/tickets-cookies -c /tmp/tickets-cookies \
  http://127.0.0.1:8080/api/auth/csrf |
  python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -sS -b /tmp/tickets-cookies -H "X-CSRF-TOKEN: $CSRF" \
  --data-urlencode 'patientCode=FAKE-001' \
  -d 'originId=00000000-0000-0000-0000-000000000001' \
  -d 'destinationId=00000000-0000-0000-0000-000000000002' \
  -d 'priority=URGENT' http://127.0.0.1:8080/api/tickets
```

Per logout usare POST `/api/auth/logout` con cookie e token. Ogni utente ha una
sessione propria; usare un diverso cookie jar per CUT e operatori.
`GET /api/auth/me` restituisce ID, username, ruolo e reparto, senza password/hash.
Il token CSRF è accettato nell'header, non come campo del form.

## Endpoint ticket

| Metodo | Endpoint | Corpo form / risultato |
| --- | --- | --- |
| GET | `/api/departments` | Reparti, richiede login |
| GET | `/api/operators` | Operatori attivi, riservato alla CUT |
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

POST con dati usa `application/x-www-form-urlencoded`. Il corpo dei ticket ha
limite 8 KiB; campi sconosciuti e duplicati vengono respinti. `priority` accetta
`NORMAL` o `URGENT`; `scheduledAt` è un istante ISO-8601 non precedente alla creazione.
Il reparto può creare soltanto con origine uguale al proprio reparto e leggere
anche i ticket creati dai colleghi con quella stessa origine.

Account demo, solo quando il bootstrap è abilitato:

| Username | Ruolo | ID utente | Reparto |
| --- | --- | --- | --- |
| department-demo | DEPARTMENT | UUID con suffisso 001 | Reparto demo, UUID con suffisso 001 |
| department2-demo | DEPARTMENT | UUID con suffisso 005 | Reparto demo |
| radiology-demo | DEPARTMENT | UUID con suffisso 006 | Radiologia demo, UUID con suffisso 002 |
| cut-demo | CUT | UUID con suffisso 002 | Nessuno |
| operator-demo | OPERATOR | UUID con suffisso 003 | Nessuno |
| operator2-demo | OPERATOR | UUID con suffisso 004 | Nessuno |

Il prefisso degli UUID demo è `00000000-0000-0000-0000-000000000`.

## Errori

| HTTP | Significato |
| --- | --- |
| 400 | Dati, UUID, priorità, orario o codice paziente non validi |
| 401 | Login richiesto, credenziali errate o sessione revocata |
| 403 | Ruolo/reparto vietato oppure CSRF mancante o non valido |
| 404 | Endpoint o ticket inesistente/non visibile |
| 405 | Metodo non consentito |
| 409 | Transizione vietata o riassegnazione dopo l'inizio |
| 413 | Corpo oltre 8 KiB |
| 415 | Formato diverso dal form URL-encoded |
| 500 | Dati persistiti corrotti o errore di serializzazione |
| 503 | Archivio ticket/utenti indisponibile |

Gli errori applicativi usano `{"error":"..."}`, le risposte del livello sicurezza
usano `{"message":"..."}`. Una richiesta mutante senza token CSRF può ricevere
403 anche se manca l'autenticazione, perché il filtro CSRF la respinge prima.
