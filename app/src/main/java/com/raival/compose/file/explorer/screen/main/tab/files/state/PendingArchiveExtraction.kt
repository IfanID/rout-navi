package com.raival.compose.file.explorer.screen.main.tab.files.state

import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder

/**
 * Menyimpan permintaan ekstraksi sementara, misalnya saat dialog password
 * atau dialog lokasi tujuan sedang ditampilkan.
 */
data class PendingArchiveExtraction(
    val archive: LocalFileHolder,
    val destinationPath: String
)
