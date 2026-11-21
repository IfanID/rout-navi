package com.rout.navi.screen.main.tab.files.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rout.navi.R
import com.rout.navi.common.limitLength
import com.rout.navi.screen.main.tab.files.FilesTab
import com.rout.navi.screen.main.tab.files.misc.CategoryChoice

/**
 * Deretan tab folder untuk halaman virtual seperti Video. Tab riwayat
 * tontonan tidak di sini, melainkan di baris atas sejajar tab Video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRow(tab: FilesTab) {
    // Daftar folder bisa berubah setelah penyegaran; pilihan yang sudah tidak
    // ada dikembalikan ke SEMUA supaya layar tidak kosong tanpa penjelasan.
    LaunchedEffect(tab.categories.size) {
        val chosen = tab.categoryChoice
        if (chosen is CategoryChoice.Folder && tab.categories.none { it.folderPath == chosen.path }) {
            tab.categoryChoice = CategoryChoice.All
        }
    }

    val selectedChoice = tab.categoryChoice
    val selectedIndex = when (selectedChoice) {
        is CategoryChoice.Folder ->
            tab.categories.indexOfFirst { it.folderPath == selectedChoice.path } + 1
        else -> 0
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceAtLeast(0),
        edgePadding = 0.dp,
        divider = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Tab(
            selected = selectedChoice is CategoryChoice.All,
            onClick = {
                tab.categoryChoice = CategoryChoice.All
                tab.reloadFiles()
            },
            text = { Text(text = stringResource(R.string.all)) }
        )
        tab.categories.forEach { category ->
            Tab(
                selected = selectedChoice is CategoryChoice.Folder &&
                        selectedChoice.path == category.folderPath,
                onClick = {
                    tab.categoryChoice = CategoryChoice.Folder(category.folderPath)
                    tab.reloadFiles()
                },
                text = { Text(text = category.name.limitLength(18)) }
            )
        }
    }
}
