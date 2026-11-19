package com.rout.navi.screen.main.tab.files.coil

import com.rout.navi.screen.main.tab.files.holder.ContentHolder
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.apkFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.audioFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.imageFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.pdfFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.videoFileType

fun canUseCoil(contentHolder: ContentHolder): Boolean {
    return (contentHolder.isFile()
            && imageFileType.contains(contentHolder.extension)
            || videoFileType.contains(contentHolder.extension)
            || audioFileType.contains(contentHolder.extension)
            || contentHolder.extension == apkFileType
            || contentHolder.extension == pdfFileType)
}