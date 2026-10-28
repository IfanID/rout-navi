# Rout Navi v0.0.1 — Release Notes

> Tag: `v0.0.1` · Target: `main` · Label: None
> Berkas rilis: `File Explorer-release.apk`

Rilis pertama **Rout Navi v0.0.1** — fork pribadi [IfanID](https://github.com/IfanID) dari [Prism File Explorer](https://github.com/Raival-e/Prism-File-Explorer). Dibangun dengan Kotlin dan Jetpack Compose.

## ✨ Fitur

### 🎬 Subtitle dan terjemahan

- Format subtitle teks didukung di editor: `srt`, `vtt`, `ass`, `ssa`, `sub`, `sbv`, `smi`, `sami`, `ttml`, `dfxp`, `lrc`, `scc`, `mpl2`.
- Grammar TextMate untuk `srt`, `vtt`, `ass`, dan `lrc`; `ttml`/`dfxp` memakai grammar XML.
- `idx`/`sup` ditandai subtitle bitmap; `.sub` biner (VobSub) ditolak lewat deteksi header MPEG-PS.
- Terjemahan Indonesia lengkap (455 string).

### 🗜️ Arsip ZIP, 7Z, TAR, RAR

- Ekstrak ke folder saat ini, folder arsip, atau path tujuan pilihan.
- Buka isi arsip tanpa ekstrak (live browsing); format dideteksi dari magic bytes.
- Kompres ke ZIP, 7Z, atau TAR dengan pemilih format; format terakhir diingat.
- Dialog sandi untuk arsip terenkripsi dan penanganan konflik nama berkas.

### 🔐 Akses Root

- Layanan root libsu NIO dengan `FLAG_MOUNT_MASTER` agar isi `/data/data` terbaca lengkap.
- Daftar Root dinamis tanpa folder hardcode, trail back, dan urutan nama/folder.
- Metadata symlink lewat `lstat`; ikon link dan link rusak dibedakan.
- Buka berkas teks area Root di editor lewat pipe NIO; fallback ke shell root.

### 🔖 Markah folder Root

- Markah dan sematan Root dibangun ulang lewat `RootShell.holderFor()`.
- Disimpan langsung ke DataStore; opsi markah tersedia di Root home.

### 🔑 Berkas teks, konfigurasi, dan kunci SSH

- Kunci publik dan sertifikat: `*.pub`, `*.pem`, `*.crt`, `*.cer`, `authorized_keys`, `known_hosts`, `ssh_config`, `sshd_config`.
- Script shell, konfigurasi (`*.properties`, `*.conf`, `*.cfg`, `*.ini`, `*.env`), dan dotfile tanpa ekstensi (`.zshrc`, `.bashrc`, `.vimrc`, `.gitconfig`, dll.).

### ☑️ Properti pilihan multi-item

- Menampilkan **Lokasi** dan **Nama** untuk berkas, folder, dan area Root.
- Pilihan dari beberapa folder dikelompokkan per folder.
- Long-press menyalin path lengkap semua item, satu path per baris.

### 🗂️ Pilihan lintas folder dan tab

- **Beda folder**: pindah folder tidak menghapus pilihan.
- **Beda tab**: pilihan digabung antar tab; tab baru dibuka di Penyimpanan internal.
- Badge **ON/OFF** di strip seleksi, long-press saat OFF menyalakan mode, tombol **Batal** mereset semuanya.

## 📄 Dokumentasi

- README dua bahasa: [Bahasa Indonesia](https://github.com/IfanID/rout-navi/blob/main/README.md) dan [English](https://github.com/IfanID/rout-navi/blob/main/README.en.md).

## 📦 Instalasi

- APK release sudah ditandatangani; mendukung **Android 8.0 (API 26)** ke atas.
- Unduh `File Explorer-release.apk` di bagian bawah, lalu pasang.

> [!WARNING]
> Ini rilis pertama dengan `versionCode 1`. Kalau di HP masih terpasang versi lama (`1.3.2`, versionCode 10), **uninstall dulu** sebelum memasang APK ini — Android menolak downgrade.
