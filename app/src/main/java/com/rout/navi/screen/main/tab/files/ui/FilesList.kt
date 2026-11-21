package com.rout.navi.screen.main.tab.files.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import com.rout.navi.screen.main.tab.files.misc.CategoryChoice
import com.rout.navi.screen.main.tab.files.provider.StorageProvider
import com.rout.navi.screen.main.tab.files.ui.dialog.HistorySelectionMenu
import com.rout.navi.screen.viewer.video.WatchEntry
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.emptyString
import com.rout.navi.common.toFormattedDate
import com.rout.navi.common.toFormattedSize
import com.rout.navi.common.toFormattedTime
import com.rout.navi.common.ui.Isolate
import com.rout.navi.common.ui.Space
import com.rout.navi.screen.main.tab.files.FilesTab
import com.rout.navi.screen.main.tab.files.coil.canUseCoil
import com.rout.navi.screen.main.tab.files.holder.ContentHolder
import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.imageFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.videoFileType
import com.rout.navi.screen.main.tab.files.misc.ViewConfigs
import com.rout.navi.screen.main.tab.files.misc.ViewType
import com.rout.navi.screen.main.tab.files.provider.RootShell
import com.rout.navi.screen.preferences.constant.FileItemSizeMap.getFileListFontSize
import com.rout.navi.screen.preferences.constant.FileItemSizeMap.getFileListIconSize
import com.rout.navi.screen.preferences.constant.FileItemSizeMap.getFileListSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.FilesList(tab: FilesTab) {
    val preferencesManager = globalClass.preferencesManager
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(Modifier.weight(1f)) {
        if (tab.activeFolderContent.isEmpty() && !tab.isLoading) {
            // Halaman riwayat punya penyebab kosong yang berbeda dari folder
            // biasa, jadi keterangannya juga berbeda.
            if (tab.categoryChoice is CategoryChoice.History) RiwayatKosong()
            else EmptyFolderContent(tab)
        }

        if (preferencesManager.disablePullDownToRefresh) {
            FilesListContent(tab)
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    tab.openFolder(tab.activeFolder, true, true)
                    coroutineScope.launch {
                        delay(100)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                FilesListContent(tab)
            }
        }

        LoadingOverlay(tab)
    }
}

/**
 * Keadaan kosong khusus halaman riwayat: menyebut sebabnya (belum ada video
 * yang ditonton) dan menjelaskan kapan daftar ini akan terisi.
 */
@Composable
private fun RiwayatKosong() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier
                .alpha(0.3f)
                .size(56.dp),
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = colorScheme.onSurface
        )
        Space(16.dp)
        Text(
            text = stringResource(R.string.history_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Space(6.dp)
        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.history_empty_message),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = colorScheme.onSurface.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun EmptyFolderContent(tab: FilesTab) {
    val preferencesManager = globalClass.preferencesManager

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.4f),
            text = stringResource(
                when {
                    !tab.activeFolder.canRead -> R.string.cant_access_content
                    else -> R.string.empty
                }
            ),
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        if (tab.activeFolder.canRead && !preferencesManager.showHiddenFiles) {
            Space(12.dp)
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.4f),
                text = stringResource(R.string.empty_without_hidden_files),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingOverlay(tab: FilesTab) {
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = tab.isLoading
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.surface.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { }
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun FilesListContent(tab: FilesTab) {
    // Penanda versi daftar: berubah setiap tab aktif kembali dari pemutar,
    // sehingga keterangan berkas dihitung ulang di semua gaya tampilan.
    val versi by produceState(initialValue = 0, tab.id) {
        tab.tabResumeVersi.collect { value = it }
    }

    // Tab riwayat memakai daftar satu kolom dengan sampul, mengikuti susunan
    // daftar riwayat vlc-android. Gaya daftar biasa tidak dipakai di sini
    // karena butuh garis kemajuan dan waktu tonton.
    if (tab.categoryChoice is CategoryChoice.History) {
        HistoryList(tab, versi)
        return
    }

    // Menu aksi mode pilih ikut dibuka dari halaman ini, supaya tampil hanya
    // saat tab riwayat yang sedang dilihat.
    HistorySelectionMenu(
        show = tab.showHistorySelectionMenu,
        tab = tab,
        onDismissRequest = { tab.showHistorySelectionMenu = false }
    )

    when (tab.viewConfig.viewType) {
        ViewType.LIST -> FilesListColumns(tab, versi)
        ViewType.GRID -> FilesListGrid(tab, versi)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(tab: FilesTab, versi: Int) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uid }
        ) { index, item ->
            val holder = item as? LocalFileHolder ?: return@itemsIndexed
            val path = item.uniquePath
            var dipilih by remember(tab.selectedFiles.containsKey(path)) {
                mutableStateOf(tab.selectedFiles.containsKey(path))
            }

            // Kemajuan dibaca ulang setiap versi berubah, jadi baris langsung
            // benar setelah kembali dari pemutar.
            var entry by remember(path, versi) { mutableStateOf<WatchEntry?>(null) }
            LaunchedEffect(path, versi) {
                entry = withContext(Dispatchers.IO) { StorageProvider.getWatchEntry(path) }
            }

            fun togglePilih() {
                if (tab.selectedFiles.containsKey(path)) {
                    tab.selectedFiles.remove(path)
                    tab.lastSelectedFileIndex = -1
                } else {
                    tab.selectedFiles[path] = item
                    tab.lastSelectedFileIndex = index
                }
                dipilih = tab.selectedFiles.containsKey(path)
                tab.onSelectionChange()
            }

            HistoryRow(
                item = holder,
                entry = entry,
                dipilih = dipilih,
                onClick = {
                    if (tab.isSelectionModeActive) togglePilih()
                    else tab.openFile(context, item)
                },
                // Ketuk lama langsung masuk mode pilih, seperti daftar riwayat
                // vlc-android. Menu opsi berkas tidak dipakai di halaman ini.
                onLongClick = {
                    tab.selectionModeEnabled = true
                    if (!tab.selectedFiles.containsKey(path)) {
                        tab.selectedFiles[path] = item
                    }
                    dipilih = true
                    tab.onSelectionChange()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListColumns(tab: FilesTab, versi: Int) {
    val context = LocalContext.current
    val selectionHighlightColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = colorScheme.primary.copy(alpha = 0.05f)

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        state = tab.activeListState,
        columns = GridCells.Fixed(tab.viewConfig.columnCount),
    ) {
        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uid }
        ) { index, item ->
            val currentItemPath = item.uniquePath
            val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
            var isSelectedItem by remember(isAlreadySelected) { mutableStateOf(isAlreadySelected) }
            ColumnFileItem(
                item = item,
                index = index,
                tab = tab,
                selectionHighlightColor = selectionHighlightColor,
                highlightColor = highlightColor,
                context = context,
                viewConfigs = tab.viewConfig,
                isSelectedItem = isSelectedItem,
                onSelection = { isSelectedItem = it },
                versi = versi
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListGrid(tab: FilesTab, versi: Int) {
    val context = LocalContext.current
    val selectionHighlightColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = colorScheme.primary.copy(alpha = 0.05f)

    LazyVerticalGrid(
        state = tab.activeListState,
        columns = GridCells.Fixed(tab.viewConfig.columnCount),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uid }
        ) { index, item ->
            val currentItemPath = item.uniquePath
            val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
            var isSelectedItem by remember(isAlreadySelected) { mutableStateOf(isAlreadySelected) }
            GridFileItem(
                itemPath = currentItemPath,
                isSelected = isSelectedItem,
                item = item,
                index = index,
                tab = tab,
                selectionHighlightColor = selectionHighlightColor,
                highlightColor = highlightColor,
                context = context,
                viewConfigs = tab.viewConfig,
                onSelection = { isSelectedItem = it },
                versi = versi
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnFileItem(
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    selectionHighlightColor: Color,
    highlightColor: Color,
    context: Context,
    currentItemPath: String = item.uniquePath,
    isSelectedItem: Boolean,
    onSelection: (Boolean) -> Unit,
    viewConfigs: ViewConfigs,
    versi: Int
) {
    fun toggleSelection() {
        if (tab.selectedFiles.containsKey(currentItemPath)) {
            tab.selectedFiles.remove(currentItemPath)
            tab.lastSelectedFileIndex = -1
            onSelection(false)
        } else {
            tab.selectedFiles[currentItemPath] = item
            tab.lastSelectedFileIndex = index
            onSelection(true)
        }
        tab.onSelectionChange()
    }

    val holderVideo = (item as? LocalFileHolder)?.takeIf {
        item.isFile() && videoFileType.contains(item.extension)
    }

    // Kemajuan dibaca satu kali per baris; kartu video memakainya.
    var entryVideo by remember(currentItemPath, versi) { mutableStateOf<WatchEntry?>(null) }
    if (holderVideo != null) {
        LaunchedEffect(currentItemPath, versi) {
            entryVideo = withContext(IO) { StorageProvider.getWatchEntry(currentItemPath) }
        }
    }

    // Berkas video memakai kartu yang sama persis dengan halaman riwayat,
    // lengkap dengan garis kemajuan dan persen tonton. Klik dan menu berkas
    // diserahkan ke kartu supaya tidak ada penanganan klik ganda.
    if (holderVideo != null) {
        HistoryRow(
            item = holderVideo,
            entry = entryVideo,
            dipilih = isSelectedItem,
            onClick = {
                if (tab.isSelectionModeActive) {
                    toggleSelection()
                } else {
                    tab.openFile(context, item)
                }
            },
            onLongClick = {
                handleLongClick(tab, currentItemPath, item, index)
            }
        )
        return
    }

    // Foto memakai kartu yang sama dengan video. Berkas lain (dokumen,
    // arsip, folder) tetap memakai baris polos seperti awal.
    val adalahFoto = item.isFile() && imageFileType.contains(item.extension.lowercase())
    if (adalahFoto) {
        BarisFoto(
            item = item,
            dipilih = isSelectedItem,
            onIconClick = {
                if (tab.isSelectionModeActive) {
                    toggleSelection()
                } else {
                    tab.openFile(context, item)
                }
            },
            onClick = {
                if (tab.isSelectionModeActive) {
                    toggleSelection()
                } else {
                    tab.openFile(context, item)
                }
            },
            onLongClick = { handleLongClick(tab, currentItemPath, item, index) }
        )
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelectedItem) {
                    selectionHighlightColor
                } else if (tab.highlightedFiles.contains(currentItemPath)) {
                    highlightColor
                } else {
                    Color.Unspecified
                }
            )
            .combinedClickable(
                onClick = {
                    if (tab.isSelectionModeActive) {
                        toggleSelection()
                    } else {
                        if (item.isFile()) {
                            tab.openFile(context, item)
                        } else {
                            tab.openFolder(item, false)
                        }
                    }
                },
                onLongClick = {
                    handleLongClick(tab, currentItemPath, item, index)
                }
            )
    ) {
        Space(size = getFileListSpace(tab.activeFolder).dp)

        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(
                item = item,
                size = getFileListIconSize(tab.activeFolder).dp,
                viewConfigs = viewConfigs,
                onClick = {
                    if (tab.isSelectionModeActive) {
                        toggleSelection()
                    } else {
                        if (item.isFile()) {
                            tab.openFile(context, item)
                        } else {
                            tab.openFolder(item, false)
                        }
                    }
                },
                onLongClick = { handleLongClick(tab, currentItemPath, item, index) }
            )

            Space(size = 8.dp)

            val fontSize = getFileListFontSize(tab.activeFolder)

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontSize = fontSize.sp,
                    maxLines = 1,
                    lineHeight = (fontSize + 2).sp,
                    overflow = TextOverflow.Ellipsis,
                    color = if (tab.highlightedFiles.contains(currentItemPath)) {
                        colorScheme.primary
                    } else {
                        Color.Unspecified
                    }
                )

                KeteranganBerkas(
                    item = item,
                    versi = versi
                )
            }
        }

        Space(size = getFileListSpace(tab.activeFolder).dp)

        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            thickness = 0.5.dp
        )
    }
}

/**
 * Baris foto memakai bentuk kartu seperti kartu video: sampul 96x60 di kiri,
 * judul, lalu keterangan yang dipisah tiga bagian rata kanan. Tanggal memakai
 * lebar sisa supaya ruang baris terpakai, ukuran dan ekstensi menempel kanan
 * sehingga lurus antarberkas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarisFoto(
    item: ContentHolder,
    dipilih: Boolean,
    onIconClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = LebarSampul, height = TinggiSampul)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = ImageRequest
                        .Builder(globalClass)
                        .data(item)
                        .build(),
                    filterQuality = FilterQuality.Low,
                    contentScale = ContentScale.Fit,
                    contentDescription = null,
                    onError = { }
                )
            }

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.7f)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = item.lastModified.toFormattedDate(hideSeconds = false),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = item.size.toFormattedSize(),
                        fontSize = 12.sp,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = item.extension.uppercase(),
                        fontSize = 12.sp,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (dipilih) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Garis pemisah di kaki kartu, sama seperti baris berkas lain, supaya
        // foto yang berurutan tetap terpisah walau latarnya menyatu.
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun KeteranganBaris(teks: String) {
    Text(
        modifier = Modifier
            .alpha(0.7f)
            .padding(top = 2.dp),
        text = teks,
        fontSize = 12.sp,
        maxLines = 1,
        lineHeight = 16.sp,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Teks keterangan berkas selain foto. Isinya tetap diambil dari getDetails()
 * supaya aturan tersembunyi, jumlah item folder, dan tanggal tidak berubah.
 */
@Composable
private fun KeteranganBerkas(
    item: ContentHolder,
    versi: Int
) {
    var details by remember(item.uniquePath, item.lastModified, versi) {
        mutableStateOf(emptyString)
    }
    LaunchedEffect(item.uniquePath, item.lastModified, versi) {
        details = withContext(IO) { item.getDetails() }
    }
    if (details.isNotEmpty()) KeteranganBaris(details)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileItem(
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    selectionHighlightColor: Color,
    highlightColor: Color,
    context: Context,
    itemPath: String,
    isSelected: Boolean,
    onSelection: (Boolean) -> Unit,
    viewConfigs: ViewConfigs,
    versi: Int = 0
) {
    fun toggleSelection() {
        if (tab.selectedFiles.containsKey(itemPath)) {
            tab.selectedFiles.remove(itemPath)
            tab.lastSelectedFileIndex = -1
            onSelection(false)
        } else {
            tab.selectedFiles[itemPath] = item
            tab.lastSelectedFileIndex = index
            onSelection(true)
        }
        tab.onSelectionChange()
    }

    // Progres tontonan untuk kartu video. Hanya berkas video yang membacanya
    // supaya kartu gambar dan folder tidak membayar biaya IO.
    val adalahVideo = item.isFile() && videoFileType.contains(item.extension)
    var entry by remember(itemPath, versi) { mutableStateOf<WatchEntry?>(null) }
    if (adalahVideo) {
        LaunchedEffect(itemPath, versi) {
            entry = withContext(IO) { StorageProvider.getWatchEntry(itemPath) }
        }
    }
    val kemajuan = if (adalahVideo) entry else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (viewConfigs.galleryMode) Modifier.aspectRatio(1f) else Modifier)
            .combinedClickable(
                onClick = {
                    if (tab.isSelectionModeActive) {
                        toggleSelection()
                    } else {
                        if (item.isFile()) {
                            tab.openFile(context, item)
                        } else {
                            tab.openFolder(item, false)
                        }
                    }
                },
                onLongClick = {
                    handleLongClick(tab, itemPath, item, index)
                }
            )
            .background(
                color = if (isSelected) {
                    selectionHighlightColor
                } else if (tab.highlightedFiles.contains(itemPath)) {
                    highlightColor
                } else {
                    Color.Unspecified
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (viewConfigs.galleryMode) 1.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = if (viewConfigs.galleryMode) Modifier
                    .fillMaxWidth()
                    .weight(1f)
                else Modifier
            ) {
                FileIcon(
                    item = item,
                    size = (getFileListIconSize(tab.activeFolder) * 1.5).dp,
                    onClick = {
                        if (tab.isSelectionModeActive) {
                            toggleSelection()
                        } else {
                            if (item.isFile()) {
                                tab.openFile(context, item)
                            } else {
                                tab.openFolder(item, false)
                            }
                        }
                    },
                    viewConfigs = viewConfigs,
                    onLongClick = {
                        handleLongClick(tab, itemPath, item, index)
                    }
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
                            imageVector = Icons.Rounded.CheckCircle,
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    }
                }
                val adaKemajuan = kemajuan != null && kemajuan.durationMs > 0
                if (viewConfigs.galleryMode) {
                    val tampilkanNama = item.isFolder || (item.isFile() &&
                            ((!videoFileType.contains(item.extension) &&
                                    !imageFileType.contains(item.extension)) ||
                                    !viewConfigs.hideMediaNames))
                    // Persen hanya untuk video yang sedang berjalan; video
                    // selesai cukup ditandai garis penuh di tepi bawah.
                    val persen = kemajuan
                        ?.takeIf { adaKemajuan && !it.isFinished }
                        ?.let { stringResource(R.string.history_percent, it.percent) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        if (tampilkanNama) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color = colorScheme.surface.copy(alpha = 0.75f))
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val fontSize = getFileListFontSize(tab.activeFolder) * 0.8
                                // Nama berlabuh di kiri ketika ada persen di
                                // kanan, agar kedua info tidak saling berdesak.
                                Text(
                                    text = item.displayName,
                                    fontSize = fontSize.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 2,
                                    lineHeight = (fontSize + 2).sp,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = if (persen != null) TextAlign.Start else TextAlign.Center,
                                    color = if (tab.highlightedFiles.contains(itemPath)) {
                                        colorScheme.primary
                                    } else {
                                        colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (persen != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = persen,
                                        fontSize = fontSize.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (adaKemajuan) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(color = colorScheme.surface.copy(alpha = 0.75f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(kemajuan.percent / 100f)
                                        .height(3.dp)
                                        .background(
                                            if (kemajuan.isFinished) colorScheme.onSurface.copy(alpha = 0.45f)
                                            else colorScheme.primary
                                        )
                                )
                            }
                        }
                    }

                    // Kartu tanpa nama media tetap perlu tempat persen, jadi
                    // chip kecil dipasang di pojok kanan atas thumbnail.
                    if (persen != null && !tampilkanNama) {
                        Text(
                            text = persen,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (adaKemajuan) {
                    // Video di grid biasa: garis kemajuan di tepi bawah ikon,
                    // mengikuti gaya galeri tanpa mengubah susunan grid.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(3.dp)
                            .background(color = colorScheme.surface.copy(alpha = 0.75f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(kemajuan.percent / 100f)
                                .height(3.dp)
                                .background(
                                    if (kemajuan.isFinished) colorScheme.onSurface.copy(alpha = 0.45f)
                                    else colorScheme.primary
                                )
                        )
                    }
                }
            }
            if (!viewConfigs.galleryMode) {
                Spacer(modifier = Modifier.height(4.dp))
                val fontSize = getFileListFontSize(tab.activeFolder) * 0.8
                Text(
                    text = item.displayName,
                    fontSize = fontSize.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    lineHeight = (fontSize + 2).sp,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (tab.highlightedFiles.contains(itemPath)) {
                        colorScheme.primary
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FileIcon(
    item: ContentHolder,
    size: Dp,
    viewConfigs: ViewConfigs,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val sizeModifier = if (viewConfigs.viewType == ViewType.GRID && viewConfigs.galleryMode) {
        Modifier.fillMaxSize()
    } else {
        Modifier.size(size)
    }

    Isolate {
        Box(
            modifier = Modifier
                .then(sizeModifier)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .graphicsLayer { alpha = if (item.isHidden()) 0.4f else 1f },
        ) {
            var useCoil by remember(item.uid) {
                mutableStateOf(canUseCoil(item))
            }

            if (useCoil) {
                // Video memakai Fit agar bingkai potret tidak terpotong di
                // kotak persegi galeri; dengan Crop, bagian tengah saja yang
                // terlihat. Gambar tetap memakai pilihan pengguna.
                val skala = when {
                    viewConfigs.cropThumbnails && !item.isVideoMedia() -> ContentScale.Crop
                    else -> ContentScale.Fit
                }
                AsyncImage(
                    modifier = sizeModifier,
                    model = ImageRequest
                        .Builder(globalClass)
                        .data(item)
                        .build(),
                    filterQuality = FilterQuality.Low,
                    contentScale = skala,
                    contentDescription = null,
                    onError = { useCoil = false }
                )
            } else {
                FileContentIcon(item)
            }

            if (!item.canRead && !RootShell.isGranted) {
                Icon(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .alpha(if (item.isHidden()) 0.4f else 1f),
                    imageVector = Icons.Rounded.Lock,
                    tint = Color.Red,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun FileDetailsCompact(
    item: ContentHolder,
    currentItemPath: String,
    isHighlighted: Boolean,
    versi: Int
) {
    Isolate {
        var details by remember(
            key1 = currentItemPath,
            key2 = item.lastModified,
            key3 = versi
        ) { mutableStateOf(emptyString) }

        LaunchedEffect(
            key1 = currentItemPath,
            key2 = item.lastModified,
            key3 = versi
        ) {
            val det = item.getDetails()
            withContext(Dispatchers.Main) { details = det }
        }

        Text(
            modifier = Modifier.alpha(0.6f),
            text = details,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (isHighlighted) {
                colorScheme.primary
            } else {
                colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun handleLongClick(
    tab: FilesTab,
    currentItemPath: String,
    item: ContentHolder,
    index: Int
) {
    val preferencesManager = globalClass.preferencesManager
    // Long-press selalu masuk mode seleksi, termasuk saat saklar sedang nonaktif.
    tab.selectionModeEnabled = true
    val isFirstSelection = tab.selectedFiles.isEmpty()
    val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
    val isNewSelection = !isAlreadySelected

    tab.selectedFiles[currentItemPath] = item

    if ((isFirstSelection && preferencesManager.showFileOptionMenuOnLongClick)
        || !isNewSelection
    ) {
        tab.toggleFileOptionsMenu(item)
    }

    if (isNewSelection) {
        if (tab.lastSelectedFileIndex >= 0) {
            if (tab.lastSelectedFileIndex > index) {
                for (i in tab.lastSelectedFileIndex downTo index) {
                    tab.selectedFiles[tab.activeFolderContent[i].uniquePath] =
                        tab.activeFolderContent[i]
                }
            } else {
                for (i in tab.lastSelectedFileIndex..index) {
                    tab.selectedFiles[tab.activeFolderContent[i].uniquePath] =
                        tab.activeFolderContent[i]
                }
            }
        }
    }
    tab.lastSelectedFileIndex = index
    tab.quickReloadFiles()
}

/** Video biasa yang sampulnya diambil dari frame, bukan format lain. */
private fun ContentHolder.isVideoMedia(): Boolean =
    isFile() && videoFileType.contains(extension.lowercase())