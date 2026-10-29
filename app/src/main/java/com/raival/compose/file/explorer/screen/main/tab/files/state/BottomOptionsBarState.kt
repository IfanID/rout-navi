package com.raival.compose.file.explorer.screen.main.tab.files.state

data class BottomOptionsBarState(
    val showMoreOptionsButton: Boolean = false,
    val showEmptyRecycleBinButton: Boolean = false,
    val showCreateNewContentButton: Boolean = true,
    val showQuickOptions: Boolean = false,
    // Jumlah pilihan disimpan di state agar strip ikut recompose setiap pilihan berubah.
    val selectedCount: Int = 0,
    val selectedFolderCount: Int = 0
)
