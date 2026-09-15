# Getting started

To setup the development environment, you need either Windows or Linux, and Java 25+ installed.

Run `./gradlew setupHytaleDev` to get the decompiled classes and Assets cached.

Run `./gradlew runServer` to launch, with `run/` as the server directory.

Running with `-Ddebug=true` opens the debugger on port 5005; add `-Dhotswap=true` for hot swap.

Run `./gradlew hytaleDoctor` to diagnose project, asset, and dependency problems.
