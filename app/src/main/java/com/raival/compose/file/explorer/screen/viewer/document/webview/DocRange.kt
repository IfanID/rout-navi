package com.raival.compose.file.explorer.screen.viewer.document.webview

import android.webkit.MimeTypeMap
import java.io.Closeable
import java.io.InputStream

/**
 * Menyajikan dokumen yang sedang dibuka ke WebView lewat HTTP.
 *
 * Diadaptasi dari Gander (MIT, github.com/mokshablr/gander). Halaman penampil
 * mengambil dokumennya dari /doc/, yang dijawab oleh shouldInterceptRequest
 * di WebViewClient. Respons range ditulis manual, jadi penguraian header dan
 * pembatasan byte dipisah di sini agar mudah diuji tanpa WebView.
 */

/** Di atas batas ini dokumen disajikan per-range, bukan dibaca sekaligus. */
internal const val RANGE_THRESHOLD_BYTES = 16L * 1024 * 1024

/**
 * Apakah dokumen disajikan dalam range. Diputuskan di satu tempat karena
 * header respons dan pilihan loader di halaman JS harus sepakat.
 */
internal fun useRanges(total: Long): Boolean = total >= RANGE_THRESHOLD_BYTES

/**
 * "bytes=start-end" yang diselesaikan terhadap total yang diketahui. Null
 * berarti sajikan utuh: header tak terurai, tak terpenuhi, atau penyedia
 * yang tidak memberi panjang berkas.
 */
internal fun parseRange(header: String, total: Long): Pair<Long, Long>? {
    if (total <= 0) return null
    // Hanya range pertama dari satu set; renderer tidak meminta lebih.
    val spec = header.substringAfter("bytes=", "").substringBefore(',').trim()
    if (spec.isEmpty()) return null
    val start = spec.substringBefore('-').trim().toLongOrNull() ?: return null
    val end = spec.substringAfter('-').trim().toLongOrNull() ?: (total - 1)
    if (start < 0 || start > end || start >= total) return null
    return start to minOf(end, total - 1)
}

/** Content type dokumen, dari ekstensi alih-alih dari penyedia. */
internal fun documentMime(ext: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: "application/octet-stream"

/** Berhenti pada [remaining] byte, dan menutup descriptor bersama stream. */
internal class LimitedInputStream(
    private val source: InputStream,
    private var remaining: Long,
    private val alsoClose: Closeable
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        return source.read().also { if (it >= 0) remaining-- }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }

    override fun available(): Int = minOf(source.available().toLong(), remaining).toInt()

    override fun close() {
        runCatching { source.close() }
        runCatching { alsoClose.close() }
    }
}
