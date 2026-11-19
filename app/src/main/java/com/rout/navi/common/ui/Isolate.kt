package com.rout.navi.common.ui

import androidx.compose.runtime.Composable

@Composable
fun Isolate(content: @Composable () -> Unit) {
    content()
}