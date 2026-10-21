#!/usr/bin/env python3
"""Publish this fork's signed releases while retaining its legacy ECH release history."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import urllib.request

LEGACY_APK_NAME = re.compile(r"nodyssey-(\d+\.\d+\.\d+)-ech(-test)?\.(\d+)-release\.apk")
APK_NAME = re.compile(r"nodyssey-(\d+\.\d+\.\d+)(?:-dev\.(\d+))?-release\.apk")


def fork_release(release: dict, repository: str) -> dict | None:
    if release.get("draft") or not release.get("tag_name", "").startswith(("ech-", "release-")):
        return None
    legacy = release["tag_name"].startswith("ech-")
    pattern = LEGACY_APK_NAME if legacy else APK_NAME
    assets = [(asset, pattern.fullmatch(asset["name"])) for asset in release.get("assets", [])]
    signed = [(asset, match) for asset, match in assets if match is not None]
    if not signed:
        return None
    if len(signed) != 1:
        raise ValueError("A fork release must contain exactly one signed release APK")
    asset, match = signed[0]
    if legacy:
        core, test, revision = match.groups()
        version = f"{core}-ech{test or ''}.{revision}"
        tag = f"ech-{core}{'-test' if test else ''}.{revision}"
        order = (*map(int, core.split('.')), 0, int(revision), not bool(test))
    else:
        core, revision = match.groups()
        test = revision is not None
        version = f"{core}-dev.{revision}" if test else core
        tag = f"release-{version}"
        order = (*map(int, core.split('.')), 1 if test else 2, int(revision or 0), not test)
    if release["tag_name"] != tag:
        raise ValueError("Release tag and APK version disagree")
    if bool(release.get("prerelease")) != bool(test):
        raise ValueError("Test releases must be marked as prereleases")
    expected_url = f"https://github.com/{repository}/releases/download/{tag}/{asset['name']}"
    digest = asset.get("digest", "")
    if asset.get("browser_download_url") != expected_url:
        raise ValueError("APK download must belong to this fork's release")
    if not re.fullmatch(r"sha256:[0-9a-fA-F]{64}", digest) or asset.get("size", 0) <= 0:
        raise ValueError("GitHub must provide a SHA-256 and size for the uploaded APK")
    return {
        "schema": 1,
        "versionName": version,
        "tag": tag,
        "notes": (release.get("body") or "").strip(),
        "publishedOn": (release.get("published_at") or "")[:10],
        "releaseUrl": f"https://github.com/{repository}/releases/tag/{tag}",
        "apk": {"name": asset["name"], "url": expected_url,
                "sizeBytes": asset["size"], "sha256": digest[7:].lower()},
        "_preRelease": bool(test),
        "_order": order,
    }


def build(releases: list[dict], repository: str) -> dict[str, dict]:
    entries = [entry for release in releases if (entry := fork_release(release, repository)) is not None]
    entries.sort(key=lambda entry: entry["_order"], reverse=True)
    stable = next((entry for entry in entries if not entry["_preRelease"]), None)
    if stable is None:
        raise ValueError("No published signed fork release exists; refusing to replace the feed")

    def manifest(entry: dict, channel: str) -> dict:
        return {key: value for key, value in dict(entry, channel=channel).items() if not key.startswith("_")}

    notes = [{**{key: entry[key] for key in ("versionName", "tag", "notes", "publishedOn", "releaseUrl")},
              "preRelease": entry["_preRelease"]} for entry in entries]
    return {"stable.json": manifest(stable, "stable"),
            "dev.json": manifest(entries[0], "dev"),
            "changelog.json": {"schema": 1, "versions": notes}}


def fetch_releases(repository: str) -> list[dict]:
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "Nodyssey-fork-updates"}
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    releases = []
    for page in range(1, 101):
        url = f"https://api.github.com/repos/{repository}/releases?per_page=100&page={page}"
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=45) as response:
            batch = json.load(response)
        releases.extend(batch)
        if len(batch) < 100:
            return releases
    raise ValueError("Release history exceeded the supported pagination limit")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--releases", type=Path, help="Saved release API response for offline verification")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        raise ValueError("Invalid repository")
    releases = json.loads(args.releases.read_text(encoding="utf-8")) if args.releases else fetch_releases(args.repository)
    files = build(releases, args.repository)
    args.out.mkdir(parents=True, exist_ok=True)
    for name, content in files.items():
        (args.out / name).write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.out / ".nojekyll").touch()
    (args.out / "index.html").write_text(
        '<!doctype html><meta charset="utf-8"><title>Nodyssey updates</title>'
        f'<h1>Nodyssey</h1><p><a href="https://github.com/{args.repository}/releases">下载 / Releases</a></p>'
        '<p>非官方分支，基于 <a href="https://github.com/5151561/nodyssey">Nodyssey</a>，GPL-3.0。</p>\n',
        encoding="utf-8",
    )
    print(f"Wrote fork manifests for {files['stable.json']['versionName']}")


if __name__ == "__main__":
    main()
