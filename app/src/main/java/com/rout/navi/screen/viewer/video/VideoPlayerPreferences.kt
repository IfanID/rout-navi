package com.rout.navi.screen.viewer.video

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Setelan pemutar video. Nama key dan nilai default mengikuti vlc-android
 * supaya perilaku gestur sama dengan aplikasi VLC.
 */
class VideoPlayerPreferences private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var volumeGesture by mutableStateOf(
        prefs.getBoolean(KEY_VOLUME_GESTURE, true)
    )
        private set

    var brightnessGesture by mutableStateOf(
        prefs.getBoolean(KEY_BRIGHTNESS_GESTURE, false)
    )
        private set

    var saveBrightness by mutableStateOf(
        prefs.getBoolean(KEY_SAVE_BRIGHTNESS, false)
    )
        private set

    var swipeSeek by mutableStateOf(
        prefs.getBoolean(KEY_SWIPE_SEEK, true)
    )
        private set

    var scaleGesture by mutableStateOf(
        prefs.getBoolean(KEY_SCALE_GESTURE, true)
    )
        private set

    var doubleTapSeek by mutableStateOf(
        prefs.getBoolean(KEY_DOUBLE_TAP_SEEK, true)
    )
        private set

    var doubleTapJumpDelaySeconds by mutableIntStateOf(
        prefs.getInt(KEY_DOUBLE_TAP_JUMP_DELAY, 10)
    )
        private set

    var doubleTapPlay by mutableStateOf(
        prefs.getBoolean(KEY_DOUBLE_TAP_PLAY, true)
    )
        private set

    // Aktif secara bawaan supaya menahan layar langsung mempercepat video
    // seperti YouTube, tanpa perlu mengubah setelan lebih dulu.
    var fastPlay by mutableStateOf(
        prefs.getBoolean(KEY_FASTPLAY, true)
    )
        private set

    // Video yang pernah ditonton dilanjutkan dari posisi terakhir. Bila
    // dimatikan, semua video dimulai dari awal seperti pemutar biasa.
    var resumePlayback by mutableStateOf(
        prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
    )
        private set

    var fastPlaySpeed by mutableFloatStateOf(
        prefs.getInt(KEY_FASTPLAY_SPEED, 20) / 10f
    )
        private set

    fun updateVolumeGesture(value: Boolean) = putBoolean(KEY_VOLUME_GESTURE, value) {
        volumeGesture = it
    }

    fun updateBrightnessGesture(value: Boolean) = putBoolean(KEY_BRIGHTNESS_GESTURE, value) {
        brightnessGesture = it
    }

    fun updateSaveBrightness(value: Boolean) = putBoolean(KEY_SAVE_BRIGHTNESS, value) {
        saveBrightness = it
    }

    fun updateSwipeSeek(value: Boolean) = putBoolean(KEY_SWIPE_SEEK, value) {
        swipeSeek = it
    }

    fun updateScaleGesture(value: Boolean) = putBoolean(KEY_SCALE_GESTURE, value) {
        scaleGesture = it
    }

    fun updateDoubleTapSeek(value: Boolean) = putBoolean(KEY_DOUBLE_TAP_SEEK, value) {
        doubleTapSeek = it
    }

    fun setDoubleTapJumpDelay(seconds: Int) {
        val safe = seconds.coerceIn(5, 30)
        doubleTapJumpDelaySeconds = safe
        prefs.edit().putInt(KEY_DOUBLE_TAP_JUMP_DELAY, safe).apply()
    }

    fun updateDoubleTapPlay(value: Boolean) = putBoolean(KEY_DOUBLE_TAP_PLAY, value) {
        doubleTapPlay = it
    }

    fun updateFastPlay(value: Boolean) = putBoolean(KEY_FASTPLAY, value) {
        fastPlay = it
    }

    fun updateResumePlayback(value: Boolean) = putBoolean(KEY_RESUME_PLAYBACK, value) {
        resumePlayback = it
    }

    fun updateFastPlaySpeed(speed: Float) {
        val safe = speed.coerceIn(1.1f, 8f)
        fastPlaySpeed = safe
        prefs.edit().putInt(KEY_FASTPLAY_SPEED, (safe * 10).toInt()).apply()
    }

    private inline fun putBoolean(key: String, value: Boolean, apply: (Boolean) -> Unit) {
        apply(value)
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        private const val FILE_NAME = "video_player_controls"

        const val KEY_VOLUME_GESTURE = "enable_volume_gesture"
        const val KEY_BRIGHTNESS_GESTURE = "enable_brightness_gesture"
        const val KEY_SAVE_BRIGHTNESS = "save_brightness"
        const val KEY_SWIPE_SEEK = "enable_swipe_seek"
        const val KEY_SCALE_GESTURE = "enable_scale_gesture"
        const val KEY_DOUBLE_TAP_SEEK = "enable_double_tap_seek"
        const val KEY_DOUBLE_TAP_JUMP_DELAY = "video_double_tap_jump_delay"
        const val KEY_DOUBLE_TAP_PLAY = "enable_double_tap_play"
        const val KEY_FASTPLAY = "enable_fastplay"
        const val KEY_FASTPLAY_SPEED = "fastplay_speed"
        const val KEY_RESUME_PLAYBACK = "resume_playback"

        @Volatile
        private var instance: VideoPlayerPreferences? = null

        fun get(context: Context): VideoPlayerPreferences =
            instance ?: synchronized(this) {
                instance ?: VideoPlayerPreferences(context).also { instance = it }
            }
    }
}
