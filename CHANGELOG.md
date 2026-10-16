# Changelog

## v1.1.4

Prima versione pubblica di **AmmiraTv**, lettore IPTV per Android: telefono, tablet, Android TV e Fire TV.
Il progetto non include né playlist né contenuti: si usano le proprie sorgenti M3U o Xtream.

### Novità
- **Nuovo nome e logo** AmmiraTv, in app e nelle icone (telefono, banner TV).
- **Fonte ufficiale RaiPlay**: per Rai 1, 2, 3, 4, Movie, Premium, Storia, News 24 e Sport usa RaiPlay come prima scelta
  (gratuito, solo con IP italiano) e passa alla playlist se non è disponibile.
- **Più server (DNS) per playlist**: si inseriscono più indirizzi, l'app sceglie il più veloce che funziona e,
  se un canale non parte, prova gli altri server.
- **VPN integrata** in Impostazioni: WireGuard dentro l'app (solo il traffico di AmmiraTv) e OpenVPN tramite
  l'app OpenVPN installata (NordVPN, ProtonVPN, Surfshark...). Non è incluso nessun servizio VPN.
- **Riproduzione in background** attivabile e disattivabile (telefono e TV).
- **Pulsante Schermo intero** su tutti i dispositivi; su TV un solo OK mostra la barra dei controlli e
  su/giù cambiano canale senza uscirne.
- **Tastiera su TV solo quando serve**: i campi dei moduli si aprono con OK e le frecce non fanno più apparire la tastiera.
- **Numerazione canali stile TV** per i canali italiani (database canonico, digitazione dal telecomando, tastierino a video).
- **Guida TV (EPG)**, film e serie (VOD), ricerca, registrazioni, multi-view, profili con PIN genitori.
- **Layout adattivi**: barra in basso su telefono, rail su tablet, menu a tile su TV; testi lunghi scorrevoli con "Mostra tutto".
- **Variante Fire TV**: variante `amazon` solo orizzontale e solo icona TV (`LEANBACK_LAUNCHER`) per la riga "App e giochi".

### Correzioni
- Chiusura della finestra del QR "Importa dal telefono" (il server locale non si fermava).
- Contorno di selezione ora visibile su tutti i pulsanti e tasto OK che attiva una sola volta.
- Crash su Fire TV per le playlist grandi (limite di variabili SQLite sulle versioni Android < 11) e lentezza dovuta
  all'abbinamento automatico della guida e alla lista canali.
- Importazione Xtream e M3U in streaming (meno memoria), guida non più cancellata se il download si interrompe.
- Schermate Sorgenti, Impostazioni, Aggiungi sorgente e dettaglio film adattate al telefono in verticale.
- Barra dei controlli e pannello OK raggiungibili col D-pad.

### Da sapere
- L'APK è firmato con la chiave di debug: va bene per l'installazione manuale, non per uno store.
- Il pacchetto è `com.ammiratv`: si installa accanto alla vecchia app PirateTV e non la aggiorna.
- RaiPlay richiede un IP italiano. Mediaset Infinity non è supportato (login e DRM).
- La riproduzione RaiPlay e il comportamento su Fire TV non sono stati verificati su un dispositivo reale.