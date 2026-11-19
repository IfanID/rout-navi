package com.rout.navi.rootfs

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

/**
 * Service yang berjalan di proses root dan menyediakan akses filesystem NIO.
 * Klien memakai [FileSystemManager.getRemote] untuk membaca direktori.
 */
class RootFsService : RootService() {
    override fun onBind(intent: Intent): IBinder = FileSystemManager.getService()
}
