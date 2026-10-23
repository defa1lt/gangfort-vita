#!/usr/bin/env python3
"""Apply Vita-specific fixes to TeaVM's generated C runtime."""

from pathlib import Path
import sys


def patch_file(root: Path, name: str, edits: list[tuple[str, str, int]]) -> bool:
    path = root / name
    text = path.read_text()
    original = text
    for old, new, expected in edits:
        if new in text:
            continue
        count = text.count(old)
        if count != expected:
            raise SystemExit(f"{name}: expected {expected} instance(s) of {old!r}, found {count}")
        text = text.replace(old, new)
    if text != original:
        path.write_text(text)
        return True
    return False


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: vita_patch_runtime.py <generated-c-dir>")
    root = Path(sys.argv[1])
    if not root.is_dir():
        raise SystemExit(f"not a directory: {root}")

    changes = [
        ("definitions.h", [
            ("#ifndef TEAVM_USE_SETJMP", """/* Vita uses TeaVM's homebrew heap and setjmp runtime. */
#ifdef TEAVM_VITA
    #undef TEAVM_UNIX
    #define TEAVM_UNIX 0
    #undef TEAVM_PSP
    #define TEAVM_PSP 1
#endif

#ifndef TEAVM_USE_SETJMP""", 1),
        ]),
        ("fiber.c", [
            ("#if TEAVM_PSP\n    #include <pspkernel.h>\n#endif", """#if TEAVM_VITA
    #include <psp2/kernel/threadmgr.h>
#elif TEAVM_PSP
    #include <pspkernel.h>
#endif""", 1),
        ]),
        ("uchar.h", [
            ("#if TEAVM_PSP\n", "#if TEAVM_PSP || defined(TEAVM_VITA)\n", 1),
            ("#include <wchar.h>", "#include <wchar.h>\n#include <stdint.h>", 1),
        ]),
        ("core.h", [
            ("#define TEAVM_ARRAY_DATA(array, type) ((type*) TEAVM_ALIGN((((TeaVM_Array*) (array)) + 1), sizeof(type)))",
             """/* Array data must use a fixed offset after the compacting GC moves it. */
#define TEAVM_ARRAY_DATA(array, type) ((type*) ((char*) (array) + ((sizeof(TeaVM_Array) + sizeof(type) - 1) / sizeof(type) * sizeof(type))))""", 1),
        ]),
        ("reflection.c", [
            ("char* data = TEAVM_ALIGN(((TeaVM_Array*) array) + 1, size);",
             "char* data = (char*) array + ((sizeof(TeaVM_Array) + size - 1) / size * size);", 2),
        ]),
        ("arrayclass.c", [
            ("static TEAVM_OBJECT_CLASS teavm_dynamicClassPool[TEAVM_DYNAMIC_CLASS_POOL_CAPACITY];",
             """/* Compressed class pointers require each dynamic class slot to be 8-byte aligned. */
typedef union { TEAVM_OBJECT_CLASS c; } __attribute__((aligned(8))) teavm_dynamic_class_slot;
static teavm_dynamic_class_slot teavm_dynamicClassPool[TEAVM_DYNAMIC_CLASS_POOL_CAPACITY];""", 1),
            ("TEAVM_OBJECT_CLASS* ptr = &teavm_dynamicClassPool[teavm_dynamicClassPoolSize++];",
             "TEAVM_OBJECT_CLASS* ptr = &teavm_dynamicClassPool[teavm_dynamicClassPoolSize++].c;", 1),
            ("return &teavm_dynamicClassPool[index].parent;",
             "return &teavm_dynamicClassPool[index].c.parent;", 1),
        ]),
        ("time.c", [
            ("#if TEAVM_PSP\n    int64_t teavm_currentTimeMillis() {",
             "#if TEAVM_PSP\n    #include <psp2/kernel/processmgr.h>\n    int64_t teavm_currentTimeMillis() {", 1),
            ("return (int64_t) sceKernelLibcTime(NULL) * 1000000000;",
             "return (int64_t) sceKernelGetProcessTimeWide() * 1000;", 1),
            ("return (int64_t) sceKernelLibcTime(NULL) * 1000;",
             "SceKernelTimeval now;\n        sceKernelLibcGettimeofday(&now, NULL);\n        return (int64_t) now.sec * 1000 + now.usec / 1000;", 1),
        ]),
        ("log.c", [
            ("#if !TEAVM_CUSTOM_LOG", """#if defined(TEAVM_VITA)
static void vita_log_codepoint(FILE* stream, uint32_t c) {
    unsigned char bytes[4];
    int n;
    if (c > 0x10FFFF || (c >= 0xD800 && c <= 0xDFFF)) c = 0xFFFD;
    if (c < 0x80) { bytes[0] = c; n = 1; }
    else if (c < 0x800) {
        bytes[0] = 0xC0 | (c >> 6); bytes[1] = 0x80 | (c & 63); n = 2;
    } else if (c < 0x10000) {
        bytes[0] = 0xE0 | (c >> 12); bytes[1] = 0x80 | ((c >> 6) & 63);
        bytes[2] = 0x80 | (c & 63); n = 3;
    } else {
        bytes[0] = 0xF0 | (c >> 18); bytes[1] = 0x80 | ((c >> 12) & 63);
        bytes[2] = 0x80 | ((c >> 6) & 63); bytes[3] = 0x80 | (c & 63); n = 4;
    }
    fwrite(bytes, 1, n, stream);
}
#endif

#if !TEAVM_CUSTOM_LOG""", 1),
            ('#elif TEAVM_PSP\n                fprintf(stderr, "%ls", s);',
             '''#elif defined(TEAVM_VITA)
                while (*s) {
                    char32_t c;
                    s = teavm_utf16ToUtf32(s, &c);
                    vita_log_codepoint(stderr, c);
                }
            #elif TEAVM_PSP
                fprintf(stderr, "%ls", s);''', 1),
            ("            putwchar(c);", """            #if defined(TEAVM_VITA)
                vita_log_codepoint(stdout, (uint32_t)c);
            #else
                putwchar(c);
            #endif""", 1),
        ]),
    ]

    for name, edits in changes:
        changed = patch_file(root, name, edits)
        print(f"[vita-patch] {name}: {'patched' if changed else 'already patched'}")

    file_c = root / "file.c"
    text = file_c.read_text()
    if "vita_file_mbpath" not in text:
        old_guard = "#if TEAVM_UNIX\n#include <unistd.h>"
        if text.count(old_guard) != 1:
            raise SystemExit("file.c: POSIX file-I/O block anchor not found")
        helper = """/* TeaVM prepends cwd to Vita device paths such as app0:/ and ux0:. */
static char* vita_file_mbpath(char16_t* name, int32_t size) {
    char* path = teavm_char16ToMb(name, size);
    char* colon = strrchr(path, ':');
    if (colon != NULL) {
        char* device = colon;
        while (device > path) {
            char c = device[-1];
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                --device;
            } else {
                break;
            }
        }
        if (device != path) {
            memmove(path, device, strlen(device) + 1);
        }
    }
    return path;
}

"""
        text = text.replace(old_guard,
                            "#if TEAVM_UNIX || defined(TEAVM_VITA)\n#include <unistd.h>", 1)
        marker = "int32_t teavm_file_homeDirectory(char16_t** result) {"
        if marker not in text:
            raise SystemExit("file.c: home-directory function anchor not found")
        text = text.replace(marker, helper + marker, 1)
        text = text.replace("teavm_char16ToMb(name, nameSize)",
                            "vita_file_mbpath(name, nameSize)")
        file_c.write_text(text)
        changed = True
    else:
        changed = False
    text = file_c.read_text()
    old_destination = "teavm_char16ToMb(newName, newNameSize)"
    if old_destination in text:
        text = text.replace(old_destination, "vita_file_mbpath(newName, newNameSize)")
        file_c.write_text(text)
        changed = True
    print(f"[vita-patch] file.c: {'patched' if changed else 'already patched'}")

    patch_file(root, "file.c", [
        ("    return utime(mbName, &newTime) == 0;",
         "    int result = utime(mbName, &newTime);\n    free(mbName);\n    return result == 0;", 1),
        ("    FILE* file = fopen(mbName, modeString);\n    if (file == NULL)",
         "    FILE* file = fopen(mbName, modeString);\n    free(mbName);\n    if (file == NULL)", 1),
    ])

    patch_file(root, "classes/org/teavm/runtime/ExceptionHandling.c", [
        ('#include "ExceptionHandling.h"',
         '#include "ExceptionHandling.h"\n#include <stdio.h>', 1),
        ("void teavm_throwArrayIndexOutOfBoundsException() {",
         """void teavm_throwArrayIndexOutOfBoundsException() {
#if defined(TEAVM_VITA)
    fprintf(stderr, "[bounds] caller=%p\\n", __builtin_return_address(0));
    fflush(stderr);
#endif""", 1),
    ])


if __name__ == "__main__":
    main()
