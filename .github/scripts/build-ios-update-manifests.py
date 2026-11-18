"""Write the IPA inventory read by iOS clients from the existing Pages updates branch."""

import argparse
from functools import cmp_to_key
from itertools import zip_longest
import json
from pathlib import Path
import re
import subprocess


TAG = re.compile(r"v(?P<core>\d+(?:\.\d+)*)(?:-(?P<kind>test|dev|ios-test)\.(?P<number>\d+))?\Z")


def compare_versions(left, right):
    a, b = TAG.fullmatch(left["tag"]), TAG.fullmatch(right["tag"])
    for x, y in zip_longest(map(int, a["core"].split(".")), map(int, b["core"].split(".")), fillvalue=0):
        if x != y:
            return (x > y) - (x < y)
    if a["kind"] != b["kind"]:
        if a["kind"] is None:
            return 1
        if b["kind"] is None:
            return -1
        return (a["kind"] > b["kind"]) - (a["kind"] < b["kind"])
    x, y = int(a["number"] or 0), int(b["number"] or 0)
    return (x > y) - (x < y)


def build(releases, repository):
    entries = []
    for release in releases:
        tag = release.get("tag_name", "")
        match = TAG.fullmatch(tag)
        if release.get("draft") or match is None:
            continue
        name = f"nodyssey-{tag}.ipa"
        assets = [asset for asset in release.get("assets", []) if asset.get("name") == name and asset.get("state") == "uploaded"]
        if not assets:
            continue
        if len(assets) != 1:
            raise ValueError("A release must have exactly one uploaded IPA")
        asset = assets[0]
        url = f"https://github.com/{repository}/releases/download/{tag}/{name}"
        if asset.get("browser_download_url") != url or asset.get("size", 0) <= 0:
            raise ValueError("The IPA must be a nonempty asset on this repository's release")
        entries.append({
            "versionName": tag[1:],
            "tag": tag,
            "notes": (release.get("body") or "").strip(),
            "publishedOn": (release.get("published_at") or "")[:10],
            "preRelease": bool(release.get("prerelease")) or match["kind"] is not None,
            "releaseUrl": f"https://github.com/{repository}/releases/tag/{tag}",
            "ipa": {"name": name, "url": url, "sizeBytes": asset["size"]},
        })
    if not entries:
        raise ValueError("No published IPA exists; refusing to replace the feed")
    entries.sort(key=cmp_to_key(compare_versions), reverse=True)
    return {"schema": 1, "versions": entries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--releases", type=Path, help="Saved API response for offline verification")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        raise ValueError("Invalid repository")
    if args.releases:
        releases = json.loads(args.releases.read_text(encoding="utf-8"))
    else:
        result = subprocess.run(
            ["gh", "api", "--paginate", "--slurp", f"repos/{args.repository}/releases?per_page=100"],
            check=True, text=True, encoding="utf-8", capture_output=True,
        )
        releases = [release for page in json.loads(result.stdout) for release in page]
    manifest = build(releases, args.repository)
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "ios.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
