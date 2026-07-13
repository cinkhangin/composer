// Serve index.html for any route so deep links like /edit work on reload.
config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;
