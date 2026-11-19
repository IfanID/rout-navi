package com.rout.navi.screen.viewer.pdf

import android.net.Uri
import androidx.activity.compose.setContent
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.ui.SafeSurface
import com.rout.navi.screen.viewer.ViewerActivity
import com.rout.navi.screen.viewer.ViewerInstance
import com.rout.navi.screen.viewer.pdf.ui.PdfViewerContent
import com.rout.navi.theme.FileExplorerTheme
import net.engawapg.lib.zoomable.ExperimentalZoomableApi

class PdfViewerActivity : ViewerActivity() {
    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return PdfViewerInstance(uri, uid)
    }

    @OptIn(ExperimentalZoomableApi::class)
    override fun onReady(instance: ViewerInstance) {
        if (instance is PdfViewerInstance) {
            setContent {
                FileExplorerTheme {
                    SafeSurface(false) {
                        PdfViewerContent(
                            instance = instance,
                            onBackPress = { onBackPressedDispatcher.onBackPressed() }
                        )
                    }
                }
            }
        } else {
            globalClass.showMsg(getString(R.string.invalid_pdf))
            finish()
        }
    }
}