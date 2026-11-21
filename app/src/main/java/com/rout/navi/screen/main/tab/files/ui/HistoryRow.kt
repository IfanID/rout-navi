package com.rout.navi.screen.main.tab.files.ui

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rout.navi.common.toFormattedDate
import com.rout.navi.common.toFormattedSize
import com.rout.navi.common.toFormattedTime
import com.rout.navi.screen.main.tab.files.coil.canUseCoil
import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder
import com.rout.navi.screen.viewer.video.WatchEntry
import java.io.File

/**
 * Ukuran bingkai sampul di baris berkas dan kartu riwayat. Bingkainya tetap
 * supaya semua baris rata; isi gambar yang menyesuaikan di dalamnya.
 */
val LebarSampul = 96.dp
val TinggiSampul = 60.dp

/**
 * Durasi video dibaca sekali dari metadata berkas dan disimpan di cache
 * proses, karena MediaMetadataRetriever mahal kalau dipanggil setiap kali
 * daftar digulir. Kunci cache memuat ukuran dan waktu ubah supaya berkas
 * yang diganti durasinya ikut terbaca ulang.
 */
private val cacheDurasiVideo = java.util.concurrent.ConcurrentHashMap<String, Long>()

fun durasiVideo(file: File): Long {
    val kunci = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
    cacheDurasiVideo[kunci]?.let { return it }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val durasi = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L
        cacheDurasiVideo[kunci] = durasi
        durasi
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Kartu satu video di halaman riwayat: sampul 16:10 di kiri, judul dan waktu
 * tonton di tengah, persen kemajuan berdiri sendiri di kolom kanan. Garis
 * kemajuan tipis ditutup di bawah kartu karena itulah alasan halaman ini ada.
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
    // Durasi dari riwayat dipakai lebih dulu; bila belum ada, durasi dibaca
    // dari metadata berkas supaya keterangan tidak kosong untuk video baru.
    val durasiTonton = entry?.durationMs?.takeIf { it > 0 }
    var durasiBerkas by remember(item.uid, durasiTonton) { mutableStateOf(durasiTonton ?: 0L) }
    if (durasiTonton == null && durasiBerkas <= 0) {
        LaunchedEffect(item.uid) {
            durasiBerkas = withContext(Dispatchers.IO) { durasiVideo(item.file) }
        }
    }

    // Kartu dengan latar lembut dan sudut membulat memisahkan tiap video
    // tanpa garis pemisah yang membuat daftar terasa padat. Susunannya sama
    // dengan baris berkas di daftar: sampul 96x60, judul, satu baris
    // keterangan, kendali kanan, lalu garis kemajuan di kaki kartu.
    // Latar kartu dibuat transparan agar menyatu dengan latar daftar, sama
    // seperti baris berkas lain. Warna sorotan hanya muncul saat dipilih.
    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (dipilih) MaterialTheme.colorScheme.surfaceContainerHigh
                else Color.Transparent
            )
            .semantics { selected = dipilih }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 80.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SampulRiwayat(item)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontSize = 15.sp,
                    maxLines = 1,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val durasi = durasiTonton ?: durasiBerkas
                val rincian = buildList {
                    if (durasi > 0) add(durasi.toFormattedTime())
                    add(item.size.toFormattedSize())
                    if (item.extension.isNotEmpty()) add(item.extension.uppercase())
                }.joinToString("  |  ")

                val tanggal = (entry?.lastWatchedAt ?: item.lastModified)
                    .toFormattedDate(hideSeconds = true)

                Text(
                    modifier = Modifier
                        .alpha(0.7f)
                        .padding(top = 2.dp),
                    text = "$rincian  |  ${keteranganPosisi(entry)}  |  $tanggal",
                    fontSize = 12.sp,
                    maxLines = 1,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.width(8.dp))

            // Saat memilih, tanda terpilih menggantikan persen karena
            // itulah informasi yang sedang dicari pengguna.
            if (dipilih) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                PersenDiKanan(entry, durasiTonton ?: durasiBerkas)
            }
        }

        GarisKemajuan(entry)

        // Garis pemisah di kaki kartu, sama seperti baris berkas lain, supaya
        // video dan foto yang berurutan tetap terpisah walau latarnya menyatu.
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp, start = LebarSampul + 12.dp),
            thickness = 0.5.dp
        )
    }
}

/**
 * Sampul mengisi bingkai 96x60 dengan gambar yang menyesuaikan (Fit), bukan
 * dipotong, supaya video potret tetap terlihat utuh. Berkas tanpa gambar
 * jatuh ke ikon konten yang biasa dipakai daftar berkas.
 */
@Composable
private fun SampulRiwayat(item: LocalFileHolder) {
    val pakaiGambar = remember(item.uid) { canUseCoil(item) }
    var gambarGagal by remember(item.uid) { mutableStateOf(false) }
    Box(
        Modifier.size(width = LebarSampul, height = TinggiSampul),
        contentAlignment = Alignment.Center
    ) {
        if (pakaiGambar && !gambarGagal) {
            AsyncImage(
                modifier = Modifier.size(width = LebarSampul, height = TinggiSampul),
                model = ImageRequest.Builder(globalClass).data(item).build(),
                contentScale = ContentScale.Fit,
                contentDescription = null,
                onError = { gambarGagal = true }
            )
        } else {
            FileContentIcon(item)
        }
    }
}

/**
 * Persen kemajuan di ujung kanan supaya terbaca sekali lamat tanpa harus
 * membaca keterangan. Video yang belum ditonton tetap menampilkan 0% dan
 * kolomnya tetap disiapkan supaya kartu tidak berubah ukuran.
 */
@Composable
private fun PersenDiKanan(entry: WatchEntry?, durasiBerkas: Long) {
    val teks = when {
        entry != null && entry.durationMs > 0 ->
            stringResource(R.string.history_percent, entry.percent)
        durasiBerkas > 0 -> stringResource(R.string.history_percent, 0)
        else -> ""
    }
    Text(
        text = teks,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(min = 38.dp)
    )
}

/**
 * Baris keterangan: posisi tonton, atau keadaan khusus bila durasi belum
 * pernah terekam. Video tanpa riwayat diberi label jelas, bukan baris
 * kosong, supaya kartunya selalu terisi.
 */
@Composable
private fun keteranganPosisi(entry: WatchEntry?): String = when {
    entry == null -> stringResource(R.string.history_not_watched)
    entry.durationMs <= 0 -> stringResource(R.string.history_missing_progress)
    else -> stringResource(
        R.string.history_position,
        entry.positionMs.toFormattedTime(),
        entry.durationMs.toFormattedTime()
    )
}

/**
 * Garis kemajuan hanya muncul bila durasi sudah terekam. Video selesai
 * memakai warna netral, bukan warna aksen, supaya tidak terbaca sebagai
 * video yang sedang berjalan.
 */
@Composable
private fun GarisKemajuan(entry: WatchEntry?) {
    val tercatat = entry ?: return
    if (tercatat.durationMs <= 0) return
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(tercatat.percent / 100f)
                .height(3.dp)
                .background(
                    if (tercatat.isFinished) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.primary
                )
        )
    }
}
