package com.rout.navi.screen.main.tab.files.state

import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder

/**
 * Menyimpan permintaan ekstraksi sementara, misalnya saat dialog password
 * atau dialog lokasi tujuan sedang ditampilkan.
 */
data class PendingArchiveExtraction(
    val archive: LocalFileHolder,
    val destinationPath: String
)
