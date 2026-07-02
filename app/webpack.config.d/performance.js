// Kotlin/Wasm bundles are inherently multi-MB (the Skiko runtime alone is ~8 MiB);
// webpack's 244 KiB asset-size hints are pure noise here.
config.performance = config.performance || {};
config.performance.hints = false;
