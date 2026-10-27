package com.raival.compose.file.explorer.screen.main.tab.files.archive

import com.github.junrar.Archive
import com.github.junrar.exception.WrongPasswordException
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ZipTree
import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File

object ArchiveFormat {
    private val zipExtensions = setOf("zip", "jar", "apk", "apks")

    private const val HEADER_SIZE = 512

    private enum class DetectedFormat {
        ZIP, RAR, SEVEN_Z, TAR, UNKNOWN
    }

    fun isZip(extension: String): Boolean = extension.lowercase() in zipExtensions

    fun createTree(source: LocalFileHolder, password: String? = null): ArchiveTree? =
        when (detectFormat(source)) {
            DetectedFormat.ZIP -> ZipTree(source, password)
            DetectedFormat.RAR -> RarTree(source, password)
            DetectedFormat.SEVEN_Z -> SevenZTree(source, password)
            DetectedFormat.TAR -> TarTree(source)
            DetectedFormat.UNKNOWN -> null
        }

    /**
     * Mengecek apakah arsip butuh kata sandi sebelum ekstraksi dimulai.
     * Header 7z terenkripsi terdeteksi saat dibuka, sedangkan 7z yang hanya
     * mengenkripsi isi baru terdeteksi saat blok pertama didekode.
     */
    fun requiresPassword(source: LocalFileHolder): Boolean {
        return try {
            when (detectFormat(source)) {
                DetectedFormat.ZIP -> ZipFile(source.file).isEncrypted
                DetectedFormat.RAR -> isRarPasswordRequired(source)
                DetectedFormat.SEVEN_Z -> isSevenZPasswordRequired(source)
                else -> false
            }
        } catch (e: PasswordRequiredException) {
            true
        } catch (e: WrongPasswordException) {
            true
        } catch (e: Exception) {
            logger.logError(e)
            false
        }
    }

    private fun isRarPasswordRequired(source: LocalFileHolder): Boolean {
        return try {
            Archive(source.file).use { it.isEncrypted }
        } catch (e: WrongPasswordException) {
            true
        } catch (e: Exception) {
            // RAR dengan header terenkripsi gagal dibuka sebelum kata sandi
            // diberikan, jadi pesan junrar dipakai sebagai petunjuk.
            val message = e.message?.lowercase() ?: return false
            message.contains("password") || message.contains("encrypted")
        }
    }

    private fun isSevenZPasswordRequired(source: LocalFileHolder): Boolean {
        return try {
            SevenZFile(source.file).use { sevenZ ->
                var required = false

                while (true) {
                    val entry = sevenZ.nextEntry ?: break

                    // Lewati folder dan entri kosong, lalu coba baca satu byte
                    // agar dekripsi blok pertama benar-benar dijalankan.
                    if (entry.isDirectory || entry.size <= 0) continue

                    required = try {
                        sevenZ.read(ByteArray(1), 0, 1)
                        false
                    } catch (e: PasswordRequiredException) {
                        true
                    }
                    break
                }

                required
            }
        } catch (e: PasswordRequiredException) {
            true
        } catch (e: Exception) {
            logger.logError(e)
            false
        }
    }

    /**
     * Menentukan format dari magic bytes, lalu jatuh ke ekstensi bila isi
     * berkas tidak dikenali. Ini penting untuk arsip yang namanya salah,
     * misalnya ZIP yang diberi akhiran .rar.
     */
    private fun detectFormat(source: LocalFileHolder): DetectedFormat {
        val extension = source.extension.lowercase()
        val header = readHeader(source.file)

        if (header != null) {
            when {
                isZipHeader(header) -> return DetectedFormat.ZIP
                isRarHeader(header) -> return DetectedFormat.RAR
                isSevenZHeader(header) -> return DetectedFormat.SEVEN_Z
                header.size >= HEADER_SIZE && isTarHeader(header) -> return DetectedFormat.TAR
            }
        }

        return when {
            isZip(extension) -> DetectedFormat.ZIP
            extension == "rar" -> DetectedFormat.RAR
            extension == "7z" -> DetectedFormat.SEVEN_Z
            extension == "tar" -> DetectedFormat.TAR
            else -> DetectedFormat.UNKNOWN
        }
    }

    private fun readHeader(file: File): ByteArray? {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(HEADER_SIZE)
                var read = 0

                while (read < header.size) {
                    val count = input.read(header, read, header.size - read)
                    if (count <= 0) break
                    read += count
                }

                if (read == 0) null else header.copyOf(read)
            }
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    private fun isZipHeader(header: ByteArray): Boolean {
        // ZIP diawali "PK" diikuti 03 04 (lokal), 05 06 (kosong), atau 07 08.
        return header.size >= 4 &&
                header[0] == 'P'.code.toByte() &&
                header[1] == 'K'.code.toByte() &&
                (header[2].toInt() == 3 || header[2].toInt() == 5 || header[2].toInt() == 7)
    }

    private fun isRarHeader(header: ByteArray): Boolean =
        header.startsWithAscii("Rar!")

    private fun isSevenZHeader(header: ByteArray): Boolean {
        val magic = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        return header.startsWith(magic)
    }

    private fun ByteArray.startsWithAscii(value: String): Boolean {
        if (size < value.length) return false

        return value.indices.all { index -> this[index] == value[index].code.toByte() }
    }

    private fun ByteArray.startsWith(value: ByteArray): Boolean {
        if (size < value.size) return false

        return value.indices.all { index -> this[index] == value[index] }
    }

    private fun isTarHeader(header: ByteArray): Boolean {
        // Format tar modern menulis "ustar" di offset 257.
        val magic = String(header, 257, 5, Charsets.US_ASCII)
        if (magic == "ustar") return true

        // Tar lama tidak punya magic, jadi validasi field checksum.
        val storedChecksum = String(header, 148, 8, Charsets.US_ASCII)
            .trim()
            .trimEnd('\u0000')
            .toIntOrNull(8) ?: return false

        var calculatedChecksum = 0L
        header.forEachIndexed { index, byte ->
            calculatedChecksum += if (index in 148..155) {
                32
            } else {
                byte.toInt() and 0xff
            }
        }

        return calculatedChecksum == storedChecksum.toLong()
    }
}
