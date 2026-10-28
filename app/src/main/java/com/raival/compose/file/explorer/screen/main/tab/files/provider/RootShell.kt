package com.raival.compose.file.explorer.screen.main.tab.files.provider

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.rootfs.RootFsClient
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.RootLocalFileHolder
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Akses folder yang tidak bisa dibaca app biasa.
 *
 * Jalur utama memakai root service berbasis java.nio.file (seperti
 * MaterialFiles). Shell `su` hanya dipakai sebagai fallback kalau service tidak
 * bisa dijalankan, misalnya pada root manager yang tidak kompatibel.
 */
object RootShell {
    /** Tunggu cukup lama karena prompt Magisk pertama butuh interaksi pengguna. */
    private const val TIMEOUT_SECONDS = 60L

    private val availabilityLock = Mutex()

    @Volatile
    private var granted: Boolean? = null

    suspend fun isAvailable(): Boolean {
        if (granted == true) return true

        // Pemanggil bisa datang bersamaan (startup + buka folder), jadi cek
        // izin diserialkan agar prompt Magisk tidak muncul dobel. Hasil gagal
        // tidak di-cache supaya izin yang diberikan belakangan langsung dipakai.
        return availabilityLock.withLock {
            if (granted == true) return@withLock true

            val ok = RootFsClient.isRootAvailable() ||
                    run("id")?.contains("uid=0") == true
            granted = ok
            if (!ok) {
                logWarning("akses root tidak tersedia: ${RootFsClient.lastError}")
            }
            ok
        }
    }

    /**
     * Folder yang isinya perlu dibaca lewat root agar lengkap. Penyimpanan
     * pengguna tetap dibaca langsung supaya operasi tulis tidak ikut dibatasi.
     */
    fun prefersRootListing(directory: File): Boolean {
        val path = directory.absolutePath

        // Alias penyimpanan pengguna, termasuk yang dipakai Android modern.
        val userStorage = listOf(
            "/storage",
            "/mnt/user",
            "/mnt/runtime",
            "/mnt/pass_through",
            "/data/media"
        )

        if (userStorage.any { prefix -> path == prefix || path.startsWith("$prefix/") }) {
            return false
        }

        // /proc, /sys, dan /dev sudah bisa dibaca langsung. Membacanya lewat
        // root hanya memperlambat karena jumlah entrinya sangat banyak.
        val virtualFs = listOf("/proc", "/sys", "/dev")
        if (virtualFs.any { prefix -> path == prefix || path.startsWith("$prefix/") }) {
            return false
        }

        return true
    }

    /**
     * Membaca isi [directory] lewat root.
     *
     * Mengembalikan null kalau root tidak tersedia atau permintaan ditolak,
     * sehingga pemanggil bisa membedakannya dari folder yang memang kosong.
     */
    suspend fun list(directory: File): ArrayList<LocalFileHolder>? {
        if (!isAvailable()) return null

        // Jalur utama: root service membaca direktori dengan NIO di proses root.
        val nioEntries = RootFsClient.list(directory)

        if (nioEntries != null) {
            // Bandingkan dengan shell root. Kalau NIO mengembalikan daftar parsial,
            // shell yang lebih lengkap dipakai supaya isi folder tidak hilang.
            val shellEntries = listUsingShell(directory)

            if (shellEntries != null && shellEntries.size > nioEntries.size) {
                return shellEntries
            }

            val shellByName = shellEntries?.associateBy { it.displayName }

            return nioEntries.mapTo(ArrayList(nioEntries.size)) { entry ->
                val shellEntry = shellByName?.get(entry.name)
                val isSymlink = entry.isSymlink

                RootLocalFileHolder(
                    file = File(directory, entry.name),
                    folder = entry.isDirectory,
                    // stat (lstat) memberi ukuran/waktu symlink itu sendiri;
                    // NIO File.length() mengikuti target sehingga bisa 0.
                    length = if (isSymlink && shellEntry != null) {
                        shellEntry.size
                    } else {
                        entry.size
                    },
                    modified = if (isSymlink && shellEntry != null) {
                        shellEntry.lastModified
                    } else {
                        entry.modified
                    },
                    isSymlink = isSymlink,
                    isBrokenSymlink = entry.isBrokenSymlink,
                )
            }
        }

        return listUsingShell(directory)
    }

    /** Fallback lama: daftar lewat `ls` dan metadata lewat `stat`. */
    private suspend fun listUsingShell(directory: File): ArrayList<LocalFileHolder>? {
        val path = directory.absolutePath

        // -p menambahkan "/" pada folder, jadi jenis entri tidak bergantung
        // pada stat. Kalau -p tidak didukung, pakai daftar nama biasa.
        val raw = run("ls -A1p ${quote(path)}")
            ?: run("ls -A1 ${quote(path)}")
            ?: run("/system/bin/ls -A1p ${quote(path)}")

        if (raw == null) {
            logError("ls gagal: $path")
            return null
        }

        val entries = raw.lines().filter { it.isNotEmpty() }
        if (entries.isEmpty()) return arrayListOf()

        val metadata = readMetadata(path, entries.map { it.removeSuffix("/") })

        return entries.mapTo(ArrayList<LocalFileHolder>(entries.size)) { entry ->
            val isFolder = entry.endsWith("/")
            val file = File(directory, entry.removeSuffix("/"))
            val meta = metadata[file.absolutePath]

            RootLocalFileHolder(
                file = file,
                // Symlink seperti /sdcard tidak diberi "/", jadi cek juga
                // lewat File.isDirectory sebelum dianggap berkas.
                folder = isFolder || file.isDirectory,
                length = meta?.first ?: 0L,
                modified = meta?.second ?: 0L,
            )
        }
    }

    /**
     * Membuat holder dengan metadata root untuk path yang disimpan sebagai
     * markah/sematan. Tanpa ini folder Root dianggap file biasa.
     */
    suspend fun holderFor(path: File): LocalFileHolder {
        if (!prefersRootListing(path)) return LocalFileHolder(path)

        val stat = RootFsClient.stat(path) ?: return LocalFileHolder(path)

        return RootLocalFileHolder(
            file = path,
            folder = stat.isDirectory,
            length = stat.size,
            modified = stat.modified,
            isSymlink = stat.isSymlink,
            isBrokenSymlink = stat.isBrokenSymlink,
        )
    }

    /**
     * Cek keberadaan path. Null berarti status root tidak bisa dipastikan;
     * pemanggil sebaiknya tidak menghapus markah dalam kondisi itu.
     */
    suspend fun exists(path: File): Boolean? =
        if (prefersRootListing(path)) {
            RootFsClient.exists(path)
        } else {
            path.exists()
        }

    /**
     * Membaca ukuran dan waktu modifikasi dalam satu panggilan `stat`.
     * Metadata bersifat opsional: kalau gagal, entri tetap ditampilkan.
     */
    private suspend fun readMetadata(
        directory: String,
        names: List<String>,
    ): Map<String, Pair<Long, Long>> {
        if (names.isEmpty()) return emptyMap()

        // Folder sangat besar seperti /data/dalvik-cache bisa membuat satu
        // perintah stat melewati batas argumen. Metadata opsional, jadi daftar
        // nama tetap ditampilkan meski ukuran dan tanggalnya tidak dibaca.
        if (names.size > 1500) return emptyMap()

        val command = buildString {
            append("stat -c '%n|%s|%Y'")
            names.forEach { append(' ').append(quote("$directory/$it")) }
        }
        val output = run(command) ?: return emptyMap()

        val metadata = HashMap<String, Pair<Long, Long>>(names.size)
        output.lines().filter { it.isNotEmpty() }.forEach { line ->
            // Nama berkas bisa memuat "|", jadi pisahkan dua field terakhir.
            val lastSeparator = line.lastIndexOf('|')
            if (lastSeparator <= 0) return@forEach
            val secondSeparator = line.lastIndexOf('|', lastSeparator - 1)
            if (secondSeparator <= 0) return@forEach

            val size = line.substring(secondSeparator + 1, lastSeparator).toLongOrNull()
                ?: return@forEach
            val modified = line.substring(lastSeparator + 1).toLongOrNull()
                ?: return@forEach

            metadata[line.substring(0, secondSeparator)] = size to modified * 1_000L
        }
        return metadata
    }

    /**
     * Menjalankan [command] sebagai root dan mengembalikan stdout-nya.
     * Null berarti su tidak ada, izin ditolak, timeout, atau exit code bukan 0.
     */
    suspend fun run(command: String): String? = withContext(Dispatchers.IO) {
        // Shell libsu dipakai lebih dulu karena sama dengan root service NIO.
        // Kalau gagal, baru memakai proses `su -c` seperti sebelumnya.
        try {
            val result = Shell.cmd(command).exec()
            if (result.isSuccess) {
                result.out.joinToString("\n")
            } else {
                logWarning("libsu exit=${result.code} cmd=$command")
                runUsingProcessBuilder(command)
            }
        } catch (_: Throwable) {
            runUsingProcessBuilder(command)
        }
    }

    private suspend fun runUsingProcessBuilder(command: String): String? =
        withContext(Dispatchers.IO) {
            try {
                // stderr dipisah agar pesan error Magisk tidak bercampur dengan
                // nama berkas hasil ls/stat. -M memakai mount namespace global.
                val process = try {
                    ProcessBuilder("su", "-M", "-c", command)
                        .redirectErrorStream(false)
                        .start()
                } catch (_: Exception) {
                    ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(false)
                        .start()
                }

                val output = StringBuilder()
                val reader = Thread {
                    try {
                        process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
                    } catch (_: Exception) {
                        // Proses bisa dimatikan karena timeout; abaikan.
                    }
                }.apply {
                    isDaemon = true
                    start()
                }

                // Buang stderr, tapi simpan cuplikannya untuk log kegagalan.
                val errors = StringBuilder()
                val errorDrain = Thread {
                    try {
                        process.errorStream.bufferedReader().forEachLine {
                            if (errors.length < 500) errors.appendLine(it)
                        }
                    } catch (_: Exception) {
                    }
                }.apply {
                    isDaemon = true
                    start()
                }

                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    reader.join(1_000)
                    errorDrain.join(1_000)
                    logWarning("su timeout: $command")
                    return@withContext null
                }

                reader.join(2_000)
                errorDrain.join(2_000)
                if (process.exitValue() == 0) {
                    output.toString()
                } else {
                    logWarning("su exit=${process.exitValue()} cmd=$command err=${errors.toString().trim()}")
                    null
                }
            } catch (e: Exception) {
                logWarning("su gagal: ${e.message}")
                null
            }
        }

    private fun logWarning(message: String) {
        runCatching { globalClass.logger.logWarning("RootShell: $message") }
    }

    private fun logError(message: String) {
        runCatching { globalClass.logger.logError("RootShell: $message") }
    }

    /** Kutip path agar aman terhadap spasi dan karakter shell. */
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
