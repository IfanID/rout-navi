package com.rout.navi.screen.logs

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rout.navi.base.BaseActivity
import com.rout.navi.screen.logs.ui.LogsScreen
import com.rout.navi.theme.FileExplorerTheme

class LogsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
    }

    override fun onPermissionGranted() {
        setContent {
            FileExplorerTheme {
                LogsScreen { onBackPressedDispatcher.onBackPressed() }
            }
        }
    }
}