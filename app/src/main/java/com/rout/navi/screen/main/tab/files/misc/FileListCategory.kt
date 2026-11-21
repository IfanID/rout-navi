package com.rout.navi.screen.main.tab.files.misc

import java.io.File

data class FileListCategory(
    val name: String,
    val data: Any
) {
    /** Path folder, dipakai tab kategori supaya pilihan tetap dikenali. */
    val folderPath: String get() = (data as File).path
}