package com.rout.navi.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.ui.Space
import com.rout.navi.screen.main.tab.files.FilesTab
import kotlinx.coroutines.launch

/**
 * Konfirmasi menghapus catatan riwayat. Berkas video tidak disentuh, jadi
 * dialog ini sengaja tidak memakai alur hapus berkas atau recycle bin.
 */
@Composable
fun RemoveHistoryDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val jumlah = tab.selectedFiles.size

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val paths = tab.selectedFiles.keys.toList()
                    onDismissRequest()
                    tab.unselectAllFiles()
                    tab.scope.launch {
                        globalClass.preferencesManager.deleteWatchHistory(paths)
                        // Penanda versi dipakai supaya keterangan berkas tidak
                        // menampilkan persentase lama setelah entri dihapus.
                        tab.reloadFiles {
                            tab.tabResumeVersi.value += 1
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.remove_from_history))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dismiss))
            }
        },
        title = { Text(stringResource(R.string.remove_from_history_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.remove_from_history_message))
                if (jumlah > 1) {
                    Space(size = 8.dp)
                    Text(stringResource(R.string.remove_history_count, jumlah))
                }
            }
        }
    )
}
