package com.rout.navi.screen.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rout.navi.R

/**
 * Tombol riwayat tontonan di samping kanan tab Video. Latarnya hitam tetap
 * supaya sama dengan daftar berkas. Saat dipilih, teksnya lebih tegas dan
 * diberi garis bawah, karena warna latar tidak lagi membedakan.
 */
@Composable
fun HistoryTabButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = 100.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(if (selected) Color.Black else Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.watch_history),
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.alpha(if (selected) 1f else 0.5f)
            )
            // Garis bawah hanya muncul saat dipilih, penanda tab aktif.
            if (selected) {
                Spacer(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .height(2.dp)
                        .background(Color.White)
                )
            }
        }
    }
}
