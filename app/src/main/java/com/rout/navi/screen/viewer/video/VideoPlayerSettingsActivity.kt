package com.rout.navi.screen.viewer.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.rout.navi.common.ui.SafeSurface
import com.rout.navi.screen.viewer.video.ui.VideoPlayerSettingsContent
import com.rout.navi.theme.FileExplorerTheme

/** Layar setelan pemutar video, bisa dibuka dari HUD maupun dari Preferensi. */
class VideoPlayerSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileExplorerTheme {
                SafeSurface {
                    VideoPlayerSettingsContent(
                        prefs = VideoPlayerPreferences.get(this),
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
