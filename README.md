# 🎵 VmusiX — `com.zaaam.vmusix`

[![build](https://github.com/Verucaa/musikku/actions/workflows/build.yml/badge.svg)](https://github.com/Verucaa/musikku/actions/workflows/build.yml)
![minSdk 33](https://img.shields.io/badge/minSdk-33-blue)
![target 35](https://img.shields.io/badge/targetSdk-35-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple)
![Compose](https://img.shields.io/badge/Compose-Material3-orange)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-red)

Player musik hybrid ala Apple Music: **lagu lokal + trending & streaming
YouTube dalam satu app, satu UI.** Build sendiri, sideload pribadi — GitHub
Actions menandatangani dan menerbitkan APK tiap push.

> **Untuk pengguna HP, bukan developer:** lompat ke
> [Pasang APK](#-pasang-apk) dan [Masalah & Solusi](#-masalah--solusi).
> Tidak perlu Android Studio, tidak perlu build apa pun.

## ✨ Fitur

| | |
|---|---|
| 📁 Library folder-scoped | Scan **hanya** `Music/VmusiX` — ringtone & suara WA tidak ikut. `Music/LiPhify` ikut dibaca sebagai warisan, jadi library lama tidak hilang. |
| 🔥 Trending di Home | Kiosk Trending YouTube, selalu ada isi walau trending kosong |
| 🔍 Unified search | Lokal (instant) + YouTube (async), scope Semua/Perangkat/YouTube |
| ▶️ Satu engine | Media3 untuk semua sumber; URL YouTube di-resolve ulang tiap play |
| 🔁 Anti-diam | Lagu gagal dimuat otomatis di-muat ulang sekali, lalu di-skip, lalu berhenti dengan pesan jelas (tidak looping diam) |
| 📃 Queue + playlist | Campur lokal & YouTube, persist restart, favorit via ☆ |
| 💬 Lirik synced | LRCLIB, highlight ikut posisi lagu |
| 🌃 Liquid Glass | Haze blur, rim-light, motion `pressable`/`appear` |
| 🩺 Crash log | Stack trace tersimpan di dalam app — bisa dibaca tanpa adb |

## 🚀 Build dari sumber

**Butuh:** JDK 17, Android Studio (Ladybug+), Android SDK 35. Gradle tidak
perlu diinstal sendiri — sudah pakai *wrapper*.

```sh
git clone https://github.com/Verucaa/musikku.git
cd musikku
./gradlew :app:assembleDebug      # APK debug, bisa langsung dipasang
```

VmusiX sekarang dipush ke `Verucaa/musikku`. Kalau mau repository sendiri:

```sh
git remote rename origin upstream
git remote add origin <url-repo-mu>
git push -u origin main
```

Hasilnya di `app/build/outputs/apk/`. Nama file sudah diberi versi, jadi
tidak perlu menebak yang mana yang benar.

| Perintah | Hasil |
|---|---|
| `./gradlew :app:assembleDebug` | `VmusiX-debug-1.0.0.apk`, **tidak** perlu signing |
| `./gradlew :app:assembleRelease` | APK release, **hanya terpasang kalau env signing terisi** (lihat di bawah) |
| `./gradlew :app:installDebug` | Build + pasang ke HP yang tersambung USB |

Buka langsung di Android Studio juga bisa: `File ▸ Open` → pilih folder ini
→ tunggu Gradle sync → tombol ▶.

## 📦 Pasang APK (tidak perlu build)

1. Buka tab **Releases** di repo ini
2. Unduh `VmusiX-<tag>-<commit>-signed.apk`
3. Pasang lewat LADB, atau salin ke HP lalu buka berkasnya
   (butuh izinkan "Install unknown apps" untuk aplikasi Files/browser)

> ⚠️ Hanya file berakhiran `-signed.apk` yang bisa dipasang. Tanpa tanda
> tangan digital, Android menolak dengan pesan yang membingungkan.

### Kalau "app tidak terpasang" / `INSTALL_PARSE_FAILED_NO_CERTIFICATES`

APK-nya **unsigned** — artinya `VMUSIX_KEYSTORE_B64` di GitHub Secrets belum
diisi.Build ulang sendiri dengan signing (lihat bagian siguiente), atau
pakai build debug yang tidak butuh signing sama sekali.

## 🔑 Signing

Tidak ada satu pun password atau keystore di dalam repo ini. APK release
ditandatangani memakai keystore yang **kamu buat sendiri**.

### sekali saja, di komputer kamu

```sh
tools/make_keystore.sh ~/keys/vmusix-release.jks
```

Script akan meminta password (minimal 6 karakter) lalu membuat keystore
PKCS12 RSA-4096 valid 27 tahun.

> 🚨 **Backup file `.jks` itu sekarang juga.** Kalau hilang atau berubah,
> kamu **tidak akan pernah bisa** menerbitkan update untuk app ini — Android
> menolak upgrade yang ditandatangani key berbeda. Ini penyebab paling
> sering orang dipaksa ganti package name.

### di GitHub (untuk build otomatis)

Tambahkan 4 secret ini di `Settings ▸ Secrets and variables ▸ Actions`:

| Nama secret | Isi |
|---|---|
| `VMUSIX_KEYSTORE_B64` | `base64 < ~/keys/vmusix-release.jks` |
| `VMUSIX_KEYSTORE_PASSWORD` | password keystore |
| `VMUSIX_KEY_ALIAS` | `vmusix` |
| `VMUSIX_KEY_PASSWORD` | sama dengan password keystore |

Kalau keempatnya kosong, workflow tetap berjalan dan meng-upload APK
unsigned sebagai artifact — tapi **tidak** menerbitkannya ke Release, supaya
user tidak mengunduh file yang gagal dipasang.

> Proyek asal pernah menaruh keystore sebagai teks (`keystore.txt`) di dalam
> repo, sehingga ikut ter-push. Key itu dianggap bocor dan **tidak boleh
> dipakai lagi**. Kalau kamu migrasi dari app lama, buat key baru.

## 🛠 Stack

Kotlin 2.0.20 · Compose Material3 · Media3 1.9.0 · Room 2.6.1 · Hilt ·
NewPipeExtractor v0.26.5 · Coil · Haze · LRCLIB — single-module,
`minSdk 33`, `compile/target 35`.

```text
ui/ → ViewModel → repository → Room / NewPipeExtractor (via data/youtube SAJA)
              ↘ MediaController → VmusiXSessionService (ExoPlayer)
```

## 🗺 Peta

```text
app/src/main/java/com/zaaam/vmusix/
├── MainActivity.kt     # tab Home/New/Library + search, Haze, BackHandler
├── VmusiXApp.kt        # Hilt + NewPipe.init + CrashLog
├── core/CrashLog.kt    # stack trace terakhir ke filesDir/crash.log
├── playback/           # VmusiXSessionService (Media3, ExoPlayer)
├── domain/model/       # Track, PlaybackSource(Local|YouTube), Lyrics
├── data/local/         # Room (v1) + scanner folder-scoped
├── data/youtube/       # SATU-SATUNYA pemanggil NewPipeExtractor
├── data/…Repository    # MusicRepository, LyricsRepository
└── ui/ theme|common|home|browse|library|search|playlist|player|nav/

tools/
├── make_icons.py       # regenerate ikon launcher (butuh Pillow)
└── make_keystore.sh    # buat keystore rilis
```

## 🧭 Keputusan final (jangan dibalik diam-diam)

Scan `Music/VmusiX` (+ legacy `Music/LiPhify`) · Media3 tanpa BOM, pin
`1.9.0` · NewPipeExtractor pin eksak · `target 35` · tanpa Radio/Download/
Login · app ini **bukan** continuation LiPhify yang sudah dihentikan
pengembangnya, melainkan fork yang dipelihara sendiri · GPL-3.0.

## 🩺 Masalah & Solusi

| Gejala | Penyebab | Solusi |
|---|---|---|
| Home kosong | DB kosong / offline | Cek `Music/VmusiX` ada isinya, lalu Library ▸ **Refresh** |
| Lagu tidak berbunyi | URL YouTube kedaluwarsa, atau file lokal hilang | Ketuk lagunya lagi — app otomatis ambil URL baru sekali lagi sebelum menyerah |
| Cyclic skip terus | Antrian besar, hampir semua URL YouTube sudah basi | App berhenti setelah 5 gagal beruntun dengan pesan. Muat ulang app, atau hapus antrian dan putar dari Library lokal |
| Force close | Crash | Buka app lagi: `logcat` dari log crash tersimpan otomatis. Owner repo bisa lihat `CrashLog.read()` di `MainActivity` |
| Tidak bisa install | APK unsigned | Pakai file `-signed.apk`, atau isi 4 secret signing di atas |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK lama ditandatangani key berbeda (biasanya key app asal) | Uninstall dulu, lalu pasang yang baru. Library **tidak** ikut terhapus, tapi scan ulang perlu dilakukan manual |
| Izin file tidak muncul | Android ≥33 masking file | Settings ▸ Apps ▸ VmusiX ▸ Permissions ▸ Music and audio |
| Lirik tidak ketemu | Lagu tidak ada di LRCLIB, atau butuh internet | Normal — lirik bersifat opsional |
| Build gagal `SDK location not found` | `ANDROID_HOME` belum diset | Buat `local.properties` berisi `sdk.dir=/path/ke/Android/Sdk` |

Butuh log lengkap saat develop?

```sh
adb logcat -d | grep -E "VmusiX|FATAL EXCEPTION" -A 25
```

## 📄 Lisensi

**GPL-3.0** — file [`LICENSE`](LICENSE).

Proyek ini fork dari aplikasi musik yang juga berlisensi GPL dan memakai
NewPipeExtractor (GPL). Karena itu repo ini **wajib** tetap terbuka: bebas
dipakai dan dimodifikasi, tapi semua perubahan juga harus dibagikan dengan
lisensi yang sama. Meng klaim karya tertutup, atau menutup sumbernya,
adalah pelanggaran lisensi.

Atribusi dan teks lisensi asli **wajib dipertahankan** meski nama produk
diganti menjadi VmusiX. Kalau kamu membuat versi turunan, tidak ada kewajiban
tambahan di luar menyertakan `CREDIT.md` dan `LICENSE` apa adanya. Lihat
[`CREDIT.md`](CREDIT.md).

## 📚 Dokumen

[**ARCHITECTURE**](docs/ARCHITECTURE.md) · [**CONTRIBUTING**](docs/CONTRIBUTING.md) · [**UI.md**](UI.md) · [**CREDIT**](CREDIT.md)
