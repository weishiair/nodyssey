import os
from pathlib import Path
import runpy
import subprocess
import sys
import time
import json

root = Path(os.environ['RUNNER_TEMP']) / 'ipad-delivery'
root.mkdir(exist_ok=True)
deadline = time.monotonic() + 2400
while True:
    response = json.loads(subprocess.check_output([
        'gh', 'api', 'repos/weishiair/nodyssey/actions/runs/41454587442/artifacts',
    ], text=True))
    if any(item['name'] == 'nodyssey-ios-simulator-41454587442' for item in response['artifacts']):
        break
    status = json.loads(subprocess.check_output([
        'gh', 'run', 'view', '41454587442', '-R', 'weishiair/nodyssey', '--json', 'status,conclusion',
    ], text=True))
    assert status['status'] != 'completed' or status['conclusion'] == 'success', status
    assert time.monotonic() < deadline, 'Simulator artifact did not arrive within 40 minutes'
    print('Waiting for the simulator artifact', flush=True)
    time.sleep(20)
subprocess.run([
    'gh', 'run', 'download', '41454587442', '-R', 'weishiair/nodyssey',
    '-n', 'nodyssey-ios-simulator-41454587442', '-D', str(root),
], check=True)
archive = next(root.glob('*simulator-arm64.zip'))
subprocess.run(['ditto', '-x', '-k', str(archive), str(root)], check=True)
sys.argv = ['test-ios-ipad.py', str(root / 'Nodyssey.app'), str(root / 'smoke')]
runpy.run_path('.github/scripts/test-ios-ipad.py', run_name='__main__')
