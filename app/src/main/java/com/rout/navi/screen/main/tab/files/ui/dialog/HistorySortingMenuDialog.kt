package com.rout.navi.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.ui.BottomSheetDialog
import com.rout.navi.common.ui.Space
import com.rout.navi.screen.main.tab.files.FilesTab
import com.rout.navi.screen.preferences.PreferencesManager

/**
 * Menu urutan khusus tab riwayat tontonan. Dipisah dari menu urutan berkas
 * karena riwayat tidak punya folder, ukuran, atau tipe yang perlu diatur, dan
 * pilihannya disimpan sendiri sehingga tidak mengubah halaman lain.
 */
@Composable
fun HistorySortingMenuDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val prefs = globalClass.preferencesManager

    fun pick(order: Int) {
        prefs.historySortOrder = order
        tab.reloadFiles()
        onDismissRequest()
    }

    BottomSheetDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
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
                    imageVector = Icons.Rounded.SortByAlpha,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Space(8.dp)
                Text(
                    text = stringResource(R.string.sort_by),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .selectableGroup()
                        .padding(8.dp)
                ) {
                    PilihanUrutan(
                        teks = stringResource(R.string.history_sort_newest),
                        dipilih = prefs.historySortOrder == PreferencesManager.HISTORY_SORT_NEWEST,
                        onPick = { pick(PreferencesManager.HISTORY_SORT_NEWEST) }
                    )
                    GarisPemisah()
                    PilihanUrutan(
                        teks = stringResource(R.string.history_sort_oldest),
                        dipilih = prefs.historySortOrder == PreferencesManager.HISTORY_SORT_OLDEST,
                        onPick = { pick(PreferencesManager.HISTORY_SORT_OLDEST) }
                    )
                    GarisPemisah()
                    PilihanUrutan(
                        teks = stringResource(R.string.history_sort_name_asc),
                        dipilih = prefs.historySortOrder == PreferencesManager.HISTORY_SORT_NAME_ASC,
                        onPick = { pick(PreferencesManager.HISTORY_SORT_NAME_ASC) }
                    )
                    GarisPemisah()
                    PilihanUrutan(
                        teks = stringResource(R.string.history_sort_name_desc),
                        dipilih = prefs.historySortOrder == PreferencesManager.HISTORY_SORT_NAME_DESC,
                        onPick = { pick(PreferencesManager.HISTORY_SORT_NAME_DESC) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PilihanUrutan(
    teks: String,
    dipilih: Boolean,
    onPick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = dipilih,
                onClick = onPick,
                role = Role.RadioButton
            ),
        color = if (dipilih) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = teks,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dipilih) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            RadioButton(selected = dipilih, onClick = null)
        }
    }
}

@Composable
private fun GarisPemisah() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    )
}
