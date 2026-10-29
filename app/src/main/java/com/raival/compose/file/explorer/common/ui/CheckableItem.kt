package com.raival.compose.file.explorer.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp

@Composable
fun CheckableItem(
    text: String,
    isChecked: Boolean,
    icon: ImageVector?,
    supportingText: String? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(text = text)
                // Teks pendukung dipakai untuk menampilkan detail teknis tanpa mengganggu keterbacaan
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        onClick = { onClick() },
        trailingIcon = {
            if (isChecked) {
                Icon(imageVector = Icons.Rounded.Done, contentDescription = null)
            }
        },
        leadingIcon = if (icon != null) {
            { Icon(imageVector = icon, contentDescription = null) }
        } else null
    )
}