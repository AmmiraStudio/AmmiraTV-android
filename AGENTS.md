# AGENTS.md — AmmiraTv per Android

Fonte di verità per chiunque lavori sul repository (persone e agenti).
**Si legge per intero prima di toccare qualsiasi file e si aggiorna in ogni PR che cambia le regole o lo stato del progetto.**

**Non è infallibile.** Può essere rimasto indietro. Quando il file e la realtà (codice, git, issue, produzione) non coincidono, vince la realtà e il file va corretto.

Ultimo aggiornamento: 2026-10-20 (primo AGENTS.md: descrizione del progetto, regole, stato iniziale).

---

## 0. Cosa è questo progetto

**AmmiraTv** è un lettore IPTV per **telefono, tablet, Android TV e Fire TV**. È l'app di riferimento: le versioni [Windows](https://github.com/AmmiraStudio/ammiratv-windows) e [Apple](https://github.com/AmmiraStudio/ammiratv-apple) sono porting di questo codice. Una funzione nuova nasce qui.

| Cosa | Dove |
|---|---|
| Codice | Gradle multi-modulo: `app`, `core:*` (model, common, ui, player, network, database, datastore, testing, numbering, vpn), `domain`, `data`, `feature:*` (live, epg, vod, search, multiview, settings). Kotlin 2.1.21, AGP 8.9.2, Compose (BOM 2025.05.01), Media3 1.6.1, Hilt 2.56.2, Room, DataStore |
| Versioni librerie | `gradle/libs.versions.toml` |
| Identificativo | `com.ammiratv`, minSdk 21, targetSdk/compileSdk 35, Java 17 |
| Flavor di distribuzione | `play`, `amazon`, `sideload` (`BuildConfig.STORE`); oggi identici nel codice. `app/src/amazon` ha il manifest per Fire TV (solo orizzontale, icona `LEANBACK_LAUNCHER`) |
| Test | `gradlew.bat test` (22 file di test unitari: parser M3U/XMLTV/Xtream, numerazione, EPG, failover, salute stream, controllo parentale, multi-view, ecc.) |
| Architettura multi-dispositivo | `MULTI_DEVICE.md`: il tipo di dispositivo si legge in un solo punto, `core/ui/.../device/DeviceProfile.kt` |
| Numerazione canali | `numerazione_canali_revisione.md` e modulo `core:numbering` |
| Produzione | nessun backend. APK installato a mano (sideload) |

`.gitignore` esclude APK/AAB, `build/`, `.gradle/`, `.kotlin/`, keystore (`*.jks`, `*.keystore`), `local.properties`, `google-services.json`, la cartella `share/` e i PNG nella radice. Non è cosa da tracciare.

### Principi di prodotto
- **Le playlist le mette l'utente.** L'app non include, non indica e non scarica playlist di terzi. Sorgenti: M3U e Xtream. Unica fonte inclusa: RaiPlay ufficiale per i canali Rai (prima scelta, solo con IP italiano), con ripiego sulla playlist. Mediaset Infinity non è supportato (login e DRM).
- **Tutto sul dispositivo**: playlist, preferiti e cronologia restano locali. Nessun account, nessun backend, nessuna telemetria.
- **Nessun paywall, mai. Nessuna limitazione artificiale.** Niente limiti a playlist, canali, preferiti o profili; niente pubblicità, banner o tracciamento; niente attivazione legata a dispositivo o account; niente Google Play Services, Firebase, Cast o Play Billing (oggi non ci sono e l'app gira anche su Fire OS).
- **Un'unica app, UI adattiva**: barra in basso su telefono, rail su tablet, tile focusabili su TV; D-pad e touch.
- **VPN integrata, sempre opzionale.** WireGuard dentro l'app (solo il traffico di AmmiraTv) e OpenVPN tramite l'app OpenVPN installata. Non è incluso nessun servizio VPN. Le configurazioni (contengono chiavi private) non vanno mai nei log né fuori dal dispositivo.
- **Si adatta all'hardware**: `DeviceProfile` (RAM bassa, decoder hardware, riduci animazioni). Su hardware debole zapping, guida e VPN devono comunque funzionare.

---

## 1. Regole

### Non si fa
1. **Segreti in chat, nei file tracciati o nei commit.** Keystore, `local.properties` e simili restano fuori dal repo.
2. **Scrivere direttamente su `main` o fare force-push.** Si lavora su un branch, si apre un PR, si mergia dal PR.
3. **Fare azioni difficili da annullare o visibili all'esterno senza conferma**: pubblicazione su store, cancellare dati o branch altrui, invio di messaggi. L'approvazione data una volta non vale per la volta dopo.
4. **Mettere nel repo, nei test o nei dati di esempio URL di playlist o stream pirata**, né credenziali di servizi IPTV. Per i test si usano playlist finte o sorgenti legali.
5. **Codificare sorgenti di contenuti non ufficiali.** Le sorgenti incluse di serie sono solo quelle legali, in un elenco dichiarato.
6. **Introdurre paywall, limiti artificiali, pubblicità, tracciamento o attivazioni**, nemmeno temporanei. Un limite tecnico reale (memoria, rete) si documenta come tale.
7. **Configurazioni, chiavi o credenziali VPN nel repo, nei test o nei log.**
8. **Aggiungere dipendenze da Google Play Services, Firebase, Cast o billing**: romperebbero Fire TV.
9. **Cambiare nome pacchetto, struttura dello schema Room o numerazione senza una migrazione.** Gli aggiornamenti passano da `AutoMigration`; solo il downgrade può azzerare i dati. Mai cancellare preferiti o progressi per una migrazione mancante.

### Si fa sempre
- Leggere questo file e controllare `origin` prima di ogni modifica.
- Leggere un file prima di modificarlo.
- Provare il cambiamento su emulatore o dispositivo reale, non solo con i test, e anche su un'immagine **Android TV** quando la parte toccata ha UI o focus.
- Far passare ogni accesso alla rete dal client comune (`core:network`). Nessun client HTTP o socket aperto "per conto suo".
- Pensare **anche agli altri client**: una funzione o un cambiamento di comportamento va segnalato nel PR perché Windows e Apple vanno allineati (vedi sezione 4).
- Aggiornare questo file come **ultimo commit** del lavoro, prima del push.

### Si avvisa, ma la scelta è di chi sviluppa
- Dipendenze nuove pesanti o con licenze incompatibili con la distribuzione.
- Aggiornamenti di Kotlin, Compose, AGP o Media3: i plugin Kotlin devono restare allineati (vedi `libs.versions.toml`). Il porting Windows usa lo stesso Kotlin (2.1.21).

---

## 2. Inizio sessione

```bash
git fetch --prune origin
git status -sb
git log --oneline HEAD..origin/main
git checkout main && git pull --ff-only origin main
gh pr list --state open
gh issue list --state open
```

Poi leggi questo file, **confrontalo con le altre fonti (sezione 3)** e crea un branch da `main` aggiornato: `tipo/descrizione-breve`. Tipi: `feat/`, `fix/`, `docs/`, `chore/`.

Ambiente: `gradle.properties` punta a un JDK di Android Studio con un percorso Windows (`org.gradle.java.home`). Su un'altra macchina quel percorso va adattato **senza** committare la modifica.

---

## 3. Non fidarti ciecamente di questo file

| Fonte | Comando | Cosa controlli |
|---|---|---|
| Storia di `main` | `git log --oneline -30 origin/main` | Commit dopo l'ultimo aggiornamento del file |
| PR chiusi | `gh pr list --state merged --limit 15` | Cosa è stato mergiato e il file non dice |
| Issue | `gh issue list --state all --limit 30` | I "problemi noti" sono ancora aperti? Ce ne sono di nuovi? |
| Codice | `ls`, `grep` | Moduli e funzioni che esistono davvero |
| Versione | `app/build.gradle.kts` | `versionName` / `versionCode` attuali |
| Branch e bozze | `gh pr list --state open --draft`, `git branch -r` | Lavori in corso di altri |

Se trovi qualcosa che il file non dice o dice male, **lo correggi** nell'ultimo commit del tuo lavoro. Se la differenza è grande o non sei sicuro di cosa sia voluto, chiedi invece di inventare. Un comportamento che sembra strano non si "corregge" per adeguarlo al file: prima si capisce perché è così (log di git, PR, issue).

---

## 4. Come si lavora

- **Piccolo e mirato**: un tema per PR, niente refactoring mescolato a funzioni nuove.
- **Un branch per tema, non uno per commit.** Prima di crearne uno controlla se ne esiste già uno aperto sullo stesso tema. Il PR si apre a tema finito (in bozza se dura più giorni).
- **Ordine di chiusura**: 1) finisci e provi; 2) aggiorni questo file e lo committi per ultimo; 3) `git push` e `gh pr create`.
- **Prima di aprire il PR**: `git fetch origin`; se `main` è avanzato, `git merge origin/main` e risolvi i conflitti **leggendoli**.
- **Commit**: in italiano, descrittivi, spiegano il perché.
- **PR**: base `main`. La descrizione dice cosa cambia, come è stato verificato, se serve una migrazione dei dati locali e **cosa devono recepire Windows e Apple**. Autore come assignee (`--assignee @me`).
- **Dopo il merge** si cancella il branch, remoto e locale.
- **File aggiunti per nome**, mai `git add -A` (rischio di tracciare APK, keystore o file locali).
- **Stile del codice**: segui quello che c'è, commenti compresi. Nessuna dipendenza nuova senza motivo scritto nel PR.
- **Prima il design, poi il codice** per le funzioni nuove: spec scritta in `docs/superpowers/specs/` e piano, approvati.
- **Moduli puri**: `core:model`, `domain` e simili non importano API Android. Il porting Windows li riusa quasi invariati, quindi tenerli puliti ha un costo zero oggi e un valore alto.

---

## 5. Verifiche prima del PR

1. Compilazione e test: `gradlew.bat test` e una build dei flavor toccati (`assemblePlayDebug`, `assembleAmazonDebug`, `assembleSideloadDebug`).
2. **Prova reale**: avvia l'app su emulatore o dispositivo con i log aperti e usa la parte toccata. Casi limite: playlist vuota o malformata, stream che non risponde, EPG mancante, nessuna rete.
3. **Touch e D-pad**: la parte toccata funziona su telefono/tablet (touch) e su TV (D-pad); focus visibile e ordinato, nessuna trappola del focus, tastiera su TV solo quando serve.
4. **Layout**: telefono verticale, tablet/finestra larga, TV. Testi lunghi gestiti con `ScrollingText` / `ExpandableText` (vedi `MULTI_DEVICE.md`).
5. **Hardware debole**: prova con RAM bassa (emulatore Android TV o box vecchio). Zapping, guida e VPN senza blocchi né chiusure per memoria.
6. **Se tocchi rete o VPN**: con tunnel attivo verifica IP pubblico e DNS e che nulla esca fuori dal tunnel; fai cadere il tunnel a stream in corso; prova il cambio di rete.
7. **Se tocchi Room**: schema esportato e migrazione verificata su un database di una versione precedente.

Un test verde non dimostra che funzioni: dimostra solo che i test passano.

---

## 6. Deploy

Oggi solo APK sideload (ADB, app Downloader). Non è pubblicata su Play né su Amazon Appstore.

1. Annota come tornare indietro (versione attuale, SHA di `main`) **prima** di pubblicare.
2. La build release è firmata con la chiave di debug (solo per prove). Per uno store serve una chiave vera, **fuori dal repo**, e va sostituita in `app/build.gradle.kts` prima di pubblicare.
3. Dopo la pubblicazione installa l'artefatto pubblicato, non basta l'esito della build.
4. Per il codice il rollback è `git revert` su un branch e un PR, mai force-push.
5. Amazon Appstore: i requisiti di pubblicazione non sono stati verificati (vedi `MULTI_DEVICE.md`). Fire OS con Vega OS non è supportato.

---

## 7. Limiti di servizi esterni e risorse

| Risorsa | Limite / rischio | Come si evita |
|---|---|---|
| Stream e server delle playlist dell'utente | Alcuni provider limitano le connessioni contemporanee | Una sola connessione attiva; limite connessioni (`ConnectionBudget`); niente precaricamento aggressivo |
| Guida EPG (XMLTV) | File grandi, anche decine di MB | Scaricare e analizzare fuori dal thread UI, con cache locale; non cancellare la guida se il download si interrompe |
| Playlist grandi (50 000+ canali) | Limite di variabili SQLite su Android < 11, memoria | Importazione M3U/Xtream in streaming, query a blocchi |
| RaiPlay | Solo IP italiano | Ripiego automatico sulla playlist |

Ogni scrittura o chiamata periodica nuova si **stima al giorno** prima di aggiungerla.

---

## 8. Errori già fatti (non ripeterli)

| Cosa è successo | Regola |
|---|---|
| Crash su Fire TV con playlist grandi (limite variabili SQLite su Android < 11) | Le query con liste lunghe vanno a blocchi; provare su RAM bassa e API vecchia |
| Server locale "Importa dal telefono" non si fermava alla chiusura della finestra QR | Ogni server o risorsa aperta ha una chiusura esplicita |
| Guida cancellata quando il download si interrompeva | Si sostituisce la guida solo a download completato |

Un errore fatto due volte entra qui.

---

## 9. Stato attuale

Data: **2026-10-20**. Il repository ha un solo commit (`chore: initial import of AmmiraTv`). Il changelog dice v1.1.4 come prima versione pubblica; `app/build.gradle.kts` riporta ancora `versionName = "0.1.0"` / `versionCode = 1`: va verificato quale è giusto.

**Funziona** (da riverificare)
- Secondo il changelog 1.1.4: Live con zapping e numerazione canali, EPG, VOD, ricerca, registrazioni, multi-view, profili con PIN, VPN WireGuard/OpenVPN, fonte RaiPlay, layout adattivi, variante Fire TV.

**Problemi noti**

| # | Cosa |
|---|---|
| 1 | Versione in `build.gradle.kts` (0.1.0) non allineata al changelog (1.1.4) |
| 2 | Riproduzione RaiPlay e Fire TV non verificate su dispositivo reale (da changelog) |
| 3 | `CHANGELOG.md` non è nel repository (c'è una copia in `docs/android` del progetto Windows) |
| 4 | Build release firmata con chiave di debug |

**Decisioni in sospeso**
- Politica di merge dei PR e righe di attribuzione nei commit.
- Pubblicazione su Play / Amazon Appstore e relativa chiave di firma.
- Come comunicare ai porting Windows e Apple le novità di questa app.

---

## 10. Chi fa cosa (ignora per ora)

| Persona | Ruolo |
|---|---|
| DisPaisy | Responsabile del progetto |

---

## 11. Come tenere aggiornato questo file

- Ogni PR controlla questo file prima del push, anche contro il codice.
- È l'**ultimo commit** del branch. Messaggio: `AGENTS.md: <cosa cambia nello stato>`.
- Un problema nuovo si apre come issue **e** si aggiunge ai problemi noti.
- Non creare documenti sparsi: una regola o un fatto sul progetto sta qui. Le spec di design stanno in `docs/superpowers/specs/`.
- `CLAUDE.md` contiene solo la riga `@AGENTS.md` e non si modifica.
