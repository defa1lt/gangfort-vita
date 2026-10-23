"""Exercise stale-output and publication recovery with real temporary files."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


class FingerprintChecks(unittest.TestCase):
    def test_publication_and_stale_recovery(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'tools').mkdir()
            (root / 'build/input').mkdir(parents=True)
            (root / 'source/java').mkdir(parents=True)
            (root / 'source/resources').mkdir()
            script = root / 'tools/build_fingerprint.py'
            shutil.copyfile(Path(__file__).with_name('build_fingerprint.py'), script)
            for name in ('build.gradle', 'tools/inputs.lock.json', 'tools/vita_patch_runtime.py',
                         'build/input/gangfort.jar', 'source/java/Game.java'):
                (root / name).write_bytes(b'input')
            pending, generated = root / 'build/pending', root / 'build/generated'
            pending.mkdir()
            (pending / 'all.txt').write_text('Game.c\n')
            (pending / 'Game.c').write_text('int game;\n')
            old = time.time() - 120
            os.utime(pending / 'Game.c', (old, old))

            def run(mode, *extra, succeeds=True):
                result = subprocess.run([sys.executable, str(script), mode, str(generated), *map(str, extra)],
                                        capture_output=True, text=True)
                self.assertEqual(result.returncode == 0, succeeds, result.stdout + result.stderr)
                return result

            run('snapshot')
            object_built = time.time_ns()
            run('publish', pending)
            self.assertGreater((generated / 'Game.c').stat().st_mtime_ns, object_built,
                               'changed C must be newer than an overlapping old object')
            original_time = (generated / 'Game.c').stat().st_mtime_ns
            run('snapshot')
            run('publish', pending)
            self.assertEqual((generated / 'Game.c').stat().st_mtime_ns, original_time,
                             'unchanged C must not trigger recompilation')
            marker = generated / 'ready'
            expected = hashlib.sha256(marker.read_bytes()).hexdigest()
            run('verify', expected)
            (generated / 'Game.c').write_text('corrupted')
            run('verify', expected, succeeds=False)
            run('publish', pending)
            run('snapshot')
            marker.unlink()
            (root / 'source/java/Game.java').write_text('changed during generation')
            run('publish', pending, succeeds=False)
            self.assertFalse(marker.exists(), 'failed generation must not certify old output')


if __name__ == '__main__':
    unittest.main()
