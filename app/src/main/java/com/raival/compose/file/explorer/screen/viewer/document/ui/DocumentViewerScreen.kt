package com.raival.compose.file.explorer.screen.viewer.document.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.webkit.WebView
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.screen.viewer.document.DocumentViewerInstance
import com.raival.compose.file.explorer.screen.viewer.document.parser.DocumentBlock
import com.raival.compose.file.explorer.screen.viewer.document.parser.DocumentContent
import com.raival.compose.file.explorer.screen.viewer.document.parser.SheetContent
import com.raival.compose.file.explorer.screen.viewer.document.parser.SlideContent
import com.raival.compose.file.explorer.screen.viewer.pdf.ui.ErrorScreen
import com.raival.compose.file.explorer.screen.viewer.pdf.ui.InfoDialog
import com.raival.compose.file.explorer.screen.viewer.pdf.ui.LoadingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Layar penampil dokumen Microsoft Office. Jalur utamanya adalah WebView
 * dengan halaman vendor ala Gander, yang menyerupai tampilan dokumen asli.
 * Bila WebView tidak bisa dibuat, jatuh ke penampil teks terstruktur POI.
 */
@Composable
fun DocumentViewerContent(
    instance: DocumentViewerInstance,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current

    // WebView dibuat sekali per dokumen, di luar AndroidView, supaya gagalnya
    // bisa ditangkap: Android tidak punya WebView bila provider-nya sedang
    // diperbarui atau dimatikan.
    val webView = remember(instance.id) {
        runCatching { WebView(context) }.getOrNull()
    }

    DisposableEffect(instance.id) {
        onDispose { webView?.destroy() }
    }

    if (webView != null) {
        DocumentWebViewContent(
            instance = instance,
            webView = webView,
            onBackPress = onBackPress
        )
    } else {
        DocumentPoiContent(instance = instance, onBackPress = onBackPress)
    }
}

/**
 * Penampil teks terstruktur lewat Apache POI: fallback bila perangkat tidak
 * punya WebView yang bisa dipakai.
 */
@Composable
private fun DocumentPoiContent(
    instance: DocumentViewerInstance,
    onBackPress: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf<DocumentContent?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            instance.prepare { success ->
                if (success) {
                    content = instance.content
                    isLoading = false
                } else {
                    errorMessage = instance.failureReason.ifEmpty {
                        globalClass.getString(R.string.failed_to_read_document)
                    }
                    isLoading = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            instance.onClose()
        }
    }

    if (showInfoDialog) {
        InfoDialog(
            title = globalClass.getString(R.string.document_info),
            properties = instance.info,
            onDismiss = { showInfoDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DocumentTopBar(
            title = instance.name,
            onBackClick = onBackPress,
            onInfoClick = { showInfoDialog = true }
        )

        when {
            isLoading -> Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                LoadingScreen()
            }

            errorMessage isNot null -> Box(modifier = Modifier.weight(1f)) {
                ErrorScreen(
                    message = errorMessage!!,
                    onClose = onBackPress
                )
            }

            else -> {
                val document = content
                if (document == null) {
                    Box(modifier = Modifier.weight(1f)) {
                        ErrorScreen(
                            message = globalClass.getString(R.string.failed_to_read_document),
                            onClose = onBackPress
                        )
                    }
                } else {
                    DocumentBody(document = document)
                }
            }
        }
    }
}

@Composable
fun DocumentTopBar(
    title: String,
    onBackClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colorScheme.surfaceContainer)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = colorScheme.onSurface
                )
            }

            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.onSurface
            )

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colorScheme.onSurface
                )
            }
        }
    }
}

/** Merender isi dokumen sesuai jenisnya. */
@Composable
private fun DocumentBody(document: DocumentContent) {
    when (document) {
        is DocumentContent.WordDocument -> WordContent(document.blocks)
        is DocumentContent.Spreadsheet -> SpreadsheetContent(document.sheets)
        is DocumentContent.Presentation -> PresentationContent(document.slides)
    }
}

@Composable
private fun WordContent(blocks: List<DocumentBlock>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 16.dp, vertical = 16.dp
        )
    ) {
        itemsIndexed(blocks) { _, block ->
            when (block) {
                is DocumentBlock.Paragraph -> Text(
                    text = block.text,
                    style = if (block.isHeading) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (block.isHeading) FontWeight.SemiBold else null,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                is DocumentBlock.Table -> DocumentTable(
                    rows = block.rows,
                    firstRowHighlighted = false
                )
            }
        }
    }
}

/** Tabel dokumen: setiap kolom lebarnya sama dan bisa digeser horizontal. */
@Composable
private fun DocumentTable(rows: List<List<String>>, firstRowHighlighted: Boolean) {
    val horizontalState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalState)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (firstRowHighlighted && rowIndex == 0) {
                            FontWeight.SemiBold
                        } else {
                            null
                        },
                        color = colorScheme.onSurface,
                        modifier = Modifier
                            .width(120.dp)
                            .background(
                                if (firstRowHighlighted && rowIndex == 0) {
                                    colorScheme.surfaceContainerHigh
                                } else {
                                    colorScheme.surface
                                }
                            )
                            .border(1.dp, colorScheme.outlineVariant)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetContent(sheets: List<SheetContent>) {
    var selectedSheet by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedSheet,
            edgePadding = 16.dp
        ) {
            sheets.forEachIndexed { index, sheet ->
                Tab(
                    selected = selectedSheet == index,
                    onClick = { selectedSheet = index },
                    text = {
                        Text(
                            text = sheet.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        val sheet = sheets.getOrNull(selectedSheet)
        if (sheet == null) {
            Box(modifier = Modifier.fillMaxSize())
        } else {
            // Satu state geser horizontal dipakai semua baris supaya kolom
            // tetap sejajar saat digeser.
            val horizontalState = rememberScrollState()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(sheet.rows) { _, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalState)
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface,
                                modifier = Modifier
                                    .width(110.dp)
                                    .border(0.5.dp, colorScheme.outlineVariant)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresentationContent(slides: List<SlideContent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(slides) { _, slide ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${slide.number}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onPrimary,
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = colorScheme.primary,
                                    shape = RoundedCornerShape(50)
                                )
                                .wrapContentSize(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = slide.title.ifEmpty {
                                globalClass.getString(R.string.slide)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                    }

                    slide.body.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

