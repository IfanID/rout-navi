package com.rout.navi.screen.viewer.video

import android.net.Uri
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.screen.viewer.ViewerActivity
import com.rout.navi.screen.viewer.ViewerInstance
import com.rout.navi.screen.viewer.video.ui.VideoPlayerScreen
import com.rout.navi.theme.FileExplorerTheme

class VideoPlayerActivity : ViewerActivity() {

    companion object {
        /** Posisi awal dalam milidetik; -1 berarti mulai dari awal. */
        const val EXTRA_START_POSITION = "start_position"
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        // Tombol kembali sistem juga mematikan audio seketika, bukan menunggu
        // onStop/onDestroy yang datang beberapa ratus milidetik kemudian.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                (activeInstance as? VideoPlayerInstance)?.stopPlayback()
                finish()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        // Setelan pemutar berupa panel di layar yang sama, jadi onPause hanya
        // terjadi saat aplikasi benar-benar ditinggal: audio harus berhenti
        // seketika, sedangkan surface video tidak disentuh.
        if (isFinishing) {
            (activeInstance as? VideoPlayerInstance)?.stopPlayback()
        } else {
            (activeInstance as? VideoPlayerInstance)?.pause()
        }
    }
    override fun onCreateNewInstance(
        uri: Uri,
        uid: String
    ): ViewerInstance {
        // Hanya boleh ada satu pemutar video. Tanpa ini, membuka video lain
        // dari task berbeda meninggalkan player lama yang audio-nya terus jalan.
        val manager = globalClass.viewersManager
        val others = manager.instances.filterIsInstance<VideoPlayerInstance>()
        if (others.isNotEmpty()) {
            VideoPlayerLog.info("tutup ${others.size} pemutar video lama")
            others.forEach { runCatching { it.onClose() } }
            manager.instances.removeAll(others.toSet())
        }
        return VideoPlayerInstance(
            uri = uri,
            id = uid,
            startPositionMs = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        )
    }

    override fun onStart() {
        super.onStart()
        // Kembali dari aplikasi lain: posisi dan pemutaran dipulihkan tanpa
        // menyentuh layout video.
        (activeInstance as? VideoPlayerInstance)?.resumeAfterReturn()
    }

    override fun onStop() {
        super.onStop()
        // VLC tidak memanggil pause di onStop; audio sudah dihentikan di
        // onPause. Pemanggilan ulang di sini hanya menambah satu putaran
        // output video baru dan justru mematikan gambar saat kembali.
        VideoPlayerLog.info(
            "onStop finishing=$isFinishing changing=$isChangingConfigurations"
        )
        if (isFinishing) (activeInstance as? VideoPlayerInstance)?.stopPlayback()
    }

    override fun onReady(instance: ViewerInstance) {
        val videoPlayerInstance = instance as VideoPlayerInstance
        setContent {
            FileExplorerTheme {
                VideoPlayerScreen(
                    videoUri = videoPlayerInstance.uri,
                    videoPlayerInstance = videoPlayerInstance,
                    onBackPressed = {
                        // Hentikan audio sebelum activity ditutup.
                        videoPlayerInstance.stopPlayback()
                        finish()
                    }
                )
            }
        }
    }

}