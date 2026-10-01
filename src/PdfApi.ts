import { Platform } from 'react-native';

import NativePdfApiModule, { type Spec } from './NativePdfApi';

import type {
  INativeOpenDocumentResult,
  IPdfDocument,
  IPdfRenderOptions,
  IPdfSearchOptions,
  IPreparedPdfSource,
  TypePdfSource,
} from './types';

const LINKING_ERROR =
  `The package '@meedwire/react-native-pdf-api' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const unsupportedCapabilities = {
  supportsMetadata: false,
  supportsPageText: false,
  supportsSearch: false,
  supportsLinks: false,
  supportsForms: false,
  supportsAnnotations: false,
};

const webModule: Spec = {
  getCapabilities() {
    return unsupportedCapabilities;
  },
  async prepareSourceAsync(uri: string) {
    return { uri, fromCache: false };
  },
  async openDocumentAsync() {
    throw new Error('PdfApi is not available on web.');
  },
  async closeDocumentAsync() {},
  async closeAllDocumentsAsync() {},
  async getMetadataAsync() {
    throw new Error('PdfApi is not available on web.');
  },
  async getPageInfoAsync() {
    throw new Error('PdfApi is not available on web.');
  },
  async renderPageAsync() {
    throw new Error('PdfApi is not available on web.');
  },
  async getTextAsync() {
    return null;
  },
  async searchTextAsync() {
    return [];
  },
  async clearPdfCacheAsync() {},
};

const NativePdfApi: Spec =
  Platform.OS === 'web'
    ? webModule
    : (NativePdfApiModule ??
      new Proxy({} as Spec, {
        get() {
          return () => {
            throw new Error(LINKING_ERROR);
          };
        },
      }));

export { NativePdfApi };

export function normalizePdfSource(source: TypePdfSource) {
  return typeof source === 'string' ? { uri: source } : source;
}

export function isRemotePdfUri(uri: string) {
  return /^https?:\/\//i.test(uri);
}

/**
 * Resolves a PDF source to a local file URI. Remote (`http`/`https`) sources
 * are downloaded into the native cache; local sources are returned as-is.
 *
 * The cached copy is identified by `cacheKey` (or, without one, by the full
 * URI) and named after `fileName` (or the URI's last path segment).
 */
export async function preparePdfSourceAsync(
  source: TypePdfSource
): Promise<IPreparedPdfSource> {
  const normalizedSource = normalizePdfSource(source);

  if (!isRemotePdfUri(normalizedSource.uri)) {
    return { uri: normalizedSource.uri, fromCache: false };
  }

  return NativePdfApi.prepareSourceAsync(
    normalizedSource.uri,
    JSON.stringify(normalizedSource.headers ?? {}),
    normalizedSource.fileName ?? '',
    normalizedSource.cacheKey ?? ''
  );
}

class PdfDocument implements IPdfDocument {
  readonly documentId: string;
  readonly pageCount: number;
  readonly sourceUri: string;
  readonly capabilities: INativeOpenDocumentResult['capabilities'];
  private closed = false;

  constructor(result: INativeOpenDocumentResult) {
    this.documentId = result.documentId;
    this.pageCount = result.pageCount;
    this.sourceUri = result.sourceUri;
    this.capabilities = result.capabilities;
  }

  async getMetadataAsync() {
    return NativePdfApi.getMetadataAsync(this.documentId);
  }

  async getPageInfoAsync(pageIndex: number) {
    return NativePdfApi.getPageInfoAsync(this.documentId, pageIndex);
  }

  async renderPageAsync(pageIndex: number, options?: IPdfRenderOptions) {
    return NativePdfApi.renderPageAsync(
      this.documentId,
      pageIndex,
      JSON.stringify(options ?? {})
    );
  }

  async getThumbnailAsync(pageIndex: number, options?: IPdfRenderOptions) {
    return this.renderPageAsync(pageIndex, {
      maxPixels: 1_048_576,
      scale: 0.4,
      ...options,
    });
  }

  async getTextAsync(pageIndex?: number) {
    return NativePdfApi.getTextAsync(this.documentId, pageIndex ?? -1);
  }

  async searchTextAsync(query: string, options?: IPdfSearchOptions) {
    return NativePdfApi.searchTextAsync(
      this.documentId,
      query,
      JSON.stringify(options ?? {})
    );
  }

  async closeAsync() {
    if (this.closed) return;

    this.closed = true;
    await NativePdfApi.closeDocumentAsync(this.documentId);
  }
}

export async function openDocumentAsync(
  source: TypePdfSource
): Promise<IPdfDocument> {
  const preparedSource = await preparePdfSourceAsync(source);
  const result = await NativePdfApi.openDocumentAsync(preparedSource.uri);

  return new PdfDocument(result);
}

export async function clearPdfCacheAsync() {
  await NativePdfApi.closeAllDocumentsAsync();
  await NativePdfApi.clearPdfCacheAsync();
}
