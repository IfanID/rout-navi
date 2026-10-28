package com.raival.compose.file.explorer.screen.main.tab.files.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder

/**
 * Pilihan bersama untuk mode lintas tab.
 *
 * Selama aktif, semua tab memakai daftar yang sama sehingga berkas dari tab
 * berbeda bisa diproses bersama. Daftar dikosongkan saat mode dimatikan.
 */
object CrossTabSelection {
    var enabled by mutableStateOf(false)

    val files = linkedMapOf<String, ContentHolder>()
}
