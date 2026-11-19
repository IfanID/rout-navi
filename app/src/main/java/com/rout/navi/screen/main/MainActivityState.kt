package com.rout.navi.screen.main

import androidx.compose.foundation.lazy.LazyListState
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.emptyString
import com.rout.navi.screen.main.tab.Tab
import com.rout.navi.screen.main.tab.files.holder.StorageDevice

data class MainActivityState(
    val title: String = globalClass.getString(R.string.main_activity_title),
    val subtitle: String = emptyString,
    val showAppInfoDialog: Boolean = false,
    val showJumpToPathDialog: Boolean = false,
    val showSaveEditorFilesDialog: Boolean = false,
    val showStartupTabsDialog: Boolean = false,
    val isSavingFiles: Boolean = false,
    val selectedTabIndex: Int = 0,
    val storageDevices: List<StorageDevice> = emptyList(),
    val tabs: List<Tab> = emptyList(),
    val tabLayoutState: LazyListState = LazyListState(),
    val hasNewUpdate: Boolean = false
)