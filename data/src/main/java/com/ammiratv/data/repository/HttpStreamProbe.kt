package com.ammiratv.data.repository

import com.ammiratv.domain.repository.StreamProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpStreamProbe @Inject constructor(
    okHttpClient: OkHttpClient
) : StreamProbe {

    // A dead channel must cost seconds, not the default 30 s
    private val client = okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun isAlive(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use false
                // Read a first slice only: enough to know a stream is really flowing
                val buffer = ByteArray(2048)
                (response.body?.byteStream()?.read(buffer) ?: -1) > 0
            }
        } catch (_: Exception) {
            false
        }
    }
}
