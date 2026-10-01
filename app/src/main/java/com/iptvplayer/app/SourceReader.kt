package com.iptvplayer.app

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Lê a lista M3U de um arquivo do aparelho (content://) ou de um link http/https. */
fun readSource(context: Context, source: String): String {
    if (source.startsWith("content://")) {
        return context.contentResolver.openInputStream(Uri.parse(source))
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("não foi possível abrir o arquivo")
    }
    var address = source
    for (attempt in 0 until 5) {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("redirecionamento inválido")
                address = URL(URL(address), location).toString()
                continue
            }
            if (code !in 200..299) throw IOException("servidor respondeu HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
    throw IOException("muitos redirecionamentos")
}
