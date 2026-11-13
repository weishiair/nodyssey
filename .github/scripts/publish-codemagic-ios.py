"""Attach a verified Codemagic iOS build to its matching GitHub release."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time


TAG = re.compile(r"v\d+(?:\.\d+)*(?:-(?:test|dev|ios-test)\.\d+)?\Z")
IOS_TEST_TAG = re.compile(r"v\d+(?:\.\d+)*-ios-test\.\d+\Z")
MARKER = "### iOS（未签名，需要自己签）"


def gh(*arguments, check=True):
    result = subprocess.run(
        ["gh", *arguments],
        text=True,
        capture_output=True,
    )
    if check and result.returncode:
        detail = (result.stderr or result.stdout).strip()
        token = os.environ.get("GH_TOKEN")
        if token:
            detail = detail.replace(token, "[redacted]")
        raise RuntimeError(f"gh {' '.join(arguments[:2])} failed: {detail}")
    return result


def wait_for_android_release(tag, commit):
    # Match the original iOS job's `needs: release`: wait for the Android job,
    # including its update manifests, rather than merely the release's creation.
    # Do not wait for the whole run: the GitHub Mac job may still be queued.
    for attempt in range(11):
        result = gh(
            "run", "list", "--workflow", "release.yml", "--branch", tag,
            "--event", "push", "--limit", "1", "--json", "databaseId,headSha,status,conclusion",
        )
        runs = json.loads(result.stdout)
        if runs:
            run = runs[0]
            if run["headSha"] != commit:
                raise RuntimeError("The Android release ran for a different commit")
            jobs = json.loads(gh("run", "view", str(run["databaseId"]), "--json", "jobs").stdout)["jobs"]
            release_job = next((job for job in jobs if job["name"] == "release"), None)
            if release_job and release_job["status"] == "completed":
                if release_job["conclusion"] != "success":
                    raise RuntimeError("The Android release job did not succeed")
                gh("release", "view", tag)
                return
            if run["status"] == "completed":
                raise RuntimeError("The Android release job did not complete successfully")
        if attempt < 10:
            time.sleep(30)
    raise RuntimeError("The Android release has not finished; the IPA remains in Codemagic artifacts")


def publish(tag, version, build_dir):
    if not TAG.fullmatch(tag) or tag != f"v{version}":
        raise ValueError("The release tag must match the version in gradle.properties")

    output = Path(build_dir) / "build" / "ios-unsigned"
    source = output / f"nodyssey-{version}.ipa"
    info = output / "build-info.txt"
    commit = (output / "source-commit.txt").read_text(encoding="utf-8").strip()
    if not source.is_file() or not info.is_file() or not commit:
        raise ValueError("The verified IPA, build information and source commit are required")

    gh("auth", "status")
    if IOS_TEST_TAG.fullmatch(tag):
        if gh("release", "view", tag, check=False).returncode != 0:
            # The Android release workflow deliberately excludes iOS-only test tags.
            gh(
                "release", "create", tag, "--verify-tag", "--prerelease",
                "--title", f"Nodyssey {tag}",
                "--notes", "iOS 测试版；安装包未签名，需要自行签名后安装。",
            )
    else:
        wait_for_android_release(tag, commit)

    dist = output / "github-release"
    dist.mkdir(exist_ok=True)
    ipa = dist / f"nodyssey-{tag}.ipa"
    shutil.copyfile(source, ipa)
    digest = hashlib.sha256()
    with ipa.open("rb") as package:
        for chunk in iter(lambda: package.read(1024 * 1024), b""):
            digest.update(chunk)
    checksum = dist / f"{ipa.name}.sha256"
    checksum.write_text(f"{digest.hexdigest()}  {ipa.name}\n", encoding="utf-8")
    release_info = dist / f"ios-build-{tag}.txt"
    shutil.copyfile(info, release_info)
    gh("release", "upload", tag, str(ipa), str(checksum), str(release_info), "--clobber")

    body = gh("release", "view", tag, "--json", "body", "--jq", ".body").stdout.rstrip()
    if MARKER not in body:
        notes = dist / "release-notes-with-ios.md"
        notes.write_text(
            f"{body}\n\n{MARKER}\n\n"
            "此版本提供未签名的 iOS `.ipa`，需要自行签名后安装。\n"
            "支持 iOS 16 及以上；iOS 26/27 使用系统液态玻璃底栏，较早系统使用普通底栏。\n"
            f"iOS 源码提交：`{commit}`。\n"
            "本次使用 ARM Mac 构建，Kotlin/Native 与 iOS 模拟器回归测试通过。\n",
            encoding="utf-8",
        )
        gh("release", "edit", tag, "--notes-file", str(notes))


def main():
    tag = os.environ["CM_TAG"]
    os.environ["GH_REPO"] = os.environ["CM_REPO_SLUG"]
    if not os.environ.get("GH_TOKEN"):
        raise RuntimeError("Set GH_TOKEN as a secret in Codemagic's github_release group")
    properties = Path("gradle.properties").read_text(encoding="utf-8").splitlines()
    version = next(line.split("=", 1)[1] for line in properties if line.startswith("nodyssey.versionName="))
    publish(tag, version, os.environ["CM_BUILD_DIR"])


if __name__ == "__main__":
    main()
