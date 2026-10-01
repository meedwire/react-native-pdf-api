# @meedwire/react-native-pdf-api

[![npm version](https://img.shields.io/npm/v/@meedwire/react-native-pdf-api.svg)](https://www.npmjs.com/package/@meedwire/react-native-pdf-api)
[![license](https://img.shields.io/npm/l/@meedwire/react-native-pdf-api.svg)](./LICENSE)
[![New Architecture](https://img.shields.io/badge/React%20Native-New%20Architecture-blue.svg)](https://reactnative.dev/architecture/landing-page)

Native **PDF rendering, text extraction and search** for React Native (iOS &
Android), built on **TurboModules + Fabric**. Ships a native `PdfView`
component for displaying documents plus an imperative API for metadata, page
rendering, thumbnails, text extraction and search.

- 📄 Native viewer with vertical scroll, pinch‑zoom and initial page
- 🔎 Text search with page coordinates to highlight / focus results
- 🖼️ Render any page to a PNG/JPEG file (full size or thumbnail)
- 📝 Extract text per page or for the whole document
- 🌐 Built‑in native download + cache for remote (`http`/`https`) PDFs — **no
  extra file‑system dependency required**
- ⚡ New Architecture only (TurboModule + Fabric), iOS in Swift, Android in
  Kotlin — no hand‑written C++

## Platform support

| Feature           | iOS                  | Android                         | Web                 |
| ----------------- | -------------------- | ------------------------------- | ------------------- |
| Viewer (`PdfView`)| ✅ PDFKit            | ✅ `PdfRenderer`                | ⛔ falls back to View |
| Render page       | ✅                   | ✅                              | ⛔                  |
| Extract text      | ✅                   | ✅ Android 15 / API 35+         | returns `null`      |
| Search text       | ✅                   | ✅ Android 15 / API 35+         | returns `[]`        |
| Metadata          | ✅                   | Page count only                 | ⛔                  |
| Password‑protected| ⛔ (planned)         | ⛔ (planned)                    | ⛔                  |

Use `document.capabilities` (from `openDocumentAsync()`) to check, at runtime,
whether text/search are available on the current platform/OS.

## Requirements

- React Native **0.79+** with the **New Architecture enabled**
  (`newArchEnabled=true`). This package does not support the legacy
  architecture (Paper).
- iOS 15+ with PDFKit. Android `minSdkVersion 24` (text/search require API 35+).

## Installation

```sh
yarn add @meedwire/react-native-pdf-api
# or
npm install @meedwire/react-native-pdf-api
```

iOS:

```sh
cd ios && pod install
```

Then rebuild the native app (`yarn ios` / `yarn android`). This package contains
native code, so it does **not** work in Expo Go — use a development build
(`npx expo prebuild` + `npx expo run:ios|android`).

## Quick start — `PdfView`

```tsx
import { useRef } from 'react';
import { Button, StyleSheet, View } from 'react-native';
import { PdfView, type IPdfViewRef } from '@meedwire/react-native-pdf-api';

export function PdfScreen() {
  const pdfRef = useRef<IPdfViewRef>(null);

  return (
    <View style={styles.container}>
      <PdfView
        ref={pdfRef}
        source="https://example.com/document.pdf"
        style={styles.pdf}
        initialPage={0}
        maxZoom={5}
        pageSpacing={16}
        backgroundColor="#f4f4f1"
        onLoad={({ nativeEvent }) => console.log('pages', nativeEvent.pageCount)}
        onPageChange={({ nativeEvent }) => console.log('page', nativeEvent.currentPage)}
        onError={({ nativeEvent }) => console.warn(nativeEvent.code, nativeEvent.message)}
      />
      <Button
        title="Find"
        onPress={() => pdfRef.current?.searchTextAsync('contract', { focus: true })}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  pdf: { flex: 1 },
});
```

> `currentPage`, `initialPage`, `pageIndex` and `resultIndex` are **zero‑based**.

### Sources

```ts
type TypePdfSource =
  | string
  | { uri: string; headers?: Record<string, string>; cacheKey?: string; fileName?: string };
```

`http`/`https` sources are downloaded to the native cache before rendering;
`headers` are sent with that request. Local `file://`, absolute paths and (on
Android) `content://` URIs are passed straight through.

- `cacheKey` — opaque, stable identity of the document in the cache. Without
  it, the identity is the **full URI, query string included**. Use it for URLs
  that change on every fetch (e.g. pre‑signed S3 URLs), and change it when the
  document changes (e.g. `report-42-v3`).
- `fileName` — only names the cached file (e.g. what a share sheet shows). It
  never decides which document is served. See [Cache](#cache).

```ts
// A pre-signed URL whose query changes on every fetch, shared as "laudo.pdf".
const { uri } = await preparePdfSourceAsync({
  uri: presignedUrl,
  cacheKey: `exam-${exam.id}-v${exam.version}`,
  fileName: 'laudo.pdf',
});
```

## Imperative API

```ts
import { openDocumentAsync, clearPdfCacheAsync } from '@meedwire/react-native-pdf-api';

const document = await openDocumentAsync({
  uri: 'https://example.com/document.pdf',
  headers: { Authorization: `Bearer ${token}` },
  cacheKey: 'document-v1',
});

try {
  const metadata = await document.getMetadataAsync();
  const page0 = await document.getPageInfoAsync(0);
  const rendered = await document.renderPageAsync(0, { format: 'png', width: 1200 });
  const thumb = await document.getThumbnailAsync(0);
  const text = await document.getTextAsync();
  const matches = await document.searchTextAsync('client', { maxResults: 10 });
} finally {
  await document.closeAsync();
}

await clearPdfCacheAsync();
```

## API reference

### `PdfView` props

| Prop                  | Type             | Default     | Description                                   |
| --------------------- | ---------------- | ----------- | --------------------------------------------- |
| `source`              | `TypePdfSource`  | required    | Local or remote PDF.                          |
| `style`               | `ViewStyle`      | –           | Give it real dimensions (e.g. `flex: 1`).     |
| `backgroundColor`     | `string`         | `#f4f4f1`   | Viewer background.                            |
| `initialPage`         | `number`         | `0`         | Zero‑based initial page.                      |
| `pageSpacing`         | `number`         | `16`        | Spacing between pages.                         |
| `maxZoom`             | `number`         | `5`         | Maximum zoom factor.                          |
| `maxPageResolution`   | `number`         | `2048`      | Android: largest rendered bitmap side.        |
| `singlePage`          | `boolean`        | `false`     | Render only the initial page.                 |
| `onLoad`              | `(e) => void`    | –           | `{ documentId, pageCount, sourceUri, capabilities }` |
| `onPageChange`        | `(e) => void`    | –           | `{ currentPage, pageCount }`                  |
| `onError`             | `(e) => void`    | –           | `{ code, message }`                           |

> Handlers can be inline arrow functions — the component keeps the latest
> callbacks in refs, so passing new identities each render does not reload the
> document. `onPageChange` fires when the current page changes during scroll.

> `onError` only reports errors for the **current** `source`: a download or
> preparation that fails after the component unmounted, or after `source`
> changed, is never reported.

### `PdfView` ref (`IPdfViewRef`)

- `openDocumentAsync()` → `IPdfDocument`
- `getTextAsync(pageIndex?)` → `string | null`
- `searchTextAsync(query, options?)` → `IPdfSearchResult[]` (also highlights/focuses)
- `clearSearchAsync()`
- `closeDocumentAsync()`

### `IPdfDocument`

`documentId`, `pageCount`, `sourceUri`, `capabilities`, plus
`getMetadataAsync()`, `getPageInfoAsync(pageIndex)`,
`renderPageAsync(pageIndex, options?)`, `getThumbnailAsync(pageIndex, options?)`,
`getTextAsync(pageIndex?)`, `searchTextAsync(query, options?)`, `closeAsync()`.

### Render options

```ts
type IPdfRenderOptions = {
  format?: 'png' | 'jpeg'; // default png
  quality?: number;        // 0..1, default 0.9
  width?: number;          // keeps aspect ratio if only one is set
  height?: number;
  scale?: number;          // used when width/height absent
  backgroundColor?: string;
  maxPixels?: number;      // default 16_777_216, else ERR_PDF_RENDER_TOO_LARGE
};
```

### Error codes (`onError` / rejected promises)

`ERR_PDF_SOURCE`, `ERR_PDF_OPEN`, `ERR_PDF_LOCKED`, `ERR_PDF_PAGE_OUT_OF_BOUNDS`,
`ERR_PDF_PAGE`, `ERR_PDF_DOCUMENT_NOT_FOUND`, `ERR_PDF_RENDER_TOO_LARGE`,
`ERR_PDF_RENDER`, `ERR_PDF_TEXT_UNSUPPORTED`, `ERR_PDF_SEARCH_UNSUPPORTED`.

## Cache

Remote (`http`/`https`) sources are downloaded once into the app's cache
directory and reused afterwards. `preparePdfSourceAsync()` returns the local
file (`{ uri, fromCache }`), which is also what you pass to a share sheet.

**Identity.** A cached document is identified by `cacheKey` when it is set,
otherwise by the full `uri` (query string included). `fileName` is never part
of the identity: two different documents with the same file name never
overwrite or replace each other.

**On‑disk layout** (iOS `Caches/`, Android `Context.cacheDir`):

```
react-native-pdf-api/
  <sha256(identity)>/<file name>.pdf   one directory per document
  renders/                             renderPageAsync / getThumbnailAsync output
  tmp/                                 Android: private copies of content:// sources
```

The file name is `fileName`, or the URI's last path segment (percent‑decoded,
query ignored), restricted to `[A-Za-z0-9._-]` (other characters become `_`),
at most 100 characters before the extension, `document` when nothing usable is
left, and always ending in `.pdf`. Asking for the same document under another
`fileName` reuses the download (hard link, or a copy where links are not
allowed).

**Integrity.** A download is kept only if the response is HTTP 2xx, the body is
not empty, `%PDF-` appears within its first 1024 bytes (an HTML error or login
page served with `200` is rejected) and, on Android, the body matches
`Content-Length`. A download fails if connecting or receiving data stalls for
30 s (an idle timeout, not a limit on the whole transfer). Files are written
atomically, so a failed or interrupted download never leaves a partial
document in the cache. All these failures reject with `ERR_PDF_SOURCE`.

**Sweep.** Once per app process, the first remote source prepared (by
`preparePdfSourceAsync()`, `openDocumentAsync()` or `PdfView`; on Android also
the first `content://` source opened) starts a background cleanup that never
delays or fails that call:

- files left by 0.1.x directly under `react-native-pdf-api/` are deleted;
- temporary leftovers (interrupted downloads, orphaned `content://` copies)
  older than 1 hour are deleted;
- documents not used for 7 days, and rendered pages older than 7 days, are
  deleted;
- if the documents still take more than 200 MB, the least recently used ones
  are deleted until they fit.

Documents used by the running process are never swept. `clearPdfCacheAsync()`
deletes the whole cache directory immediately.

## Migrating from 0.1.x

- **`cacheKey` is now an opaque identity**, no longer a file name. Drop any
  `.pdf` suffix you added (`'document-v1.pdf'` → `'document-v1'`) and pass
  `fileName` if you need a specific name for the cached file.
- Without `cacheKey`, the identity is the full URI including its query, so a
  URL whose query changes on every request is downloaded again each time — set
  a `cacheKey` for those.
- Cached files from 0.1.x (stored flat under `react-native-pdf-api/`) are
  deleted the first time the new version prepares a source; they are
  downloaded again on demand.
- Non‑PDF responses (HTML error pages, non‑2xx statuses, truncated bodies) now
  reject with `ERR_PDF_SOURCE` instead of being cached.
- The native module spec changed (`prepareSourceAsync` takes a `cacheKey`
  argument): rebuild the native app — `pod install` on iOS and a fresh Gradle
  build on Android. Mixing the new JS with an old native binary does not work.

## Screenshots

The example app (`example/`) demonstrates the viewer, search highlight/focus and
page navigation. To capture screenshots, run it on a simulator/emulator and save
the images under [`docs/screenshots/`](./docs/screenshots) — see the
[capture guide](./docs/screenshots/README.md).

```sh
# iOS
cd example/ios && pod install && cd -
npx react-native run-ios --simulator "iPhone 16"

# Android (with an emulator running)
npx react-native run-android
```

## Contributing

This is a [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
project.

```sh
yarn            # install
yarn typecheck  # TypeScript
yarn lint       # ESLint + Prettier
yarn test       # Jest
yarn prepare    # build with react-native-builder-bob
```

The example app under `example/` is the development harness. After changing
native code, rebuild it (`pod install` for iOS, a fresh Gradle build for
Android).

## License

MIT © Meedwire
