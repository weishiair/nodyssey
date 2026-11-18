"""Reject iOS 26 UIKit class imports that would prevent older iOS versions from launching."""

from __future__ import annotations

import argparse
from pathlib import Path
import subprocess


GLASS_CLASSES = ("UICornerConfiguration", "UIGlassEffect")


def check_imports(symbols: str) -> list[str]:
    required = {f"_OBJC_CLASS_$_{name}" for name in GLASS_CLASSES}
    optional = {f"_OBJC_METACLASS_$_{name}" for name in GLASS_CLASSES}
    found = set()
    report = []
    strong = []
    for line in symbols.splitlines():
        tokens = set(line.split())
        matched = tokens & (required | optional)
        if matched:
            found.update(matched)
            report.append(line.strip())
            if "weak" not in tokens:
                strong.extend(sorted(matched))
    if strong:
        raise ValueError("Strong iOS 26 imports would crash older iOS at launch: " + ", ".join(strong))
    missing = required - found
    if missing:
        raise ValueError("Cannot verify expected Liquid Glass imports: " + ", ".join(sorted(missing)))
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", help="Final iPhone or simulator Mach-O executable")
    args = parser.parse_args()
    binary = Path(args.binary)
    images = [str(binary)]
    # ENABLE_DEBUG_DYLIB puts the app code here and leaves a launcher in the main executable.
    debug_dylib = binary.with_name(binary.name + ".debug.dylib")
    if debug_dylib.is_file():
        images.append(str(debug_dylib))
    result = subprocess.run(
        ["xcrun", "nm", "-m", "-u", "-A", *images],
        check=True, stdout=subprocess.PIPE, text=True,
    )
    print("\n".join(check_imports(result.stdout)))


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        raise SystemExit(str(error))
