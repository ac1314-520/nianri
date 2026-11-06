#!/usr/bin/env python3
"""生成念日的在线更新清单。

示例：
  python tools/make_update_manifest.py \
    --apk release/NianRi-Android-1.0.1.apk \
    --version-code 2 \
    --version-name 1.0.1 \
    --apk-url https://example.com/nianri/NianRi-Android-1.0.1.apk \
    --notes "· 一些小改进" \
    --out release/update.json

清单地址必须是 HTTPS 静态地址。APK 必须使用同一把 keystore 签名，
且 versionCode 必须大于手机当前安装的版本。
"""
from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import re
import sys
from pathlib import Path


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="生成念日 update.json")
    parser.add_argument("--apk", required=True, help="已签名的 APK 路径")
    parser.add_argument("--version-code", required=True, type=int, help="新的 versionCode")
    parser.add_argument("--version-name", required=True, help="新的 versionName，例如 1.0.1")
    parser.add_argument("--apk-url", required=True, help="APK 的 HTTPS 地址，或相对清单地址")
    parser.add_argument("--notes", default="", help="更新说明，可包含换行")
    parser.add_argument("--published-at", default=None, help="发布日期，默认今天")
    parser.add_argument("--out", default="release/update.json", help="输出 JSON 路径")
    args = parser.parse_args(argv)

    apk = Path(args.apk)
    if not apk.is_file():
        print(f"找不到 APK：{apk}", file=sys.stderr)
        return 2
    if args.version_code <= 0:
        print("versionCode 必须是正整数", file=sys.stderr)
        return 2
    if not args.version_name.strip():
        print("versionName 不能为空", file=sys.stderr)
        return 2
    if not re.match(r"^https?://", args.apk_url, re.IGNORECASE):
        print("提示：apkUrl 必须是 HTTPS 绝对地址，或相对清单地址", file=sys.stderr)

    digest = sha256_of(apk)
    data = {
        "versionCode": args.version_code,
        "versionName": args.version_name.strip(),
        "apkUrl": args.apk_url.strip(),
        "sha256": digest,
        "notes": args.notes.strip(),
        "publishedAt": args.published_at or _dt.date.today().isoformat(),
    }
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已写入 {out}")
    print(f"SHA-256: {digest}")
    print("请将清单与 APK 上传到 HTTPS 地址，然后在应用「设置 → 在线更新 → 更新源」填写该 JSON 地址。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
