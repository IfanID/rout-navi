package com.rout.navi.screen.main.tab.files.misc

object FileMimeType {
    const val apkFileType = "apk"
    const val isoFileType = "iso"
    const val pdfFileType = "pdf"
    const val sqlFileType = "sql"
    const val svgFileType = "svg"
    const val javaFileType = "java"
    const val kotlinFileType = "kt"
    const val jsonFileType = "json"
    const val markdownFileType = "md"
    const val xmlFileType = "xml"
    const val prismPrefsFileType = "prismprefs"
    const val anyFileType = "*/*"

    const val srtFileType = "srt"
    const val vttFileType = "vtt"
    const val assFileType = "ass"
    const val ssaFileType = "ssa"
    const val subFileType = "sub"
    const val lrcFileType = "lrc"
    const val ttmlFileType = "ttml"
    const val dfxpFileType = "dfxp"

    @JvmField
    val docFileType = arrayOf("doc", "docx")

    @JvmField
    val excelFileType = arrayOf("xls", "xlsx")

    @JvmField
    val pptFileType = arrayOf("ppt", "pptx")

    @JvmField
    val fontFileType = arrayOf("ttf", "otf")

    @JvmField
    val vectorFileType = arrayOf(
        "svg", "ai", "eps", "pdf", "dxf",
        "wmf", "emf", "cdr", "odg", "swf"
    )

    @JvmField
    val archiveFileType = arrayOf(
        "zip", "7z", "tar", "jar", "gz", "xz", "obb", "rar", "apk", "apks",
        "iso", "bz2", "tgz", "tbz2", "tbz", "txz", "lz", "lzma"
    )

    // Arsip yang bisa dibuka dan diekstrak langsung tanpa ekstraksi manual.
    @JvmField
    val supportedArchiveFileType = arrayOf(
        "zip", "jar", "apk", "apks", "rar", "7z", "tar"
    )

    // Daftar mengikuti format yang bisa dibuka libVLC, bukan hanya kontainer
    // populer, supaya berkas seperti .m2ts, .rmvb, atau .wtv tetap masuk ke
    // pemutar video dan tidak dianggap berkas tak dikenal.
    @JvmField
    val videoFileType = arrayOf(
        "3g2", "3gp", "3gp2", "3gpp", "amv", "asf", "avi", "bik", "divx",
        "dv", "f4v", "flv", "gvi", "gxf", "h264", "h265", "hdmov", "hevc",
        "m1v", "m2p", "m2t", "m2ts", "m2v", "m4v", "mj2", "mjpeg", "mjpg",
        "mkv", "mov", "mp2v", "mp4", "mp4v", "mpe", "mpeg", "mpeg1",
        "mpeg2", "mpeg4", "mpg", "mpv", "mpv2", "mts", "mxf", "mxg", "nsv",
        "nut", "nuv", "ogm", "ogv", "ogx", "ps", "rec", "rm", "rmvb", "tod",
        "tp", "trp", "ts", "tts", "vob", "vro", "webm", "wmv", "wtv",
        "xesc", "y4m"
    )

    @JvmField
    val subtitleTextFileType = arrayOf(
        srtFileType, vttFileType, assFileType, ssaFileType, subFileType,
        "sbv", "smi", "sami", ttmlFileType, dfxpFileType, lrcFileType,
        "scc", "mpl2"
    )

    // Subtitle bitmap tidak bisa dibaca sebagai teks, jadi hanya ditandai
    // dan tidak dimasukkan ke daftar file yang bisa diedit.
    @JvmField
    val subtitleBinaryFileType = arrayOf("idx", "sup")

    @JvmField
    val subtitleFileType = subtitleTextFileType + subtitleBinaryFileType

    @JvmField
    val shellFileType = arrayOf(
        "sh", "zsh", "bash", "ksh", "fish", "csh", "tcsh", "ash", "dash",
        "shell", "rc", "zshrc", "bashrc", "profile", "zsh_history", "bash_history",
        "zsh-theme"
    )

    // Konfigurasi teks umum, termasuk termux.properties.
    @JvmField
    val configFileType = arrayOf(
        "properties", "conf", "cfg", "ini", "env"
    )

    // Kunci publik dan sertifikat teks (SSH/TLS).
    @JvmField
    val keyFileType = arrayOf(
        "pub", "pem", "crt", "cer", "ssh_config", "sshd_config"
    )

    // File konfigurasi shell yang tidak bisa dideteksi dari ekstensi saja.
    private val textConfigNames = setOf(
        ".zshrc", ".zshenv", ".zprofile", ".zlogin", ".zlogout",
        ".bashrc", ".bash_profile", ".bash_login", ".bash_logout", ".profile",
        ".zsh_history", ".bash_history", ".shell.pre-oh-my-zsh",
        ".zshrc.pre-oh-my-zsh", ".bashrc.pre-oh-my-zsh",
        ".inputrc", ".vimrc", ".nanorc", ".gitconfig", ".editorconfig",
        ".tmux.conf", ".screenrc",
        "authorized_keys", "known_hosts", "ssh_config", "sshd_config"
    )

    /**
     * Mendeteksi script/konfigurasi shell dari nama file maupun ekstensi.
     * Dotfile seperti .zshrc tidak punya ekstensi yang dikenal MimeTypeMap.
     */
    fun isTextConfigFile(name: String, extension: String): Boolean {
        val lowerName = name.lowercase()
        val lowerExtension = extension.lowercase()

        if (lowerExtension in shellFileType) return true
        if (lowerExtension in configFileType) return true
        if (lowerExtension in keyFileType) return true
        if (lowerName in textConfigNames) return true
        if (lowerName.startsWith(".zcompdump") && !lowerName.endsWith(".zwc")) return true

        return lowerName.startsWith(".zshrc.pre-oh-my-zsh") ||
                lowerName.startsWith(".bashrc.pre-oh-my-zsh")
    }

    @JvmField
    val codeFileType = arrayOf(
        javaFileType, "xml", "py", "css", kotlinFileType, "cs", "xml", jsonFileType, "html",
        "js", "ts", "php", "rb", "pl", "sh", "cpp", "c", "h", "swift", "go", "rs",
        "scala", "sql", "r", "md", "ini", "yaml", "yml"
    ) + shellFileType + configFileType + keyFileType

    @JvmField
    val editableFileType = arrayOf(
        "txt", "text", "log", "dsc", "apt", "rtf", "rtx", "md",
        "csv", "tsv", "ini", "conf", "cfg", "nfo", "json", "xml"
    ) + subtitleTextFileType + configFileType + keyFileType

    // Format Office yang dibaca penampil dokumen bawaan (jalur WebView).
    // Word: docx/docm/dotx, Excel: xlsx/xls/xlsm/xlsb/xltx, PowerPoint: pptx/ppsx/pptm/potx.
    @JvmField
    val officeDocumentFileType = arrayOf(
        "docx", "docm", "dotx",
        "xlsx", "xls", "xlsm", "xlsb", "xltx",
        "pptx", "ppsx", "pptm", "potx"
    )

    @JvmField
    val imageFileType = arrayOf(
        "png", "jpeg", "jpg", "heic", "tiff", "gif", "webp", svgFileType, "bmp", "raw", "jxl"
    )

    // Sama seperti daftar video, mencakup format audio yang didukung libVLC.
    @JvmField
    val audioFileType = arrayOf(
        "3ga", "4mp", "a52", "aac", "ac3", "ac4", "acc", "adt", "adts",
        "aif", "aifc", "aiff", "amr", "ape", "au", "aup", "caf", "dts",
        "dtshd", "eac3", "flac", "it", "m4a", "m4b", "m4p", "m4r", "mid",
        "midi", "mka", "mlp", "mod", "mp1", "mp2", "mp3", "mpa", "mpc",
        "mpga", "ofr", "ofs", "oga", "ogg", "opus", "ra", "ram", "s3m",
        "spx", "tta", "w64", "wav", "wax", "wma", "wv", "xa", "xm"
    )

    @JvmField
    val apkBundleFileType = arrayOf("apks", "xapk", "apkm")

}