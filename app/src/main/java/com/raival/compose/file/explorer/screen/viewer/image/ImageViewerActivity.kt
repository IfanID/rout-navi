package com.raival.compose.file.explorer.screen.viewer.image

import android.net.Uri
import androidx.activity.compose.setContent
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.image.ui.ImageViewerScreen
import com.raival.compose.file.explorer.theme.FileExplorerTheme

class ImageViewerActivity : ViewerActivity() {
    companion object {
        /** Path folder foto yang diketuk; kosong bila dibuka dari app lain. */
        const val EXTRA_FOLDER = "folder"
        /** Nama berkas foto yang diketuk, jadi halaman awal pager. */
        const val EXTRA_NAME = "name"
        /** Daftar path seluruh foto kategori; diisi hanya dari kategori gambar. */
        const val EXTRA_PATHS = "paths"
    }

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return ImageViewerInstance(
            uri = uri,
            id = uid,
            folderPath = intent.getStringExtra(EXTRA_FOLDER) ?: emptyString,
            initialName = intent.getStringExtra(EXTRA_NAME) ?: emptyString,
            photoPaths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()
        )
    }

    override fun onReady(instance: ViewerInstance) {
        setContent {
            FileExplorerTheme {
                SafeSurface(enableStatusBarsPadding = false) {
                    ImageViewerScreen(instance)
                }
            }
        }
    }
}
