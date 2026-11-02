import json, os, pathlib, plistlib, subprocess, zipfile, time
root = pathlib.Path(os.environ['RUNNER_TEMP']) / 'ipad-real'
root.mkdir(exist_ok=True)
def run(*args, **kwargs):
    print('RUN', *map(str, args), flush=True)
    return subprocess.run(list(map(str, args)), check=True, text=True, **kwargs)
run('gh', 'run', 'download', '41435935439', '-R', 'weishiair/nodyssey', '-n', 'nodyssey-ios-simulator-41435935439', '-D', root)
archive = next(root.glob('*simulator-arm64.zip'))
with zipfile.ZipFile(archive) as z: z.extractall(root)
app = root / 'Nodyssey.app'
runtimes = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'runtimes', '-j']))['runtimes']
runtime = next(r['identifier'] for r in reversed(runtimes) if r['isAvailable'] and 'iOS' in r['name'])
types = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devicetypes', '-j']))['devicetypes']
device_type = next(r['identifier'] for r in types if 'iPad Pro 13-inch' in r['name'])
device = subprocess.check_output(['xcrun', 'simctl', 'create', 'NodysseyReal', device_type, runtime], text=True).strip()
try:
    run('xcrun', 'simctl', 'boot', device)
    run('xcrun', 'simctl', 'bootstatus', device, '-b')
    run('xcrun', 'simctl', 'install', device, app)
    with (root / 'console.log').open('w') as output:
        proc = subprocess.Popen(['xcrun', 'simctl', 'launch', '--console', device, 'io.github.nodyssey'], text=True, stdout=output, stderr=subprocess.STDOUT)
        try: proc.wait(timeout=45)
        except subprocess.TimeoutExpired: proc.terminate(); proc.wait(timeout=10)
    print((root / 'console.log').read_text(), flush=True)
    run('xcrun', 'simctl', 'io', device, 'screenshot', root / 'screen.png')
    with (root / 'system.log').open('w') as output:
        subprocess.run(['xcrun', 'simctl', 'spawn', device, 'log', 'show', '--last', '2m', '--style', 'compact', '--predicate', 'process == "Nodyssey"'], text=True, stdout=output, stderr=subprocess.STDOUT, timeout=30)
    print((root / 'system.log').read_text()[-24000:], flush=True)
finally:
    run('xcrun', 'simctl', 'shutdown', device)
