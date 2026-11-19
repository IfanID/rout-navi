package com.rout.navi.screen.main.tab.files.task

import com.rout.navi.screen.main.tab.files.holder.ContentHolder

data class TaskContentItem(
    val content: ContentHolder,
    val relativePath: String,
    var status: TaskContentStatus
)