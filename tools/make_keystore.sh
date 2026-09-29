#!/usr/bin/env bash
# Buat keystore rilis VmusiX.
#
# Kenapa skrip ini ada: proyek asal menaruh keystore rilis sebagai TEKS di dalam
# repo (keystore.txt), sehingga ikut ter-push ke GitHub. Android Studio punya
# wizard "Generate Signed Bundle" sendiri, tapi langkahnya mudah salah orang dan
# passwordnya cenderung ditaruh di file. Di sini output selalu keluar dari repo.
#
# Password TIDAK pernah di-hardcode dan tidak pernah ditulis ke file mana pun
# di dalam repo.Kalau mau CI, masukkan lewat GitHub Secrets.
#
# Pakai:
#   tools/make_keystore.sh                 # prompt password (disarankan)
#   tools/make_keystore.sh ~/keys/vmusix.jks
set -euo pipefail

OUT="${1:-$HOME/keys/vmusix-release.jks}"
ALIAS="vmusix"
VALIDITY_DAYS=10000   # ~27 tahun. Android/google play butuh minimum 25 thn
                       # untuk app yang intends publish long-term.

command -v keytool >/dev/null || {
  echo "ERROR: keytool tidak ditemukan. Pasang JDK 17+ lalu ulangi." >&2
  exit 1
}

if [ -e "$OUT" ]; then
  echo "ERROR: '$OUT' sudah ada. JANGAN overwrite keystore yang sudah dipakai" >&2
  echo "       untuk rilis — user yang sudah memasang APK tertandatangani itu" >&2
  echo "       tidak bisa meng-upgrade ke versi yang ditandatangani key lain." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"

read -rsp "Password keystore (min 6 karakter): " STORE_PASS; echo
if [ "${#STORE_PASS}" -lt 6 ]; then
  echo "ERROR: password terlalu pendek (minimal 6 karakter)." >&2
  exit 1
fi

# key password dibiarkan sama dengan store password supaya tidak ada dua
# password berbeda yang harus diingat. Tetap bisa diubah manual nanti.
read -rsp "Ulangi password: " CONFIRM; echo
if [ "$STORE_PASS" != "$CONFIRM" ]; then
  echo "ERROR: password tidak cocok." >&2
  exit 1
fi

keytool -genkeypair \
  -keystore "$OUT" \
  -storetype PKCS12 \
  -storepass "$STORE_PASS" \
  -keypass "$STORE_PASS" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity "$VALIDITY_DAYS" \
  -dname "CN=VmusiX, OU=VmusiX, O=VmusiX, L=Jakarta, S=DKI Jakarta, C=ID"

chmod 600 "$OUT"

cat <<EOF

Keystore dibuat: $OUT
Alias          : $ALIAS

LANGKAH WAJIB (jangan sampai lupa):
  1. BACKUP file ini ke tempat aman (mis. password manager / cloud terenkripsi).
     Kalau hilang, kamu TIDAK BISA pernah menerbitkan update untuk app ini.
  2. Jangan pernah commit file ini. .gitignore sudah menutup *.jks, tapi
     verify dengan 'git status' sebelum push.
  3. Untuk CI, isi GitHub Secrets (lihat README bagian Signing):
       VMUSIX_KEYSTORE_B64     = isi file di atas hasil 'base64 < $OUT'
       VMUSIX_KEYSTORE_PASSWORD = password di atas
       VMUSIX_KEY_ALIAS        = $ALIAS
       VMUSIX_KEY_PASSWORD     = password di atas
EOF
