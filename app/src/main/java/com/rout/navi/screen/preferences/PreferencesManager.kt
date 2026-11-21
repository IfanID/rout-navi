package com.rout.navi.screen.preferences

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.common.emptyString
import com.rout.navi.common.fromJson
import com.rout.navi.common.toJson
import com.rout.navi.screen.main.startup.StartupTabs
import com.rout.navi.screen.main.tab.files.holder.ContentHolder
import com.rout.navi.screen.main.tab.files.misc.DefaultOpeningMethods
import com.rout.navi.screen.main.tab.files.misc.FileSortingPrefs
import com.rout.navi.screen.main.tab.files.misc.SortingMethod.SORT_BY_NAME
import com.rout.navi.screen.main.tab.files.misc.ViewConfigs
import com.rout.navi.screen.main.tab.home.data.getDefaultHomeLayout
import com.rout.navi.screen.preferences.constant.FileItemSize
import com.rout.navi.screen.preferences.constant.ThemePreference
import com.rout.navi.screen.preferences.misc.prefDataStore
import com.rout.navi.screen.preferences.misc.prefMutableState
import com.rout.navi.screen.preferences.riwayat.KunciRiwayat
import com.rout.navi.screen.viewer.video.WatchEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class PreferencesManager {
    val singleChoiceDialog = SingleChoiceDialog()

    /** Penulisan riwayat yang ditunda, terpisah dari scope layar. */
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Kunci markah dipakai juga oleh penulisan langsung ke DataStore. */
    private val bookmarksKey = stringSetPreferencesKey("bookmarks")

    //---------- Appearance -------------//
    var theme by prefMutableState(
        keyName = "theme",
        defaultValue = ThemePreference.SYSTEM.ordinal,
        getPreferencesKey = { intPreferencesKey(it) }
    )

    var showBottomBarLabels by prefMutableState(
        keyName = "showBottomBarLabels",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var homeTabLayout by prefMutableState(
        keyName = "homeTabLayout",
        defaultValue = getDefaultHomeLayout().toJson(),
        getPreferencesKey = { stringPreferencesKey(it) }
    )

    var dateTimeFormat by prefMutableState(
        keyName = "dateTimeFormat",
        defaultValue = "MMM dd, yyyy HH:mm:ss",
        getPreferencesKey = { stringPreferencesKey(it) }
    )

    var hideToolbar by prefMutableState(
        keyName = "hideToolbar",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )


    //---------- File List -------------//
    var itemSize by prefMutableState(
        keyName = "fileListSize",
        defaultValue = FileItemSize.MEDIUM.ordinal,
        getPreferencesKey = { intPreferencesKey(it) }
    )

    var showFolderContentCount by prefMutableState(
        keyName = "showFolderContentCount",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var showHiddenFiles by prefMutableState(
        keyName = "showHiddenFiles",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    //---------- Behavior -------------//
    var showFileOptionMenuOnLongClick by prefMutableState(
        keyName = "showFileOptionMenuOnLongClick",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var disablePullDownToRefresh by prefMutableState(
        keyName = "disablePullDownToRefresh",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var skipHomeWhenTabClosed by prefMutableState(
        keyName = "skipHomeWhenTabClosed",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var closeTabOnBackPress by prefMutableState(
        keyName = "closeTabOnBackPress",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var confirmBeforeAppClose by prefMutableState(
        keyName = "confirmBeforeAppClose",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    /** Penyambutan pertama dibaca sinkron saat aktivitas dibuka. */
    var onboardingCompleted by prefMutableState(
        keyName = "onboardingCompleted",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var rememberLastSession by prefMutableState(
        keyName = "rememberLastSession",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var lastSessionTabs by prefMutableState(
        keyName = "lastSessionTabs",
        defaultValue = StartupTabs.default().toJson(),
        getPreferencesKey = { stringPreferencesKey(it) }
    )

    var useBuiltInViewer by prefMutableState(
        keyName = "useBuiltInViewer",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var startupTabs by prefMutableState(
        keyName = "startupTabs",
        defaultValue = StartupTabs.default().toJson(),
        getPreferencesKey = { stringPreferencesKey(it) }
    )

    var bookmarks by prefMutableState(
        keyName = "bookmarks",
        defaultValue = emptySet(),
        getPreferencesKey = { stringSetPreferencesKey(it) }
    )

    /**
     * Menyimpan markah langsung ke DataStore lalu menyelaraskan state sesi.
     *
     * Penulisan lewat state Compose saja tidak cukup: nilainya bisa kembali ke
     * versi lama saat proses aplikasi berakhir sebelum DataStore selesai
     * ditulis. Fungsi ini mengembalikan isi yang benar-benar tersimpan sehingga
     * pemanggil bisa memverifikasinya.
     */
    suspend fun saveBookmarks(value: Set<String>): Set<String> {
        globalClass.prefDataStore.edit { it[bookmarksKey] = value }
        bookmarks = value
        return globalClass.prefDataStore.data.first()[bookmarksKey] ?: emptySet()
    }

    /** Membaca markah dari DataStore, bukan dari state sesi yang mungkin basi. */
    suspend fun loadBookmarks(): Set<String> {
        val stored = globalClass.prefDataStore.data.first()[bookmarksKey] ?: emptySet()
        if (stored != bookmarks) bookmarks = stored
        return stored
    }

    //---------- Riwayat tontonan -------------//

    /**
     * Riwayat tontonan disimpan dengan format "path|waktu|posisi|durasi".
     * Path disimpan apa adanya; kunci untuk membandingkan berkas dihitung saat
     * dibaca lewat KunciRiwayat, sehingga aturannya bisa diperbaiki tanpa
     * menulis ulang data lama.
     */
    private val watchHistoryKey = stringSetPreferencesKey("watchHistory")

    /** Penulisan tertunda terakhir, diganti bila ada data yang lebih baru. */
    private var tulisTertunda: Job? = null

    /** Antrean penulisan riwayat, supaya tidak ada dua penulisan yang menimpa. */
    private val historyMutex = Mutex()

    /**
     * Penanda perubahan riwayat. Setiap kali catatan ditulis, nilainya naik,
     * sehingga halaman riwayat bisa memuat ulang tanpa menunggu tab diganti.
     */
    private val _historyVersion = MutableStateFlow(0L)
    val historyVersion: StateFlow<Long> = _historyVersion.asStateFlow()

    /**
     * Mencatat video yang sedang ditonton. Entri dengan berkas sama diganti,
     * bukan ditambah, sehingga daftar tetap satu baris per video. Semua
     * penulisan lewat antrean supaya dua penulisan tidak saling menimpa.
     */
    suspend fun recordWatchHistory(path: String, positionMs: Long, durationMs: Long) {
        historyMutex.withLock {
            tulisRiwayat(path, positionMs, durationMs)
        }
    }

    /**
     * Mencatat posisi berkala tanpa menahan pemanggil. Kalau ada penulisan
     * yang belum jalan, diganti dengan data terbaru.
     */
    fun catatPosisiTontonan(path: String, positionMs: Long, durationMs: Long) {
        tulisTertunda?.cancel()
        tulisTertunda = historyScope.launch {
            delay(HISTORY_WRITE_DELAY_MS)
            historyMutex.withLock { tulisRiwayat(path, positionMs, durationMs) }
        }
    }

    /**
     * Menulis posisi seketika, dipakai saat pemutar ditutup atau dijeda.
     * Tidak ditunda supaya riwayat langsung benar ketika pengguna kembali.
     */
    fun simpanPosisiSeketika(path: String, positionMs: Long, durationMs: Long) {
        tulisTertunda?.cancel()
        historyScope.launch {
            historyMutex.withLock { tulisRiwayat(path, positionMs, durationMs) }
        }
    }

    private suspend fun tulisRiwayat(path: String, positionMs: Long, durationMs: Long) {
        val kunci = KunciRiwayat.dari(path)
        val tersimpan = globalClass.prefDataStore.data.first()[watchHistoryKey] ?: emptySet()

        // Catatan lama untuk berkas yang sama, termasuk yang tersimpan dengan
        // jalur atau URI berbeda bentuk. Semuanya dibuang lalu diganti satu.
        val dibuang = tersimpan.filter { item ->
            item.split('|').size < 4 || KunciRiwayat.sama(bacaKunci(item), kunci)
        }
        val sebelumnya = tersimpan.firstOrNull {
            it.split('|').size >= 4 && KunciRiwayat.sama(bacaKunci(it), kunci)
        }

        // Durasi nol berarti pencatatan pembukaan saja; progres lama dipertahankan.
        val posisi = if (durationMs <= 0L && sebelumnya != null) bacaPosisi(sebelumnya) else positionMs.coerceAtLeast(0L)
        val durasi = if (durationMs <= 0L && sebelumnya != null) bacaDurasi(sebelumnya) else durationMs.coerceAtLeast(0L)

        // Path asli disimpan, bukan kunci. Kunci dihitung saat dibaca, sehingga
        // aturan kunci bisa diperbaiki tanpa merusak data yang sudah ada.
        val entri = WatchEntry(
            key = KunciRiwayat.berkasDari(path).path,
            lastWatchedAt = System.currentTimeMillis(),
            positionMs = posisi,
            durationMs = durasi
        ).let { "${it.key}|${it.lastWatchedAt}|${it.positionMs}|${it.durationMs}" }

        val baru = tersimpan
            .filterNot { it in dibuang }
            .plus(entri)
            .sortedByDescending { bacaWaktu(it) }
            .take(200)

        globalClass.prefDataStore.edit {
            // Data lama berbentuk "waktu|path" dikonversi sekali di sini.
            val bersih = baru.map { if (it.split('|').size >= 4) it else konversiLama(it) }
            it[watchHistoryKey] = bersih.toSet()
        }
        _historyVersion.value += 1
    }

    /** Menghapus posisi simpanan, dipakai tombol "Tonton dari awal". */
    suspend fun clearWatchPosition(path: String) {
        historyMutex.withLock {
            val kunci = KunciRiwayat.dari(path)
            val tersimpan = globalClass.prefDataStore.data.first()[watchHistoryKey] ?: emptySet()
            val lama = tersimpan.firstOrNull {
                it.split('|').size >= 4 && KunciRiwayat.sama(bacaKunci(it), kunci)
            } ?: return@withLock
            val dibuang = tersimpan.filter { KunciRiwayat.sama(bacaKunci(it), kunci) }.toSet()
            val direset = "${bacaKunci(lama)}|${bacaWaktu(lama)}|0|${bacaDurasi(lama)}"
            globalClass.prefDataStore.edit {
                it[watchHistoryKey] = (tersimpan.filterNot { item -> item in dibuang } + direset).toSet()
            }
            _historyVersion.value += 1
        }
    }

    /**
     * Menghapus entri riwayat tanpa menyentuh berkas videonya. Dipakai tombol
     * hapus di tab riwayat.
     */
    suspend fun deleteWatchHistory(paths: Collection<String>) {
        if (paths.isEmpty()) return
        historyMutex.withLock {
            val tersimpan = globalClass.prefDataStore.data.first()[watchHistoryKey] ?: emptySet()
            val dibuang = tersimpan.filter { item ->
                paths.any { KunciRiwayat.sama(bacaKunci(item), it) }
            }.toSet()
            globalClass.prefDataStore.edit { it[watchHistoryKey] = tersimpan.filterNot { item -> item in dibuang }.toSet() }
            _historyVersion.value += 1
        }
    }

    /** Mengosongkan seluruh riwayat, seperti clearHistory di vlc-android. */
    suspend fun clearWatchHistory() {
        historyMutex.withLock {
            globalClass.prefDataStore.edit { it.remove(watchHistoryKey) }
            _historyVersion.value += 1
        }
    }

    /** Riwayat tontonan, entri untuk berkas yang sama digabung jadi satu. */
    suspend fun loadWatchHistory(): List<WatchEntry> {
        val tersimpan = globalClass.prefDataStore.data.first()[watchHistoryKey] ?: emptySet()
        val parsed = tersimpan.mapNotNull { parseEntri(it) }
        return parsed
            .groupBy { KunciRiwayat.dari(it.key) }
            .map { (_, entri) ->
                // Data dengan progres diutamakan supaya penggabungan tidak
                // menghapus posisi tonton; waktu dipakai dari yang terbaru.
                val terbaru = entri.maxByOrNull { it.lastWatchedAt }!!
                val berprogres = entri.filter { it.durationMs > 0 }.maxByOrNull { it.lastWatchedAt }
                if (berprogres != null) berprogres.copy(lastWatchedAt = terbaru.lastWatchedAt) else terbaru
            }
            .sortedByDescending { it.lastWatchedAt }
    }

    /** Posisi tersimpan untuk satu berkas, null bila belum pernah ditonton. */
    suspend fun watchEntryFor(path: String): WatchEntry? {
        val kunci = KunciRiwayat.dari(path)
        return loadWatchHistory().firstOrNull { KunciRiwayat.dari(it.key) == kunci }
    }

    private fun parseEntri(item: String): WatchEntry? {
        val konversi = if (item.split('|').size >= 4) item else konversiLama(item)
        val parts = konversi.split('|')
        if (parts.size < 4) return null
        val waktu = parts[1].toLongOrNull() ?: return null
        return WatchEntry(
            key = parts[0],
            lastWatchedAt = waktu,
            positionMs = parts[2].toLongOrNull() ?: 0L,
            durationMs = parts[3].toLongOrNull() ?: 0L
        )
    }

    /** Data lama "waktu|path" diubah ke bentuk baru tanpa kehilangan waktu. */
    private fun konversiLama(item: String): String {
        val waktu = item.substringBefore('|').toLongOrNull() ?: 0L
        val jalur = item.substringAfter('|', "")
        if (jalur.isBlank()) return ""
        return "${KunciRiwayat.berkasDari(jalur).path}|$waktu|0|0"
    }

    private fun bacaKunci(item: String): String = item.substringBefore('|')

    private fun bacaWaktu(item: String): Long =
        item.split('|').let { if (it.size >= 4) it[1].toLongOrNull() ?: 0L else it[0].toLongOrNull() ?: 0L }

    private fun bacaPosisi(item: String): Long =
        item.split('|').let { if (it.size >= 4) it[2].toLongOrNull() ?: 0L else 0L }

    private fun bacaDurasi(item: String): Long =
        item.split('|').let { if (it.size >= 4) it[3].toLongOrNull() ?: 0L else 0L }

    //---------- Urutan riwayat tontonan -------------//

    /**
     * Urutan riwayat tontonan. Nilai terakhir adalah bawaan, karena riwayat
     * biasanya dibaca dari yang paling baru diputar.
     */
    var historySortOrder by prefMutableState(
        keyName = "historySortOrder",
        defaultValue = HISTORY_SORT_NEWEST,
        getPreferencesKey = { intPreferencesKey(it) }
    )

    companion object {
        /** Penundaan penulisan riwayat agar tidak menulis tiap detik. */
        private const val HISTORY_WRITE_DELAY_MS = 3_000L

        const val HISTORY_SORT_NEWEST = 0
        const val HISTORY_SORT_OLDEST = 1
        const val HISTORY_SORT_NAME_ASC = 2
        const val HISTORY_SORT_NAME_DESC = 3
    }

    var pinnedFiles by prefMutableState(
        keyName = "pinnedFiles",
        defaultValue = emptySet(),
        getPreferencesKey = { stringSetPreferencesKey(it) }
    )

    var excludedPathsFromRecentFiles by prefMutableState(
        keyName = "excludedPathFromRecentFiles",
        defaultValue = emptySet(),
        getPreferencesKey = { stringSetPreferencesKey(it) }
    )

    var removeHiddenPathsFromRecentFiles by prefMutableState(
        keyName = "removeHiddenPathsFromRecentFiles",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    //---------- File Operation -------------//
    var signMergedApkBundleFiles by prefMutableState(
        keyName = "signMergedApkBundleFiles",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var moveToRecycleBin by prefMutableState(
        keyName = "moveToRecycleBin",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var defaultOpeningMethods by prefMutableState(
        keyName = "defaultOpeningMethods",
        defaultValue = DefaultOpeningMethods().toJson(),
        getPreferencesKey = { stringPreferencesKey(it) }
    )

    //---------- Text Editor -------------//
    var pinLineNumber by prefMutableState(
        keyName = "pinLineNumber",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var symbolPairAutoCompletion by prefMutableState(
        keyName = "symbolPairAutoCompletion",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var autoIndent by prefMutableState(
        keyName = "autoIndent",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var enableMagnifier by prefMutableState(
        keyName = "enableMagnifier",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var useICULibToSelectWords by prefMutableState(
        keyName = "useICULibToSelectWords",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var deleteEmptyLineFast by prefMutableState(
        keyName = "deleteEmptyLineFast",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var deleteMultiSpaces by prefMutableState(
        keyName = "deleteMultiSpaces",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var recentFilesLimit by prefMutableState(
        keyName = "textEditorRecentFilesLimit",
        defaultValue = -1,
        getPreferencesKey = { intPreferencesKey(it) }
    )

    var readOnly by prefMutableState(
        keyName = "readOnly",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var wordWrap by prefMutableState(
        keyName = "wordWrap",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )


    // //---------- File Sorting -------------//
    var defaultSortMethod by prefMutableState(
        keyName = "filesSortingMethod",
        defaultValue = SORT_BY_NAME,
        getPreferencesKey = { intPreferencesKey(it) }
    )

    var showFoldersFirst by prefMutableState(
        keyName = "showFoldersFirst",
        defaultValue = true,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    var reverse by prefMutableState(
        keyName = "reverseFilesSortingMethod",
        defaultValue = false,
        getPreferencesKey = { booleanPreferencesKey(it) }
    )

    fun getSortingPrefsFor(content: ContentHolder): FileSortingPrefs {
        return runBlocking {
            fromJson(
                globalClass.prefDataStore.data.first()[stringPreferencesKey("fileSortingPrefs_${content.uniquePath}")]
            ) ?: getDefaultSortingPrefs()
        }
    }

    fun getDefaultSortingPrefs() = FileSortingPrefs(
        sortMethod = defaultSortMethod,
        showFoldersFirst = showFoldersFirst,
        reverseSorting = reverse
    )

    fun getDefaultViewConfigPrefs(): ViewConfigs {
        return runBlocking {
            fromJson(
                globalClass.prefDataStore.data.first()[stringPreferencesKey("viewConfigPrefs")]
            ) ?: ViewConfigs()
        }
    }

    fun setDefaultViewConfigPrefs(prefs: ViewConfigs) {
        runBlocking {
            globalClass.prefDataStore.edit {
                it[stringPreferencesKey("viewConfigPrefs")] = prefs.toJson()
            }
        }
    }

    fun getViewConfigPrefsFor(content: ContentHolder): ViewConfigs {
        return runBlocking {
            fromJson(
                globalClass.prefDataStore.data.first()[stringPreferencesKey("viewConfigPrefs_${content.uniquePath}")]
            ) ?: getDefaultViewConfigPrefs()
        }
    }

    fun setViewConfigPrefsFor(content: ContentHolder, prefs: ViewConfigs) {
        if (prefs == getDefaultViewConfigPrefs()) {
            runBlocking {
                globalClass.prefDataStore.edit {
                    it.remove(stringPreferencesKey("viewConfigPrefs_${content.uniquePath}"))
                }
            }
        } else {
            runBlocking {
                globalClass.prefDataStore.edit {
                    it[stringPreferencesKey("viewConfigPrefs_${content.uniquePath}")] =
                        prefs.toJson()
                }
            }
        }
    }

    //---------- Compression -------------//
    var defaultCompressFormat by prefMutableState(
        keyName = "defaultCompressFormat",
        defaultValue = "ZIP",
        getPreferencesKey = { stringPreferencesKey(it) }
    )

    fun setSortingPrefsFor(content: ContentHolder, prefs: FileSortingPrefs) {
        runBlocking {
            globalClass.prefDataStore.edit {
                it[stringPreferencesKey("fileSortingPrefs_${content.uniquePath}")] =
                    prefs.toJson()
            }
        }
    }

    fun deleteSortingPrefsFor(content: ContentHolder) {
        runBlocking {
            globalClass.prefDataStore.edit {
                it.remove(stringPreferencesKey("fileSortingPrefs_${content.uniquePath}"))
            }
        }
    }

    class SingleChoiceDialog {
        var show by mutableStateOf(false)

        var title = emptyString
            private set
        var description = emptyString
            private set
        var choices = mutableListOf<String>()
            private set
        var supportingTexts = mutableListOf<String>()
            private set
        var onSelect: (choice: Int) -> Unit = {}
            private set
        var selectedChoice = -1

        fun dismiss() {
            show = false
            title = emptyString
            description = emptyString
            choices.clear()
            supportingTexts.clear()
            selectedChoice = -1
            onSelect = {}
        }

        fun show(
            title: String,
            description: String,
            choices: List<String>,
            selectedChoice: Int,
            onSelect: (choice: Int) -> Unit,
            supportingTexts: List<String> = emptyList()
        ) {
            this.title = title
            this.description = description
            this.choices.clear()
            this.choices.addAll(choices)
            this.supportingTexts.clear()
            this.supportingTexts.addAll(supportingTexts)
            this.onSelect = onSelect
            this.selectedChoice = selectedChoice
            show = true
        }
    }
}
