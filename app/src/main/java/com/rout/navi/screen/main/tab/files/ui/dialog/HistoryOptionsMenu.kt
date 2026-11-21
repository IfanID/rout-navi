package com.rout.navi.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.ui.BottomSheetDialog
import com.rout.navi.screen.main.tab.files.FilesTab
import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder

/**
 * Menu opsi halaman riwayat. Isinya sengaja kecil, mengikuti menu riwayat
 * vlc-android: urutkan, lalu kosongkan seluruh catatan. Tidak ada aksi berkas
 * karena halaman ini tidak mengurus berkas.
 */
@Composable
fun HistoryOptionsMenu(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    BottomSheetDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(R.string.watch_history),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OpsiBaris(
                ikon = Icons.Rounded.Sort,
                teks = stringResource(R.string.sort_by)
            ) {
                onDismissRequest()
                tab.showHistorySortMenu = true
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            OpsiBaris(
                ikon = Icons.Rounded.DeleteSweep,
                teks = stringResource(R.string.clear_history),
                warna = MaterialTheme.colorScheme.error
            ) {
                onDismissRequest()
                tab.showClearHistoryDialog = true
            }
        }
    }
}

@Composable
private fun OpsiBaris(
    ikon: ImageVector,
    teks: String,
    warna: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = warna,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = teks,
                style = MaterialTheme.typography.bodyLarge,
                color = warna
            )
        }
    }
}

/**
 * Aksi mode pilih di halaman riwayat, mengikuti menu aksi vlc-android: putar,
 * buka folder, lalu hapus catatan. Tidak ada aksi berkas lain karena halaman
 * ini bukan pengelola berkas.
 */
@Composable
fun HistorySelectionMenu(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    BottomSheetDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            OpsiBaris(
                ikon = Icons.Rounded.PlayArrow,
                teks = stringResource(R.string.history_play)
            ) {
                onDismissRequest()
                val terpilih = tab.selectedFiles.values.firstOrNull() as? LocalFileHolder ?: return@OpsiBaris
                terpilih.openVideoWithResume(globalClass)
            }

            OpsiBaris(
                ikon = Icons.Rounded.FolderOpen,
                teks = stringResource(R.string.history_go_to_folder)
            ) {
                onDismissRequest()
                val terpilih = tab.selectedFiles.values.firstOrNull() ?: return@OpsiBaris
                tab.unselectAllFiles(false)
                tab.leaveHistoryCategory()
                tab.openFolder(terpilih, rememberListState = false,
                    rememberSelectedFiles = false)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )

            OpsiBaris(
                ikon = Icons.Rounded.DeleteSweep,
                teks = stringResource(R.string.remove_from_history),
                warna = MaterialTheme.colorScheme.error
            ) {
                onDismissRequest()
                tab.showDeleteHistoryDialog = true
            }
        }
    }
}

/**
 * Konfirmasi mengosongkan seluruh riwayat. Berkas video tetap di penyimpanan,
 * jadi teksnya menyebut itu dengan jelas.
 */
@Composable
fun ClearHistoryDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                    globalClass.showMsg(R.string.history_cleared)
                }
            ) {
                Text(stringResource(R.string.clear_history))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dismiss))
            }
        },
        title = { Text(stringResource(R.string.clear_history_title)) },
        text = { Text(stringResource(R.string.clear_history_message)) }
    )
}
