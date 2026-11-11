package com.raival.compose.file.explorer.screen.viewer.document

import android.net.Uri
import androidx.activity.compose.setContent
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.document.ui.DocumentViewerContent
import com.raival.compose.file.explorer.theme.FileExplorerTheme

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
