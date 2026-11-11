package com.raival.compose.file.explorer.screen.viewer.document.webview

import android.content.ContentResolver
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** Host tempat halaman penampil dan dokumen yang dibuka disajikan oleh aplikasi sendiri. */
internal const val ASSET_HOST = "appassets.androidplatform.net"

/**
 * Menjawab setiap permintaan halaman penampil untuk dokumen [uri] bertipe [mime].
 * Diadaptasi dari Gander (MIT, github.com/mokshablr/gander): dokumen di /doc/,
 * halaman dan library dari aset dengan policy penampil, dan 404 kosong untuk
 * semua permintaan jaringan lain. Kelas ini tidak memegang view karena WebView
 * memanggil dari thread-nya sendiri.
 */
internal class DocumentServer(
    private val resolver: ContentResolver,
    private val assets: WebViewAssetLoader,
    private val uri: Uri,
    private val mime: String,
) {

    /** Panjang dokumen menurut penyedianya, atau -1; diisi sebelum halaman dimuat. */
    @Volatile
    var total = -1L

    fun respond(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        // Dokumen disajikan di sini, bukan lewat WebViewAssetLoader, karena
        // PathHandler hanya menerima path dan menjawab permintaan range butuh
        // header Range dari permintaan itu sendiri.
        if (url.scheme == "https" && url.host == ASSET_HOST &&
            url.path?.startsWith("/doc/") == true
        ) {
            return docResponse(request.requestHeaders["Range"], total)
        }
        return assets.shouldInterceptRequest(url)?.withViewerPolicy() ?: when (url.scheme) {
            // Apa pun lain yang diminta halaman ke jaringan dijawab kosong di
            // sini, bukan diteruskan ke stack jaringan. Penggunaan izin INTERNET
            // aplikasi dihentikan di lapisan ini juga, tapi batas ini berdiri
            // sendiri supaya permintaan dari script yang seharusnya tidak jalan
            // tetap tertahan.
            "http", "https" -> notFound()
            // data: adalah byte yang sudah dipegang halaman. file: dan content:
            // ditolak oleh allowFileAccess dan allowContentAccess di WebView.
            else -> null
        }
    }

    /**
     * Menyajikan dokumen yang dibuka di /doc/<apa pun>, dengan dukungan
     * permintaan range. Stream baru per permintaan: halaman meminta banyak,
     * dan tidak berurutan.
     */
    private fun docResponse(range: String?, total: Long): WebResourceResponse {
        return try {
            val rangeable = useRanges(total)
            val span = if (rangeable) range?.let { parseRange(it, total) } else null
            // nosniff membuat browser hanya mengeksekusi berkas sebagai script
            // bila tipe datanya memang script, sehingga markup yang lolos dari
            // renderer tidak bisa memuat berkas lain sebagai script.
            val nosniff = "X-Content-Type-Options" to "nosniff"
            if (span == null) {
                val headers = mutableMapOf(nosniff)
                if (rangeable) headers["Accept-Ranges"] = "bytes"
                if (total >= 0) headers["Content-Length"] = total.toString()
                WebResourceResponse(mime, null, 200, "OK", headers, resolver.openInputStream(uri))
            } else {
                val (start, end) = span
                WebResourceResponse(
                    mime, null, 206, "Partial Content",
                    mapOf(
                        nosniff,
                        "Accept-Ranges" to "bytes",
                        "Content-Range" to "bytes $start-$end/$total",
                        "Content-Length" to (end - start + 1).toString()
                    ),
                    slice(start, end)
                )
            }
        } catch (e: Exception) {
            notFound()
        }
    }

    /**
     * Membuka tepat rentang [start, end], dengan seek ke offset alih-alih
     * membaca melewatinya. File yang lewat URI biasa selalu mulai dari 0.
     */
    private fun slice(start: Long, end: Long): InputStream {
        val afd = resolver.openAssetFileDescriptor(uri, "r")
            ?: throw IOException("Tidak bisa membuka $uri")
        val stream = FileInputStream(afd.fileDescriptor)
        val from = afd.startOffset + start
        // Seekable untuk berkas dari disk; pipe harus dibaca melewatinya.
        runCatching { stream.channel.position(from) }
            .onFailure { runCatching { stream.skip(from) } }
        return LimitedInputStream(stream, end - start + 1, afd)
    }
}

/** Lihat [ViewerPolicy]: header yang membawa policy ke dalam worker renderer. */
private fun WebResourceResponse.withViewerPolicy(): WebResourceResponse = apply {
    responseHeaders = responseHeaders.orEmpty() + ("Content-Security-Policy" to ViewerPolicy.CSP)
}

private fun notFound(): WebResourceResponse = WebResourceResponse(
    "text/plain", "utf-8", 404, "Not Found",
    null, ByteArrayInputStream(ByteArray(0))
)

/**
 * Halaman yang boleh dinavigasi oleh penampil: semuanya milik aplikasi.
 * Link di luar host ini tidak dibuka di penampil atau di mana pun, sehingga
 * sebuah link tidak bisa menyerahkan URL ke browser. Nor the document at
 * /doc/: dibuka sebagai halaman, berkas akan dirender sesuai tipenya alih-alih
 * oleh penampil yang membuatnya aman untuk dilihat.
 */
internal fun isViewerPage(url: Uri): Boolean {
    val path = url.path ?: return false
    return url.scheme == "https" && url.host == ASSET_HOST &&
        path.startsWith("/assets/viewer/") && path.endsWith(".html") && ".." !in path
}

/** Panjang dalam byte, atau -1 bila penyedia tidak menyebutkannya. */
internal fun documentLength(context: android.content.Context, uri: Uri): Long = runCatching {
    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
}.getOrNull()?.takeIf { it >= 0 } ?: -1L
