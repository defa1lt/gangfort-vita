#!/usr/bin/env python3
"""Exercise real generated pathname functions against local files and count allocations."""
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
GENERATED = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'build/generated-c'


def function(text, name):
    match = re.search(r'^[^\n]*\b' + re.escape(name) + r'\([^\n]*\) \{$',
                      text, re.MULTILINE)
    if not match:
        raise ValueError(f'Missing generated runtime function: {name}')
    end = text.index('\n}', match.end()) + 2
    return text[match.start():end]


def run_native(directory, file_source, label):
    strings = (GENERATED / 'string.c').read_text()
    dates = (GENERATED / 'date.c').read_text()
    source = r'''
#define TEAVM_PSP 1
#include "uchar.h"
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <utime.h>
static int live_paths;
static void *counted_malloc(size_t size) { void *p = malloc(size); if (p) ++live_paths; return p; }
static void counted_free(void *p) { if (p) --live_paths; free(p); }
#define malloc counted_malloc
#define free counted_free
'''
    for text, names in (
        (strings, ('teavm_mbSize', 'teavm_char16ToMb')),
        (dates, ('teavm_date_timestampToTime',)),
        (file_source, ('vita_file_mbpath', 'teavm_file_open', 'teavm_file_setLastModified')),
    ):
        source += '\n'.join(function(text, name) for name in names) + '\n'
    source += r'''
int main(void) {
    char16_t name[] = u"present.tmp", missing[] = u"absent/file.tmp";
    for (int i = 0; i < 32; ++i) {
        int64_t handle = teavm_file_open(name, 11, 2);
        assert(handle != 0);
        assert(fputs("save", (FILE *)(intptr_t)handle) >= 0);
        assert(fclose((FILE *)(intptr_t)handle) == 0);
        assert(teavm_file_setLastModified(name, 11, 1700000000000LL));
        assert(teavm_file_open(missing, 15, 1) == 0);
        assert(!teavm_file_setLastModified(missing, 15, 1700000000000LL));
    }
    struct stat result;
    assert(stat("present.tmp", &result) == 0);
    assert(result.st_mtime == teavm_date_timestampToTime(1700000000000LL));
    remove("present.tmp");
    if (live_paths) { fprintf(stderr, "LEAK: %d pathname allocations\n", live_paths); return 1; }
    puts("PASS: 32 real file open/close/time updates and failed opens, zero pathname allocations retained");
}
'''
    c_file = directory / (label + '.c')
    c_file.write_text(source)
    binary = directory / label
    subprocess.run(['cc', '-std=c11', '-Wall', '-Werror', '-Wno-unused-parameter',
                    '-iquote', str(GENERATED.resolve()), str(c_file), '-o', str(binary)], check=True)
    return subprocess.run([str(binary)], cwd=directory, capture_output=True, text=True)


scratch = ROOT / '.agents/scratch'
scratch.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='runtime-paths-', dir=scratch) as name:
    directory = Path(name)
    for filename in ('definitions.h', 'fiber.c', 'uchar.h', 'core.h', 'reflection.c',
                     'arrayclass.c', 'time.c', 'log.c', 'file.c',
                     'classes/org/teavm/runtime/ExceptionHandling.c'):
        (directory / filename).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(GENERATED / filename, directory / filename)
    subprocess.run([sys.executable, str(ROOT / 'tools/vita_patch_runtime.py'), str(directory)], check=True)
    patched = (directory / 'file.c').read_text()
    before = {str(p.relative_to(directory)): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in directory.rglob('*') if p.is_file()}
    subprocess.run([sys.executable, str(ROOT / 'tools/vita_patch_runtime.py'), str(directory)], check=True)
    assert before == {str(p.relative_to(directory)): hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in directory.rglob('*') if p.is_file()}
    leaking = patched.replace('    int result = utime(mbName, &newTime);\n    free(mbName);\n    return result == 0;',
                              '    return utime(mbName, &newTime) == 0;')
    leaking = leaking.replace('    FILE* file = fopen(mbName, modeString);\n    free(mbName);',
                              '    FILE* file = fopen(mbName, modeString);')
    negative = run_native(directory, leaking, 'before')
    assert negative.returncode == 1 and 'LEAK:' in negative.stderr, negative
    positive = run_native(directory, patched, 'after')
    assert positive.returncode == 0, positive.stderr
    print(positive.stdout.strip())
    print('PASS: regression detects original leak; runtime patch is idempotent')
