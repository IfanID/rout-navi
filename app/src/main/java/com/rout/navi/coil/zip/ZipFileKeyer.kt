package com.rout.navi.coil.zip

import coil3.key.Keyer
import coil3.request.Options
import com.rout.navi.screen.main.tab.files.holder.ZipFileHolder

class ZipFileKeyer : Keyer<ZipFileHolder> {
    override fun key(data: ZipFileHolder, options: Options): String {
        return "zip_file:${data.zipTree.source.file.absolutePath}:${data.uniquePath}"
    }
}