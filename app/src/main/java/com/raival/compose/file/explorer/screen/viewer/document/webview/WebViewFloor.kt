package com.raival.compose.file.explorer.screen.viewer.document.webview

/**
 * Cek versi WebView untuk renderer yang dipakai penampil dokumen.
 *
 * Diadaptasi dari Gander (MIT, github.com/mokshablr/gander). Hanya docx-preview
 * yang punya floor eksplisit: skripnya memakai sintaks (seperti `??` dan `?.`)
 * yang baru bisa diurai Chromium 80. Halaman xlsx dan pptx tidak punya floor,
 * jadi bila WebView sangat tua halaman JS-nya sendiri yang menampilkan pesan.
 */

/**
 * Chromium major yang dibutuhkan docx-preview. Floor ini hanya untuk pesan:
 * minSdk aplikasi adalah 31 (Android 12), jadi WebView yang disertakan Android
 * selalu jauh di atasnya.
 */
internal const val DOCX_PREVIEW_MIN_CHROMIUM_MAJOR = 80

/** Floor untuk [ext], atau null bila formatnya tidak punya floor. */
internal fun minChromiumMajor(ext: String): Int? = when (ext) {
    "docx", "docm", "dotx" -> DOCX_PREVIEW_MIN_CHROMIUM_MAJOR
    else -> null
}

/**
 * Chromium major di user agent WebView, seperti "Chrome/138.0.7204.179".
 * -1 bila UA tidak menyebut Chrome sama sekali.
 */
internal fun chromiumMajor(userAgent: String): Int {
    val from = userAgent.indexOf("Chrome/")
    if (from < 0) return -1
    val digits = userAgent.substring(from + "Chrome/".length).takeWhile { it.isDigit() }
    // WebView baru bisa diperbarui sejak Chromium 33, jadi angka yang lebih kecil
    // berarti penyedianya tidak memakai nomor versi Chromium; diabaikan.
    val major = digits.toIntOrNull() ?: return -1
    return if (major >= 33) major else -1
}

/**
 * Parameter URL untuk halaman saat engine lebih tua dari floor: "webview" adalah
 * versi yang dimiliki, "needs" versi minimalnya. Halaman JS memakai keduanya
 * untuk menampilkan kartu pesan yang tepat alih-alih gagal render diam-diam.
 */
internal fun webViewFloorParams(major: Int, floor: Int?): String {
    if (floor == null) return ""
    if (major >= floor) return ""
    if (major < 0) return ""
    return "&webview=$major&needs=$floor"
}
