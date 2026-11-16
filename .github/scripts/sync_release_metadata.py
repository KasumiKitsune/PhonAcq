#!/usr/bin/env python3
"""Sync stable release version & date across README.md, CITATION.cff, docs/index.html, and docs/sitemap.xml."""

import argparse
import re
from pathlib import Path


def sync_readme(root: Path, version: str, apk_filename: str | None) -> None:
    path = root / "README.md"
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    tag = f"v{version}"
    apk_name = apk_filename or f"PhonAcq-{tag}-android.apk"

    # Update SVG download links & listed installer filenames
    text = re.sub(
        r"releases/download/v[^/]+/PhonAcq-v[^/\"]+-windows-x64-setup\.msi",
        f"releases/download/{tag}/PhonAcq-{tag}-windows-x64-setup.msi",
        text,
    )
    text = re.sub(
        r"releases/download/v[^/]+/PhonAcq-v[^/\"]+-macos-arm64\.dmg",
        f"releases/download/{tag}/PhonAcq-{tag}-macos-arm64.dmg",
        text,
    )
    text = re.sub(
        r"releases/download/v[^/]+/PhonAcq-v[^/\"]+-android(?:-debug)?\.apk",
        f"releases/download/{tag}/{apk_name}",
        text,
    )
    text = re.sub(r"PhonAcq-v[0-9A-Za-z._-]+-windows-x64-setup\.msi", f"PhonAcq-{tag}-windows-x64-setup.msi", text)
    text = re.sub(r"PhonAcq-v[0-9A-Za-z._-]+-macos-arm64\.dmg", f"PhonAcq-{tag}-macos-arm64.dmg", text)
    text = re.sub(r"PhonAcq-v[0-9A-Za-z._-]+-android(?:-debug)?\.apk", apk_name, text)
    text = re.sub(r"-PappVersion=[0-9A-Za-z._-]+", f"-PappVersion={version}", text)

    path.write_text(text, encoding="utf-8")


def sync_citation(root: Path, version: str, release_date: str) -> None:
    path = root / "CITATION.cff"
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    text = re.sub(r'(?m)^version:\s*"[^"]+"', f'version: "{version}"', text)
    text = re.sub(r'(?m)^date-released:\s*"[^"]+"', f'date-released: "{release_date}"', text)
    path.write_text(text, encoding="utf-8")


def sync_portal(root: Path, version: str, release_date: str) -> None:
    index_path = root / "docs/index.html"
    if index_path.exists():
        html = index_path.read_text(encoding="utf-8")
        tag = f"v{version}"
        html = re.sub(
            r'(<span class="version-tag js-version-badge">)v[^<]+(</span>)',
            rf"\g<1>{tag}\2",
            html,
        )
        html = re.sub(
            r'(<span class="js-version-text">[^<]*?\s)v[^<\s]+(</span>)',
            rf"\g<1>{tag}\2",
            html,
        )
        html = re.sub(
            r'("softwareVersion":\s*")[^"]+(")',
            rf"\g<1>{version}\2",
            html,
        )
        html = re.sub(
            r"(fallbackVersion:\s*')v[^']+(')",
            rf"\g<1>{tag}\2",
            html,
        )
        index_path.write_text(html, encoding="utf-8")

    sitemap_path = root / "docs/sitemap.xml"
    if sitemap_path.exists():
        xml = sitemap_path.read_text(encoding="utf-8")
        xml = re.sub(r"<lastmod>\d{4}-\d{2}-\d{2}</lastmod>", f"<lastmod>{release_date}</lastmod>", xml)
        sitemap_path.write_text(xml, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--date", required=True)
    parser.add_argument("--apk-filename", default="")
    args = parser.parse_args()

    version = args.version.lstrip("vV").strip()
    root = Path(__file__).resolve().parents[2]
    apk_filename = args.apk_filename.strip() or None

    sync_readme(root, version, apk_filename)
    sync_citation(root, version, args.date.strip())
    sync_portal(root, version, args.date.strip())


if __name__ == "__main__":
    main()
