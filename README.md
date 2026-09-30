# Transport Tickets — modello MVP

Progetto didattico indipendente, ispirato al flusso dei trasporti ospedalieri. Non è il codice di TapMyLife e non è un prodotto affiliato. Usare esclusivamente dati fittizi.

## Classi e motivazioni

- `UserRole`: separa reparto, CUT e operatore.
- `User`: identità e ruolo, senza password. Autenticazione e hash delle password saranno aggiunti nel livello di sicurezza.
- `Department`: identifica origine e destinazione evitando stringhe libere ripetute. È un record immutabile.
- `TicketStatus`: insieme degli stati validi; l'enum non controlla da solo le transizioni.
- `TicketPriority`: separa urgenza e avanzamento del lavoro.
- `TicketEvent`: storico immutabile dell'attore, dell'azione e dell'operatore assegnato.
- `Ticket`: dati e comportamento del singolo trasporto. Nessun setter pubblico dello stato: le modifiche passano da azioni esplicite che verificano ruolo, assegnazione e stato. Le regole sono qui per avere un modello verificabile senza Spring; un futuro service orchestrerà accesso, transazioni e persistenza.

## Regole implementate

Il reparto crea; la CUT assegna o riassegna prima dell'inizio. La riassegnazione richiede una nuova accettazione e conserva la prima assegnazione. Solo l'operatore assegnato avanza il ticket. Il codice paziente errato non modifica lo stato. Più ticket possono essere accettati e iniziati: ogni ticket è indipendente.

Percorso: UNASSIGNED → ASSIGNED → ACCEPTED → STARTED → PATIENT_IDENTIFIED → IN_TRANSIT → ARRIVED → COMPLETED.

Per gli urgenti, le scadenze sono calcolate in 20 minuti dalla creazione per l'assegnazione e 20 minuti dalla prima assegnazione per il completamento. Questa è un'ipotesi didattica da verificare, non una regola ufficiale. Il ritardo non blocca l'esecuzione.

## Esecuzione

Richiede JDK 17+ e Maven. Compilare con `mvn compile`.

Questa versione contiene il dominio, non un server avviabile. Mancano API, database, login e frontend. L'orario programmato è conservato, ma non applica vincoli finché non viene chiarito se indica prelievo o arrivo.

## Concorrenza e sicurezza da completare

Il chiamante deve fornire identità e orario autorevoli dal server, mai fidarsi dei valori inviati dal frontend. Questa classe non gestisce chiamate concorrenti: il futuro livello di persistenza dovrà usare una transazione con controllo della versione o lock, per far prevalere una sola modifica tra inizio e riassegnazione.

## Fuori dalla prima versione

Ritorni collegati, scanner, modalità di trasporto, integrazioni, notifiche e separazione delle organizzazioni. Per il ritorno è già concordato che lo sblocco manuale richiede l'andata completata.
