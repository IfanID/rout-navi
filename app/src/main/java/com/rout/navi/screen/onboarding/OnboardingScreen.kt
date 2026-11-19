package com.rout.navi.screen.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rout.navi.R
import com.rout.navi.common.ui.SafeSurface
import kotlinx.coroutines.launch

/**
 * Alur penyambutan pertama kali dibuka.
 *
 * Halaman terakhir baru bisa dicapai setelah izin penyimpanan diberikan, karena
 * tombol Mulai hanya menandai onboarding selesai lalu masuk ke layar utama.
 */
@Composable
fun OnboardingScreen(
    canAccessStorage: Boolean,
    onGrantPermission: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    val page = pages[pagerState.currentPage]
    val isLastPage = pagerState.currentPage == OnboardingPage.lastIndex
    val needsPermission = page == OnboardingPage.Permission && !canAccessStorage
    val grantedPermission = page == OnboardingPage.Permission && canAccessStorage

    // Langkah 4 hanya boleh digeser setelah izin ada. Tombol Mulai di sana
    // langsung masuk ke layar utama.
    LaunchedEffect(pagerState.currentPage, canAccessStorage) {
        if (!canAccessStorage && pages[pagerState.currentPage] == OnboardingPage.Ready) {
            pagerState.animateScrollToPage(OnboardingPage.Permission.ordinal)
        }
    }

    fun goBack() {
        if (pagerState.currentPage == 0) {
            onExit()
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    SafeSurface {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { goBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.onboarding_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                // Kunci hanya sisi kanan langkah 3, supaya pengguna tetap bisa
                // kembali ke langkah sebelumnya selama izin belum ada.
                userScrollEnabled = !(page == OnboardingPage.Permission && !canAccessStorage)
            ) { index ->
                OnboardingPageContent(pages[index], canAccessStorage)
            }

            OnboardingIndicator(
                count = pages.size,
                selected = pagerState.currentPage,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    needsPermission -> {
                        Button(
                            onClick = onGrantPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.grant_access),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        TextButton(onClick = onExit, modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = stringResource(R.string.onboarding_exit),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    isLastPage -> {
                        Button(
                            onClick = onFinish,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_start),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    grantedPermission -> {
                        TextButton(onClick = onExit) {
                            Text(
                                text = stringResource(R.string.onboarding_exit),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Langkah 1 dan 2 hanya punya petunjuk geser di dalam halaman.
                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, permissionGranted: Boolean) {
    val grantedPermission = page == OnboardingPage.Permission && permissionGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ikon izin berganti centang sebagai bukti visual bahwa akses sudah ada.
        Icon(
            imageVector = if (grantedPermission) Icons.Outlined.CheckCircle else page.icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(if (grantedPermission) R.string.onboarding_permission_title else page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(if (grantedPermission) R.string.onboarding_permission_body else page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (page != OnboardingPage.Ready) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (grantedPermission) {
                    stringResource(R.string.onboarding_permission_granted)
                } else {
                    stringResource(R.string.onboarding_swipe_hint)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Indikator titik. Lebar titik aktif memanjang sedikit sebagai penanda posisi,
 * tanpa animasi berulang supaya sesuai MOTION 1.
 */
@Composable
private fun OnboardingIndicator(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${selected + 1} / $count"
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val active = index == selected
            val width by animateDpAsState(
                targetValue = if (active) 24.dp else 8.dp,
                label = "indicatorWidth"
            )

            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}
