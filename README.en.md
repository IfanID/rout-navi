[Bahasa Indonesia](README.md) | **English**

> [!NOTE]
> Personal fork by **IfanID** of [Prism File Explorer](https://github.com/Raival-e/Prism-File-Explorer).

<div align="center">

<img src="assets/app_icon.png" width="120" alt="Rout Navi logo"/>

# Rout Navi

<sub>Fork of [Prism File Explorer](https://github.com/Raival-e/Prism-File-Explorer)</sub>

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg?logo=android)](https://www.android.com/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Release](https://img.shields.io/github/v/release/IfanID/rout-navi?label=Release)](https://github.com/IfanID/rout-navi/releases)
[![APK Downloads](https://img.shields.io/github/downloads/IfanID/rout-navi/total.svg?label=APK%20Downloads)](https://github.com/IfanID/rout-navi/releases)
[![Stars](https://img.shields.io/github/stars/IfanID/rout-navi?style=flat&logo=github)](https://github.com/IfanID/rout-navi/stargazers)
[![Forks](https://img.shields.io/github/forks/IfanID/rout-navi?style=flat&logo=github)](https://github.com/IfanID/rout-navi/network/members)

**A modern, feature-rich, and lightweight file manager for Android, built entirely with Kotlin and Jetpack Compose.**

*Delivering a seamless file management experience with a beautiful Material Design interface*

</div>

---

## 📱 Screenshots

<div align="center">
  <img src="assets/image1.png" width="22%" alt="Main Interface"/>
  <img src="assets/image2.png" width="22%" alt="File Operations"/>
  <img src="assets/image3.png" width="22%" alt="Apps Extractor"/>
  <img src="assets/image4.png" width="22%" alt="Text Editor"/>
</div>

<div align="center">
  <img src="assets/image6.png" width="21%" alt="Audio Player"/>
  <img src="assets/image7.png" width="21%" alt="Image Viewer"/>
  <img src="assets/image5.png" width="21%" alt="PDF Viewer"/>
</div>

---

## ✨ Key Features

### 🆕 Additional Features (IfanID Fork)

Summary of the features built in this fork, from commit `068ad33` up to the latest commit.

#### 🎬 Subtitles and Translation

- Text subtitle formats supported in the editor: `srt`, `vtt`, `ass`, `ssa`, `sub`, `sbv`, `smi`, `sami`, `ttml`, `dfxp`, `lrc`, `scc`, and `mpl2`.
- TextMate grammars for `srt`, `vtt`, `ass`, and `lrc`; `ttml`/`dfxp` reuse the existing XML grammar.
- `idx` and `sup` are marked as bitmap subtitles that cannot be edited; binary `.sub` (VobSub) is rejected via MPEG-PS header detection.
- Subtitle/closed-caption icons and plain-text fallback when a grammar fails to load.
- Complete Indonesian translation (`values-in`, 455 strings).

#### 🗜️ ZIP, 7Z, TAR, and RAR Archives

- Extract archives to the current folder, into a dedicated archive folder, or to a chosen destination path.
- Browse archive contents without extracting (live ZIP, 7Z, TAR, RAR); the format is detected from magic bytes, not just the extension.
- Compress to ZIP, 7Z, or TAR with a format picker next to the name field; the last format is remembered.
- Password dialog for encrypted archives and file-name conflict handling during extraction.

#### 🔐 Root Access

- libsu NIO root service with `FLAG_MOUNT_MASTER` so the full `/data/data` contents are readable.
- Dynamic Root listing without hardcoded folders, including a back trail and name/folder ordering.
- Symlink metadata read via `lstat`; link and broken-link icons are distinguished.
- Text files under Root can be opened in the editor through an NIO pipe.
- Falls back to a root shell if the root service fails.

#### 🔖 Root Folder Bookmarks

- Root bookmarks and pins are rebuilt through `RootShell.holderFor()` so they are still recognized as folders.
- Bookmarks are written to and read from DataStore directly, so they survive unsynchronized session state.
- Add/remove bookmark options are also available while in Root home.

#### 🔑 Text, Configuration, and SSH Key Files

- Public key and text certificate files open directly in the editor: `*.pub`, `*.pem`, `*.crt`, `*.cer`, `authorized_keys`, `known_hosts`, `ssh_config`, and `sshd_config`.
- Shell scripts recognized as text: `*.sh`, `*.zsh`, `*.bash`, `*.ksh`, `*.fish`, `*.csh`, `*.tcsh`, `*.ash`, `*.dash`, `*.shell`, `*.rc`, `*.zshrc`, `*.bashrc`, `*.profile`, shell history files, and Zsh themes.
- Text configuration files: `*.properties`, `*.conf`, `*.cfg`, `*.ini`, `*.env`, including `termux.properties`.
- Extension-less dotfiles such as `.zshrc`, `.zshenv`, `.zprofile`, `.bashrc`, `.bash_profile`, `.profile`, `.vimrc`, `.nanorc`, `.gitconfig`, `.editorconfig`, `.tmux.conf`, and `.screenrc` are still detected as text.

#### ☑️ Multi-Item Selection Properties

- Selection Properties show **Location** and **Name** for every file, folder, and Root area.
- Selections from multiple folders are grouped per folder.
- Long-pressing a property row copies the full path of every item, one path per line.

#### 🗂️ Cross-Folder and Cross-Tab Selection

- The options menu provides **Different folder** and **Different tab**.
- **Different folder**: switching folders no longer clears the selection, so files from several folders can be processed together.
- **Different tab**: the selection is merged across tabs, and a new tab opens in Internal Storage so you can pick from another location.
- An **ON/OFF** badge in the selection toolbar strip: `ON` means a tap selects, `OFF` means a tap opens the file/folder while the selection is kept.
- Long-pressing while the badge is `OFF` turns selection mode on immediately.
- The `Cancel` button clears the whole selection and disables cross-folder/tab mode.

### 📦 Built-in Prism Features

<details>
<summary><b>Show the built-in feature list</b></summary>

### 🎨 **Modern User Interface**

- **Jetpack Compose UI**: Fully declarative and responsive interface
- **Material Design 3**: Beautiful, consistent design language
- **Dark/Light Theme**: Automatic theme switching support
- **Smooth Animations**: Fluid transitions and interactions

### 📁 **Comprehensive File Management**

- **Complete File Operations**: Create, copy, move, rename, delete files and folders
- **Advanced Selection**: Multi-select with intuitive gestures
- **Smart Cut/Copy/Paste**: Clipboard operations with visual feedback
- **Batch Operations**: Perform actions on multiple files simultaneously
- **File Properties**: Detailed information about files and directories

### 🗂️ **Advanced Features**

- **Multi-Tab Interface**: Manage multiple directories simultaneously
- **Quick Navigation**: Breadcrumb navigation and quick access shortcuts
- **Search Functionality**: Find files and folders quickly
- **Sorting Options**: Sort by name, size, date, type with ascending/descending order
- **View Modes**: Grid and list view options
- **Root Access**: Browse protected system directories through a root service

### 📺 **Built-in Media Viewers**

- **Image Viewer**: Support for JPEG, PNG, GIF, WebP, and more
- **Video Player**: Play MP4, AVI, MKV, MOV, and other formats
- **Audio Player**: MP3, WAV, FLAC, OGG playback support
- **PDF Viewer**: View PDF documents natively
- **Text Editor**: Syntax highlighting for code files
- **Subtitle Support**: View and edit SRT, VTT, ASS, SSA, LRC, and other subtitle formats with syntax highlighting

### 🗜️ **Archive Management**

- **Extract Archives**: Support for ZIP, 7Z, TAR, and RAR formats
- **Create Archives**: Compress files and folders into ZIP, 7Z, or TAR format
- **Archive Preview**: Browse archive contents without extraction (live ZIP, 7Z, TAR, and RAR)

### ⚡ **Performance & Efficiency**

- **Lightweight**: Minimal resource usage and fast startup
- **Optimized**: Smooth performance on low-end devices
- **Background Operations**: Non-blocking file operations
- **Memory Efficient**: Smart memory management for large directories

</details>

---

## 📥 Download Options

| Platform            | Link                                                                                                                                        |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| **GitHub Releases** | [![GitHub](https://img.shields.io/badge/GitHub-Download-black?style=for-the-badge&logo=github)](https://github.com/IfanID/rout-navi/releases) |

### 📋 System Requirements

- **Android Version**: 8.0 (API 26) or higher
- **Permissions**: Storage access for file management

---

## 🛠️ Built With

| Technology                                                                                                               | Purpose                  | Version        |
|--------------------------------------------------------------------------------------------------------------------------|--------------------------|----------------|
| ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)                             | **Programming Language** | Latest         |
| ![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white) | **UI Framework**         | Latest Stable  |
| ![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)                          | **Platform**             | SDK 36         |
| ![Material Design](https://img.shields.io/badge/Material%20Design-757575?style=flat&logo=materialdesign&logoColor=white) | **Design System**        | Material 3     |

---

## 🔨 Building from Source

### Prerequisites

- **JDK**: 17–21 (Gradle 8.7)
- **Android SDK**: API level 36
- **Git**: For cloning the repository

### Build Instructions

1. **Clone the repository**:
   ```bash
   git clone https://github.com/IfanID/rout-navi.git
   cd rout-navi
   ```

2. **Build the project**:
   ```bash
   ./gradlew assembleRelease
   ```

3. **Find the APK**:
    - Generated APK will be in `app/build/outputs/apk/release/`

### Optional Note: Building on ARM64 (Termux)

This section is **not a requirement** — it only applies when building on an ARM64 device (for example Termux with proot-distro). Building on x86_64 with Android Studio does not need these settings.

- Use JDK 21, because Gradle 8.7 fails on JDK 25.
- Point `android.aapt2FromMavenOverride` to the ARM64 aapt2 and set `android.enableResourceOptimizations=false` through `~/.gradle/gradle.properties` (not the project `gradle.properties`), so the x86_64 CI build keeps working.
- Never commit absolute paths from your local machine into project files.

---

## 🤝 Contributing

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### 📝 **Development Guidelines**

- Follow Kotlin coding conventions
- Write meaningful commit messages

---

## 📊 Project Stats

![GitHub stats](https://github-readme-stats.vercel.app/api?username=IfanID&repo=rout-navi&show_icons=true&theme=default)

---

## 🙏 Original Source

This project is a fork of **Prism File Explorer** by [Raival-e](https://github.com/Raival-e).
Thanks to the original author for the codebase and the GPLv3 license that makes this fork possible.

- Original repository: https://github.com/Raival-e/Prism-File-Explorer

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0**.

```
Prism File Explorer - A modern Android file manager
Copyright (C) 2024 Raival-e
Fork modifications © 2026 IfanID

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

See the [LICENSE](LICENSE) file for the full license text.

---

## 💬 Support & Community

| Platform               | Purpose                       |
|------------------------|-------------------------------|
| **GitHub Issues**      | Bug reports, feature requests |
| **GitHub Discussions** | Community support, questions  |
| **Email**              | Private inquiries             |

**⭐ If you find this project useful, please consider giving it a star!**

[![Star History](https://img.shields.io/github/stars/IfanID/rout-navi?style=social)](https://github.com/IfanID/rout-navi/stargazers)
