#!/usr/bin/env python3
"""Archive an executable with its matching symbol ELF and local build evidence."""
import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
native = root / 'build/gangfort-native'

def run(*command):
    return subprocess.check_output(command, cwd=root, text=True).strip()

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

subprocess.run([sys.executable, str(root / 'tools/build_fingerprint.py'),
                'verify', str(root / 'build/generated-c')], check=True)
elf, eboot = native / 'GangfortVita', native / 'eboot.bin'
for path in (elf, eboot):
    if not path.is_file():
        sys.exit(f'Missing build output: {path}')
if eboot.stat().st_mtime_ns < elf.stat().st_mtime_ns:
    sys.exit('Executable predates the symbol ELF; build eboot.bin-self first')
header = (native / 'gangfort_build_id.h').read_text()
match = re.search(r'GANGFORT_BUILD_ID\s+"([0-9a-f]{7,40}-[0-9a-f]{12})"', header)
if not match:
    sys.exit('Build ID missing from generated header')
build_id = match.group(1)
if build_id.encode() + b'\0' not in elf.read_bytes():
    sys.exit('Symbol ELF does not contain the configured build ID; finish the native build first')
archive = root / 'build/releases' / (build_id + '-' + digest(eboot)[:12])
archive.mkdir(parents=True, exist_ok=True)
for source, name in ((elf, 'GangfortVita.elf'), (eboot, 'eboot.bin')):
    target = archive / name
    if target.exists() and digest(target) != digest(source):
        sys.exit(f'Refusing to replace a different archived artifact: {target}')
    if not target.exists():
        shutil.copy2(source, target)
manifest = {
    'built_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'build_id': build_id, 'commit': run('git', 'rev-parse', 'HEAD'),
    'working_tree': run('git', 'status', '--short', '--untracked-files=all'),
    'diff_sha256': hashlib.sha256(subprocess.check_output(['git', 'diff', 'HEAD'], cwd=root)).hexdigest(),
    'inputs': json.loads((root / 'build/input/manifest.json').read_text()),
    'toolchain': json.loads((root / 'build/toolchain-manifest.json').read_text()),
    'teavm': json.loads((root / 'build/generated-c/ready').read_text()),
    'artifacts': {p.name: {'bytes': p.stat().st_size, 'sha256': digest(p)}
                  for p in (archive / 'GangfortVita.elf', archive / 'eboot.bin')},
}
(archive / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(archive)
print(json.dumps(manifest['artifacts'], indent=2))
