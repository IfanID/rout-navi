package com.rout.navi.screen.main.tab.files

import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider.getUriForFile
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.App.Companion.logger
import com.rout.navi.R
import com.rout.navi.common.getIndexIf
import com.rout.navi.common.getMimeType
import com.rout.navi.common.isNot
import com.rout.navi.common.isStorageRootPath
import com.rout.navi.common.orIf
import com.rout.navi.common.removeIf
import com.rout.navi.screen.main.MainActivity
import com.rout.navi.screen.main.tab.Tab
import com.rout.navi.screen.main.tab.files.archive.ArchiveFormat
import com.rout.navi.screen.main.tab.files.holder.ArchiveFileHolder
import com.rout.navi.screen.main.tab.files.holder.ContentHolder
import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder
import com.rout.navi.screen.main.tab.files.holder.RootLocalFileHolder
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder
import com.rout.navi.screen.main.tab.files.holder.ZipFileHolder
import com.rout.navi.screen.main.tab.files.misc.CategoryChoice
import com.rout.navi.screen.main.tab.files.misc.FileListCategory
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.anyFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.apkFileType
import com.rout.navi.screen.main.tab.files.provider.RootShell
import com.rout.navi.screen.main.tab.files.provider.StorageProvider
import com.rout.navi.screen.main.tab.files.state.BottomOptionsBarState
import com.rout.navi.screen.main.tab.files.state.CrossTabSelection
import com.rout.navi.screen.main.tab.files.state.DialogsState
import com.rout.navi.screen.main.tab.files.state.PendingArchiveExtraction
import com.rout.navi.screen.main.tab.files.task.CompressTask
import com.rout.navi.screen.main.tab.files.task.ExtractTask
import com.rout.navi.screen.main.tab.files.task.ExtractTaskParameters
import com.rout.navi.screen.main.tab.files.zip.ZipTree
import com.reandroid.archive.ZipAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class FilesTab(
    val source: ContentHolder,
    context: Context? = null
) : Tab() {
    companion object {
        fun isValidLocalPath(path: String) = File(path).exists()
    }

    override val id = globalClass.generateUid()
    val scope = CoroutineScope(Dispatchers.IO)

    val homeDir: ContentHolder =
        if (source is VirtualFileHolder || source.isFolder) source
        else runBlocking { source.getParent() }
            ?: StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder

    var activeFolder: ContentHolder = homeDir
    var activeFolderContent = mutableStateListOf<ContentHolder>()
    val contentListStates = hashMapOf<String, LazyGridState>()
    var activeListState by mutableStateOf(LazyGridState())

    var viewConfig by mutableStateOf(
        globalClass.preferencesManager.getViewConfigPrefsFor(activeFolder)
    )

    val highlightedFiles = arrayListOf<String>()

    /**
     * Pilihan milik tab ini. Dipakai langsung selama mode lintas tab mati,
     * sehingga perilaku lama tidak berubah.
     */
    private val localSelectedFiles = linkedMapOf<String, ContentHolder>()

    /** Pilihan yang sedang aktif; dipakai bersama semua tab saat lintas tab. */
    val selectedFiles: LinkedHashMap<String, ContentHolder>
        get() = if (CrossTabSelection.enabled) CrossTabSelection.files else localSelectedFiles

    var lastSelectedFileIndex = -1

    /**
     * Mode pilih lintas folder. Saat aktif, pindah folder tidak menghapus
     * pilihan sehingga berkas dari beberapa folder bisa diproses bersamaan.
     * Nilainya hanya berlaku untuk tab ini dan tidak disimpan permanen.
     */
    var crossFolderSelectionEnabled by mutableStateOf(false)

    /**
     * Saklar mode seleksi di strip. Saat nonaktif, ketukan pada item membuka
     * berkas/folder seperti biasa, tetapi pilihan yang sudah ada tetap
     * dipertahankan sehingga pengguna bisa berpindah lokasi lebih dulu.
     */
    var selectionModeEnabled by mutableStateOf(CrossTabSelection.enabled)

    var currentPathSegments by mutableStateOf(listOf<ContentHolder>())
    var highlightedPathSegment by mutableStateOf(activeFolder)
    val currentPathSegmentsListState = LazyListState()

    var showCategories by mutableStateOf(false)
    var categories = mutableStateListOf<FileListCategory>()
    var selectedCategory by mutableStateOf<FileListCategory?>(null)

    /**
     * Tab kategori yang sedang dipilih. Setter meneruskannya ke kotak folder
     * aktif, karena kotak itu yang dipakai saat daftar disusun ulang. Pilihan
     * folder terakhir disimpan di [lastFolderChoice] supaya kembali ke tab
     * Video menampilkan folder yang sama, bukan SEMUA.
     */
    private var categoryChoiceState by mutableStateOf<CategoryChoice>(CategoryChoice.All)

    /** Folder terakhir sebelum masuk tab riwayat. */
    private var lastFolderChoice: CategoryChoice = CategoryChoice.All

    /** Folder terakhir sebelum halaman riwayat dibuka. */
    private var folderSebelumRiwayat: ContentHolder? = null

    /**
     * Membuka halaman riwayat dari halaman berkas mana pun. Folder yang sedang
     * dibuka dicatat dulu agar tombol kembali mengarah ke sana.
     */
    fun bukaRiwayat() {
        folderSebelumRiwayat = activeFolder
        categoryChoice = CategoryChoice.History
        reloadFiles()
    }

    /** Kembali dari riwayat ke halaman berkas sebelumnya. */
    fun tutupRiwayat() {
        val tujuan = folderSebelumRiwayat ?: homeDir
        // Pilihan folder terakhir dipulihkan, bukan dikembalikan ke SEMUA,
        // supaya kembali dari riwayat membuka sub tab yang tadi dipakai.
        categoryChoice = lastFolderChoice
        openFolder(tujuan, rememberListState = false)
    }

    /** Menu urutan khusus riwayat sedang dibuka. */
    var showHistorySortMenu by mutableStateOf(false)

    /** Menu opsi riwayat (bersihkan semua). */
    var showHistoryOptionsMenu by mutableStateOf(false)

    /** Konfirmasi hapus catatan riwayat di tab riwayat. */
    var showDeleteHistoryDialog by mutableStateOf(false)

    /** Konfirmasi mengosongkan seluruh riwayat. */
    var showClearHistoryDialog by mutableStateOf(false)

    /** Menu aksi mode pilih di halaman riwayat. */
    var showHistorySelectionMenu by mutableStateOf(false)

    /**
     * Salinan ringkas riwayat yang selalu terbaru. Dipakai bar riwayat di
     * semua halaman, jadi tidak perlu menunggu penyegaran daftar berkas.
     */
    val salinanRiwayat = mutableStateListOf<LocalFileHolder>()

    /** Pemantau perubahan riwayat, hidup selama tab ada. */
    private var pemantauRiwayat: Job? = null

    /**
     * Scope khusus pemantau riwayat. Dipisah dari [scope] karena scope itu
     * dipakai pekerjaan daftar berkas yang bisa dibatalkan saat tab berpindah.
     */
    private val scopeTips = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Penanda agar dua pemicu berdekatan tidak memuat riwayat dua kali. */
    private var riwayatDimuatPada = 0L

    /**
     * Naik setiap tab ini aktif kembali. Daftar berkas memakainya untuk
     * menghitung ulang keterangan, sehingga kemajuan tontonan langsung tampil
     * setelah kembali dari pemutar.
     */
    val tabResumeVersi = kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * Tombol Histori hanya untuk tab yang bersumber dari galeri Video. Di tab
     * Dokumen, Gambar, atau Audio tombol itu tidak relevan.
     */
    val showsHistoryButton: Boolean
        get() = source is VirtualFileHolder &&
                (source as VirtualFileHolder).type == VirtualFileHolder.VIDEO

    var categoryChoice: CategoryChoice
        get() = categoryChoiceState
        set(value) {
            categoryChoiceState = value
            if (value !is CategoryChoice.History) lastFolderChoice = value
            val holder = activeFolder as? VirtualFileHolder ?: return
            holder.historyChosen = value is CategoryChoice.History
            holder.chosenFolderPath = (value as? CategoryChoice.Folder)?.path
        }

    /**
     * Kembali dari riwayat ke halaman sebelumnya. Sub tab yang dipilih terakhir
     * dipulihkan, jadi kembali ke galeri Video membuka folder yang sama.
     */
    fun leaveHistoryCategory() {
        if (categoryChoiceState is CategoryChoice.History) {
            tutupRiwayat()
        }
    }

    // Holds the file that has been long-clicked
    var targetFile: ContentHolder? = null
    var compressTaskHolder: CompressTask? = null
    var pendingArchiveExtraction by mutableStateOf<PendingArchiveExtraction?>(null)

    // Dipakai untuk mendeteksi perubahan pada ArchiveTree yang sudah diperbarui.
    var zipSourceTimestamp = -1L

    private val _dialogsState = MutableStateFlow(DialogsState())
    val dialogsState = _dialogsState.asStateFlow()

    private val _bottomOptionsBarState = MutableStateFlow(BottomOptionsBarState())
    val bottomOptionsBarState = _bottomOptionsBarState.asStateFlow()

    var handleBackGesture by mutableStateOf(true)
    var tabViewLabel by mutableStateOf(createTabLabel())

    var isLoading by mutableStateOf(false)

    private var foldersCount = 0
    private var filesCount = 0

    init {
        // If the tab point to a file, open it immediately without waiting for its parent content to be loaded
        if (source.isFile()) {
            context?.let { openFile(context, source) }
        }
    }

    override fun onTabStarted() {
        super.onTabStarted()
        // Pemantau perubahan riwayat dipasang sekali dan hidup selama tab ada,
        // bukan hanya saat tab aktif. Dengan begitu bar riwayat dan halaman
        // riwayat langsung benar di tab mana pun, seperti daftar riwayat VLC
        // yang dibaca ulang setiap kali media dibuka.
        if (pemantauRiwayat == null) {
            pemantauRiwayat = scopeTips.launch {
                globalClass.preferencesManager.historyVersion.collect {
                    muatUlangRiwayat()
                }
            }
        }
        // Load either the source file or the home tab
        scope.launch {
            if (source.isFile()) { // This is true when locating a file
                // Check if we can access the parent folder and open it
                source.getParent()?.let { parent ->
                    openFolderImpl(parent) {
                        // Scroll to the file once the content has been loaded
                        CoroutineScope(Dispatchers.Main).launch {
                            getFileListState().scrollToItem(
                                maxOf(
                                    activeFolderContent.getIndexIf { uniquePath == source.uniquePath },
                                    0
                                ),
                                0
                            )
                        }
                    }
                } ?: also {
                    // If parent folder cannot be accessed, revert to home folder
                    openFolderImpl(homeDir)
                }

                // Highlight the opened file
                highlightedFiles.apply {
                    clear()
                    add(source.uniquePath)
                }
            } else {
                // If the source file is a folder, open it
                openFolderImpl(homeDir)
            }
        }
    }

    override fun onTabResumed() {
        // Keterangan berkas dihitung ulang setelah kembali dari pemutar atau
        // aplikasi lain, supaya kemajuan tontonan tidak tertinggal.
        tabResumeVersi.value += 1
        scope.launch {
            // Riwayat dimuat ulang di sini juga, jadi bar riwayat dan halaman
            // riwayat sudah benar tanpa menunggu penyegaran manual.
            muatUlangRiwayat()
        }
        scope.launch {
            validateActiveFolder()
            // Important to clear any information from previous tabs (when switching tabs)
            requestHomeToolbarUpdate()
            // Detect any content changes
            detectFileChanges()
            // Check for display mode change
            updateDisplayConfig()
        }
    }

    /**
     * Memuat ulang ringkasan riwayat. Dipanggil saat tab aktif dan saat
     * penanda perubahan riwayat naik, jadi bar dan daftar riwayat tidak perlu
     * menunggu penyegaran berkas.
     */
    suspend fun muatUlangRiwayat() {
        // Pemuatan yang berdekatan dilewati supaya tidak ada kedip dua kali.
        if (SystemClock.elapsedRealtime() - riwayatDimuatPada < 300L) return
        riwayatDimuatPada = SystemClock.elapsedRealtime()

        val daftar = withContext(Dispatchers.IO) { StorageProvider.getWatchHistory() }
        salinanRiwayat.clear()
        salinanRiwayat.addAll(daftar)
        // Halaman riwayat ikut disusun ulang dari ringkasan yang baru.
        if (categoryChoice is CategoryChoice.History) {
            activeFolderContent.clear()
            activeFolderContent.addAll(daftar)
        }
    }

    override val header: String
        get() = tabViewLabel

    override suspend fun getTitle(): String {
        return createTitle()
    }

    override suspend fun getSubtitle(): String {
        return createSubtitle()
    }

    private suspend fun createSubtitle(): String {
        var selectedFolders = 0
        var selectedFiles = 0

        this.selectedFiles.values.forEach {
            if (it.isFolder) selectedFolders++
            else selectedFiles++
        }

        return buildString {
            if (foldersCount + filesCount == 0) {
                append(globalClass.getString(R.string.empty_folder))
                return@buildString
            }

            if (foldersCount > 0) {
                append(activeFolder.getFormattedFileCount(0, foldersCount))
                if (selectedFolders > 0) {
                    append(globalClass.getString(R.string.files_selected).format(selectedFolders))
                }
            }

            if (filesCount > 0 && foldersCount > 0) append(", ")

            if (filesCount > 0) {
                append(activeFolder.getFormattedFileCount(filesCount, 0))
                if (selectedFiles > 0) {
                    append(globalClass.getString(R.string.files_selected).format(selectedFiles))
                }
            }
        }
    }

    private suspend fun createTitle() = when (activeFolder) {
        is VirtualFileHolder -> activeFolder.displayName
        else -> globalClass.getString(R.string.files_tab_title)
    }

    override fun onBackPressed(): Boolean {
        // Saat mode lintas pilihan aktif, tombol back langsung bernavigasi agar
        // pilihan dari folder sebelumnya tidak hilang.
        if (!isCrossSelectionActive && unselectAnySelectedFiles()) {
            return true
        } else if (handleBackGesture) {
            // Ambil dari breadcrumb di thread utama, lalu fallback ke parent
            // holder di dalam coroutine bila jejak belum tersedia.
            val trailParent = currentPathSegments.getOrNull(
                currentPathSegments.lastIndex - 1
            )

            scope.launch {
                val parent = trailParent ?: activeFolder.getParent()

                if (parent != null) {
                    highlightedFiles.apply {
                        clear()
                        add(activeFolder.uniquePath)
                    }
                    openFolderImpl(parent)
                }
            }

            return true
        }

        return false
    }

    /**
     * Unselects all the selected files.
     * returns true if any files were selected
     */
    fun unselectAnySelectedFiles(): Boolean {
        if (selectedFiles.isNotEmpty()) {
            unselectAllFiles()
            return true
        }
        return false
    }


    fun unselectAllFiles(quickReload: Boolean = true) {
        val wasCrossTab = CrossTabSelection.enabled

        selectedFiles.clear()
        lastSelectedFileIndex = -1

        // Tanpa pilihan, mode lintas tidak lagi diperlukan.
        if (wasCrossTab) {
            CrossTabSelection.enabled = false
            CrossTabSelection.files.clear()
            globalClass.mainActivityManager.state.value.tabs
                .filterIsInstance<FilesTab>()
                .forEach {
                    it.crossFolderSelectionEnabled = false
                    it.selectionModeEnabled = false
                    it.localSelectedFiles.clear()
                    it.lastSelectedFileIndex = -1
                }
        } else if (crossFolderSelectionEnabled) {
            crossFolderSelectionEnabled = false
            selectionModeEnabled = false
        }

        if (quickReload) quickReloadFiles()
    }

    /** Mode pilih lintas folder atau lintas tab sedang aktif. */
    val isCrossSelectionActive: Boolean
        get() = crossFolderSelectionEnabled || CrossTabSelection.enabled

    /** Mode lintas tab aktif; semua tab memakai daftar pilihan yang sama. */
    val isCrossTabSelectionEnabled: Boolean
        get() = CrossTabSelection.enabled

    /**
     * Ketukan pada item harus memilih (bukan membuka) saat mode seleksi aktif.
     * Di luar mode lintas pilihan, keberadaan pilihan sudah menandakan mode
     * seleksi seperti perilaku sebelumnya.
     */
    val isSelectionModeActive: Boolean
        get() = if (isCrossSelectionActive) selectionModeEnabled else selectedFiles.isNotEmpty()

    /**
     * Membalik saklar mode seleksi tanpa menyentuh pilihan yang sudah ada.
     * Saat dimatikan, pengguna kembali ke mode normal (ketukan membuka item).
     */
    fun toggleSelectionMode() {
        selectionModeEnabled = !selectionModeEnabled
    }

    /**
     * Menyalakan mode pilih lintas folder dari menu opsi. Pilihan yang sedang
     * ada tetap dipakai, lalu pindah folder tidak menghapusnya.
     */
    fun enableCrossFolderSelection() {
        crossFolderSelectionEnabled = true
        selectionModeEnabled = true
    }

    /**
     * Menyalakan mode lintas tab. Pilihan dari semua tab digabung ke satu
     * daftar bersama supaya bisa diproses bersamaan.
     */
    fun enableCrossTabSelection() {
        if (!CrossTabSelection.enabled) {
            CrossTabSelection.files.clear()
            globalClass.mainActivityManager.state.value.tabs
                .filterIsInstance<FilesTab>()
                .forEach {
                    CrossTabSelection.files.putAll(it.localSelectedFiles)
                    // Semua tab berbagi pilihan, jadi semuanya masuk mode seleksi.
                    it.crossFolderSelectionEnabled = true
                    it.selectionModeEnabled = true
                }
            CrossTabSelection.enabled = true
        }
        crossFolderSelectionEnabled = true
        selectionModeEnabled = true
        quickReloadFiles()
    }

    /** Membatalkan mode lintas pilihan sekaligus seluruh pilihan. */
    fun cancelSelection() {
        val wasCrossTab = CrossTabSelection.enabled

        CrossTabSelection.enabled = false
        CrossTabSelection.files.clear()
        crossFolderSelectionEnabled = false
        selectionModeEnabled = false
        localSelectedFiles.clear()
        lastSelectedFileIndex = -1

        // Mode lintas tab bersifat global, jadi semua tab ikut dikembalikan.
        if (wasCrossTab) {
            globalClass.mainActivityManager.state.value.tabs
                .filterIsInstance<FilesTab>()
                .forEach {
                    it.crossFolderSelectionEnabled = false
                    it.selectionModeEnabled = false
                    it.localSelectedFiles.clear()
                    it.lastSelectedFileIndex = -1
                }
        }

        quickReloadFiles()
    }

    /** Jumlah folder unik dari berkas yang sedang dipilih. */
    fun countSelectedFolders(): Int =
        selectedFiles.keys.map { getParentPath(it) }.distinct().size

    /** Folder induk sebuah path, termasuk untuk path akar. */
    private fun getParentPath(path: String): String {
        val normalized = path.trimEnd('/')
        if (normalized.isEmpty()) return "/"

        val separatorIndex = normalized.lastIndexOf('/')
        return if (separatorIndex <= 0) "/" else normalized.substring(0, separatorIndex)
    }

    fun openFile(context: Context, item: ContentHolder) {
        if (item is LocalFileHolder && item.isApk()) {
            toggleApkDialog(item)
        } else {
            item.open(
                context = context,
                anonymous = false,
                skipSupportedExtensions = !globalClass.preferencesManager.useBuiltInViewer,
                customMimeType = null
            )
        }
    }

    fun openFolder(
        item: ContentHolder,
        rememberListState: Boolean = true,
        rememberSelectedFiles: Boolean = false,
        postEvent: () -> Unit = {}
    ) {
        scope.launch {
            openFolderImpl(item, rememberListState, rememberSelectedFiles, postEvent)
        }
    }

    suspend fun openFolderImpl(
        item: ContentHolder,
        rememberListState: Boolean = true,
        rememberSelectedFiles: Boolean = false,
        postEvent: () -> Unit = {}
    ) {
        // Block UI
        if (isLoading) return

        // Prevent opening invalid files
        if (!item.isValid()) return

        // For virtual folders, update the category
        if (item is VirtualFileHolder) {
            item.selectedCategory = selectedCategory
        }

        // Folder sebelum masuk riwayat diingat, supaya kembali dari halaman
        // riwayat membuka folder yang sama, bukan galeri kosong.
        if (categoryChoice !is CategoryChoice.History) {
            folderSebelumRiwayat = item
        }

        // Switch to the new folder
        activeFolder = item

        // Update header label
        withContext(Dispatchers.Main) {
            tabViewLabel = createTabLabel()
        }

        // Mode lintas pilihan mempertahankan pilihan saat berpindah folder.
        if (!rememberSelectedFiles && !isCrossSelectionActive) {
            selectedFiles.clear()
            lastSelectedFileIndex = -1
        } else {
            // Otherwise, validate the selection
            selectedFiles.removeIf { key, value -> runBlocking { !value.isValid() } }
            if (selectedFiles.isEmpty()) lastSelectedFileIndex = -1
        }

        // Update the bottom bar options to fit the new folder
        _bottomOptionsBarState.update {
            it.copy(
                showQuickOptions = selectedFiles.isNotEmpty(),
                showCreateNewContentButton = activeFolder.canAddNewContent,
                showMoreOptionsButton = selectedFiles.isNotEmpty(),
                showEmptyRecycleBinButton = activeFolder is LocalFileHolder &&
                        ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath),
                selectedCount = selectedFiles.size,
                selectedFolderCount = countSelectedFolders()
            )
        }

        // Update path list dulu karena back memakai daftar ini sebagai trail.
        updatePathList()

        // Check if the back gesture can be handled
        handleBackGesture = selectedFiles.isNotEmpty() || runBlocking {
            !(activeFolder !is RootLocalFileHolder && activeFolder.uniquePath.isStorageRootPath()) &&
                    !shouldNavigateToParentTab() &&
                    (currentPathSegments.size > 1 || activeFolder.hasParent())
        }

        // Update the zipSourceTimestamp if the active folder is inside an archive
        if (activeFolder is ArchiveFileHolder) {
            zipSourceTimestamp = (activeFolder as ArchiveFileHolder).archiveTree.timeStamp
        }

        // Get the content of the new folder
        listFiles { newContent -> // Main thread
            // Update the active folder content
            activeFolderContent.clear()
            activeFolderContent.addAll(newContent)

            // Once the content has been loaded, update the home toolbar title and subtitle
            requestHomeToolbarUpdate()

            // If a new folder is opened, the list must be at the starting position,
            // but when navigating back to parent folder, the saved location must be maintained
            if (!rememberListState) {
                contentListStates[item.uniquePath] = LazyGridState(0, 0)
            }

            // Update the active list state
            activeListState = contentListStates[item.uniquePath] ?: LazyGridState()
                .also { contentListStates[item.uniquePath] = it }

            // Get display config for this folder
            updateDisplayConfig()

            // Update the categories
            if (activeFolder is VirtualFileHolder) {
                categories.clear()
                categories.addAll((activeFolder as VirtualFileHolder).getCategories())
                showCategories = categories.isNotEmpty()
                // Sinkronkan tab dengan pilihan yang tersimpan di kotak folder,
                // supaya tab tidak kembali ke SEMUA setelah penyegaran. Kotak
                // folder hanya memakai tab folder dan riwayat untuk halaman
                // media seperti Video.
                val folder = activeFolder as VirtualFileHolder
                val holdsCategories = folder.type == VirtualFileHolder.VIDEO ||
                        folder.type == VirtualFileHolder.IMAGE ||
                        folder.type == VirtualFileHolder.AUDIO
                categoryChoiceState = when {
                    folder.historyChosen -> CategoryChoice.History
                    holdsCategories && folder.chosenFolderPath != null ->
                        CategoryChoice.Folder(folder.chosenFolderPath!!)
                    else -> CategoryChoice.All
                }
            } else {
                showCategories = false
            }

            // Call any posted events
            postEvent()
        }
    }

    private fun shouldNavigateToParentTab(): Boolean {
        return parentTab isNot null
                && activeFolder is ArchiveFileHolder
                && with(activeFolder as ArchiveFileHolder) {
            runBlocking { getParent()?.uniquePath == archiveTree.source.getParent()?.uniquePath }
        }
    }

    suspend fun validateActiveFolder() {
        if (!activeFolder.isValid()) {
            // Try to find a valid parent folder
            var validParent: ContentHolder? = null
            var current = activeFolder

            while (current.hasParent()) {
                current = current.getParent() ?: break
                if (current.isValid()) {
                    validParent = current
                    break
                }
            }
            validParent?.let {
                openFolder(it)
                return
            }

            if (homeDir.isValid()) {
                openFolder(homeDir)
            } else {
                openFolder(StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder)
            }
        }
    }

    suspend fun detectFileChanges(): Boolean {
        // terminate the check if the tab is busy
        if (isLoading) return false

        // check if any file has been changed
        if (activeFolder is VirtualFileHolder) {
            validateBookmarks()
            validateSearchResult()
            val newContent = (activeFolder as VirtualFileHolder).listContent()
            // Check if the content size has changed
            if (newContent.size != activeFolderContent.size) {
                reloadFiles()
                return true
            }
            // Check each file to see if the source has changed
            if (activeFolderContent.any { (it as LocalFileHolder).hasSourceChanged() }) {
                reloadFiles()
                return true
            }
        } else if (activeFolder is LocalFileHolder) {
            val localFolder = activeFolder as LocalFileHolder

            // Folder sistem dibaca lewat root, jadi File.listFiles() di sini
            // bisa memberi jumlah parsial dan memicu reload terus-menerus.
            if (localFolder !is RootLocalFileHolder &&
                !RootShell.prefersRootListing(localFolder.file)
            ) {
                val newContent =
                    localFolder.file.listFiles()?.toCollection(arrayListOf())
                        ?.apply {
                            if (!globalClass.preferencesManager.showHiddenFiles) {
                                removeIf { it.name.startsWith(".") }
                            }
                        }
                // Check if the content size has changed
                if (newContent != null && newContent.size != activeFolderContent.size) {
                    reloadFiles()
                    return true
                }
            }

            // Check each file to see if the source has changed
            if (activeFolderContent.any { (it as LocalFileHolder).hasSourceChanged() }) {
                reloadFiles()
                return true
            }
        } else if (activeFolder is ArchiveFileHolder) {
            val archiveTree = (activeFolder as ArchiveFileHolder).archiveTree
            val invalidTrees = globalClass.archiveManager.validateArchiveTrees()
            if (invalidTrees.contains(archiveTree.source.uniquePath)) {
                validateActiveFolder()
            } else if (globalClass.archiveManager.checkForSourceChanges()) {
                // Perubahan pada file hasil ekstrak hanya bisa ditulis balik ke ZIP.
                val changedFiles = archiveTree.checkExtractedFiles()
                if (changedFiles.isNotEmpty()) {
                    if (archiveTree is ZipTree) {
                        isLoading = true
                        changedFiles.forEach { changedFile ->
                            archiveTree.getRelatedNode(changedFile)?.let { node ->
                                archiveTree.replaceEntry(node, changedFile)
                            }
                            changedFile.resetCachedTimestamp()
                        }
                        if (archiveTree.source.extension == apkFileType) {
                            try {
                                ZipAlign.alignApk(archiveTree.source.file)
                            } catch (e: Exception) {
                                logger.logError(e)
                            }
                        }
                        isLoading = false
                    } else {
                        // Arsip lain bersifat baca-saja, cukup tandai perubahan
                        // sudah diketahui agar tidak memicu reload berulang.
                        changedFiles.forEach { it.resetCachedTimestamp() }
                    }
                }
                archiveTree.reset()
                reloadFiles()
                return true
            } else if (zipSourceTimestamp isNot archiveTree.timeStamp) {
                reloadFiles()
                return true
            }
        }
        return false
    }

    private suspend fun validateBookmarks() {
        val current = globalClass.preferencesManager.loadBookmarks()

        val invalid = current.filter { path ->
            val file = File(path)
            val exists = if (RootShell.prefersRootListing(file)) {
                RootShell.exists(file)
            } else {
                file.exists()
            }
            exists == false
        }.toSet()

        if (invalid.isNotEmpty()) {
            // Baca ulang sebelum menulis agar markah yang baru ditambahkan
            // saat validasi berjalan tidak ikut terhapus.
            val latest = globalClass.preferencesManager.loadBookmarks()
            globalClass.preferencesManager.saveBookmarks(latest - invalid)
        }
    }

    private fun validateSearchResult() {
        globalClass.searchManager.searchResults.removeIf { !File(it.file.uniquePath).exists() }
    }

    fun quickReloadFiles() {
        scope.launch {
            // terminate if the tab is busy
            if (isLoading) return@launch

            // Put the content in a temporary list
            val temp = arrayListOf<ContentHolder>().apply { addAll(activeFolderContent) }

            // Recheck the back gesture
            handleBackGesture = selectedFiles.isNotEmpty()
                    || (!(activeFolder !is RootLocalFileHolder &&
                    activeFolder.uniquePath.isStorageRootPath()) &&
                    activeFolder.hasParent() && !shouldNavigateToParentTab())

            // Update the bottom bar options
            _bottomOptionsBarState.update {
                it.copy(
                    showQuickOptions = selectedFiles.isNotEmpty(),
                    showMoreOptionsButton = selectedFiles.isNotEmpty(),
                    showEmptyRecycleBinButton = activeFolder is LocalFileHolder &&
                            ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                    activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath),
                    selectedCount = selectedFiles.size,
                    selectedFolderCount = countSelectedFolders()
                )
            }

            withContext(Dispatchers.Main) {
                // Reload the list
                activeFolderContent.clear()
                activeFolderContent.addAll(temp)

                // Update title and subtitle
                requestHomeToolbarUpdate()
            }
        }
    }

    fun onSelectionChange() {
        scope.launch {
            // Mode lintas pilihan tidak berguna tanpa pilihan; kembalikan ke
            // mode normal supaya ketukan berikutnya membuka item seperti biasa.
            if (selectedFiles.isEmpty() && isCrossSelectionActive) {
                CrossTabSelection.enabled = false
                crossFolderSelectionEnabled = false
                selectionModeEnabled = false
            }

            // Recheck the back gesture
            handleBackGesture =
                selectedFiles.isNotEmpty() || (!(activeFolder !is RootLocalFileHolder &&
                        activeFolder.uniquePath.isStorageRootPath()) &&
                        activeFolder.hasParent() && !shouldNavigateToParentTab())

            // Update the bottom bar options
            _bottomOptionsBarState.update {
                it.copy(
                    showQuickOptions = selectedFiles.isNotEmpty(),
                    showMoreOptionsButton = selectedFiles.isNotEmpty(),
                    showEmptyRecycleBinButton = activeFolder is LocalFileHolder &&
                            ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                    activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath),
                    selectedCount = selectedFiles.size,
                    selectedFolderCount = countSelectedFolders()
                )
            }

            // Update title and subtitle
            requestHomeToolbarUpdate()
        }
    }

    fun reloadFiles(postEvent: () -> Unit = {}) {
        scope.launch {
            openFolderImpl(activeFolder) { postEvent() }
        }
    }

    private suspend fun listFiles(onReady: (ArrayList<out ContentHolder>) -> Unit) {
        isLoading = true

        val result = activeFolder.listSortedContent()

        activeFolder.getContentCount().let { contentCount ->
            foldersCount = contentCount.folders
            filesCount = contentCount.files
        }

        withContext(Dispatchers.Main) {
            onReady(result)
            isLoading = false
        }
    }

    // Called when a new file/folder is created
    fun onNewFileCreated(newFile: ContentHolder, openFolder: Boolean = false) {
        scope.launch {
            if (openFolder) {
                openFolderImpl(newFile)
            } else {
                highlightedFiles.apply {
                    clear()
                    add(newFile.uniquePath)
                }

                reloadFiles {
                    CoroutineScope(Dispatchers.Main).launch {
                        val newItemIndex =
                            activeFolderContent.getIndexIf { displayName == newFile.displayName }
                        if (newItemIndex > -1) {
                            getFileListState().scrollToItem(newItemIndex, 0)
                        }
                    }
                }
            }
        }
    }

    fun getFileListState() = contentListStates[activeFolder.uniquePath] ?: LazyGridState().also {
        contentListStates[activeFolder.uniquePath] = it
    }

    private fun createTabLabel(): String {
        val fullName =
            activeFolder.displayName.orIf(globalClass.getString(R.string.internal_storage)) {
                activeFolder.uniquePath == Environment.getExternalStorageDirectory().absolutePath
            }
        return if (fullName.length > 18) fullName.substring(0, 15) + "..." else fullName
    }

    private suspend fun updatePathList() {
        // Walk through parent files. Pembatasan akar penyimpanan hanya untuk
        // penjelajahan biasa; lewat Root (RootLocalFileHolder) jejak tetap penuh.
        val paths = generateSequence(activeFolder) { current ->
            val stopAtStorageRoot = current !is RootLocalFileHolder &&
                    current.uniquePath.isStorageRootPath()
            if (stopAtStorageRoot) null else runBlocking { current.getParent() }
        }

        // Filter yang masih valid saja. Jangan memakai canRead di sini:
        // folder seperti /data bisa saja tidak bisa dibaca langsung oleh app,
        // tetapi tetap bagian dari jejak navigasi (seperti TrailData).
        val newPathSegments =
            paths.filter { runBlocking { it.isValid() } }.toList().reversed()

        // Bandingkan berdasarkan path, bukan hanya awalan nama. Saat kembali
        // ke folder induk, daftar lama bisa lebih panjang sehingga breadcrumb
        // harus benar-benar dipotong.
        val oldPaths = currentPathSegments.joinToString("\u0000") { it.uniquePath }
        val newPaths = newPathSegments.joinToString("\u0000") { it.uniquePath }

        if (oldPaths != newPaths) {
            // Update the state to reflect the new path
            withContext(Dispatchers.Main) {
                currentPathSegments = newPathSegments
            }
        } else {
            // validate path segments if not changed
            currentPathSegments = currentPathSegments.filter { it.isValid() }
        }

        highlightedPathSegment = activeFolder
    }

    fun updateDisplayConfig() {
        viewConfig = globalClass.preferencesManager.getViewConfigPrefsFor(activeFolder)
    }

    fun requestNewTab(tab: Tab) {
        globalClass.mainActivityManager.addTabAndSelect(tab)
    }

    /**
     * Shares the selected files.
     * Only local content can be shared, other types must create a local copy first.
     */
    fun shareSelectedFiles(context: Context) {
        val uris = arrayListOf<Uri>()

        selectedFiles.forEach { selectedFile ->
            val content = selectedFile.component2()
            if (content is LocalFileHolder) {
                uris.add(
                    getUriForFile(context, globalClass.packageName + ".provider", content.file)
                )
            }
        }

        val builder = ShareCompat.IntentBuilder(globalClass)
            .setType(if (uris.size == 1) uris[0].getMimeType(globalClass) else anyFileType)
        uris.forEach {
            builder.addStream(it)
        }

        context.startActivity(
            builder.intent.apply {
                if (uris.size > 1) action = Intent.ACTION_SEND_MULTIPLE

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        )
    }

    fun addToHomeScreen(context: Context, file: LocalFileHolder) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        val pinShortcutInfo = ShortcutInfo
            .Builder(context, file.uniquePath)
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("filePath", file.uniquePath)
                    flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                }
            )
            .setIcon(
                android.graphics.drawable.Icon.createWithResource(
                    context,
                    if (file.isFile()) R.mipmap.file_shortcut else R.mipmap.folder_shortcut
                )
            )
            .setShortLabel(file.displayName)
            .build()
        val pinnedShortcutCallbackIntent =
            shortcutManager.createShortcutResultIntent(pinShortcutInfo)
        shortcutManager.requestPinShortcut(
            pinShortcutInfo,
            PendingIntent.getBroadcast(
                context,
                0,
                pinnedShortcutCallbackIntent,
                PendingIntent.FLAG_IMMUTABLE
            ).intentSender
        )
    }

    fun extractArchiveHolderForPreview(holder: ArchiveFileHolder, onDone: (String) -> Unit) {
        isLoading = true
        scope.launch {
            val newFilePath = holder.extractForPreview()
            isLoading = false
            withContext(Dispatchers.Main) {
                onDone(newFilePath)
            }
        }
    }

    fun toggleApkDialog(file: LocalFileHolder?) {
        if (file isNot null) {
            targetFile = file
            _dialogsState.update { it.copy(showApkDialog = true) }
        } else {
            targetFile = null
            _dialogsState.update { it.copy(showApkDialog = false) }
        }
    }

    fun toggleFileOptionsMenu(file: ContentHolder?, clear: Boolean = true) {
        if (file isNot null) {
            targetFile = file
            _dialogsState.update { it.copy(showFileOptionsDialog = true) }
        } else {
            if (clear) targetFile = null
            _dialogsState.update { it.copy(showFileOptionsDialog = false) }
        }
    }

    fun toggleCompressTaskDialog(task: CompressTask?) {
        if (task isNot null) {
            compressTaskHolder = task
            _dialogsState.update { it.copy(showNewZipFileDialog = true) }
        } else {
            _dialogsState.update { it.copy(showNewZipFileDialog = false) }
        }
    }

    /**
     * Nama folder tujuan untuk "Ekstrak ke ./<nama arsip>/".
     * Akhiran gabungan seperti .tar.gz dibuang sekaligus.
     */
    fun getArchiveBaseName(archive: LocalFileHolder): String {
        val fileName = archive.file.name
        val compoundSuffixes = listOf(
            ".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".tbz", ".txz"
        )

        compoundSuffixes.forEach { suffix ->
            if (fileName.endsWith(suffix, ignoreCase = true)) {
                return fileName.dropLast(suffix.length)
            }
        }

        return fileName.substringBeforeLast('.', fileName)
    }

    fun toggleExtractArchiveDialog(archive: LocalFileHolder?) {
        if (archive == null) {
            pendingArchiveExtraction = null
            _dialogsState.update { it.copy(showExtractArchiveDialog = false) }
            return
        }

        val currentFolder = activeFolder as? LocalFileHolder ?: return
        pendingArchiveExtraction = PendingArchiveExtraction(
            archive = archive,
            destinationPath = File(currentFolder.file, getArchiveBaseName(archive)).absolutePath
        )
        _dialogsState.update { it.copy(showExtractArchiveDialog = true) }
    }

    fun dismissExtractArchiveDialog() {
        pendingArchiveExtraction = null
        _dialogsState.update { it.copy(showExtractArchiveDialog = false) }
    }

    fun confirmExtractDestination(destinationPath: String) {
        val pending = pendingArchiveExtraction ?: return
        pendingArchiveExtraction = null
        _dialogsState.update { it.copy(showExtractArchiveDialog = false) }
        startArchiveExtraction(pending.archive, destinationPath)
    }

    /**
     * Memulai ekstraksi. Jika arsip terenkripsi, dialog password ditampilkan
     * lebih dulu agar task tidak langsung gagal.
     */
    fun startArchiveExtraction(archive: LocalFileHolder, destinationPath: String) {
        scope.launch {
            val needsPassword = ArchiveFormat.requiresPassword(archive)

            withContext(Dispatchers.Main) {
                if (needsPassword) {
                    pendingArchiveExtraction = PendingArchiveExtraction(
                        archive = archive,
                        destinationPath = destinationPath
                    )
                    _dialogsState.update { it.copy(showArchivePasswordDialog = true) }
                } else {
                    runExtractTask(archive, destinationPath, null)
                }
            }
        }
    }

    fun dismissArchivePasswordDialog() {
        pendingArchiveExtraction = null
        _dialogsState.update { it.copy(showArchivePasswordDialog = false) }
    }

    fun confirmArchivePassword(password: String) {
        val pending = pendingArchiveExtraction ?: return
        pendingArchiveExtraction = null
        _dialogsState.update { it.copy(showArchivePasswordDialog = false) }
        runExtractTask(pending.archive, pending.destinationPath, password)
    }

    private fun runExtractTask(
        archive: LocalFileHolder,
        destinationPath: String,
        password: String?
    ) {
        globalClass.taskManager.addTaskAndRun(
            ExtractTask(archive, password),
            ExtractTaskParameters(destinationPath, password)
        )
    }

    fun toggleBookmarksDialog(show: Boolean) {
        _dialogsState.update { it.copy(showBookmarkDialog = show) }
    }

    fun toggleDeleteConfirmationDialog(show: Boolean) {
        _dialogsState.update { it.copy(showConfirmDeleteDialog = show) }
    }

    fun toggleCreateNewFileDialog(show: Boolean) {
        _dialogsState.update { it.copy(showCreateNewFileDialog = show) }
    }

    fun toggleTasksPanel(show: Boolean) {
        _dialogsState.update { it.copy(showTasksPanel = show) }
    }

    fun toggleSortingMenu(show: Boolean) {
        _dialogsState.update { it.copy(showSortingMenu = show) }
    }

    fun toggleViewConfigDialog(show: Boolean) {
        _dialogsState.update { it.copy(showViewConfigDialog = show) }
    }

    fun toggleSearchPenal(show: Boolean) {
        _dialogsState.update { it.copy(showSearchPenal = show) }
    }

    fun toggleRenameDialog(show: Boolean) {
        _dialogsState.update { it.copy(showRenameDialog = show) }
    }

    fun toggleOpenWithDialog(show: Boolean) {
        _dialogsState.update { it.copy(showOpenWithDialog = show) }
    }

    fun toggleFilePropertiesDialog(show: Boolean) {
        _dialogsState.update { it.copy(showFileProperties = show) }
    }

    fun toggleImportPrefsDialog(file: LocalFileHolder?) {
        targetFile = file
        _dialogsState.update { it.copy(showImportPrefsDialog = file != null) }
    }
}