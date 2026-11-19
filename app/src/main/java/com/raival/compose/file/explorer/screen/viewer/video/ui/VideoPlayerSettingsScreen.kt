package com.raival.compose.file.explorer.screen.viewer.video.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.preferences.ui.PreferenceItem
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerPreferences

/**
 * Setelan gestur pemutar video. Nama, urutan, dan arti setiap baris mengikuti
 * layar kontrol video VLC, dengan tampilan daftar milik aplikasi ini.
 */
@Composable
fun VideoPlayerSettingsContent(
    prefs: VideoPlayerPreferences,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.video_player_settings),
                fontSize = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        VideoPlayerSettingsBody(prefs = prefs)
    }
}

/**
 * Daftar setelan tanpa header. Dipakai dua tempat: layar Preferensi dan panel
 * di dalam pemutar.
 */
@Composable
fun VideoPlayerSettingsBody(
    prefs: VideoPlayerPreferences,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
) {
        SectionTitle(stringResource(R.string.video_gesture_section))

        PreferenceItem(
            label = stringResource(R.string.video_gesture_volume),
            supportingText = stringResource(R.string.video_gesture_volume_summary),
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            switchState = prefs.volumeGesture,
            onSwitchChange = prefs::updateVolumeGesture
        )

        PreferenceItem(
            label = stringResource(R.string.video_gesture_brightness),
            supportingText = stringResource(R.string.video_gesture_brightness_summary),
            icon = Icons.Rounded.Brightness6,
            switchState = prefs.brightnessGesture,
            onSwitchChange = prefs::updateBrightnessGesture
        )

        PreferenceItem(
            label = stringResource(R.string.video_gesture_save_brightness),
            supportingText = stringResource(R.string.video_gesture_save_brightness_summary),
            icon = Icons.Rounded.LightMode,
            switchState = prefs.saveBrightness,
            onSwitchChange = prefs::updateSaveBrightness
        )

        PreferenceItem(
            label = stringResource(R.string.video_gesture_swipe_seek),
            supportingText = stringResource(R.string.video_gesture_swipe_seek_summary),
            icon = Icons.Rounded.Swipe,
            switchState = prefs.swipeSeek,
            onSwitchChange = prefs::updateSwipeSeek
        )

        PreferenceItem(
            label = stringResource(R.string.video_gesture_scale),
            supportingText = stringResource(R.string.video_gesture_scale_summary),
            icon = Icons.Rounded.ZoomOutMap,
            switchState = prefs.scaleGesture,
            onSwitchChange = prefs::updateScaleGesture
        )

        PreferenceItem(
            label = stringResource(R.string.video_gesture_double_tap_seek),
            supportingText = stringResource(R.string.video_gesture_double_tap_seek_summary),
            icon = Icons.Rounded.TouchApp,
            switchState = prefs.doubleTapSeek,
            onSwitchChange = prefs::updateDoubleTapSeek
        )

        SliderPreference(
            label = stringResource(R.string.video_double_tap_delay),
            valueText = stringResource(
                R.string.video_seconds_value,
            prefs.doubleTapJumpDelaySeconds
        ),
            value = prefs.doubleTapJumpDelaySeconds.toFloat(),
            range = 5f..30f,
            steps = 24,
            enabled = prefs.doubleTapSeek,
            onValueChange = { prefs.setDoubleTapJumpDelay(it.toInt()) }
        )

        PreferenceItem(
            label = stringResource(R.string.video_gesture_double_tap_play),
            supportingText = stringResource(R.string.video_gesture_double_tap_play_summary),
            icon = Icons.Rounded.PlayCircle,
            switchState = prefs.doubleTapPlay,
            onSwitchChange = prefs::updateDoubleTapPlay
        )

        SectionTitle(stringResource(R.string.video_fastplay_section))

        PreferenceItem(
            label = stringResource(R.string.video_fastplay),
            supportingText = stringResource(R.string.video_fastplay_summary),
            icon = Icons.Rounded.FastForward,
            switchState = prefs.fastPlay,
            onSwitchChange = prefs::updateFastPlay
        )

        SliderPreference(
            label = stringResource(R.string.video_fastplay_speed),
            valueText = stringResource(
                R.string.video_speed_value,
                formatSpeed(prefs.fastPlaySpeed)
            ),
            value = prefs.fastPlaySpeed,
            range = 1.1f..8f,
            steps = 0,
            enabled = prefs.fastPlay,
            onValueChange = { prefs.updateFastPlaySpeed(it) }
        )
    }
}


@Composable
private fun SectionTitle(title: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun SliderPreference(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}
