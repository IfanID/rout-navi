package com.rout.navi.screen.main.tab.files.misc

/** Pilihan tab kategori di halaman virtual seperti Video. */
sealed interface CategoryChoice {
    data object All : CategoryChoice

    /** Satu folder, disimpan sebagai path supaya tetap sama setelah penyegaran. */
    data class Folder(val path: String) : CategoryChoice

    /** Riwayat tontonan, isinya video yang pernah diputar. */
    data object History : CategoryChoice
}
