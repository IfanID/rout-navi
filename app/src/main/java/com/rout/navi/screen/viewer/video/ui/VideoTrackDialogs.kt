package com.rout.navi.screen.viewer.video.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rout.navi.R
import com.rout.navi.screen.viewer.video.model.VideoTrackInfo
import kotlin.math.abs

/**
 * Jarak menu ke tepi bawah, dihitung dari tinggi bar kontrol supaya menu
 * berhenti sekitar 8dp di atas tombol pemicunya.
 */
private val MenuBottomGap = 164.dp

private val MenuShape = RoundedCornerShape(PlayerRadius.Large)

/** Sudut asal animasi menu, mengikuti tombol yang membukanya. */
private val OriginBottomStart = TransformOrigin(0f, 1f)
private val OriginBottomEnd = TransformOrigin(1f, 1f)

/**
 * Daftar audio dan subtitle sebagai menu kecil di atas bar kontrol.
 *
 * Jumlah track biasanya sedikit, jadi menu dibatasi lebarnya dan tumbuh sesuai
 * isi, bukan memenuhi layar. Ketukan di luar menu menutupnya.
 */
@Composable
fun MediaTracksPanel(
    audioTracks: List<VideoTrackInfo>,
    subtitleTracks: List<VideoTrackInfo>,
    selectedAudioId: Int,
    selectedSubtitleId: Int,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onAddSubtitle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MenuScrim(onDismiss = onDismiss)

        AnimatedMenu(
            transformOrigin = OriginBottomStart,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, bottom = MenuBottomGap)
                .widthIn(min = 200.dp, max = 320.dp)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                TrackSectionTitle(stringResource(R.string.video_audio_track))

                TrackChoice(
                    label = stringResource(R.string.video_track_none),
                    selected = selectedAudioId < 0,
                    onClick = { onSelectAudio(-1) }
                )

                audioTracks.forEach { track ->
                    TrackChoice(
                        label = track.name,
                        selected = track.id == selectedAudioId,
                        onClick = { onSelectAudio(track.id) }
                    )
                }

                MenuDivider()

                TrackSectionTitle(stringResource(R.string.video_subtitle_track))

                TrackChoice(
                    label = stringResource(R.string.video_track_none),
                    selected = selectedSubtitleId < 0,
                    onClick = { onSelectSubtitle(-1) }
                )

                subtitleTracks.forEach { track ->
                    TrackChoice(
                        label = track.name,
                        selected = track.id == selectedSubtitleId,
                        onClick = { onSelectSubtitle(track.id) }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(onClick = onAddSubtitle)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download_subtitles),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = stringResource(R.string.video_add_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Menu opsi lain, menempel di atas tombol tiga titik pada bar kontrol kanan.
 * Volume sistem ikut di sini supaya bar kontrol tidak perlu tombol tambahan
 * yang sudah tidak muat.
 */
@Composable
fun MoreOptionsPanel(
    isMuted: Boolean,
    speedText: String,
    onToggleMute: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MenuScrim(onDismiss = onDismiss)

        AnimatedMenu(
            transformOrigin = OriginBottomEnd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 12.dp, bottom = MenuBottomGap)
                .widthIn(min = 240.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MenuOption(
                    icon = painterResource(R.drawable.ic_player_volume),
                    label = stringResource(if (isMuted) R.string.unmute else R.string.mute),
                    onClick = onToggleMute
                )

                MenuDivider()

                MenuOption(
                    icon = painterResource(R.drawable.ic_auto_speed),
                    label = stringResource(R.string.video_speed),
                    value = speedText,
                    onClick = onOpenSpeed
                )
                MenuOption(
                    icon = painterResource(R.drawable.ic_display_settings),
                    label = stringResource(R.string.video_player_settings),
                    onClick = onOpenSettings
                )
            }
        }
    }
}

/** Menu kecepatan putar, memakai bentuk menu kecil yang sama. */
@Composable
fun SpeedPickerPanel(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    Box(modifier = modifier.fillMaxSize()) {
        MenuScrim(onDismiss = onDismiss)

        AnimatedMenu(
            transformOrigin = OriginBottomEnd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 12.dp, bottom = MenuBottomGap)
                .widthIn(min = 180.dp, max = 240.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                TrackSectionTitle(stringResource(R.string.video_speed))
                MenuDivider()

                speeds.forEach { speed ->
                    TrackChoice(
                        label = stringResource(R.string.video_speed_value, formatSpeed(speed)),
                        selected = abs(current - speed) < 0.01f,
                        onClick = {
                            onSelect(speed)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Pembungkus menu kecil: permukaan melayang dengan animasi masuk singkat dari
 * sudut tombol pemicu, jadi terasa tumbuh dari tempatnya, bukan muncul tiba-tiba.
 */
@Composable
private fun AnimatedMenu(
    transformOrigin: TransformOrigin,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val transition = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = transition,
        enter = fadeIn(tween(120)) + scaleIn(
            initialScale = 0.94f,
            transformOrigin = transformOrigin,
            animationSpec = tween(120)
        ),
        exit = fadeOut(tween(90)),
        modifier = modifier
    ) {
        Surface(
            shape = MenuShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        ) {
            content()
        }
    }
}

/** Lapisan transparan penangkap ketukan di luar menu. */
@Composable
private fun MenuScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
    )
}

@Composable
private fun TrackSectionTitle(title: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

/**
 * Satu baris pilihan. Pilihan aktif ditandai centang dan warna aksen, jadi
 * tidak perlu tombol radio seperti dialog lama.
 */
@Composable
private fun TrackChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun MenuOption(
    icon: Painter,
    label: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (value != null) {
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
