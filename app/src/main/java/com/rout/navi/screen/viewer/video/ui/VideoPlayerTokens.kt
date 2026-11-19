package com.rout.navi.screen.viewer.video.ui

import androidx.compose.ui.unit.dp

/**
 * Skala radius pemutar. Dipakai bersama oleh badge, menu kecil, dan sheet
 * supaya bentuk tiap lapisan tetap satu bahasa.
 */
internal object PlayerRadius {
    /** Badge waktu mengambang dan penanda subtitle. */
    val Small = 8.dp

    /** Petunjuk gestur. */
    val Medium = 12.dp

    /** Semua menu kecil dan popover. */
    val Large = 18.dp

    /** Sheet setelan dari bawah. */
    val Sheet = 28.dp
}
