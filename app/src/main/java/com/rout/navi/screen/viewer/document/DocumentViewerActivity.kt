package com.rout.navi.screen.viewer.document

import android.net.Uri
import androidx.activity.compose.setContent
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.ui.SafeSurface
import com.rout.navi.screen.viewer.ViewerActivity
import com.rout.navi.screen.viewer.ViewerInstance
import com.rout.navi.screen.viewer.document.ui.DocumentViewerContent
import com.rout.navi.theme.FileExplorerTheme

class DocumentViewerActivity : ViewerActivity() {
    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return DocumentViewerInstance(uri, uid)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance is DocumentViewerInstance) {
            setContent {
                FileExplorerTheme {
                    SafeSurface(false) {
                        DocumentViewerContent(
                            instance = instance,
                            onBackPress = { onBackPressedDispatcher.onBackPressed() }
                        )
                    }
                }
            }
        } else {
            globalClass.showMsg(getString(R.string.failed_to_read_document))
            finish()
        }
    }
}
