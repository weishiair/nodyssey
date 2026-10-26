"""Bundle Cargo dependency notices beside the unsigned app, without executing downloaded text."""
from pathlib import Path
import json
import subprocess
import sys

repo = Path(__file__).resolve().parents[2]
metadata = json.loads(subprocess.check_output(
    ['cargo', 'metadata', '--locked', '--format-version', '1'],
    cwd=repo / 'native/ios-network',
))
parts = ['Nodyssey native network dependency notices\n\nSource versions are pinned in native/ios-network/Cargo.lock.\n']
for package in sorted(metadata['packages'], key=lambda value: (value['name'], value['version'])):
    directory = Path(package['manifest_path']).parent
    parts.append(f"\n{'=' * 72}\n{package['name']} {package['version']}\nLicense: {package.get('license') or 'See files below'}\nSource: {package.get('repository') or package.get('source') or 'https://github.com/weishiair/nodyssey'}\n")
    candidates = {file for file in directory.rglob('*') if file.is_file()
                  and file.name.lower().startswith(('license', 'licence', 'notice', 'copying', 'copyright'))
                  and not any(name in ('target', '.git') for name in file.relative_to(directory).parts)}
    if package.get('license_file'):
        candidates.add(directory / package['license_file'])
    if package['name'] == 'nodyssey-network':
        candidates.add(repo / 'LICENSE')
    for file in sorted(candidates):
        if file.stat().st_size > 2 * 1024 * 1024:
            raise ValueError('Unexpectedly large notice file')
        parts.append('\n--- ' + file.name + ' ---\n' + file.read_text(encoding='utf8', errors='replace'))
output = Path(sys.argv[1])
output.write_text('\n'.join(parts), encoding='utf8')
print('Bundled native dependency notices:', len(metadata['packages']), 'packages;', output.stat().st_size, 'bytes')
