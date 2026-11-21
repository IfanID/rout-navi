package com.rout.navi.screen.viewer.video.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rout.navi.R
import com.rout.navi.screen.viewer.video.OrientationMode
import com.rout.navi.common.toFormattedTime
import com.rout.navi.screen.viewer.video.model.VideoPlayerState
import com.rout.navi.screen.viewer.video.model.VideoScaleMode

/**
 * Lapisan kontrol mengikuti susunan HUD VLC: tombol putar di bar bawah,
 * diapit mundur/maju 10 detik, seekbar di atasnya, track di kiri, dan mode
 * layar serta opsi lain di kanan.
 *
 * Bahasa visual: gelap hanya di area kontrol supaya teks tetap terbaca di atas
 * video terang, sementara bagian tengah video dibiarkan bersih. Satu aksen
 * tema dipakai untuk posisi putar dan tombol utama.
 */
@Composable
fun VideoHud(
    state: VideoPlayerState,
    visible: Boolean,
    onToggleLock: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPickTracks: () -> Unit,
    onChangeScale: () -> Unit,
    onCycleOrientation: () -> Unit,
    orientationMode: OrientationMode,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Gelap tetap dipakai walau aplikasi bertema terang, karena kontrol ini
    // berada di atas video yang terus berubah warna.
    val scrim = Color.Black
    val slidePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val scrimEnter = fadeIn(tween(150))
    val scrimExit = fadeOut(tween(220))

    Box(modifier = modifier.fillMaxSize()) {
        // Skrim atas hanya setinggi top bar, cukup untuk judul dan tombol kembali.
        AnimatedVisibility(
            visible = visible,
            enter = scrimEnter,
            exit = scrimExit,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to scrim.copy(alpha = 0.72f),
                            1f to Color.Transparent
                        )
                    )
            )
        }

        // Skrim bawah lebih tinggi dari bar kontrol, dengan alfa 0.55 di awal
        // area teks supaya baris waktu tetap lolos kontras di atas video putih.
        AnimatedVisibility(
            visible = visible,
            enter = scrimEnter,
            exit = scrimExit,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to scrim.copy(alpha = 0.55f),
                            1f to scrim.copy(alpha = 0.90f)
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + slideInVertically(
                animationSpec = tween(150),
                initialOffsetY = { -slidePx }
            ),
            exit = fadeOut(tween(220)) + slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { -slidePx }
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            VideoTopBar(
                title = state.title,
                onBack = onBack
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + slideInVertically(
                animationSpec = tween(150),
                initialOffsetY = { slidePx }
            ),
            exit = fadeOut(tween(220)) + slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { slidePx }
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoBottomBar(
                state = state,
                onToggleLock = onToggleLock,
                onTogglePlay = onTogglePlay,
                onSeekBy = onSeekBy,
                onSeekTo = onSeekTo,
                onPickTracks = onPickTracks,
                onChangeScale = onChangeScale,
                onCycleOrientation = onCycleOrientation,
                orientationMode = orientationMode,
                onShowMore = onShowMore
            )
        }
    }
}

/** Warna isi tombol dan teks di dalam HUD. Netral putih, bukan warna tema. */
private val HudContent = Color.White

/**
 * Ikon dan label tombol rotasi mengikuti mode aktif. Auto memakai ikon
 * putar otomatis, lanskap dan potret memakai ikon layar dengan arahnya.
 */
@Composable
private fun OrientationMode.hudIconRes(): Int = when (this) {
    OrientationMode.AUTO_VIDEO -> R.drawable.ic_player_screen_rotation
    OrientationMode.LANDSCAPE -> R.drawable.ic_player_stay_landscape
    else -> R.drawable.ic_player_stay_portrait
}

@Composable
private fun OrientationMode.labelRes(): Int = when (this) {
    OrientationMode.AUTO_VIDEO -> R.string.video_orientation_auto
    OrientationMode.LANDSCAPE -> R.string.video_orientation_landscape
    else -> R.string.video_orientation_portrait
}

@Composable
private fun VideoTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircleIconButton(
            onClick = onBack,
            iconRes = R.drawable.ic_arrow_back,
            description = stringResource(R.string.back)
        )

        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                // Bayangan tipis menjaga judul tetap terbaca saat video sangat terang.
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 1f),
                    blurRadius = 2f
                )
            ),
            fontWeight = FontWeight.SemiBold,
            color = HudContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoBottomBar(
    state: VideoPlayerState,
    onToggleLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPickTracks: () -> Unit,
    onChangeScale: () -> Unit,
    onCycleOrientation: () -> Unit,
    orientationMode: OrientationMode,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = state.duration
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val position = if (dragging) dragValue else state.currentPosition.toFloat()
    val progress = if (duration > 0) (position / duration).coerceIn(0f, 1f) else 0f

    // Posisi dari libVLC datang setiap 250 md, jadi digerus halus hanya saat
    // tidak sedang digeser; saat digeser thumb harus mengikuti jari persis.
    val smoothProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "seekProgress"
    )
    val shownProgress = if (dragging) progress else smoothProgress

    Column(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            )
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Baris waktu disembunyikan saat menggeser supaya badge waktu
            // mengambang tidak bertumpuk dengan angka di sini.
            TimeLabel(
                text = position.toLong().toFormattedTime(),
                emphasized = dragging,
                modifier = Modifier.alpha(if (dragging) 0f else 1f)
            )
            TimeLabel(
                text = duration.toFormattedTime(),
                emphasized = false,
                alignEnd = true,
                modifier = Modifier.alpha(if (dragging) 0f else 1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        VideoSeekBar(
            progress = shownProgress,
            timeText = position.toLong().toFormattedTime(),
            dragging = dragging,
            enabled = duration > 0,
            onDrag = {
                dragging = true
                dragValue = it * duration
            },
            onDragEnd = {
                onSeekTo(dragValue.toLong())
                dragging = false
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tombol inti disusun di tengah dalam satu kelompok, jadi mata membaca
        // satu kontrol utama saja. Track dan mode layar tetap di kiri/kanan.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    onClick = onPickTracks,
                    iconRes = R.drawable.ic_player_audiotrack,
                    description = stringResource(R.string.video_tracks)
                )
                CircleIconButton(
                    onClick = onToggleLock,
                    iconRes = R.drawable.ic_player_lock_portrait,
                    description = stringResource(R.string.video_lock_controls)
                )
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSeekBy(-10_000) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(28.dp),
                        painter = painterResource(R.drawable.ic_player_rewind_10),
                        contentDescription = stringResource(R.string.video_rewind_10),
                        tint = HudContent
                    )
                }

                PlayPauseButton(
                    isPlaying = state.isPlaying,
                    onClick = onTogglePlay
                )

                IconButton(
                    onClick = { onSeekBy(10_000) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(28.dp),
                        painter = painterResource(R.drawable.ic_player_forward_10),
                        contentDescription = stringResource(R.string.video_forward_10),
                        tint = HudContent
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    onClick = onCycleOrientation,
                    iconRes = orientationMode.hudIconRes(),
                    description = stringResource(orientationMode.labelRes())
                )
                CircleIconButton(
                    onClick = onChangeScale,
                    iconRes = R.drawable.ic_player_ratio,
                    description = stringResource(state.scaleMode.labelRes())
                )
                CircleIconButton(
                    onClick = onShowMore,
                    iconRes = R.drawable.ic_player_more,
                    description = stringResource(R.string.video_more_options)
                )
            }
        }
    }
}

/**
 * Tombol ikon di sisi HUD, tanpa latar supaya hanya tombol putar yang
 * menonjol di baris kontrol. Ukuran ikon diseragamkan ke 24dp.
 */
@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    description: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(iconSize),
            tint = HudContent
        )
    }
}

/**
 * Tombol putar adalah fokus utama HUD. Ukuran dan aksennya berbeda dari tombol
 * lain supaya jelas ini kontrol yang paling sering dipakai. Ikon berganti
 * dengan transisi pendek, dan tombol mengecil sedikit saat ditekan.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(90),
        label = "playButtonScale"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(accent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(120)
                )) togetherWith fadeOut(tween(90))
            },
            label = "playPauseIcon"
        ) { playing ->
            Icon(
                modifier = Modifier.size(32.dp),
                painter = painterResource(
                    if (playing) R.drawable.ic_pause_player else R.drawable.ic_play_player
                ),
                contentDescription = stringResource(
                    if (playing) R.string.pause else R.string.play
                ),
                tint = onAccent
            )
        }
    }
}

@Composable
private fun TimeLabel(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Text(
        modifier = modifier.widthIn(min = 48.dp),
        text = text,
        // Angka tabular menjaga lebar label tetap, jadi baris tidak bergetar
        // setiap detik berganti.
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        color = HudContent.copy(alpha = if (emphasized) 1f else 0.72f)
    )
}

/**
 * Seekbar yang bisa digeser di mana saja pada barisnya, bukan hanya pada
 * thumb. Area sentuh tetap 44dp; garis yang terlihat menebal saat digeser dan
 * thumb diberi garis tepi gelap supaya tidak hilang di atas video terang.
 */
@Composable
private fun VideoSeekBar(
    progress: Float,
    timeText: String,
    dragging: Boolean,
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 4.5.dp else 3.dp,
        animationSpec = tween(120),
        label = "seekTrackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (dragging) 8.dp else 5.dp,
        animationSpec = tween(120),
        label = "seekThumbRadius"
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.20f else 0f,
        animationSpec = tween(120),
        label = "seekThumbHalo"
    )
    val jumpString = stringResource(R.string.video_seek_hint, "", "")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset -> onDrag((offset.x / size.width).coerceIn(0f, 1f)); onDragEnd() },
                    onPress = { onDrag((it.x / size.width).coerceIn(0f, 1f)) }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { start -> onDrag((start.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onDrag((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            }
            .semantics { contentDescription = jumpString }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val heightPx = trackHeight.toPx()
            val outline = 1.dp.toPx()
            val centerY = size.height / 2f
            val radius = CornerRadius((heightPx + outline * 2f) / 2f)

            // Garis tepi gelap tipis di belakang track menjaga batas bar tetap
            // terlihat saat video di belakangnya juga terang.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(0f, centerY - heightPx / 2f - outline),
                size = Size(size.width, heightPx + outline * 2f),
                cornerRadius = radius
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(0f, centerY - heightPx / 2f),
                size = Size(size.width, heightPx),
                cornerRadius = CornerRadius(heightPx / 2f)
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(0f, centerY - heightPx / 2f),
                size = Size(size.width * progress, heightPx),
                cornerRadius = CornerRadius(heightPx / 2f)
            )

            val radiusPx = thumbRadius.toPx()
            val thumbX = (size.width * progress).coerceIn(radiusPx, size.width - radiusPx)

            if (haloAlpha > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = haloAlpha),
                    radius = 24.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
            // Lingkaran gelap sedikit lebih besar dari thumb memberi garis tepi 1dp.
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = radiusPx + outline,
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = radiusPx,
                center = Offset(thumbX, centerY)
            )
        }

        val badgeWidth = 64.dp
        val badgeX = (maxWidth - badgeWidth) * progress

        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(tween(120)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(120)
            ),
            exit = fadeOut(tween(90)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = badgeX, top = 0.dp)
        ) {
            Text(
                modifier = Modifier
                    .widthIn(min = badgeWidth)
                    .background(
                        Color.Black.copy(alpha = 0.72f),
                        RoundedCornerShape(PlayerRadius.Small)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                text = timeText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = HudContent
            )
        }
    }
}

/**
 * Pil "2x" ala YouTube: di atas tengah layar, memakai aksen tema aplikasi,
 * dan tidak menangkap sentuhan.
 */
@Composable
fun VideoFastPlayBadge(
    speed: Float,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        text = "${formatSpeed(speed)}x",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary
    )
}

/**
 * Penanda subtitle aktif saat kontrol dikunci. Duduk di samping kanan baris
 * subtitle, jadi teks dialog tidak tertutup.
 */
@Composable
fun VideoLockedSubtitleBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(PlayerRadius.Small)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        text = text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = HudContent.copy(alpha = 0.75f)
    )
}

/** Tombol buka kunci saat kontrol sedang dikunci, di tengah bawah. */
@Composable
fun VideoLockedOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        IconButton(
            onClick = onUnlock,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(bottom = 32.dp)
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                // Garis tepi tipis menjaga tombol tetap terlihat di atas video gelap.
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_player_lock_portrait),
                contentDescription = stringResource(R.string.video_unlock_controls),
                modifier = Modifier.size(28.dp),
                tint = HudContent
            )
        }
    }
}

/** Teks melayang untuk umpan balik gestur, misalnya hasil lompatan seek. */
@Composable
fun VideoGestureHint(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                )
                // Digeser turun dari pil 2x supaya keduanya tidak bertumpuk.
                .padding(top = 112.dp)
                .background(
                    Color.Black.copy(alpha = 0.68f),
                    RoundedCornerShape(PlayerRadius.Medium)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = HudContent
        )
    }
}

private fun VideoScaleMode.labelRes(): Int = when (this) {
    VideoScaleMode.BEST_FIT -> R.string.video_scale_best_fit
    VideoScaleMode.FIT_SCREEN -> R.string.video_scale_fit_screen
    VideoScaleMode.FILL -> R.string.video_scale_fill
    VideoScaleMode.ORIGINAL -> R.string.video_scale_original
}

fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).toInt() / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}
