package com.raival.compose.file.explorer.screen.viewer.document.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.webkit.WebViewAssetLoader
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.viewer.document.DocumentViewerInstance
import com.raival.compose.file.explorer.screen.viewer.document.webview.ASSET_HOST
import com.raival.compose.file.explorer.screen.viewer.document.webview.DocumentServer
import com.raival.compose.file.explorer.screen.viewer.document.webview.isViewerPage
import com.raival.compose.file.explorer.screen.viewer.document.webview.useRanges
import com.raival.compose.file.explorer.screen.viewer.document.webview.chromiumMajor
import com.raival.compose.file.explorer.screen.viewer.document.webview.webViewFloorParams
import com.raival.compose.file.explorer.screen.viewer.document.webview.minChromiumMajor
import com.raival.compose.file.explorer.screen.viewer.document.webview.documentLength
import com.raival.compose.file.explorer.screen.viewer.pdf.ui.ErrorScreen
import com.raival.compose.file.explorer.screen.viewer.pdf.ui.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri.encode

/**
 * Layar penampil dokumen berbasis WebView, mengikuti pola Gander (MIT,
 * github.com/mokshablr/gander): halaman HTML vendor memuat dokumen dari
 * /doc/ yang disajikan aplikasi sendiri, tanpa jaringan.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentWebViewContent(
    instance: DocumentViewerInstance,
    webView: WebView,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    // Benar bila proses render WebView mati dan halaman tidak bisa dipulihkan.
    var renderGone by remember { mutableStateOf(false) }

    val server = remember(instance.id) {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        DocumentServer(context.contentResolver, loader, instance.uri, instance.mime)
    }

    DisposableEffect(instance.id) {
        onDispose { server.total = -1L }
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

        if (renderGone) {
            Box(modifier = Modifier.fillMaxSize()) {
                ErrorScreen(
                    message = globalClass.getString(R.string.failed_to_read_document),
                    onClose = onBackPress
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                AndroidWebView(
                    instance = instance,
                    webView = webView,
                    server = server,
                    context = context,
                    onRenderGone = { renderGone = true }
                )
            }
        }
    }
}

/**
 * Tempat WebView diletakkan. WebView dibuat sekali di [DocumentViewerContent]
 * supaya kegagalannya bisa ditangkap dan aplikasi jatuh ke penampil POI.
 */
@Composable
private fun AndroidWebView(
    instance: DocumentViewerInstance,
    webView: WebView,
    server: DocumentServer,
    context: Context,
    onRenderGone: () -> Unit
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView }
    ) { web ->
        // Blok ini jalan setiap recomposition; aturan sandbox dibuat idempoten.
        with(web.settings) {
            javaScriptEnabled = true
            // Matikan disengaja: tidak ada halaman vendor yang membacanya, dan
            // penyimpanan di halaman yang merender dokumen tak tepercaya hanya
            // jadi tempat script yang seharusnya tidak jalan untuk mengintip.
            domStorageEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
        }
        // Cookie dinonaktifkan bersama CSP: halaman tidak perlu state jaringan.
        runCatching { CookieManager.getInstance().setAcceptCookie(false) }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = server.respond(request)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = !isViewerPage(request.url)

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                // True membuat WebView tidak membunuh seluruh aplikasi; halaman
                // yang mati diganti pesan error.
                onRenderGone()
                return true
            }
        }
    }

    LaunchedEffect(instance.id) {
        val total = withContext(Dispatchers.IO) {
            documentLength(context, instance.uri)
        }
        server.total = total
        // Strategi muat diputuskan di sini, bukan di halaman, supaya header
        // yang disajikan dan loader yang dipilih halaman tidak bisa berbeda.
        val ranged = if (useRanges(total)) 1 else 0
        val floor = webViewFloorParams(
            major = chromiumMajor(webView.settings.userAgentString),
            floor = minChromiumMajor(instance.ext)
        )
        webView.loadUrl(
            "https://$ASSET_HOST/assets/viewer/${instance.viewerPage}" +
                "?name=${encode(instance.name)}&ext=${encode(instance.ext)}" +
                "&ranged=$ranged" +
                (if (total > 0) "&length=$total" else "") +
                "&night=0" +
                // Lembar kerja membaca zoom ponsel dari sini; 100 = zoom bawaan.
                (if (instance.viewerPage == "xlsx.html") "&type=100" else "") +
                floor
        )
    }
}
