#!/usr/bin/env python3
"""Run the real simulator app through iPad window creation and repeated rotations."""

import json
import os
from pathlib import Path
import subprocess
import sys


def run(*args, **kwargs):
    print("+", *map(str, args), flush=True)
    return subprocess.run(list(map(str, args)), check=True, text=True, **kwargs)


def sim_list(kind):
    return json.loads(subprocess.check_output(["xcrun", "simctl", "list", kind, "-j"]))[kind]


def main():
    app = Path(sys.argv[1]).resolve()
    output = Path(sys.argv[2]).resolve()
    assert (app / "Info.plist").is_file(), app
    output.mkdir(parents=True, exist_ok=True)
    sdk = subprocess.check_output(
        ["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], text=True,
    ).strip()
    library = output / "ipad-orientation-smoke.dylib"
    run(
        "xcrun", "clang", "-target", "arm64-apple-ios16.0-simulator", "-isysroot", sdk,
        "-fobjc-arc", "-dynamiclib", "-framework", "UIKit", "-framework", "Foundation",
        Path(__file__).with_name("ipad-orientation-smoke.m"), "-o", library,
    )
    run("codesign", "--force", "--sign", "-", library)
    runtime = max(
        (item for item in sim_list("runtimes") if item["isAvailable"] and "iOS" in item["name"]),
        key=lambda item: tuple(int(part) for part in item["version"].split(".")),
    )
    types = sim_list("devicetypes")
    reports = []
    for size in (11, 13):
        device_type = next(item for item in types if f"iPad Pro {size}-inch (M4)" == item["name"])
        device = subprocess.check_output(
            ["xcrun", "simctl", "create", f"Nodyssey-smoke-{size}", device_type["identifier"], runtime["identifier"]],
            text=True,
        ).strip()
        try:
            run("xcrun", "simctl", "boot", device)
            run("xcrun", "simctl", "bootstatus", device, "-b")
            run("xcrun", "simctl", "install", device, app)
            env = os.environ.copy()
            env["SIMCTL_CHILD_DYLD_INSERT_LIBRARIES"] = str(library)
            log = output / f"ipad-{size}.log"
            with log.open("w") as stream:
                run(
                    "xcrun", "simctl", "launch", "--console", device, "io.github.nodyssey",
                    env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=100,
                )
            content = log.read_text(errors="replace")
            steps = [line for line in content.splitlines() if "IPAD_SMOKE_" in line]
            print(chr(10).join(steps), flush=True)
            report = {"device": device_type["name"], "runtime": runtime["name"], "steps": steps}
            reports.append(report)
            (output / "results.json").write_text(json.dumps(reports, indent=2) + chr(10))
            assert "IPAD_SMOKE_OK" in content, f"App crashed or failed rotation; see {log}"
            assert sum("IPAD_SMOKE_STEP " in step for step in steps) == 5, steps
        finally:
            subprocess.run(["xcrun", "simctl", "shutdown", device], check=False)
            subprocess.run(["xcrun", "simctl", "delete", device], check=False)


if __name__ == "__main__":
    main()
