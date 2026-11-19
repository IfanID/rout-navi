package com.raival.compose.file.explorer.screen.viewer.video.engine

/**
 * Titik masuk pemilihan pabrik mesin per varian aplikasi.
 *
 * Berkas ini punya salinan di sourceSet `media3` dan `vlc`. Salinan varian vlc
 * memakai libVLC, salinan varian media3 tidak, sehingga kode bersama tidak pernah
 * merujuk kelas libVLC dan R8 tidak menyimpan paket yang tidak dipakai.
 */
object EngineFactoryProvider {

    /** Pabrik sesuai varian yang sedang dibangun. */
    fun get(): EngineFactory = engineFactory

    private val engineFactory: EngineFactory = createFactory()
}

/** Dipenuhi salinan berkas ini di sourceSet varian. */
private fun createFactory(): EngineFactory = Media3OnlyEngineFactory
