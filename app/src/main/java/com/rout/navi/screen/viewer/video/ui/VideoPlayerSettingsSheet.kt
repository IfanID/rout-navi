package com.rout.navi.screen.viewer.video.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rout.navi.R
import com.rout.navi.screen.viewer.video.VideoPlayerPreferences
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Setelan pemutar sebagai sheet yang mengikuti tinggi isinya, bukan memenuhi
 * layar. Geser ke bawah untuk menutup; kalau isinya lebih tinggi dari layar,
 * daftar tetap bisa digulir.
 *
 * Tidak ada jendela atau activity baru, jadi surface video tidak dilepas.
 */
@Composable
fun VideoPlayerSettingsSheet(
    prefs: VideoPlayerPreferences,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // Sheet tidak perlu masuk ke area status bar. Batas tinggi dikurangi
        // tinggi status bar supaya header tidak perlu jarak tambahan di atasnya.
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val dragOffset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight - topInset)
                .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                // Tinggi mengikuti isi dan bergerak halus saat konten berubah.
                .animateContentSize(tween(180)),
            shape = RoundedCornerShape(
                topStart = PlayerRadius.Sheet,
                topEnd = PlayerRadius.Sheet
            ),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Hanya sisi samping yang perlu jarak aman; bagian atas
                        // sudah dibatasi tinggi maksimum sheet.
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                        .pointerInput(parentHeightPx) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (dragOffset.value > parentHeightPx * 0.2f) {
                                            dragOffset.animateTo(parentHeightPx, tween(180))
                                            onClose()
                                        } else {
                                            dragOffset.animateTo(0f, tween(200))
                                        }
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        dragOffset.snapTo(
                                            (dragOffset.value + dragAmount)
                                                .coerceIn(0f, parentHeightPx)
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    CircleShape
                                )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                            text = stringResource(R.string.video_player_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    VideoPlayerSettingsBody(prefs = prefs)
                }
            }
        }
    }
}
