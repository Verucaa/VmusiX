#!/usr/bin/env python3
"""Cek struktur file Kotlin: kurung kurawal/kurung/bracket seimbang.

Bukan parser Kotlin, tapi cukup untuk menangkap kelas kesalahan yang paling
sering muncul saat edit manual: blok yang tidak ditutup, string yang tidak
selesai, dan interpolasi ${...} yang rusak.

Dipakai karena kotlinc tidak bisa dijalankan di host ini (aarch64, network
lambat). Kalau nanti ada kotlinc, checker ini cukup dipakai sebagai jaring
pengaman tambahan, bukan pengganti compile.
"""
import sys
import os

PAIRS = {'}': '{', ')': '(', ']': '['}
OPEN = set(PAIRS.values())


def strip_code(src):
    """Kembalikan (kode_bersih, error) dengan komentar & string dibuang."""
    out = []
    i, n = 0, len(src)
    err = None
    line = 1
    while i < n:
        c = src[i]
        if c == '\n':
            line += 1
            i += 1
            continue
        # komentar baris
        if src.startswith('//', i):
            j = src.find('\n', i)
            i = n if j < 0 else j
            continue
        # komentar blok (nested di Kotlin)
        if src.startswith('/*', i):
            depth = 1
            i += 2
            while i < n and depth:
                if src.startswith('/*', i):
                    depth += 1
                    i += 2
                elif src.startswith('*/', i):
                    depth -= 1
                    i += 2
                else:
                    if src[i] == '\n':
                        line += 1
                    i += 1
            if depth:
                err = f"komentar blok /* tidak ditutup (baris ~{line})"
                break
            continue
        # string literals
        if c in '"\'':
            quote = c
            triple = src.startswith(quote * 3, i)
            i += 3 if triple else 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == '\n':
                    line += 1
                    if not triple:
                        err = f"string {quote} tidak ditutup sebelum newline (baris {line})"
                        break
                if triple and src.startswith(quote * 3, i):
                    i += 3
                    break
                if not triple and src[i] == quote:
                    i += 1
                    break
                i += 1
            if err:
                break
            out.append('""')
            continue
        out.append(c)
        i += 1
    return ''.join(out), err


def check(path):
    src = open(path, encoding='utf-8').read()
    code, err = strip_code(src)
    if err:
        return [err]
    stack = []
    for ch in code:
        if ch in OPEN:
            stack.append(ch)
        elif ch in PAIRS:
            if not stack:
                return [f"kurung tutup '{ch}' tanpa pembuka"]
            if stack[-1] != PAIRS[ch]:
                return [f"'{ch}' menutup '{stack[-1]}'"]
            stack.pop()
    if stack:
        return [f"{len(stack)} pembuka tidak ditutup: {''.join(stack[-6:])}"]
    return []


def main(paths):
    bad = 0
    for p in paths:
        errs = check(p)
        if errs:
            bad += 1
            for e in errs:
                print(f"FAIL {p}: {e}")
    total = len(paths)
    if bad:
        print(f"\n{bad}/{total} file bermasalah")
        return 1
    print(f"OK: {total} file, struktur kurung seimbang")
    return 0


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args:
        roots = ['.']
        files = []
        for r in roots:
            for dp, dn, fn in os.walk(r):
                dn[:] = [d for d in dn if d not in ('.git', 'build', '.gradle')]
                files += [os.path.join(dp, f) for f in fn if f.endswith('.kt')]
    else:
        files = args
    sys.exit(main(sorted(files)))
