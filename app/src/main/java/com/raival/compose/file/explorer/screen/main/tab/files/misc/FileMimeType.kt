package com.raival.compose.file.explorer.screen.main.tab.files.misc

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

    @JvmField
    val videoFileType = arrayOf(
        "mp4", "mov", "avi", "mkv", "wmv", "m4v", "3gp",
        "webm", "flv", "mpeg", "mpg", "ogv", "mxf", "vob", "ts"
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

    @JvmField
    val imageFileType = arrayOf(
        "png", "jpeg", "jpg", "heic", "tiff", "gif", "webp", svgFileType, "bmp", "raw"
    )

    @JvmField
    val audioFileType = arrayOf(
        "mp3", "4mp", "aup", "ogg", "3ga", "m4b", "wav", "acc",
        "m4a", "flac", "aac", "wma", "aiff", "amr", "midi", "mid", "opus"
    )

    @JvmField
    val apkBundleFileType = arrayOf("apks", "xapk", "apkm")

}