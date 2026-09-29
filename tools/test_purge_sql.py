#!/usr/bin/env python3
"""Tes SQL purge MediaStore.

Query ini menghapus baris `tracks` di luar folder aplikasi. Salah sedikit
dan akibatnya: seluruh library user hilang permanen — dan itu persis kelas
"lagu disappeared after update" yang paling dikeluhkan.

Jadi logikanya diuji di sini, bukan diasumsikan benar.

Jalankan:  python3 tools/test_purge_sql.py
"""
import sqlite3
import sys

# Salinan PERSIS dari @Query di Entities.kt. Kalau query-nya diubah, diubah
# juga di sini — atau lebih baik, test ini jadi gagal supaya terlihat.
PURGE_SQL = (
    "DELETE FROM tracks WHERE relativePath NOT LIKE ? AND relativePath != ?"
    " AND relativePath NOT LIKE ? AND relativePath != ?"
)
PARAMS = ("Music/VmusiX/%", "Music/VmusiX", "Music/LiPhify/%", "Music/LiPhify")

# (path, harus tetap ada?)
CASES = [
    ("Music/VmusiX/lagu.mp3", True),
    ("Music/VmusiX/Artist/lagu.mp3", True),
    # Tanpa trailing slash: beberapa OEM menulis begini.
    ("Music/VmusiX", True),
    # Folder legacy: library lama wajib tetap bisa dibaca.
    ("Music/LiPhify/lama.mp3", True),
    ("Music/LiPhify", True),
    # Di luar folder -> harus dibuang.
    ("Music/Other/ring.mp3", False),
    ("WhatsApp/Media/audio-2026.mp3", False),
    ("Music/LiPhifyx/nyasar.mp3", False),
    ("Music/VmusiXsomething/nyasar.mp3", False),
    ("Ringtones/default.mp3", False),
    ("Recordings/20260101.mp3", False),
]


def main():
    con = sqlite3.connect(":memory:")
    con.execute("CREATE TABLE tracks (relativePath TEXT)")
    con.executemany("INSERT INTO tracks VALUES (?)", [(p,) for p, _ in CASES])

    con.execute(PURGE_SQL, PARAMS)
    remaining = {r[0] for r in con.execute("SELECT relativePath FROM tracks")}

    failures = []
    for path, should_keep in CASES:
        kept = path in remaining
        if kept != should_keep:
            want = "dipertahankan" if should_keep else "dihapus"
            got = "dipertahankan" if kept else "dihapus"
            failures.append(f"  {path!r}: diharapkan {want}, kenyataannya {got}")

    if failures:
        print("FAIL:")
        print("\n".join(failures))
        return 1

    print(f"OK: {len(CASES)} kasus, {len(remaining)} baris dipertahankan, sisanya terhapus")
    return 0


if __name__ == "__main__":
    sys.exit(main())
