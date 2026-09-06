#!/usr/bin/env python3
"""Write nightly.json / latest.json without shell heredocs in GHA YAML."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--release-notes", required=True)
    parser.add_argument("--channel", required=True)
    parser.add_argument("--min-version-code", type=int, default=1)
    parser.add_argument("--min-android-sdk", type=int, default=26)
    args = parser.parse_args()

    payload = {
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "apkUrl": args.apk_url,
        "sha256": args.sha256,
        "releaseNotes": args.release_notes,
        "channel": args.channel,
        "minVersionCode": args.min_version_code,
        "minAndroidSdk": args.min_android_sdk,
        "publishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(json.dumps(payload, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
