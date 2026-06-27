#!/usr/bin/env python3
"""Compare zh_cn.json and en_us.json translation key sets."""
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/beatblock/lang"
ZH = LANG / "zh_cn.json"
EN = LANG / "en_us.json"


def raw_keys(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    return re.findall(r'^\s*"([^"\\]+)"\s*:', text, re.MULTILINE)


def main() -> int:
    zh = json.loads(ZH.read_text(encoding="utf-8"))
    en = json.loads(EN.read_text(encoding="utf-8"))
    zh_raw, en_raw = raw_keys(ZH), raw_keys(EN)
    zh_dupes = [k for k, n in Counter(zh_raw).items() if n > 1]
    en_dupes = [k for k, n in Counter(en_raw).items() if n > 1]
    only_zh = sorted(set(zh) - set(en))
    only_en = sorted(set(en) - set(zh))

    print(f"zh_cn.json: {len(zh)} keys ({len(zh_raw)} raw lines, {len(zh_dupes)} duplicate keys)")
    print(f"en_us.json: {len(en)} keys ({len(en_raw)} raw lines, {len(en_dupes)} duplicate keys)")
    print(f"only in zh: {len(only_zh)}")
    print(f"only in en: {len(only_en)}")

    if zh_dupes:
        print("\nDuplicate keys in zh_cn.json:")
        for k in zh_dupes:
            print(f"  {k}")

    if en_dupes:
        print("\nDuplicate keys in en_us.json:")
        for k in en_dupes:
            print(f"  {k}")

    if only_zh:
        print("\nKeys missing from en_us.json:")
        for k in only_zh:
            print(f"  {k}")

    if only_en:
        print("\nKeys missing from zh_cn.json:")
        for k in only_en:
            print(f"  {k}")

    return 1 if only_zh or only_en or zh_dupes or en_dupes else 0


if __name__ == "__main__":
    sys.exit(main())
