# Atribusi / CREDIT

VmusiX adalah **fork**, bukan karya dari nol. Karena lisensinya GPL-3.0,
atribusi di bawah ini wajib ikut disertakan di setiap versi yang beredar.
Jangan hapus berkas ini.

## Rantai asal-usul

VmusiX berdiri di atas empat lapis pekerjaan orang lain:

| Lapis | Proyek | Pembuat | Peran |
|---|---|---|---|
| 1 | [musikku](https://github.com/Verucaa/musikku) | [Verucaa](https://github.com/Verucaa) (`udangketce@gmail.com`) | Proyek asal. Repo ini dipush ke `Verucaa/musikku`. |
| 2 | Zmusic (`com.zaaam.zmusic`) | Verucaa | Sibling project. Bagian lirik, trending, dan pencarian musik di app ini **diadaptasi** dari sini — lihat komentar `Adaptasi dari Zmusic` di `LyricsRepository.kt`, `Models.kt`, `YouTubeRepository.kt`, `HomeViewModel.kt`, `PlaybackViewModel.kt`. |
| 3 | LiPhify (`com.zaaam.liphify`) | az925-crypto | Rebranding dan reviewers besar atas kode di atas. |
| 4 | **VmusiX** (`com.zaaam.vmusix`) | (repo ini) | Fork: rename produk, perbaikan playback, signing bersih, panduan lengkap. |

Tulisan "Zmusic"/"musikku" di dalam komentar kode sengaja **tidak** diganti
jadi VmusiX, karena itu jejak atribusi, bukan merek produk.

## Komponen pihak ketiga

| Komponen | Lisensi | Peran |
|---|---|---|
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | GPL-3.0 | Mengambil metadata & URL stream YouTube |
| [AndroidX Media3](https://github.com/androidx/media) | Apache-2.0 | Engine playback + media session |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Apache-2.0 | UI |
| [Room](https://developer.android.com/training/data-storage/room) | Apache-2.0 | Database lokal |
| [Hilt](https://dagger.dev/hilt/) | Apache-2.0 | DI |
| [Coil](https://github.com/coil-kt/coil) | Apache-2.0 | Gambar artwork |
| [Haze](https://github.com/chrisbanes/haze) | Apache-2.0 | Efek blur |
| [LRCLIB](https://lrclib.net/) | Public domain | Metadata & teks lirik |

## Apa yang boleh dan tidak boleh

**Boleh:**
- Mengganti nama, package, ikon, warna, dan tata letak.
- Menambah fitur, memperbaiki bug, mengubah dokumentasi.
- Menerbitkan ulang dengan nama berbeda — dengan syarat lisensi dan
  atribusi ini ikut dibawa.

**Tidak boleh:**
- Menghapus berkas `LICENSE` atau `CREDIT.md` ini.
- Menjual sebagai binary tertutup, atau menegakkan lisensi proprietary di
  atasnya.
- Menghapus komentar atribusi di dalam kode sumber.

GPL-3.0 berlaku pada kode app ini sebagai keseluruhan. Binary yang kamu
build juga harus dibagikan dengan sumbernya.

## Catatan soal keystore

Proyek asal pernah menyimpan keystore rilis sebagai teks biasa
(`keystore.txt`) di dalam repository, sehingga ikut ter-push dan bisa
diunduh siapa saja. Key tersebut dianggap **bocor**.

Key itu sengaja **tidak** disertakan di repo ini, dan dipakai ulang juga
tidak akan tepat: Android menolak memasang upgrade yang ditandatangani key
berbeda. Buat key baru dengan `tools/make_keystore.sh`.
