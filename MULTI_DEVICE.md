# AmmiraTv su telefono, tablet, Android TV e Fire TV

## Architettura scelta
Un solo codice, nessun modulo app separato. Il tipo di dispositivo si legge in **un solo punto**:
`core/ui/.../device/DeviceProfile.kt` (`LocalDeviceProfile`: `formFactor` PHONE/TABLET/TV/FIRE_TV, `hasTouch`,
`lowRam`, `maxVideoDecoders`, `reduceMotion`) più `LocalWidthClass` (COMPACT <600dp, MEDIUM <840dp, EXPANDED).

| Dispositivo | Navigazione | Layout |
|---|---|---|
| Telefono | barra in basso (`AdaptiveNavigation`) | lista a 1 colonna, player verticale: video sopra, info sotto |
| Tablet / finestra larga | rail laterale | lista a più colonne (`GridCells.Adaptive(340dp)`) |
| Android TV / Fire TV | 4 tile focusabili nella schermata Live | come prima, focus visibile |

La barra/rail sparisce durante la riproduzione a schermo intero e in PiP (`PlaybackChrome`).

## Testi lunghi
- `ScrollingText` (una riga): marquee. TV: parte col focus; telefono/tablet: automatico lento con pause; RAM bassa: solo con
  focus; "riduci animazioni": niente marquee, il testo va a capo fino a 3 righe.
- `ExpandableText` + `LongTextDialog` (più righe): "Mostra tutto" apre il testo intero, scorrevole con dito o su/giù del D-pad;
  a fine testo il focus esce verso "Chiudi" (nessuna trappola).
- Usati in: lista canali, guida EPG (celle, banner), QuickPanel, VOD (home, dettaglio, episodi, player), ricerca, "Stasera",
  registrazioni, multi-view, player live, numerazione.
- Fixture: Impostazioni → "Test testi lunghi".

## Fire TV (verificato sulla documentazione Amazon)
- Fire OS ↔ API: Fire OS 5 = API 22, 6 = API 25, 7 = API 28, 8 = API 30, 14 = API 34, 16 = API 36
  ([Fire OS overview](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html),
  [Fire OS 16](https://developer.amazon.com/docs/fire-tv/fire-os-16.html)).
- `minSdk = 21` copre già anche le chiavette più vecchie (Fire OS 5 = API 22). Non serve abbassarlo.
- Alcune chiavette 2025/2026 usano Vega OS (non Android): questo APK **non** gira lì.
- Nessuna dipendenza da Google Play Services, Firebase, Cast o Play Billing: nel progetto non ci sono.
- Flavor `play`, `amazon`, `sideload` (`BuildConfig.STORE`): oggi identici nel codice; servono a isolare in futuro parti
  legate allo store.
- Hardware debole: cache immagini 8% RAM + RGB565 e disco 50 MB se `isLowRamDevice`; multi-view limitato ai decoder
  hardware H.264 rilevati con `MediaCodecList` (max 2 su RAM bassa).
- Telecomandi senza numeri: pulsante **"Numeri"** nella barra del player apre un tastierino a video.
- Watch Next / Assistant non sono usati dall'app, quindi non c'è nulla da disattivare.

### Pubblicazione
Per ora solo APK sideload (richiesta). Prima di un eventuale Amazon Appstore: nelle ricerche fatte **non ho trovato** una
regola specifica su app IPTV; vanno lette la
[Content Policy](https://developer.amazon.com/docs/policy-center/understanding-content-policy.html) e i
[criteri di test](https://developer.amazon.com/docs/app-testing/test-criteria.html) e chiesto al supporto Amazon.
Nota ufficiale: le app che permettono di registrare/scaricare video non vengono distribuite in Germania e Austria.

### Debug su Fire TV
1. Impostazioni → Il mio Fire TV → Informazioni → clicca 7 volte sul nome del dispositivo (modalità sviluppatore).
2. Impostazioni → Il mio Fire TV → Opzioni sviluppatore → **Debug ADB** = Sì (e "App da fonti sconosciute" per sideload).
3. Impostazioni → Informazioni → Rete: annota l'indirizzo IP.
4. Sul PC: `adb connect <IP>:5555` (conferma il popup sulla TV), poi `adb devices`.
5. `gradlew assembleSideloadDebug` → `adb install -r app\build\outputs\apk\sideload\debug\app-sideload-debug.apk`.
6. Log: `adb logcat -s AndroidRuntime:E StrictMode:W` (StrictMode scrive solo nelle build debug).

## Altro introdotto
- Player: focus audio, stop con cuffie scollegate, riconnessione automatica (3 tentativi 1-2-4 s, e subito al ritorno
  della rete), rientro al live dopo "behind live window", nessuna lettura bloccante del DataStore sul main thread.
- Riproduzione in background: `PlaybackService` (MediaSession + notifica, tipo `mediaPlayback`), tasti cuffie/Bluetooth.
- PiP con rapporto 16:9; modalità immersiva nel player; `configChanges` anche per uiMode/fontScale/density;
  predictive back abilitato.
- Touch nel player: tap = controlli, swipe su/giù = cambia canale, barra VOD cercabile al tocco.
- Debug: StrictMode (solo log) e LeakCanary.

## Cosa NON ho potuto verificare / non è fatto
- Nessun dispositivo reale: provato solo su emulatore Pixel Fold (finestra larga) e su una dimensione "telefono" simulata
  con `wm size` + font 2.0. Fire TV Stick, Android TV fisico, telefono fisico: **non provati**.
- Riproduzione in background (`PlaybackService`), riconnessione automatica, PiP, tastierino a video, gesture touch del
  player: compilano e la build release R8 si installa e si avvia, ma **non li ho esercitati a runtime** (l'emulatore si è
  chiuso e non sono riuscito a riavviarlo). Vanno provati.
- Tablet in split-screen, pieghevole in piega, tastiera/mouse, controller: logica pronta, non provati.
- Baseline Profile / Macrobenchmark: non fatti (servono modulo dedicato e dispositivo).
- Crash reporting opzionale: non aggiunto (richiederebbe un servizio esterno).
- Test automatici UI Compose e screenshot test: non scritti; ci sono solo i test unitari già presenti (nessuno nuovo per
  `DeviceProfile`, che dipende da `Context`).
- Controllo vocale Alexa: non fatto.
- Limite RAM/ora di riproduzione su Fire TV Stick (1 h): da misurare sul dispositivo.
- Stato dopo morte del processo: il canale live in riproduzione non viene ripristinato (solo VOD usa `SavedStateHandle`).

## Matrice di test manuale (per ogni dispositivo)
Installazione · primo avvio · aggiunta playlist · zapping su/giù e per numero · EPG (focus, "Mostra tutto") · overlay OK ·
VOD (dettaglio, riprendi) · registrazione · PiP / Home con audio · rotazione (telefono) · font 1.0/1.3/2.0 ·
Wi-Fi spento/acceso durante la riproduzione · chiusura forzata e riavvio · Impostazioni → "Test testi lunghi".
