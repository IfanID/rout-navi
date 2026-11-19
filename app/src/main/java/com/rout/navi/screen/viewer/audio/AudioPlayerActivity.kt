package com.rout.navi.screen.viewer.audio

import android.net.Uri
import androidx.activity.compose.setContent
import com.rout.navi.screen.viewer.ViewerActivity
import com.rout.navi.screen.viewer.ViewerInstance
import com.rout.navi.screen.viewer.audio.ui.MusicPlayerScreen
import com.rout.navi.theme.FileExplorerTheme

class AudioPlayerActivity : ViewerActivity() {
    override fun onCreateNewInstance(
        uri: Uri,
        uid: String
    ): ViewerInstance {
        return AudioPlayerInstance(uri, uid)
    }

    override fun onReady(instance: ViewerInstance) {
        setContent {
            FileExplorerTheme {
                MusicPlayerScreen(
                    audioPlayerInstance = instance as AudioPlayerInstance,
                    onClosed = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }
}