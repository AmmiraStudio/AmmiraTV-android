package com.ammiratv.core.network.localimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import javax.inject.Inject

/** A source typed on the phone and sent to the TV. */
data class ImportRequest(
    val type: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String
)

/**
 * Tiny HTTP server that lets a phone on the same Wi-Fi paste a source into a web form,
 * so nobody has to type long URLs with a remote control.
 *
 * Safety: it only runs while the QR screen is open, the page lives under a random token,
 * only one tiny form is served and bodies are capped at [MAX_BODY_BYTES].
 * The data travels over plain HTTP on the local network (the same as typing on the TV
 * would expose nothing, but a hostile device on the LAN could sniff it): keep it to trusted Wi-Fi.
 */
class LocalImportServer @Inject constructor() {

    /** Random path segment: `http://<tv-ip>:<port>/<token>` is the only URL that works. */
    fun newToken(): String {
        val bytes = ByteArray(9).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Opens the server and blocks (cancel the coroutine to stop it).
     * [onReady] is called with the port once the socket is bound; [onRequest] for every valid submission.
     */
    suspend fun serve(
        token: String,
        onReady: (port: Int) -> Unit,
        onRequest: (ImportRequest) -> Unit,
        /** true: serve the "paste your WireGuard configuration" form instead of the source form. */
        vpnForm: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val server = ServerSocket(0)
        kotlinx.coroutines.coroutineScope {
            // accept() cannot be cancelled: a sibling coroutine closes the socket the moment this
            // scope is cancelled, which makes accept() throw and the loop end. (Closing it from
            // invokeOnCompletion never ran: completion waits for accept() to return.)
            val closer = launch {
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    runCatching { server.close() }
                }
            }
            try {
                onReady(server.localPort)

                while (isActive) {
                    val socket = try {
                        server.accept()
                    } catch (_: IOException) {
                        break // closed on cancellation
                    }
                    socket.use { handle(it, token, vpnForm, onRequest) }
                }
            } finally {
                closer.cancel()
                runCatching { server.close() }
            }
        }
    }

    private fun handle(socket: Socket, token: String, vpnForm: Boolean, onRequest: (ImportRequest) -> Unit) {
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')

            var contentLength = 0
            while (true) {
                val header = readLine(input) ?: break
                if (header.isEmpty()) break
                if (header.startsWith("content-length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }

            val out = socket.getOutputStream()
            if (path != "/$token") {
                respond(out, 404, "Not found")
                return
            }

            when (method) {
                "GET" -> respond(out, 200, (if (vpnForm) VPN_FORM_HTML else FORM_HTML).replace("{{TOKEN}}", token))
                "POST" -> {
                    if (contentLength !in 1..MAX_BODY_BYTES) {
                        respond(out, 400, page("Richiesta non valida"))
                        return
                    }
                    val body = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(body, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    val request = parseForm(String(body, 0, read, Charsets.UTF_8))
                    if (request == null) {
                        respond(out, 400, page("Compila tutti i campi richiesti"))
                    } else {
                        onRequest(request)
                        respond(out, 200, page("Sorgente inviata alla TV. Puoi chiudere questa pagina."))
                    }
                }
                else -> respond(out, 405, "Method not allowed")
            }
        } catch (_: IOException) {
            // A phone that disconnects mid-request is not an error worth surfacing
        }
    }

    private fun respond(out: java.io.OutputStream, status: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream()
        while (buf.size() < MAX_LINE_BYTES) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
        }
        return buf.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_BODY_BYTES = 8 * 1024
        private const val MAX_LINE_BYTES = 8 * 1024

        /** Parses an `application/x-www-form-urlencoded` body; null when required fields are missing. */
        fun parseForm(body: String): ImportRequest? {
            val fields = body.split('&').mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                if (key.isEmpty()) return@mapNotNull null
                val value = pair.substringAfter('=', "")
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }.toMap()

            val type = fields["type"]?.trim()?.uppercase().orEmpty()
            if (type == "WIREGUARD") {
                val config = fields["config"]?.trim().orEmpty()
                return if (config.isEmpty()) null else ImportRequest("WIREGUARD", "", config, "", "")
            }
            val url = fields["url"]?.trim().orEmpty()
            if (url.isEmpty()) return null
            return when (type) {
                "M3U" -> ImportRequest("M3U", fields["name"].orEmpty().trim(), url, "", "")
                "XTREAM" -> {
                    val user = fields["username"]?.trim().orEmpty()
                    val pass = fields["password"]?.trim().orEmpty()
                    if (user.isEmpty() || pass.isEmpty()) null
                    else ImportRequest("XTREAM", fields["name"].orEmpty().trim(), url, user, pass)
                }
                else -> null
            }
        }

        /** First private IPv4 address of this device (what the phone must reach), or null offline. */
        fun localIpAddress(): String? =
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress }
                    ?.hostAddress
            }.getOrNull()

        private fun page(message: String) = FORM_PAGE_SHELL.replace("{{BODY}}", "<p>$message</p>")

        private const val FORM_PAGE_SHELL = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>AmmiraTv</title>
<style>body{font-family:sans-serif;background:#0d0d0d;color:#fff;padding:20px;max-width:480px;margin:auto}
</style></head><body><h2>AmmiraTv</h2>{{BODY}}</body></html>"""

        private const val VPN_FORM_HTML = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>AmmiraTv</title>
<style>
body{font-family:sans-serif;background:#0d0d0d;color:#fff;padding:20px;max-width:480px;margin:auto}
textarea,button{width:100%;box-sizing:border-box;padding:14px;margin:6px 0 14px;border-radius:8px;border:1px solid #33334d;background:#1e1e3a;color:#fff;font-size:15px}
textarea{height:260px;font-family:monospace}
button{background:#ff2d55;color:#fff;font-weight:bold;border:0}
</style></head><body>
<h2>Configurazione VPN</h2>
<p>Incolla qui il contenuto del file del tuo provider: .conf (WireGuard) oppure .ovpn (OpenVPN).</p>
<form method="post" action="/{{TOKEN}}">
<input type="hidden" name="type" value="WIREGUARD">
<textarea name="config" required placeholder="WireGuard: [Interface] ...&#10;OpenVPN: client / dev tun / remote ..."></textarea>
<button type="submit">Invia alla TV</button>
</form></body></html>"""

        private const val FORM_HTML = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>AmmiraTv</title>
<style>
body{font-family:sans-serif;background:#0d0d0d;color:#fff;padding:20px;max-width:480px;margin:auto}
input,select,button{width:100%;box-sizing:border-box;padding:14px;margin:6px 0 14px;border-radius:8px;border:1px solid #33334d;background:#1e1e3a;color:#fff;font-size:16px}
button{background:#00d4aa;color:#0d0d0d;font-weight:bold;border:0}
#x{display:none}
</style></head><body>
<h2>Aggiungi una sorgente</h2>
<form method="post" action="/{{TOKEN}}">
<label>Tipo</label>
<select name="type" id="t" onchange="document.getElementById('x').style.display=this.value=='XTREAM'?'block':'none'">
<option value="M3U">Playlist M3U / M3U8</option><option value="XTREAM">Xtream Codes</option></select>
<label>Nome (facoltativo)</label><input name="name" autocomplete="off">
<label>URL</label><input name="url" autocomplete="off" autocapitalize="none" required>
<div id="x"><label>Username</label><input name="username" autocomplete="off" autocapitalize="none">
<label>Password</label><input name="password" autocomplete="off" autocapitalize="none"></div>
<button type="submit">Invia alla TV</button>
</form></body></html>"""
    }
}
