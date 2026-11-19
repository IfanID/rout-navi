package com.rout.navi.screen.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.rout.navi.R

/**
 * Isi satu halaman onboarding.
 *
 * Langkah 3 memakai ulang string layar izin yang sudah ada di empat locale,
 * sehingga penjelasan izin tidak ditulis dua kali.
 */
enum class OnboardingPage(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    Welcome(
        icon = Icons.Outlined.FolderOpen,
        titleRes = R.string.onboarding_welcome_title,
        bodyRes = R.string.onboarding_welcome_body,
    ),
    Features(
        icon = Icons.Outlined.Widgets,
        titleRes = R.string.onboarding_features_title,
        bodyRes = R.string.onboarding_features_body,
    ),
    Permission(
        icon = Icons.Outlined.Shield,
        titleRes = R.string.storage_access_required,
        bodyRes = R.string.storage_access_description,
    ),
    Ready(
        icon = Icons.Outlined.CheckCircle,
        titleRes = R.string.onboarding_ready_title,
        bodyRes = R.string.onboarding_ready_body,
    ),
    ;

    companion object {
        val lastIndex = entries.lastIndex
    }
}
