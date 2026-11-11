package com.raival.compose.file.explorer.screen.viewer.image.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.WidthFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.CopiableText
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.image.ImageViewerInstance
import com.raival.compose.file.explorer.screen.viewer.image.misc.ImageInfo
import com.raival.compose.file.explorer.screen.viewer.image.misc.ImageInfo.Companion.extractImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File

/** Otoritas FileProvider yang dipakai aplikasi, konsisten dengan LocalFileHolder. */
private const val FILE_PROVIDER_AUTHORITY = "com.raival.compose.file.explorer.provider"

/**
 * Satu foto dalam galeri. [file] terisi bila fotonya berkas lokal dari folder
 * yang dibuka file explorer; bila null, fotonya datang dari [uri] app lain.
 */
private data class PhotoItem(val file: File?, val uri: Uri) {
    /** Data untuk Coil: berkas lokal bila ada, uri-nya bila bukan. */
    val data: Any get() = file ?: uri

    val name: String
        get() = file?.name ?: uri.lastPathSegment ?: emptyString
}

private fun fileUri(file: File): Uri =
    FileProvider.getUriForFile(globalClass, FILE_PROVIDER_AUTHORITY, file)

/**
 * Daftar foto galeri. Untuk folder, urutannya terlama di depan sehingga
 * geser kiri menuju foto terbaru dan geser kanan menuju foto terlama.
 * Prioritas sumbernya:
 * daftar kategori ([ImageViewerInstance.photoPaths], urutannya persis
 * seperti tampilan kategori), lalu folder asal, dan bila keduanya tidak
 * ada (misal dibuka dari app lain) hanya foto yang dibuka.
 */
private suspend fun listPhotos(instance: ImageViewerInstance): List<PhotoItem> =
    withContext(Dispatchers.IO) {
        runCatching {
            instance.photoPaths.takeIf { it.isNotEmpty() }
                ?.mapNotNull { path ->
                    // Berkas yang sudah tidak ada dilewati supaya tidak
                    // menyisakan halaman galat di tengah galeri.
                    File(path).takeIf { it.isFile }
                        ?.let { PhotoItem(file = it, uri = fileUri(it)) }
                }
                ?.takeIf { it.isNotEmpty() }
                ?: instance.folderPath.takeIf { it.isNotEmpty() }
                    ?.let { folder ->
                        File(folder).listFiles { f ->
                            f.isFile &&
                                    f.extension.lowercase() in FileMimeType.imageFileType
                        }?.sortedBy { it.lastModified() }
                            ?.map { PhotoItem(file = it, uri = fileUri(it)) }
                            ?.takeIf { it.isNotEmpty() }
                    }
        }.getOrNull() ?: listOf(PhotoItem(file = null, uri = instance.uri))
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(instance: ViewerInstance) {
    val defaultColor = MaterialTheme.colorScheme.surface
    var dominantColor by remember { mutableStateOf(defaultColor) }
    var secondaryColor by remember { mutableStateOf(defaultColor) }
    val imageBackgroundColors = arrayListOf(
        Color.Transparent,
        Color.White,
        Color.Gray,
        Color.Black
    )
    var currentImageBackgroundColorIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var imageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var imageDimensions by remember { mutableStateOf("" to "") }
    var contentScale by remember { mutableStateOf(ContentScale.Fit) }
    val context = LocalContext.current

    val imageInstance = instance as? ImageViewerInstance
    var photos by remember { mutableStateOf<List<PhotoItem>?>(null) }
    var startIndex by remember { mutableIntStateOf(0) }

    // Mendaftar foto sefolder di thread IO, sekali saat layar dibuka.
    LaunchedEffect(instance.uri) {
        val items = listPhotos(imageInstance ?: ImageViewerInstance(instance.uri, instance.id))
        startIndex = items.indexOfFirst { it.name == imageInstance?.initialName }.coerceAtLeast(0)
        photos = items
        isLoading = false
    }

    val items = photos

    // key membuat state pager dibuat ulang saat daftar foto berubah, supaya
    // initialPage (foto yang diketuk) diterapkan pada daftar yang sudah siap.
    val pagerState = key(items) {
        rememberPagerState(initialPage = startIndex) { items?.size ?: 0 }
    }

    val current = items?.getOrNull(pagerState.currentPage)

    // Pindah foto: bersihkan keadaan yang khusus untuk satu foto.
    LaunchedEffect(pagerState.currentPage, items) {
        rotationAngle = 0f
        contentScale = ContentScale.Fit
        isError = false
        imageDimensions = emptyString to emptyString
        imageInfo = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.8f),
                        secondaryColor.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        when {
            isLoading -> LoadingState()
            items.isNullOrEmpty() -> ErrorState(onClose = {
                (context as? ViewerActivity)?.finish()
            })

            else -> {
                // Gambar foto yang sedang tampil, untuk ekstraksi warna.
                var image by remember(items) { mutableStateOf<Image?>(null) }

                HorizontalPager(
                    state = pagerState,
                    // Tetangga dimuat duluan supaya geserannya tidak menunggu.
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = items[page]
                    PhotoPage(
                        item = item,
                        isCurrent = page == pagerState.currentPage,
                        rotationAngle = rotationAngle,
                        contentScale = contentScale,
                        backgroundColor = imageBackgroundColors[currentImageBackgroundColorIndex],
                        onToggleControls = { showControls = !showControls },
                        onLoaded = { loaded, dimensions ->
                            // Hanya foto yang sedang tampil yang mengatur keadaan layar.
                            if (page == pagerState.currentPage) {
                                image = loaded
                                imageDimensions = dimensions
                            }
                        },
                        onError = { if (page == pagerState.currentPage) isError = true }
                    )
                }

                // Warna latar diambil dari foto yang sedang tampil.
                LaunchedEffect(image, pagerState.currentPage) {
                    val bitmap = image ?: return@LaunchedEffect
                    runCatching {
                        val copy = bitmap.toBitmap()
                            .copy(Bitmap.Config.ARGB_8888, false)
                        val palette = Palette.from(copy).generate()
                        dominantColor = Color(palette.getDominantColor(defaultColor.toArgb()))
                        secondaryColor = Color(palette.getMutedColor(defaultColor.toArgb()))
                    }.onFailure { logger.logError(it) }
                }

                // Info berkas mengikuti foto yang sedang tampil.
                LaunchedEffect(current, imageDimensions.first) {
                    val info = current ?: return@LaunchedEffect
                    if (imageDimensions.first.isNotEmpty()) {
                        imageInfo = runCatching {
                            extractImageInfo(
                                info.uri,
                                imageDimensions.first,
                                imageDimensions.second
                            )
                        }.getOrNull()
                    }
                }

                // Animated gradient overlay for top bar
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                TopAppBarDefaults.MediumAppBarCollapsedHeight
                                        + WindowInsets.statusBars.asPaddingValues()
                                    .calculateTopPadding()
                                        + 8.dp
                            )
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        defaultColor.copy(alpha = 0.8f),
                                        defaultColor.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )
                }

                // Animated gradient overlay for controls
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = showControls,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                TopAppBarDefaults.MediumAppBarCollapsedHeight
                                        + WindowInsets.statusBars.asPaddingValues()
                                    .calculateTopPadding()
                            )
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        defaultColor.copy(alpha = 0.4f),
                                        defaultColor.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )
                }

                // Top bar
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = current?.name ?: stringResource(R.string.unknown),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        imageInfo?.let { info ->
                                            append("${info.size} • ${info.dimensions}")
                                        }
                                        if (items.size > 1) {
                                            if (isNotEmpty()) append(" • ")
                                            append(
                                                stringResource(
                                                    R.string.image_position,
                                                    pagerState.currentPage + 1,
                                                    items.size
                                                )
                                            )
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                (context as? ViewerActivity)?.finish()
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showInfo = true }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }

                // Bottom controls
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = showControls,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    BottomControls(
                        onInvertBackgroundColors = {
                            currentImageBackgroundColorIndex =
                                (currentImageBackgroundColorIndex + 1) % imageBackgroundColors.size
                        },
                        onRotate = {
                            rotationAngle = (rotationAngle + 90f) % 360f
                        },
                        onEdit = {
                            val uri = current?.uri ?: return@BottomControls
                            val editIntent = Intent(Intent.ACTION_EDIT)
                            val mimeType =
                                context.contentResolver.getType(uri) ?: "image/*"
                            editIntent.setDataAndType(uri, mimeType)
                            editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            val chooser = Intent.createChooser(
                                editIntent,
                                globalClass.getString(R.string.edit_with)
                            )
                            if (editIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(chooser)
                            } else {
                                showMsg(context.getString(R.string.no_app_found_to_edit_this_image))
                            }
                        },
                        onContentScale = {
                            contentScale = it
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                // Info bottom sheet
                if (showInfo) {
                    imageInfo?.let { info ->
                        ImageInfoBottomSheet(
                            imageInfo = info,
                            onDismiss = { showInfo = false }
                        )
                    }
                }
            }
        }

        // Kegagalan memuat foto yang sedang tampil menampilkan lapisan error
        // di atas pager, dengan tombol kembali menutup penampil.
        if (isError && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.failed_to_load_image),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Satu halaman foto di pager. Warna dan kegagalan muat diurus di sini per
 * foto, sedangkan keadaan layar (rotasi, skala, info) hanya milik halaman
 * yang sedang tampil. Animasi pindah foto memakai slide bawaan pager.
 */
@Composable
private fun PhotoPage(
    item: PhotoItem,
    isCurrent: Boolean,
    rotationAngle: Float,
    contentScale: ContentScale,
    backgroundColor: Color,
    onToggleControls: () -> Unit,
    onLoaded: (Image, Pair<String, String>) -> Unit,
    onError: () -> Unit
) {
    var failed by remember(item.uri) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Animasi dasar: slide bawaan pager, hanya rotasi tombol.
                rotationZ = rotationAngle
            }
    ) {
        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.failed_to_load_image),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else {
            ZoomableAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.data)
                    .listener(
                        onSuccess = { _, state ->
                            val loaded = state.image
                            if (loaded != null) {
                                onLoaded(
                                    loaded,
                                    "${loaded.width}" to "${loaded.height}"
                                )
                            }
                        },
                        onError = { _, error ->
                            logger.logError(error.throwable)
                            failed = true
                            onError()
                        }
                    ).build(),
                contentDescription = null,
                contentScale = contentScale,
                onClick = {
                    // Ketukan hanya berlaku di foto yang sedang tampil.
                    if (isCurrent) onToggleControls()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_image),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ErrorState(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.failed_to_load_image),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.check_file_exists_and_try_again),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun BottomControls(
    onInvertBackgroundColors: () -> Unit,
    onRotate: () -> Unit,
    onEdit: () -> Unit,
    onContentScale: (contentScale: ContentScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentScales = arrayListOf<ContentScale>().apply {
        add(ContentScale.Fit)
        add(ContentScale.Crop)
        add(ContentScale.FillWidth)
        add(ContentScale.FillHeight)
        add(ContentScale.FillBounds)
        add(ContentScale.Inside)
    }
    var selectedContentScale by remember { mutableStateOf(contentScales[0]) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Invert background colors
        ActionButton(
            icon = Icons.Default.InvertColors,
            onClick = onInvertBackgroundColors,
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )

        // Fit to screen
        ActionButton(
            icon = when (selectedContentScale) {
                ContentScale.Fit -> Icons.Default.FitScreen
                ContentScale.Crop -> Icons.Default.Crop
                ContentScale.FillWidth -> Icons.Default.WidthFull
                ContentScale.FillHeight -> Icons.Default.Height
                ContentScale.FillBounds -> Icons.Default.BorderOuter
                else -> Icons.Default.FilterCenterFocus
            },
            onClick = {
                val currentScaleIndex = contentScales.indexOf(selectedContentScale)
                selectedContentScale = contentScales[
                    if (currentScaleIndex == contentScales.lastIndex) 0
                    else currentScaleIndex + 1
                ]
                onContentScale(selectedContentScale)
            },
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )

        // Rotate
        ActionButton(
            icon = Icons.AutoMirrored.Filled.RotateRight,
            onClick = onRotate,
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )

        // Edit
        ActionButton(
            icon = Icons.Default.Edit,
            onClick = onEdit,
            backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageInfoBottomSheet(
    imageInfo: ImageInfo,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.size(width = 32.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Image Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            InfoRow(stringResource(R.string.name), imageInfo.name, copiable = true)
            InfoRow(stringResource(R.string.size), imageInfo.size)
            InfoRow(stringResource(R.string.dimensions), imageInfo.dimensions)
            InfoRow(stringResource(R.string.format), imageInfo.format)
            InfoRow(stringResource(R.string.last_modified), imageInfo.lastModified)
            InfoRow(
                label = stringResource(R.string.path),
                value = imageInfo.path,
                copiable = true
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    copiable: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        if (copiable) {
            // Perilaku sama seperti properti berkas: long-press untuk menyalin
            CopiableText(
                text = value,
                maxLines = 3,
                modifier = Modifier.weight(2f)
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(2f)
            )
        }
    }
}
