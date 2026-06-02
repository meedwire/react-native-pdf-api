const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');
const { withMetroConfig } = require('react-native-monorepo-config');

const root = path.resolve(__dirname, '..');

const config = withMetroConfig(getDefaultConfig(__dirname), {
  root,
  dirname: __dirname,
});

// The library is the monorepo *root* package (not a workspace member), so make
// sure Metro watches it and resolves `@meedwire/react-native-pdf-api` to the
// root — where the `react-native` field points Metro at `src/index.ts`.
config.watchFolders = Array.from(
  new Set([...(config.watchFolders || []), root])
);
config.resolver = config.resolver || {};
config.resolver.extraNodeModules = {
  ...(config.resolver.extraNodeModules || {}),
  '@meedwire/react-native-pdf-api': root,
};
// Bundle the sample .pdf files in example/assets as assets.
config.resolver.assetExts = Array.from(
  new Set([...(config.resolver.assetExts || []), 'pdf'])
);

module.exports = config;
