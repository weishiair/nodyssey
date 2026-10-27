"""Launch one simulator test app; never treat simctl's exit status as proof alone."""
import pathlib
import subprocess
import sys

MARKER = "TOKENIZER_GUARD_TEST_SUCCESS"


def launch(udid, bundle, logfile):
    command = ["xcrun", "simctl", "launch", "--console", "--terminate-running-process", udid, bundle]
    timed_out = False
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, timeout=120)
        output, returncode = result.stdout, result.returncode
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        returncode, timed_out = None, True
    pathlib.Path(logfile).write_text(output, encoding="utf-8")
    print(output, flush=True)
    if timed_out:
        raise RuntimeError("simulator launch timed out")
    if returncode != 0 or MARKER not in output.splitlines():
        raise RuntimeError(f"launch exit={returncode}; exact success marker required")


if __name__ == "__main__":
    try:
        launch(*sys.argv[1:])
    except (RuntimeError, OSError) as error:
        sys.exit(f"FAIL: {error}")
