# Kontribusi VmusiX

## Cara kerja

1. Baca [`UI.md`](../UI.md) §12 untuk layar yang disentuh + README 5 menit.
2. Kerjakan di branch / zip seperti biasa, pastikan **build hijau** (CI).
3. Push ke `main` → build APK otomatis. Push `.md` saja tidak trigger build.
4. Rilis bertanda tangan hanya jalan kalau 4 secret `VMUSIX_*` sudah diisi
   (README bagian Signing). Tanpa itu, CI tetap hijau tapi hanya meng-upload
   APK unsigned sebagai artifact — **tidak** masuk Release.

## Aturan kode (BLOCKING secara sosial)

1. **Anti-dummy** (semangat dokumen desain awal): tidak ada list
   hardcoded di UI final; tidak ada tombol no-op — tidak ada fungsi =
   hapus, kecuali backlog P1 dengan disabled + pesan jujur.
2. **Ponytail**: YAGNI dulu → stdlib/platform sebelum kode sendiri →
   diff terkecil yang jalan. Tanpa abstraksi tak diminta, tanpa dependensi
   baru yang bisa dihindari. Simplifikasi sadar = komentar `ponytail:`
   (plafon + upgrade path).
3. **NewPipe**: hanya dari `data/youtube/`. `catch Throwable`, rethrow
   `CancellationException`. Jangan cache URL stream.
4. **DB**: tiap ubah skema = migrasi eksplisit + bump versi. Dilarang
   `fallbackToDestructiveMigration()` untuk upgrade (wipe diam-diam).
5. **State**: satu instance ViewModel per concern (activity-scoped bila
   dipakai lintas tab). Error selalu ke snackbar/log, jangan telan diam-diam.
6. **Rahasia**: tidak ada password, token, atau keystore di dalam repo —
   termasuk di screenshot, log, dan komentar. Kalau kredensial sempat
   ter-push, **angap bocor**: cabut usage, ganti kredensialnya, jangan cuma
   hapus file di commit berikutnya karena sudah ada di riwayat git.

## Checklist sebelum push

- [ ] `./gradlew :app:assembleDebug` hijau (atau serahkan ke CI, tapi baca lognya bila merah)
- [ ] `python3 tools/test_purge_sql.py` hijau (kalau menyentuh query library)
- [ ] `python3 tools/check_kotlin_structure.py` hijau
- [ ] `git status` bersih dari `*.jks` / `keystore.txt` sebelum push
- [ ] Tidak ada string/angka hardcoded di UI final
- [ ] Semua tombol manggil sesuatu yang nyata
- [ ] Layar kecil tidak overflow; rotasi tidak reset state penting
- [ ] Izin baru = deklarasi manifest + request runtime + alasan di UI
- [ ] Skema DB berubah = migrasi + diuji upgrade (install APK lama → baru)

## Kalau build CI merah

1. `gh run view <id> --log-failed | grep "e: file"` — tunjuk file:baris.
2. Pola umum di repo ini: import tak resolve (ganti stdlib setara),
   `weight` di luar `Column/Row` lexical, key Lazy duplikat, Haze tanpa
   `backgroundColor`, `remember` vs `rememberSaveable`.
3. Fix, push lagi — tiap push = build baru + Release baru otomatis.
