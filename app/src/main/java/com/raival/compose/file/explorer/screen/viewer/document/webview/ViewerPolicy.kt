package com.raival.compose.file.explorer.screen.viewer.document.webview

/**
 * Content Security Policy untuk setiap halaman penampil dokumen.
 *
 * Diadaptasi dari Gander (MIT, github.com/mokshablr/gander). Dokumen adalah
 * input yang tidak tepercaya bagi renderer yang tidak dirancang dengan
 * pertimbangan keamanan, jadi script dari dalam dokumen tidak boleh jalan:
 * - Script hanya dari berkas sendiri, tanpa inline script dan tanpa eval
 *   ('wasm-unsafe-eval' hanya untuk WebAssembly milik renderer).
 * - Style inline diizinkan karena docx-preview, PPTXjs dan SheetJS menulis
 *   style attribute saat merender.
 * - Gambar/font/media hanya dari host sendiri atau data:/blob:, yaitu byte
 *   yang sudah dipegang halaman (dokumen di /doc/ dan gambarnya).
 * - Tidak ada frame, plugin, form submission, atau base URL.
 *
 * CSP ini dipasang dua kali: sebagai meta tag di setiap halaman HTML dan
 * sebagai header pada setiap respons aset yang disajikan WebViewClient.
 */
internal object ViewerPolicy {
    const val CSP =
        "default-src 'none'; " +
            "script-src 'self' 'wasm-unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data: blob:; " +
            "media-src 'self' data: blob:; " +
            "connect-src 'self'; " +
            "worker-src 'self'; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "form-action 'none'; " +
            "base-uri 'none'"
}
