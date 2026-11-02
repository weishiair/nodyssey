import os
from pathlib import Path
import runpy
import subprocess
import sys

root = Path(os.environ['RUNNER_TEMP']) / 'ipad-delivery'
root.mkdir(exist_ok=True)
subprocess.run([
    'gh', 'run', 'download', '41454587442', '-R', 'weishiair/nodyssey',
    '-n', 'nodyssey-ios-simulator-41454587442', '-D', str(root),
], check=True)
archive = next(root.glob('*simulator-arm64.zip'))
subprocess.run(['ditto', '-x', '-k', str(archive), str(root)], check=True)
sys.argv = ['test-ios-ipad.py', str(root / 'Nodyssey.app'), str(root / 'smoke')]
runpy.run_path('.github/scripts/test-ios-ipad.py', run_name='__main__')
