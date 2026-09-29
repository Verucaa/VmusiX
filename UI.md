# UI.md — Spesifikasi UI VmusiX per-inci (untuk implementasi manual)

Acuan kebenaran berlapis: (1) 3 screenshot RESMI Apple Music iOS
(dibaca piksel per piksel saat perancangan), (2) riset tab
Home/Search/New dari support.apple.com + teardown, (3) mockup HTML yang
disetujui user (file mockup sudah dihapus setelah polanya masuk app). App = dark-only; angka warna di bawah
sudah dikonversi ke dark (terang→gelap: bg `#FFF`→`#000`, teks `#000`→`#fff`).

Satuan: pt iOS ≈ dp Android ≈ px 1:1. Lebar acuan 390.

---

## 0. Token global (jangan hardcode di luar `ui/theme/Theme.kt`)

| Token | Nilai | Pakai di |
|---|---|---|
| `BgMain` | `#000000` | background semua tab |
| `SurfaceElevated` | `#1C1C1E` | card, sheet, tab bar |
| `SurfaceSecondary` | `#2C2C2E` | field search, scope, pil off, tile |
| `Divider` | `rgba(255,255,255,0.08)` | hairline antar baris |
| `TextPrimary` | `#FFFFFF` | judul, body |
| `TextSecondary` | `rgba(255,255,255,0.6)` | artis, subtitle, hint |
| `TextHint` | `rgba(255,255,255,0.4)` | chevron, placeholder |
| `Accent` | `#FA233B` | tab aktif, bintang favorit, tombol aksen |

Tipografi (font **Inter** bundle, `AppFont` via `LocalTextStyle` — jangan
Roboto): Large title 34/700, section header 20/700, sub-header detail 22/700,
row title 16/400, row artist 14/400, tab label 10/400, caption/time 12/400,
NP title 20/600, NP artist 19/400.

Radius: artwork row 6, kartu grid 8–10, field search 12, tile genre 12,
hero 16, mini-player 14, tab pill 26, sheet 16 atas, tombol pill 20,
search FAB lingkaran penuh 44dp, drag handle 36×5 (radius 3).

Margin horizontal global 16. Section gap vertikal 20–24. Blur kaca (Haze):
tab bar + mini-player radius 24 di atas bg `rgba(255,255,255,0.10)`;
`hazeChild` WAJIB `backgroundColor` eksplisit (tanpa itu = crash
`IllegalArgumentException`, sudah kejadian 3x).

Aturan anti-dummy (berlaku per komponen di bawah):
tidak ada list/string hardcoded di UI final;
tidak ada tombol no-op (tidak ada fungsi = hapus, kecuali P1 dengan
disabled + pesan jujur: lirik, EQ, sleep timer).

---

## 1. Kerangka global (`MainActivity.kt`)

1. Status bar = sistem (jangan gambar manual).
2. `Scaffold(containerColor=BgMain)`, konten `padding(pad)`.
3. Bottom = `Column(windowInsetsPadding(navigationBars), horiz 10)`:
   - Mini-player (hanya tampil bila `current != null`, §5) + jarak 8.
   - Tab pill `RoundedCornerShape(26)`, Haze: 3 tab equal-weight
     (Home ⌂ / New ✦ / Library ▤ — Material `Home/AutoAwesome/LibraryMusic`)
     + search FAB lingkaran 44dp terpisah. Aktif = `Accent`, nonaktif =
     putih 65%. State aktif dari `currentBackStackEntryAsState()`.
     Semua `navigate` pakai `launchSingleTop + restoreState +
     popUpTo(startDestination){saveState=true}` (tanpa ini back-stack numpuk).
4. `NavHost(startDestination="library")`, route `home | new | library |
   search?preset={preset}` (preset di-`Uri.encode`, dibaca via
   `SavedStateHandle`, bukan cuma `LaunchedEffect`).
5. `BackHandler(enabled=isExpanded){ setExpanded(false) }` — Back tutup
   NP, bukan keluar app.
6. `SnackbarHost` untuk semua pesan error (jangan toast custom).

---

## 2. Home (`ui/home/`, mulai dari trending karena DB bisa kosong)

Urutan vertikal (sesuai riset Apple + keputusan lokal):

1. `LargeTitle("Home")`.
2. **Trending** (header 20/700): `LazyRow` kartu 140×140 radius 10
   (judul 1 baris + artis 1 baris, 13/12). Sumber: kiosk Trending YouTube →
   fallback 3 query (`lagu populer {tahun}`, `top hits indonesia`,
   `musik trending terbaru`), max 50/30, thumb
   `https://img.youtube.com/vi/{id}/sddefault.jpg`. State: loading
   (spinner + "Memuat trending…"), error + tombol "Coba lagi", tap kartu =
   `playTrack(t, trending)`.
3. **Recently Played** (20/700): horizontal sama, sumber `history ORDER BY
   lastPlayedAt DESC LIMIT 10`. Kosong → teks jujur
   "Belum ada riwayat, mulai putar musik dari Library" (jangan card abu-abu).
4. **Recently Added** (20/700): horizontal sama, sumber
   `ORDER BY dateAdded DESC LIMIT 10`. Kosong → "Belum ada musik. Taruh
   lagu di folder Music/VmusiX lalu pindai dari Library."
5. Kartu WAJIB `onMenu` (long-press/⋯) → action sheet (§8). Dilarang hero
   statis (sudah dibuang user).

Catatan Apple yang SENGAJA tidak ditiru: Mix/Station/Replay/Released
(butuh backend rekomendasi — di luar scope v1, dan aturan melarang dummy).

---

## 3. New (`ui/browse/`, ganti Browse)

1. `LargeTitle("New")`.
2. Sub "Browse Categories" 20/700 + hint 13 (hapus kalimat penjelas
   non-Apple saat final).
3. `LazyVerticalGrid(Fixed 2)`: 8 tile (Pop, Hip-Hop, R&B, Electronic,
   Rock, Jazz, Classical, Metal), tinggi 110, radius 12, gradient 135°
   per genre + watermark `♪` 44sp putih 25% kanan-bawah + label 17/700
   kiri-bawah. Tap = `nav "search?preset="` (query NYATA, bukan diam).
4. Tanpa featured banner (butuh kurasi server — skip jujur, bukan dummy).

---

## 4. Library (`ui/library/`)

### 4.1 Layar utama
1. `LargeTitle("Library")` (tanpa avatar/filter — single-user, tombol tanpa
   fungsi dilarang).
2. 5 baris kategori (Playlists, Artists, Albums, Songs) — Downloaded
   DILARANG (tanpa backend offline): glyph polos 22sp tanpa kotak +
   label 17 + chevron `›` hint 20sp + divider inset (mulai setelah ikon).
   Tanpa angka count (Apple tidak menampilkan angka di sini).
3. **Recently Added**: `LazyRow` kartu 140 (sama §2).
4. Header `Songs • {COUNT(*)}` 20/700 + sub `Music/VmusiX • scan: N lagu`
   12 sekunder + tombol Refresh (state "Memindai…") + teks error merah
   12 bila `scanError != null`.
5. List lagu penuh (`TrackRow` §9).
6. Permission: full-screen CTA ("Perlu izin audio untuk membaca folder
   Music/VmusiX." + hint taruh file + tombol "Pindai folder VmusiX").
   Minta `READ_MEDIA_AUDIO` (+ `POST_NOTIFICATIONS` di API 33+, multiple)
   dalam SATU dialog. `READ_EXTERNAL_STORAGE maxSdkVersion=32` wajib ada
   di manifest untuk API <33.
7. Navigasi sub-view via state string saveable (`null|songs|artists|
   artist:{nama}|albums|album:{nama}|playlists|playlist:{id}|{nama}`),
   tombol kembali `‹ Library`. Sub-view: Songs (list), Artists→nama→lagu,
   Albums→nama→lagu, Playlists (buat inline + buka detail + hapus +
   tambah/hapus lagu, reload tiap `playlists` berubah).

### 4.2 Sumber data (kontrak, bukan tampilan)
- Scan HANYA `Music/VmusiX/` (`RELATIVE_PATH LIKE ? OR = ?`,
  `COLLATE NOCASE`, argumen `Music/VmusiX/%` + `Music/VmusiX`) +
  `IS_MUSIC!=0` + `IS_RINGTONE/NOTIFICATION/ALARM=0` +
  `(DURATION IS NULL OR >=30000)` + `(SIZE IS NULL OR >=50000)`.
- Multi-volume (`getExternalVolumeNames`), kolom via `getColumnIndex`
  (bukan OrThrow), `ALBUM_ID` → `content://media/external/audio/albumart`
  (404 di Q+ = fallback UI, bukan crash), single-flight `Mutex`,
  sinkronisasi sekuensial (upsert → delete hilang per chunk 500 →
  purge non-folder eksplisit), `rowKey = contentUri` (PK unik per volume),
  `CancellationException` SELALU rethrow.
- DB v1, **tanpa** `fallbackToDestructiveMigration()`. Reset ke v1 sah karena
  `applicationId` berubah (app berbeda = database berbeda, tidak ada yang perlu
  dimigrasikan). Ke depan pakai `Migration_x_y` eksplisit. `exportSchema=true`.

---

## 5. Mini-player (`ui/player/MiniPlayer.kt`)

- TIDAK dirender saat `current == null` (bukan alpha 0 — a11y).
- Floating: artwork 44 radius 8 (dengan fallback §9) + title 14 1-baris +
  artist 12 sekunder + play/pause + next (contentDescription WAJIB diisi).
- Hairline progress 2dp di bawah (fill putih 60%, track putih 25%).
- Tap (di luar tombol) = expand. Tanpa drag (backlog).

---

## 6. Now Playing (`ui/player/NowPlayingScreen.kt`, tiru screenshot)

Urutan vertikal (padding horizontal 20, kecuali artwork):

1. Top Row: chevron-down kiri (collapse) + drag handle 36×5 tengah +
   ⋯ kanan (buka sheet lagu berjalan).
2. **Artwork full-bleed**: `fillMaxWidth aspectRatio(1)`, TANPA radius,
   tanpa margin kartu. Background = artwork blur 40 + overlay hitam 45%
   (fallback gradient `#2C2C2E→#1C1C1E` bila art null).
3. Baris judul: title 20/600 marquee + artist 19 putih-60% marquee +
   **bintang ☆/★ 24sp** (★ = `Accent`; toggle playlist "Favorit" otomatis,
   Room nyata) + ⋯.
4. Seekbar: drag-preview + `seekTo` saat dilepas; kiri elapsed `m:ss`,
   kanan `−m:ss` 12 putih-60%.
5. Kontrol: prev 38 + play/pause 72 + next 38, putih solid, gap lebar.
6. Volume: speaker kecil + slider + speaker besar (AudioManager NYATA;
   apply saat `onValueChangeFinished`, clamp `max>=1`).
7. Bawah (equal-spaced): 💬 disabled ("Lirik belum tersedia"),
   ◎ dim → snackbar output, ☰ buka queue.
8. Error playback (`onPlayerError`) → snackbar jelas, bukan diam.
9. Ukuran artwork JANGAN fixed 300dp; bayangan kartu tidak dipakai
   (full-bleed menempel tepi seperti screenshot).

---

## 7. Queue panel (Up Next, tiru screenshot `queue.png`)

1. Grab handle + header lagu berjalan (cover 44 + title 17/600 +
   artist 15 sekunder + bintang + ⋯).
2. Pil kapsul (radius 10, teks 13 putih; off = putih 12%, on = Accent 25%):
   **Shuffle** + **Repeat** (Off→All→One). AutoMix/AutoPlay DILARANG
   (tanpa backend).
3. Baris `Playing Next` 18/700 + `Clear` kanan (kosongkan UI + controller
   + Room, `current=null`).
4. Baris: cover 44 radius 6 + title (aktif = Accent) + artis 13 +
   tombol ↑/↓ (pengganti drag ≡ yang fungsional; drag ladder backlog).
   Key stabil `track.key` (bukan index).
5. `+ Add Songs` → tutup NP + buka Search (navigasi nyata).
6. `Tutup`. Panel inline max 260dp (darurat overflow); target akhir =
   bottom-sheet + scrim (backlog motion).

---

## 8. Action sheet lagu (`ui/common/TrackViews.kt`)

- `ModalBottomSheet` ( hoist `SheetState` bila dibagi overlay NP):
  header cover 52 radius 8 + title + `artist • Perangkat/YouTube`,
  aksi rata-kiri (bukan center): Play Next, Play Last, Add to Playlist…
  (tanpa Share/Station/Download — di luar scope).
- Picker playlist (Room nyata) + buat baru inline; setelah Buat,
  lagu LANGSUNG masuk playlist baru (jangan dismiss kosong).
- `TrackRow`: cover 44 radius 6 + title 16 + subtitle 14 sekunder +
  ⋯ 18 + `HorizontalDivider` (bukan `Divider` deprecated).
- `Artwork()`: `AsyncImage` + `error/fallback` painter not-balok;
  null → Box kaca + ikon putih. Dipakai di SEMUA list (dilarang kotak kosong).

---

## 9. Search (`ui/search/`)

1. `LargeTitle("Search")` + field rounded 12 bg sekunder: ikon 🔍 +
   placeholder "Artists, Songs, Lyrics and More" + tombol × clear +
   (backlog: Cancel ala Apple).
2. Scope segmented NYATA (bukan TextButton): Semua | Perangkat | YouTube —
   filter tampilan beneran. Tampil hanya saat query non-kosong.
3. Kosong: Recent Searches (ikon jam + artwork mini + hapus per-baris +
   Clear-all — backlog hapus; sekarang teks + rerun) + grid Browse
   Categories 2 kolom (8 tile sama §3, bukan 4).
4. Hasil: section `Di Perangkat` + `YouTube Music` (header 20/700,
   spinner YT terpisah, error YT = pesan + section lokal tetap jalan).
   Key komposit anti-duplikat. Query <2 char tidak Hit DB/network;
   LIKE di-escape; simpan riwayat max 8 di SharedPrefs (throttle 800ms).
5. Preset genre via `SavedStateHandle("preset")` (survive relaunch),
   divisualkan (chip aktif, backlog).

---

## 10. Fitur perilaku Apple yang WAJIB ada

| Fitur Apple | Status VmusiX | File |
|---|---|---|
| Play Next / Play Later | ✅ real | `PlaybackViewModel.playNext/addToQueue` |
| Add to Playlist + New Playlist | ✅ real | `PlaylistViewModel` + sheet |
| Favorite (☆) | ✅ real → playlist Favorit | `toggleFavorite` |
| Shuffle/Repeat/queue reorder/clear | ✅ real | player + panel |
| Search scope + recent | ✅ real | Search |
| Lyrics | ❌ P1 → disabled jujur | NowPlaying 💬 |
| Sleep timer / EQ | ❌ P1 → belum ada UI | — |
| Radio / Downloaded / AutoPlay / Share / Station | ❌ out of scope → DILARANG ada UI-nya | — |
| Trending Home | ✅ real (kiosk→fallback query) | `YouTubeRepository.trending` |

---

## 11. Peta file → layar (implementasi manual mulai dari sini)

| Layar | File |
|---|---|
| Scaffold/tab/search/back | `MainActivity.kt` |
| Tema/token/font | `ui/theme/Theme.kt` |
| Tab def | `ui/nav/Tabs.kt` |
| Home + VM | `ui/home/HomeScreen.kt`, `HomeViewModel.kt` |
| New | `ui/browse/BrowseScreen.kt` |
| Library + VM + playlist UI | `ui/library/LibraryScreen.kt`, `LibraryViewModel.kt` |
| Search + VM + riwayat | `ui/search/SearchScreen.kt`, `SearchViewModel.kt`, `RecentQueries.kt` |
| Mini-player | `ui/player/MiniPlayer.kt` |
| Now Playing + queue | `ui/player/NowPlayingScreen.kt`, `PlaybackViewModel.kt` |
| Sheet/row/artwork/title | `ui/common/TrackViews.kt` |
| Playlist VM | `ui/playlist/PlaylistViewModel.kt` |
| Service | `playback/VmusiXSessionService.kt` |
| DB/scan/repo/YT | `data/local/*`, `data/repository/*`, `data/youtube/*` |

---

## 12. Checklist terima per layar (centang manual di device)

- [ ] Tab: 3 tab + search bulat 44, tint benar, tidak numpuk back-stack,
  Back tutup NP.
- [ ] Home: Trending terisi online; offline = pesan + retry; Recently
  Played terisi setelah putar; Recently Added sesuai folder; kartu ada menu.
- [ ] New: 8 tile + watermark; tap = hasil Search nyata.
- [ ] Library: kategori tanpa angka (kecuali Songs header `COUNT(*)`);
  Recently Added; `scan: N lagu` benar; permission CTA; Refresh; sub-view
  + rotasi tahan; playlist CRUD + Favorit.
- [ ] Search: field + clear; scope filter nyata; kosong = recent + 8 tile;
  hasil 2 section; key unik; <2 char diam.
- [ ] Mini-player: muncul/hilang tepat; sinkron; hairline jalan.
- [ ] Now Playing: full-bleed; bintang toggle Favorit; seek/volume nyata;
  error snackbar; tidak overflow di layar kecil.
- [ ] Queue: pil, Clear, Add Songs → Search, reorder tahan restart.
- [ ] Sheet: 3 aksi + buat-tambah-langsung; tanpa opsi mati.
- [ ] Global: Inter ter-render (bukan Roboto); divider hairline; tidak ada
  string/angka hardcoded; tidak ada tombol no-op.
