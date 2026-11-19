package com.raival.compose.file.explorer.screen.preferences.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerSettingsActivity

@Composable
fun VideoPlayerContainer() {
    val context = LocalContext.current

    Container(title = stringResource(R.string.video_player)) {
        PreferenceItem(
            label = stringResource(R.string.video_player_settings),
            supportingText = stringResource(R.string.video_player_settings_desc),
            icon = Icons.Rounded.PlayCircle,
            onClick = {
                context.startActivity(
                    Intent(context, VideoPlayerSettingsActivity::class.java)
                )
            }
        )
    }
}
