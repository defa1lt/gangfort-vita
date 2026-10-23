Source: https://github.com/mmrmagno/stspsvita
Commit: 692473a36947260af002eaa74dd922becf7bf158
License: MIT, included in LICENSE.

The Java LibGDX backend and native bridge are reused for Gangfort. The original
Slay the Spire launcher and game-specific stubs are excluded from compilation.
Gangfort's APK uses LibGDX 1.9.3: ApplicationLogger methods were removed and
ReflectionPool.newObject was made public to match the converted APK's API.
Writable files use ux0:data/gangfort/.
