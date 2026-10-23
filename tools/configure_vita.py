#!/usr/bin/env python3
"""Configure in the current checkout, preserving any cache from a moved project."""
import datetime
import os
import io
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import PIL

root = Path(__file__).resolve().parents[1]
sdk = Path(sys.argv[1]).resolve()
native = root / 'build/gangfort-native'
source = root / 'third_party/stspsvita/native'
toolchain = sdk / 'share/vita.toolchain.cmake'
if not toolchain.is_file():
    sys.exit(f'VitaSDK not found at {sdk}; set VITASDK or -PvitaSdk=/path/to/sdk')
packages = {}
for description in (sdk / 'var/lib/pacman/local').glob('*/desc'):
    lines = description.read_text().splitlines()
    packages[lines[lines.index('%NAME%') + 1]] = lines[lines.index('%VERSION%') + 1]
expected = json.loads((root / 'tools/toolchain.lock.json').read_text())
for name, version in expected.items():
    if packages.get(name) != version:
        sys.exit(f'VitaSDK package {name}: expected {version}, found {packages.get(name)}')
(root / 'build/toolchain-manifest.json').write_text(json.dumps({
    'packages': packages,
    'python': sys.version,
    'pillow': PIL.__version__,
    'cmake': subprocess.check_output(['cmake', '--version'], text=True).strip(),
    'compiler': subprocess.check_output([str(sdk / 'bin/arm-vita-eabi-gcc'), '--version'], text=True).strip(),
}, indent=2) + '\n')

from PIL import Image, ImageOps
metadata = root / 'build/gangfort-package-sce_sys'
metadata.mkdir(parents=True, exist_ok=True)
# Vita LiveArea accepts these fixed canvas sizes, not source-resolution artwork.
livearea_sizes = {
    'bg0.png': (840, 500),
    'startup.png': (280, 158),
}
for asset in (source / 'sce_sys').rglob('*'):
    if not asset.is_file() or asset.name == 'README.md':
        continue
    destination = metadata / asset.relative_to(source / 'sce_sys')
    destination.parent.mkdir(parents=True, exist_ok=True)
    data = asset.read_bytes()
    if asset.name == 'template.xml':
        template = ET.fromstring(data)
        for image in template.iter():
            if image.tag in ('image', 'startup-image') and image.text:
                if not (asset.parent / image.text).is_file():
                    sys.exit(f'Missing LiveArea image: {image.text}')
    if asset.name in livearea_sizes:
        with Image.open(io.BytesIO(data)) as image:
            image = ImageOps.fit(image.convert('RGB'), livearea_sizes[asset.name],
                                 method=Image.Resampling.LANCZOS)
            image = image.quantize(colors=128, method=Image.Quantize.MEDIANCUT)
            encoded = io.BytesIO()
            image.save(encoded, format='PNG', optimize=True)
            data = encoded.getvalue()
    if not destination.exists() or destination.read_bytes() != data:
        destination.write_bytes(data)
icon = Image.open(root / 'build/input/launcher.png').convert('RGBA')
icon = icon.resize((128, 128), Image.Resampling.LANCZOS)
icon = icon.quantize(colors=256, method=Image.Quantize.FASTOCTREE)
encoded = io.BytesIO()
icon.save(encoded, format='PNG', optimize=False, compress_level=9)
destination = metadata / 'icon0.png'
if not destination.exists() or destination.read_bytes() != encoded.getvalue():
    destination.write_bytes(encoded.getvalue())
cache = native / 'CMakeCache.txt'
if cache.exists():
    values = dict(line.split('=', 1) for line in cache.read_text().splitlines() if '=' in line and not line.startswith('//'))
    values = {key.split(':')[0]: value for key, value in values.items()}
    if (values.get('CMAKE_HOME_DIRECTORY') != str(source)
            or values.get('CMAKE_CACHEFILE_DIR') != str(native)
            or values.get('CMAKE_TOOLCHAIN_FILE') != str(toolchain)
            or values.get('VITASDK') != str(sdk)):
        archive = root / 'build/archive' / ('native-before-relocation-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
        archive.parent.mkdir(parents=True, exist_ok=True)
        native.rename(archive)
        print(f'Preserved old native build: {archive}', flush=True)
environment = dict(os.environ, VITASDK=str(sdk))
subprocess.run(['cmake', '-S', str(source), '-B', str(native),
                '-DCMAKE_TOOLCHAIN_FILE=' + str(toolchain), '-DCMAKE_BUILD_TYPE=RelWithDebInfo',
                '-DGENERATED_C=' + str(root / 'build/generated-c'),
                '-DASSETS=' + str(root / 'build/gangfort-assets'),
                '-DSCE_SYS=' + str(metadata)], env=environment, check=True)
