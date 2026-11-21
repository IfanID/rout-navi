package com.rout.navi.screen.main.tab.files.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rout.navi.R
import com.rout.navi.common.toFormattedDate
import com.rout.navi.common.toFormattedTime
import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder
import com.rout.navi.screen.viewer.video.WatchEntry

/**
 * Baris riwayat tontonan. Susunannya mengikuti daftar riwayat vlc-android:
 * sampul persegi 16:10 di kiri, judul di atas, keterangan di bawah. Progres
 * tontonan ditambahkan sebagai garis tipis di bawah judul, karena posisi
 * tonton adalah alasan halaman ini dibuka.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    item: LocalFileHolder,
    entry: WatchEntry?,
    dipilih: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val warnaTeks = MaterialTheme.colorScheme.onSurface
    // Sampul berukuran tetap, bukan murni dari rasio, supaya FileIcon yang
    // menetapkan ukurannya sendiri tetap menempati kotak yang benar.
    val sisiSampul = 56.dp

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dipilih) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Unspecified)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = (sisiSampul.value * 1.6f).dp, height = sisiSampul)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                FileIcon(contentHolder = item)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (dipilih) MaterialTheme.colorScheme.primary else warnaTeks
                )

                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = keteranganRiwayat(entry),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = warnaTeks.copy(alpha = 0.7f)
                )
            }
        }

        // Garis kemajuan hanya muncul bila durasi sudah terekam. Data lama yang
        // belum punya durasi tidak diberi garis kosong.
        val tercatat = entry
        if (tercatat != null && tercatat.durationMs > 0) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(
                            when {
                                tercatat.isFinished -> 1f
                                else -> tercatat.percent / 100f
                            }.coerceIn(0f, 1f)
                        )
                        .height(2.dp)
                        .background(
                            if (tercatat.isFinished) warnaTeks.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
    }
}

/**
 * Satu baris keterangan: waktu tonton terakhir, lalu posisi kemajuan. Entri
 * lama tanpa durasi hanya menampilkan waktu tonton.
 */
@Composable
private fun keteranganRiwayat(entry: WatchEntry?): String {
    if (entry == null) return stringResource(R.string.history_missing_progress)
    val waktu = entry.lastWatchedAt.toFormattedDate(hideSeconds = true)
    val posisi = when {
        entry.durationMs <= 0 -> null
        entry.isFinished -> stringResource(R.string.video_finished)
        else -> stringResource(
            R.string.history_progress,
            entry.percent,
            entry.positionMs.toFormattedTime(),
            entry.durationMs.toFormattedTime()
        )
    }
    return if (posisi == null) waktu else "$waktu  |  $posisi"
}
