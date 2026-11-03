#!/usr/bin/env python3
"""Szó-hibaarány (WER) két fájl között: referencia (.txt) és felismerés (.txt vagy .srt).
Használat: python3 tools/golden_wer.py ref.txt hyp.srt"""
import re, sys

def text_of(path):
    s = open(path, encoding="utf-8-sig").read()
    if "-->" in s:
        keep = []
        for line in s.splitlines():
            line = line.strip()
            if not line or line.isdigit() or "-->" in line:
                continue
            keep.append(re.sub(r"<[^>]+>", "", line))
        s = " ".join(keep)
    return s

def words(s):
    return [w for w in re.sub(r"[^\w']+", " ", s.lower().replace("\u2019", "'")).split() if w]

def wer(ref, hyp):
    a, b = words(ref), words(hyp)
    d = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(b) + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (a[i - 1] != b[j - 1]))
            prev = cur
    return (d[len(b)] / len(a)) if a else (0.0 if not b else 1.0), len(a)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    r, n = wer(text_of(sys.argv[1]), text_of(sys.argv[2]))
    print(f"WER: {r*100:.1f}%  ({n} referenciaszó)")
