// Allow importing bundled .pdf files as assets (see metro.config.js assetExts).
declare module '*.pdf' {
  const asset: number;
  export default asset;
}
