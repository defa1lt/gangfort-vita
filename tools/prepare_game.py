#!/usr/bin/env python3
"""Convert the verified local APK into a deterministic, complete game JAR."""

import hashlib
import json
import os
import re
import struct
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def check(data, expected, name):
    actual = sha256(data)
    if actual != expected:
        raise ValueError(f"{name}: SHA-256 {actual}, expected {expected}")
    return actual


def stable_bytecode(name, data):
    """dex2jar 2.4 randomizes the names of its private hex-array decoders."""
    if not name.endswith('.class'):
        return data
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError(f'Invalid class file: {name}')
    result = bytearray(data)
    count = struct.unpack_from('>H', data, 8)[0]
    index, offset = 1, 10
    lengths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
               11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    suffix = hashlib.sha256(name.encode()).hexdigest()[:8].encode()
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, offset)[0]
            offset += 2
            value = data[offset:offset + size]
            if re.fullmatch(rb'\$d2j\$hex\$[0-9a-f]{8}\$decode_[BIJS]', value):
                prefix = b'$d2j$hex$'
                result[offset:offset + size] = prefix + suffix + value[len(prefix) + 8:]
            offset += size
        elif tag in lengths:
            offset += lengths[tag]
            if tag in (5, 6): index += 1
        else:
            raise ValueError(f'Unknown constant-pool tag {tag} in {name}')
        index += 1
    return bytes(result)


def prepare(apk, dex_tools, output):
    lock = json.loads(Path(__file__).with_name('inputs.lock.json').read_text())
    apk_hash = check(apk.read_bytes(), lock['apk']['sha256'], str(apk))
    for name, digest in lock['dex2jar']['jars'].items():
        check((dex_tools / 'lib' / name).read_bytes(), digest, name)
    actual_jars = {p.name for p in (dex_tools / 'lib').glob('*.jar')}
    if actual_jars != set(lock['dex2jar']['jars']):
        raise ValueError('dex2jar classpath differs from inputs.lock.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
    classpath = os.pathsep.join(str(p.resolve()) for p in sorted((dex_tools / 'lib').glob('*.jar')))
    entries = {}
    dex_hashes = {}
    with tempfile.TemporaryDirectory(prefix='convert-', dir=output.parent) as scratch:
        scratch = Path(scratch)
        with zipfile.ZipFile(apk) as archive:
            launcher = archive.read('res/drawable-xxxhdpi-v4/ic_launcher.png')
            dex_names = {n for n in archive.namelist() if n.endswith('.dex')}
            if dex_names != set(lock['dex']):
                raise ValueError(f'Unexpected DEX files: {sorted(dex_names)}')
            for name, digest in lock['dex'].items():
                data = archive.read(name)
                dex_hashes[name] = check(data, digest, name)
                dex = scratch / name
                dex.write_bytes(data)
                jar = dex.with_suffix('.jar')
                errors = dex.with_suffix('.errors.zip')
                subprocess.run([java, '-Xmx2g', '-cp', classpath,
                                'com.googlecode.dex2jar.tools.Dex2jarCmd',
                                '--exception-file', str(errors), '-o', str(jar), str(dex)],
                               check=True, cwd=scratch)
                if errors.exists():
                    raise ValueError(f'dex2jar reported conversion failures for {name}')
                with zipfile.ZipFile(jar) as converted:
                    for item in converted.infolist():
                        if item.is_dir() or item.filename.upper().startswith('META-INF/'):
                            continue
                        data = stable_bytecode(item.filename, converted.read(item))
                        if item.filename in entries and entries[item.filename] != data:
                            raise ValueError(f'Conflicting DEX class: {item.filename}')
                        entries[item.filename] = data
        for required in ('wl.class', 'akz.class', 'com/badlogic/gdx/Version.class'):
            if required not in entries:
                raise ValueError(f'Missing game class: {required}')
        result = scratch / 'gangfort.jar'
        with zipfile.ZipFile(result, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                jar.writestr(info, data)
        jar_hash = check(result.read_bytes(), lock['jar_sha256'], 'normalized game JAR')
        manifest = {'apk_sha256': apk_hash, 'dex_sha256': dex_hashes,
                    'jar_sha256': jar_hash, 'classes': len(entries),
                    'dex2jar': lock['dex2jar']['version'], 'libgdx': '1.9.3',
                    'java': subprocess.check_output([java, '-version'], stderr=subprocess.STDOUT, text=True).strip()}
        # Keep the pre-import JAR as evidence; publish only after every DEX succeeds.
        if output.exists() and not output.with_suffix('.previous.jar').exists():
            output.with_suffix('.previous.jar').write_bytes(output.read_bytes())
        result.replace(output)
        output.with_name('launcher.png').write_bytes(launcher)
        output.with_name('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit('usage: prepare_game.py APK DEX_TOOLS OUTPUT_JAR')
    try:
        prepare(*(Path(arg).resolve() for arg in sys.argv[1:]))
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        sys.exit(f'prepare-game: {error}')
