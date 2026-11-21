package com.rout.navi.coil.video

import android.graphics.BitmapFactory.decodeByteArray
import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.videoFileType
import java.io.File

/**
 * Membaca sampul yang tertanam di dalam berkas video, misalnya atom `covr`
 * pada MP4 hasil unduhan. Sampul ini jauh lebih informatif daripada frame
 * video pertama yang sering hitam, jadi dekoder ini didaftarkan sebelum
 * [coil3.video.VideoFrameDecoder] dan frame video dipakai sebagai cadangan
 * ketika berkas tidak membawa sampul.
 */
class VideoCoverDecoder(
    private val source: File,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val cover = retriever.embeddedPicture ?: return null
            val bitmap = decodeByteArray(cover, 0, cover.size) ?: return null
            // Sampul tertanam bisa berupa JPEG dengan penanda putar sendiri.
            // Bila bentuknya berlawanan dengan video, gambar diputar dulu
            // supaya tidak tampil miring.
            val putar = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val gambar = if (putar.butuhPutarSampul(bitmap.width, bitmap.height)) {
                android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height,
                    android.graphics.Matrix().apply { postRotate(putar.toFloat()) },
                    true
                )
            } else {
                bitmap
            }
            // isSampled = false karena sampul sudah berukuran penuh; Coil
            // menyesuaikan ukuran sendiri saat menggambar.
            DecodeResult(image = gambar.asImage(), isSampled = false)
        } catch (_: Exception) {
            null
        } finally {
            // Dilepas di finally supaya tidak bocor saat setDataSource atau
            // decode gagal di tengah jalan.
            runCatching { retriever.release() }
        }
    }

    /**
     * Sampul perlu diputar bila bentuknya diagonal berlawanan dengan sudut
     * putar video. Sampul yang sudah tegak dibiarkan apa adanya.
     */
    private fun Int.butuhPutarSampul(lebarSampul: Int, tinggiSampul: Int): Boolean =
        (this == 90 || this == 270) && lebarSampul > tinggiSampul

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val file = result.source.file().toFile()
            if (file.exists() && videoFileType.contains(file.extension.lowercase())) {
                return VideoCoverDecoder(file)
            }
            return null
        }
    }
}
