// Watch by polling instead of FS events. kqueue file-descriptor watching hits
// "EMFILE: too many open files" on macOS with the Kotlin/Wasm build tree, which
// silently breaks hot reload — the dev server keeps serving a stale bundle while
// Gradle happily recompiles. Polling is slightly slower but always works.
config.watchOptions = { poll: 600, aggregateTimeout: 200 };
