#!/usr/bin/env python3
"""Reject stale or incomplete generated C before any native build."""
import hashlib
import json
import shutil
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def digest(paths, base):
    result = hashlib.sha256()
    paths = sorted(paths)
    with ThreadPoolExecutor(max_workers=8) as readers:
        for path, data in zip(paths, readers.map(Path.read_bytes, paths)):
            result.update(str(path.relative_to(base)).encode() + b'\0')
            result.update(data)
    return result.hexdigest()


def input_digest():
    sources = [ROOT / p for p in ('build.gradle', 'tools/inputs.lock.json',
               'tools/vita_patch_runtime.py', 'tools/build_fingerprint.py', 'build/input/gangfort.jar')]
    for folder in ('source/java', 'third_party/stspsvita/backend/src/main/java',
                   'third_party/stspsvita/backend/stubs/src/main/java', 'third_party/gdx-box2d-gwt'):
        sources.extend(p for p in (ROOT / folder).rglob('*.java')
                       if p.name != 'NewInstanceRegistry.java'
                       and '/com/megacrit/' not in str(p) and '/com/codedisaster/' not in str(p))
    sources.extend(p for p in (ROOT / 'source/resources').rglob('*') if p.is_file())
    return digest(sources, ROOT)


def state(generated):
    outputs = [p for p in generated.rglob('*') if p.is_file() and p.name != 'ready']
    if not (generated / 'all.txt').is_file() or not outputs:
        raise ValueError('Generated C is incomplete')
    return {'inputs': input_digest(), 'generated': digest(outputs, generated)}


if __name__ == '__main__':
    try:
        mode, directory, *extra = sys.argv[1:]
        generated = Path(directory).resolve()
        snapshot = ROOT / 'build/teavm-inputs.json'
        if mode == 'snapshot':
            snapshot.write_text(json.dumps({'inputs': input_digest()}) + '\n')
            sys.exit(0)
        if mode == 'publish':
            pending = Path(extra[0]).resolve()
            actual = state(pending)
            if json.loads(snapshot.read_text())['inputs'] != actual['inputs']:
                raise ValueError('Sources changed during transpilation; retry with stable inputs')
            generated.mkdir(parents=True, exist_ok=True)
            names = {p.relative_to(pending) for p in pending.rglob('*') if p.is_file()}
            def publish_file(name):
                source, target = pending / name, generated / name
                if target.exists() and target.read_bytes() == source.read_bytes():
                    return
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
            with ThreadPoolExecutor(max_workers=8) as writers:
                list(writers.map(publish_file, sorted(names)))
            for target in generated.rglob('*'):
                if target.is_file() and target.relative_to(generated) not in names:
                    target.unlink()
            if input_digest() != actual['inputs']:
                raise ValueError('Sources changed while publishing generated C')
            (generated / 'ready').write_text(json.dumps(actual, indent=2) + '\n')
            print('Published generated C; unchanged files retain their timestamps')
            sys.exit(0)
        actual = state(generated)
        marker = generated / 'ready'
        if mode == 'record':
            if json.loads(snapshot.read_text())['inputs'] != actual['inputs']:
                raise ValueError('Sources changed during transpilation; retry with stable inputs')
            marker.write_text(json.dumps(actual, indent=2) + '\n')
        elif mode == 'verify':
            if json.loads(marker.read_text()) != actual:
                raise ValueError('Inputs or generated C changed; run Gradle transpile successfully')
            if extra and hashlib.sha256(marker.read_bytes()).hexdigest() != extra[0]:
                raise ValueError('Generated C changed during the native build; reconfigure and rebuild')
        else:
            raise ValueError('Expected record or verify')
    except (OSError, ValueError) as error:
        sys.exit(f'TeaVM validation failed: {error}')
