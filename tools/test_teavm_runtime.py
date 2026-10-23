#!/usr/bin/env python3
"""Compile and run the reachable TeaVM C regression program on the host."""
from pathlib import Path
import subprocess
import sys

generated = Path(sys.argv[1]).resolve()
output = generated.parent
compatibility = output / 'host_uchar.h'
# Apple libc has no uchar.h. Use the same TeaVM conversion implementation as Vita,
# while retaining the host's POSIX memory, fiber and clock implementations.
compatibility.write_text('#include <stdint.h>\n#define TEAVM_PSP 1\n'
                         f'#include "{generated / "uchar.h"}"\n#undef TEAVM_PSP\n')
binary = output / 'runtime-checks'
translation_unit = generated / 'all.c'
if sys.platform == 'darwin':
    # TeaVM's Unix fiber implementation uses POSIX timers absent from Darwin.
    # Keep its blocked-signal scheduling semantics using Darwin's real interval timer.
    (output / 'host_fiber.c').write_text(r'''
#include <stdint.h>
#include <locale.h>
#include <signal.h>
#include <sys/time.h>
static sigset_t queue_signal;
void teavm_initFiber(void) {
    setlocale(LC_ALL, "");
    sigemptyset(&queue_signal);
    sigaddset(&queue_signal, SIGALRM);
    sigprocmask(SIG_BLOCK, &queue_signal, NULL);
}
void teavm_waitFor(int64_t timeout) {
    struct itimerval timer = {0};
    timer.it_value.tv_sec = timeout / 1000;
    timer.it_value.tv_usec = (timeout % 1000) * 1000;
    setitimer(ITIMER_REAL, &timer, NULL);
    int received;
    sigwait(&queue_signal, &received);
}
void teavm_interrupt(void) {
    struct itimerval timer = {0};
    setitimer(ITIMER_REAL, &timer, NULL);
    raise(SIGALRM);
}
''')
    combined = translation_unit.read_text()
    assert combined.count('#include "fiber.c"') == 1
    translation_unit = generated / 'host-all.c'
    translation_unit.write_text(combined.replace('#include "fiber.c"', '#include "../host_fiber.c"'))
with (output / 'compile.log').open('w') as log:
    result = subprocess.run(['cc', '-std=gnu11', '-D_GNU_SOURCE', '-O0',
                             '-I', str(Path(__file__).resolve().parents[1]
                                       / 'third_party/stspsvita/native/src/glue'),
                             '-Wno-incompatible-pointer-types', '-Wno-int-conversion',
                             '-Wno-implicit-function-declaration', '-include', str(compatibility),
                             '-include', str(generated / 'classes/java/lang/Class.h'),
                             str(translation_unit), '-lm', '-o', str(binary)],
                            stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'compile.log').read_text()[-12000:], file=sys.stderr)
    sys.exit(result.returncode)
with (output / 'run.log').open('w') as log:
    result = subprocess.run([str(binary)], stdout=log, stderr=subprocess.STDOUT, timeout=60)
print((output / 'run.log').read_text(), end='')
if result.returncode:
    sys.exit(f'TeaVM C runtime regression failed (exit {result.returncode})')
