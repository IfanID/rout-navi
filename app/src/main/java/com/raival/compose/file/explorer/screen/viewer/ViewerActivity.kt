package com.raival.compose.file.explorer.screen.viewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.base.BaseActivity
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.randomString

abstract class ViewerActivity : BaseActivity() {
    private var uri: Uri? = null
    private var uid: String = emptyString
    private var currentInstance: ViewerInstance? = null

    abstract fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance
    abstract fun onReady(instance: ViewerInstance)

    /** Instance aktif, dipakai subclass untuk menghentikan pemutaran. */
    protected val activeInstance: ViewerInstance?
        get() = currentInstance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (intent.data == null) {
            finish()
            return
        }

        uri = intent.data

        if (savedInstanceState != null) {
            uid = savedInstanceState.getString("uid", String.randomString(12))
        }

        if (uid.isEmpty()) {
            uid = String.randomString(12)
        }

        val instance = globalClass.viewersManager.instances.find { it.id == uid }

        currentInstance = instance ?: onCreateNewInstance(uri!!, uid).also {
            globalClass.viewersManager.instances.add(it)
        }

        checkPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("uid", uid)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Saat rotasi, instance dipertahankan supaya pemutaran tidak dimulai
        // dari awal; surface video cukup dipasang ulang oleh activity baru.
        if (isChangingConfigurations) return
        currentInstance?.onClose()
        globalClass.viewersManager.instances.remove(currentInstance)
    }

    override fun onPermissionGranted() {
        onReady(currentInstance!!)
    }
}