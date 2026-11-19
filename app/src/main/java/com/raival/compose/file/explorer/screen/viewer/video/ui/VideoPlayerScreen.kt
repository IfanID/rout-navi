package com.raival.compose.file.explorer.screen.viewer.video.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.findActivity
import com.raival.compose.file.explorer.common.toFormattedTime
import com.raival.compose.file.explorer.common.ui.KeepScreenOn
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerInstance
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerLog
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerPreferences
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoScaleMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

private const val DRAG_NONE = 0
private const val DRAG_VERTICAL = 1
private const val DRAG_SEEK = 2
private const val DRAG_IGNORE = 3

/**
 * Layar pemutar video berbasis libVLC. Gestur mengikuti perilaku VLC:
 * geser vertikal untuk volume atau kecerahan, geser horizontal untuk seek,
 * ketuk dua kali di tepi untuk melompat, dan cubit dua jari untuk zoom.
 */
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    videoPlayerInstance: VideoPlayerInstance,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val prefs = remember { VideoPlayerPreferences.get(context) }
    val state by videoPlayerInstance.playerState.collectAsState()

    var hint by remember { mutableStateOf<String?>(null) }
    var shownHint by remember { mutableStateOf<String?>(null) }
    var showTracksPicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var rateBeforeFastPlay by remember { mutableStateOf(1f) }
    var fastPlayPressed by remember { mutableStateOf(false) }
    var fastPlayStarted by remember { mutableStateOf(false) }
    val hintSlidePx = with(LocalDensity.current) { 6.dp.roundToPx() }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) videoPlayerInstance.addSubtitleFromUri(picked)
    }

    // Tombol kembali menutup menu yang terbuka lebih dulu, bukan keluar pemutar.
    BackHandler(enabled = showTracksPicker || showMoreOptions || showSpeedPicker) {
        when {
            showSpeedPicker -> showSpeedPicker = false
            showMoreOptions -> showMoreOptions = false
            showTracksPicker -> showTracksPicker = false
        }
    }

    LaunchedEffect(videoUri) {
        videoPlayerInstance.initializePlayer(context, videoUri)
    }

    KeepScreenOn()
    VideoAudioFocus(videoPlayerInstance)


    // Kontrol menutup sendiri saat video berjalan, sama seperti HUD VLC.
    // Menu yang terbuka menahan HUD tetap terlihat supaya tombolnya tidak hilang.
    LaunchedEffect(
        state.showControls,
        state.isPlaying,
        state.isLocked,
        showTracksPicker,
        showMoreOptions,
        showSpeedPicker
    ) {
        if (state.showControls && state.isPlaying && !state.isLocked &&
            !showTracksPicker && !showMoreOptions && !showSpeedPicker
        ) {
            delay(4_000)
            if (videoPlayerInstance.playerState.value.showControls) {
                videoPlayerInstance.toggleControls()
            }
        }
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            // Teks terakhir disimpan supaya animasi keluar masih punya isi.
            shownHint = hint
            delay(1_500)
            hint = null
        }
    }

    // Kecerahan layar dikembalikan saat keluar bila pengguna tidak menyimpannya.
    val initialBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: -1f
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!prefs.saveBrightness) {
                activity?.window?.let { window ->
                    val attributes = window.attributes
                    attributes.screenBrightness = initialBrightness
                    window.attributes = attributes
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val metrics = context.resources.displayMetrics

            val baseViewConfiguration = LocalViewConfiguration.current
            // Ambang tekan lama dipendekkan supaya percepatan terasa langsung
            // seperti YouTube; nilai bawaan 500 md terasa terlambat.
            val fastPlayViewConfiguration = remember(baseViewConfiguration) {
                object : ViewConfiguration by baseViewConfiguration {
                    override val longPressTimeoutMillis: Long = 300L
                }
            }

            // Layout video dipasang lebih dulu, lalu media diputar setelahnya.
            // Urutan ini yang dipakai VLC android; urutan terbalik membuat
            // libVLC membuka output video berulang tanpa tujuan.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext: Context ->
                    VLCVideoLayout(viewContext).also { layout ->
                        videoPlayerInstance.attachLayout(layout)
                    }
                },
                onRelease = { layout -> videoPlayerInstance.releaseLayout(layout) }
            )

            CompositionLocalProvider(LocalViewConfiguration provides fastPlayViewConfiguration) {
                GestureLayer(
                    modifier = Modifier.fillMaxSize(),
                    videoPlayerInstance = videoPlayerInstance,
                    prefs = prefs,
                    locked = state.isLocked,
                    fastPlayActive = fastPlayPressed && fastPlayStarted,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    xdpi = metrics.xdpi,
                    onTap = { videoPlayerInstance.toggleControls() },
                    onDoubleTap = { x ->
                        when {
                            !prefs.doubleTapSeek -> Unit
                            x < widthPx / 4f -> seekWithHint(
                                videoPlayerInstance,
                                -prefs.doubleTapJumpDelaySeconds * 1_000L
                            ) { hint = it }
                            x > widthPx * 3 / 4f -> seekWithHint(
                                videoPlayerInstance,
                                prefs.doubleTapJumpDelaySeconds * 1_000L
                            ) { hint = it }
                            prefs.doubleTapPlay -> videoPlayerInstance.playPause()
                        }
                    },
                    onFastPlayStart = {
                        rateBeforeFastPlay = videoPlayerInstance.playerState.value.playbackSpeed
                        VideoPlayerLog.info("fastplay mulai, sebelum=$rateBeforeFastPlay")
                        videoPlayerInstance.setSpeed(prefs.fastPlaySpeed)
                        fastPlayStarted = true
                        fastPlayPressed = true
                    },
                    onFastPlayEnd = {
                        // Kembali ke kecepatan sebelum fastplay, bukan selalu 1x.
                        VideoPlayerLog.info("fastplay selesai")
                        videoPlayerInstance.setSpeed(rateBeforeFastPlay)
                        fastPlayPressed = false
                    },
                    onVolumeChange = { percent ->
                        videoPlayerInstance.setSystemVolumePercent(percent)
                        hint = context.getString(
                            R.string.video_volume_percent,
                            videoPlayerInstance.systemVolumePercent()
                        )
                    },
                    onBrightnessChange = { delta ->
                        activity?.window?.let { window ->
                            val attributes = window.attributes
                            val current = if (attributes.screenBrightness < 0f) {
                                0.5f
                            } else {
                                attributes.screenBrightness
                            }
                            val target = (current + delta).coerceIn(0.01f, 1f)
                            attributes.screenBrightness = target
                            window.attributes = attributes
                            hint = context.getString(
                                R.string.video_brightness_percent,
                                (target * 100).roundToInt()
                            )
                        }
                    },
                    onScale = { zoomIn ->
                        videoPlayerInstance.setScaleMode(
                            if (zoomIn) VideoScaleMode.FIT_SCREEN else VideoScaleMode.BEST_FIT
                        )
                    },
                    onSeekFinished = { target ->
                        videoPlayerInstance.seekTo(target)
                        hint = null
                    },
                    onSeekPreview = { target, jump ->
                        hint = buildSeekHint(context, jump, target)
                    }
                )
            }

            if (state.isLocked) {
                VideoLockedOverlay(onUnlock = { videoPlayerInstance.toggleLock() })
            } else {
                VideoHud(
                    state = state,
                    visible = state.showControls,
                    onToggleLock = { videoPlayerInstance.toggleLock() },
                    onBack = onBackPressed,
                    onTogglePlay = { videoPlayerInstance.playPause() },
                    onSeekBy = { delta ->
                        seekWithHint(videoPlayerInstance, delta) { hint = it }
                    },
                    onSeekTo = { videoPlayerInstance.seekTo(it) },
                    onPickTracks = { showTracksPicker = true },
                    onChangeScale = { videoPlayerInstance.cycleScaleMode() },
                    onShowMore = { showMoreOptions = true }
                )
            }

            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(tween(120)) + slideInVertically(
                    animationSpec = tween(120),
                    initialOffsetY = { hintSlidePx }
                ),
                exit = fadeOut(tween(180))
            ) {
                shownHint?.let { VideoGestureHint(text = it) }
            }

            if (state.isLoading && state.error == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(0.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            state.error?.let { message ->
                PlaybackError(
                    message = message,
                    onRetry = { videoPlayerInstance.retry() },
                    onBack = onBackPressed,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Penanda digambar terakhir agar berada di atas HUD dan lapisan
            // kunci. Posisinya di atas tengah, sama seperti pil "2x" YouTube.
            AnimatedVisibility(
                visible = fastPlayPressed && fastPlayStarted,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(120)
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(120)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                    )
                    .padding(top = 64.dp)
            ) {
                VideoFastPlayBadge(speed = prefs.fastPlaySpeed)
            }

            // Penanda subtitle saat kontrol dikunci: di samping kiri baris
            // subtitle, jadi tombol kunci tetap di sisi kanan.
            val lockedSubtitle = state.subtitleTracks
                .firstOrNull { it.id == state.selectedSubtitleTrack }
            if (state.isLocked && lockedSubtitle != null) {
                VideoLockedSubtitleBadge(
                    text = lockedSubtitle.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(start = 16.dp, bottom = 88.dp)
                )
            }

            // Jaring pengaman darurat: kalau event pelepasan benar-benar hilang,
            // pemutar dikembalikan ke kecepatan semula setelah 60 detik. Jangan
            // dipendekkan, karena tahan layar memang bisa dipakai lama.
            LaunchedEffect(fastPlayPressed) {
                if (!fastPlayPressed) return@LaunchedEffect
                delay(60_000)
                if (fastPlayPressed) {
                    VideoPlayerLog.info("fastplay batas waktu, kecepatan dipulihkan")
                    videoPlayerInstance.setSpeed(rateBeforeFastPlay)
                    fastPlayPressed = false
                    fastPlayStarted = false
                }
            }

            // Panel track dan setelan digambar di dalam layar pemutar, bukan
            // sebagai dialog, supaya tidak ada jendela baru yang menutup video.
            if (showTracksPicker) {
                MediaTracksPanel(
                    audioTracks = state.audioTracks,
                    subtitleTracks = state.subtitleTracks,
                    selectedAudioId = state.selectedAudioTrack,
                    selectedSubtitleId = state.selectedSubtitleTrack,
                    onSelectAudio = { videoPlayerInstance.selectAudioTrack(it) },
                    onSelectSubtitle = { videoPlayerInstance.selectSubtitleTrack(it) },
                    onAddSubtitle = {
                        showTracksPicker = false
                        subtitlePicker.launch(
                            arrayOf(
                                "text/*",
                                "application/x-subrip",
                                "application/x-ass",
                                "application/ttml+xml",
                                "application/octet-stream"
                            )
                        )
                    },
                    onDismiss = { showTracksPicker = false }
                )
            }

            if (showSettingsPanel) {
                VideoPlayerSettingsSheet(
                    prefs = prefs,
                    onClose = { showSettingsPanel = false }
                )
            }

            if (showMoreOptions) {
                MoreOptionsPanel(
                    isMuted = state.isMuted,
                    speedText = stringResource(
                        R.string.video_speed_value,
                        formatSpeed(state.playbackSpeed)
                    ),
                    onToggleMute = { videoPlayerInstance.toggleMute() },
                    onOpenSpeed = {
                        showMoreOptions = false
                        showSpeedPicker = true
                    },
                    onOpenSettings = {
                        showMoreOptions = false
                        // Setelan tetap berupa lapisan penuh karena isinya banyak.
                        showSettingsPanel = true
                    },
                    onDismiss = { showMoreOptions = false }
                )
            }

            if (showSpeedPicker) {
                SpeedPickerPanel(
                    current = state.playbackSpeed,
                    onSelect = { videoPlayerInstance.setSpeed(it) },
                    onDismiss = { showSpeedPicker = false }
                )
            }
        }
    }
}

@Composable
private fun GestureLayer(
    modifier: Modifier,
    videoPlayerInstance: VideoPlayerInstance,
    prefs: VideoPlayerPreferences,
    locked: Boolean,
    fastPlayActive: Boolean,
    widthPx: Float,
    heightPx: Float,
    xdpi: Float,
    onTap: () -> Unit,
    onDoubleTap: (Float) -> Unit,
    onFastPlayStart: () -> Unit,
    onFastPlayEnd: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onScale: (Boolean) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onSeekPreview: (Long, Long) -> Unit
) {
    // Callback terbaru dipakai di dalam pointerInput agar tidak memakai closure lama.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnFastPlayStart by rememberUpdatedState(onFastPlayStart)
    val currentOnFastPlayEnd by rememberUpdatedState(onFastPlayEnd)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnScale by rememberUpdatedState(onScale)
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)
    val currentOnSeekPreview by rememberUpdatedState(onSeekPreview)

    Box(
        modifier = modifier
            .pointerInput(
                locked,
                fastPlayActive,
                prefs.volumeGesture,
                prefs.brightnessGesture,
                prefs.swipeSeek
            ) {
                // Saat tahan layar aktif, gestur geser dimatikan sementara.
                // Kalau tidak, gerakan jari sedikit saja akan memicu deteksi
                // geser yang membatalkan tahan layar.
                if (locked || fastPlayActive) return@pointerInput

                var mode = DRAG_NONE
                var startX = 0f
                var totalDx = 0f
                var totalDy = 0f
                var startVolume = 0
                var seekTarget = 0L
                var seekActive = false

                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        totalDx = 0f
                        totalDy = 0f
                        mode = DRAG_NONE
                        seekActive = false
                        startVolume = videoPlayerInstance.systemVolumePercent()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y
                        if (mode == DRAG_NONE && (abs(totalDx) > 24f || abs(totalDy) > 24f)) {
                            mode = when {
                                abs(totalDy) > abs(totalDx) -> DRAG_VERTICAL
                                prefs.swipeSeek -> DRAG_SEEK
                                else -> DRAG_IGNORE
                            }
                        }

                        when (mode) {
                            DRAG_VERTICAL -> {
                                if (!prefs.volumeGesture && !prefs.brightnessGesture) {
                                    return@detectDragGestures
                                }
                                val onRight = startX > widthPx * 4 / 7f
                                val onLeft = startX < widthPx * 3 / 7f
                                if (!onRight && !onLeft) return@detectDragGestures
                                val useVolume = (onRight && prefs.volumeGesture) ||
                                        (onLeft && !prefs.brightnessGesture)
                                if (useVolume) {
                                    if (!prefs.volumeGesture) return@detectDragGestures
                                    val delta = -(totalDy / heightPx * 100f * 1.25f)
                                    currentOnVolumeChange((startVolume + delta).toInt())
                                } else {
                                    if (!prefs.brightnessGesture) return@detectDragGestures
                                    // Delta per gerakan, bukan total, karena kecerahan
                                    // dibaca ulang dari window setiap kali berubah.
                                    currentOnBrightnessChange(-dragAmount.y / heightPx * 1.25f)
                                }
                            }

                            DRAG_SEEK -> {
                                val current = videoPlayerInstance.playerState.value
                                if (current.duration <= 0) return@detectDragGestures
                                val gestureCm = totalDx / xdpi * 2.54f
                                var coef = if (abs(totalDx) > 1f) abs(totalDy / totalDx) else 1f
                                if (coef < 1f) coef = 1f
                                // Rumus jarak lompatan sama dengan VideoTouchDelegate VLC.
                                var jump = (
                                        sign(gestureCm) * (
                                            600_000 * (gestureCm / 8f).toDouble().pow(4.0) + 3_000
                                            ) / coef
                                        ).toLong()
                                val time = current.currentPosition
                                if (jump > 0 && time + jump > current.duration) {
                                    jump = current.duration - time
                                }
                                if (jump < 0 && time + jump < 0) jump = -time
                                seekTarget = time + jump
                                seekActive = true
                                currentOnSeekPreview(seekTarget, jump)
                            }
                        }
                    },
                    onDragEnd = {
                        if (seekActive) currentOnSeekFinished(seekTarget)
                        seekActive = false
                        mode = DRAG_NONE
                    },
                    onDragCancel = {
                        seekActive = false
                        mode = DRAG_NONE
                    }
                )
            }
            .pointerInput(locked, prefs.doubleTapSeek, prefs.doubleTapPlay, prefs.fastPlay) {
                if (locked) {
                    detectTapGestures(onTap = { currentOnTap() })
                    return@pointerInput
                }
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { offset -> currentOnDoubleTap(offset.x) },
                    onPress = {
                        if (!prefs.fastPlay) return@detectTapGestures
                        // Tekan singkat tetap dianggap ketukan biasa.
                        val releasedEarly = withTimeoutOrNull(300) { tryAwaitRelease() }
                        if (releasedEarly != null) return@detectTapGestures
                        currentOnFastPlayStart()
                        // true berarti jari diangkat normal, false berarti gestur
                        // dibatalkan oleh lapisan gestur lain.
                        val releasedNormally = runCatching {
                            tryAwaitRelease()
                        }.getOrDefault(true)
                        currentOnFastPlayEnd()
                        if (!releasedNormally &&
                            !videoPlayerInstance.playerState.value.showControls
                        ) {
                            // Kontrol dibuka kembali supaya kecepatan normalnya
                            // terlihat tanpa perlu mengetuk layar dua kali.
                            currentOnTap()
                        }
                    }
                )
            }
            .pointerInput(locked, fastPlayActive, prefs.scaleGesture) {
                if (locked || fastPlayActive || !prefs.scaleGesture) return@pointerInput
                // Hanya dua jari yang dihitung, supaya geser satu jari tetap
                // dipakai untuk volume, kecerahan, dan seek.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var accumulatedZoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                accumulatedZoom *= zoomChange
                                when {
                                    accumulatedZoom > 1.15f -> {
                                        currentOnScale(true)
                                        accumulatedZoom = 1f
                                        event.changes.forEach { it.consume() }
                                    }
                                    accumulatedZoom < 0.85f -> {
                                        currentOnScale(false)
                                        accumulatedZoom = 1f
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}

@Composable
private fun PlaybackError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 420.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
private fun VideoAudioFocus(instance: VideoPlayerInstance) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    instance.pause()
                }
            }
            .build()
        audioManager?.requestAudioFocus(request)
        onDispose {
            audioManager?.abandonAudioFocusRequest(request)
        }
    }
}

private fun seekWithHint(
    instance: VideoPlayerInstance,
    delta: Long,
    showHint: (String) -> Unit
) {
    val state = instance.playerState.value
    if (state.duration <= 0) return
    val target = (state.currentPosition + delta).coerceIn(0, state.duration)
    instance.seekTo(target)
    showHint(
        "${if (delta >= 0) "+" else ""}${delta / 1_000}s (${target.toFormattedTime()})"
    )
}

private fun buildSeekHint(context: Context, jump: Long, target: Long): String {
    val prefix = if (jump >= 0) "+" else ""
    val seconds = (jump / 1_000f).roundToInt()
    return context.getString(
        R.string.video_seek_hint,
        "$prefix${seconds}s",
        target.toFormattedTime()
    )
}
