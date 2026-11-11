package com.raival.compose.file.explorer.screen.viewer.image

import android.net.Uri
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance

/**
 * Instance penampil foto. Bila dibuka dari file explorer, [folderPath] dan
 * [initialName] menyimpan konteks folder agar foto lain di folder yang sama
 * bisa dinavigasi dengan geser halaman. Bila dibuka dari kategori gambar
 * (mis. tab Home), [photoPaths] berisi seluruh foto yang tampil di kategori
 * tersebut sehingga geseran melintasi folder. Dibuka dari aplikasi lain,
 * semuanya kosong dan hanya foto [uri] yang ditampilkan.
 */
class ImageViewerInstance(
    override val uri: Uri,
    override val id: String,
    val folderPath: String = emptyString,
    val initialName: String = emptyString,
    val photoPaths: List<String> = emptyList()
) : ViewerInstance {
    override fun onClose() {
    }
}
