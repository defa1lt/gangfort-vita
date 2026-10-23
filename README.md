# Gangfort for PS Vita

Experimental offline port of Gangfort 2.1. The original Java game is converted
from both APK DEX files, compiled to C by TeaVM, and linked with VitaSDK. No JVM
runs on the Vita. The original game code and assets are local build inputs and
are not distributed in this repository.

## Build

Required: JDK 17, Python 3 with Pillow, CMake 3.20+, VitaSDK with
vitaGL/OpenAL/Vorbis, and dex2jar 2.4. `tools/inputs.lock.json` pins the APK, DEX and converter hashes;
Gradle Wrapper pins Gradle 8.13 and verifies its distribution hash. TeaVM is
0.15.0; the APK contains libGDX 1.9.3. Third-party source attribution is in each
`third_party/` component's `UPSTREAM.md` and license/source headers.

Supply your own Gangfort **2.1** APK matching the SHA-256 in
`tools/inputs.lock.json`:

```text
59ac5b53be975827e85edea28ccdb69771dfd3fb2a290c46156edea3c940114e
```

Set `JAVA_HOME` to your JDK 17 installation. Pass the paths to your APK,
dex2jar 2.4 and VitaSDK explicitly on each Gradle invocation:

```sh
./gradlew -Papk=/path/game.apk \
  -PdexTools=/path/dex-tools-v2.4 -PvitaSdk=/path/vitasdk \
  verifyBackend verifyRuntime vitaEboot
```

This verifies the APK and both DEX conversions, checks saves/JSON/input/physics,
and runs the original game profiler and screen ownership checks through TeaVM C on the host,
transpiles reachable game code, applies Vita runtime patches, configures CMake
for the current checkout, and builds `build/gangfort-native/eboot.bin`. Relocated
CMake caches are preserved under `build/archive/`. Generated outputs, extracted
assets, caches and the SDK remain ignored.

After a C-only change, use:

```sh
cmake --build build/gangfort-native --target eboot.bin-self --parallel 4
```

CMake rejects generated C whose source fingerprint is stale. A failed or
interrupted transpilation cannot certify old output; changing sources during
transpilation also fails validation. Gradle skips unchanged Java generation.
If generated files were manually changed or removed, restore them with
`./gradlew transpile --rerun-tasks` before rebuilding the executable.

`./gradlew vitaVpk` (with the same path properties) builds the full installation
package. Initial installation needs the full VPK; later executable updates can
use `eboot.bin`. The generated VPK bundles original game code and assets from
your APK and is for your own local installation. It is not provided as a public
release asset in this repository.

## Installation and device update

Title ID is `GANGF0001`. Close Gangfort first. VitaShell FTP uses port 1337;
VitaCompanion's command port 1338 is not FTP. Read `sce_sys/param.sfo` to verify
the target before writing. Back up the installed executable, upload only the
new executable or specific changed assets, then download the uploaded file and
compare its byte count and SHA-256. Keep private IP addresses in ignored local
configuration, never in tracked files.

The port appends diagnostics to `ux0:data/gangfort/gangfort.log`, including its
build ID. Preserve logs and crash dumps. Preferences live under
`ux0:data/gangfort/preferences/`, in two checksummed snapshots per namespace;
an interrupted replacement retains the previous valid snapshot. Never replace
saves as part of a code update.

## Controls and scope

Left stick/D-pad moves; right stick aims; L/up jumps; R fires; Square/Cross/Circle
activate the corresponding class actions; Start/Triangle opens the game menu.
The front screen supports multiple simultaneous touches with stable pointer IDs.
D-pad menu navigation advances once per press. The instruction atlas uses the
original PS Vita illustration in `assets/res/textures/vita_controls.png`,
prepared through `prepareAssets` without modifying the APK or other atlas sprites.
The instruction is displayed once per saved help version; existing settings
are preserved. Orange leader lines connect the Vita controls to their action labels. Square
builds/destroys the Builder's blaster, activates Dealer's rapidrush, or toggles
Agent's cloak. Cross selects Dealer's healing target or performs Agent's stab;
Circle toggles Agent's disguise. In menus, D-pad navigates and Cross selects.
Screen replacement requested during a rendered frame waits until that frame
finishes, so leaving a match cannot dispose its world during the remaining update.
Select saves the displayed frame to `ux0:data/gangfort/screenshot.png` for device
diagnostics. Each press replaces that screenshot; download it before the next capture.

Offline bot gameplay uses the original fixed-step game controller and the
imported Java Box2D implementation. Online multiplayer is explicitly unavailable:
the original KryoNet NIO/reflection transport is not implemented by this TeaVM
Vita runtime. The server browser displays that limitation and allows returning
to the main menu. Advertising, purchases and social services do not grant rewards
or pretend that authentication succeeded.

This is an experimental port with offline bot play as its intended scope.
Hardware compatibility and gameplay must be checked on the target Vita for each
release candidate. A successful build or FTP transfer alone is not proof of gameplay.

## Build evidence and checks

`./gradlew verifyRuntime` uses the same compiler and classlib as the game. It
checks the original paired profiler calls, the exact current-thread caller
frames, an unstarted thread, capture after fiber yield/sleep, and the original
screen history's single disposal during replace/push/pop transitions. This requires
a host C compiler (`cc`). A JVM-only check cannot detect the classlib's empty
`Thread.getStackTrace()` implementation. Foreign-thread capture remains unavailable.

`python3 tools/test_build_fingerprint.py` checks corrupt generated output,
mid-generation source changes and timestamp recovery after an interrupted build.
Run one build at a time in this checkout. The generated tree is published only
after successful transpilation; unchanged generated files retain their timestamps.

`tools/toolchain.lock.json` records the tested VitaSDK packages. Configuration
rejects different versions of the required packages and preserves an old cache when the SDK changes.
Pillow is required to prepare the 128×128 indexed launcher icon from the APK;
LiveArea artwork is fitted to the Vita's fixed canvas sizes and converted to
128-color indexed PNG, following [VitaSDK samples](https://github.com/vitasdk/samples#notes-on-images).
Configuration rejects
malformed LiveArea XML and references to missing image files.

After building a release candidate, run `python3 tools/record_release.py`.
This writes `build/releases/<build-id>-<executable-hash>/` with the executable,
matching ELF and manifest. Use that same executable for the verified FTP update.
