"""Require an executed ECH interoperability test; zero discovered tests is not a pass."""
from pathlib import Path
import xml.etree.ElementTree as ET

reports = Path("app/build/outputs/androidTest-results/connected/debug")
cases = []
for path in reports.rglob("TEST-*.xml"):
    suite = ET.parse(path).getroot()
    cases.extend(
        case for case in suite.iter("testcase")
        if case.get("classname") == "io.github.nodyssey.BundledEchDeviceTest"
    )
if len(cases) != 1:
    raise SystemExit(f"Expected one executed ECH device test, found {len(cases)}")
case = cases[0]
if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
    raise SystemExit("The ECH device test did not pass")
print("ECH device test executed and confirmed encrypted SNI.")
