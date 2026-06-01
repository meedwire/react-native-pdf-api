import { Directory, File, Paths } from 'expo-file-system';

import NativePdfApi from './NativePdfApi';

import type {
  INativeOpenDocumentResult,
  IPdfDocument,
  IPdfRenderOptions,
  IPdfSearchOptions,
  IPreparedPdfSource,
  TypePdfSource,
} from './types';

const PDF_CACHE_DIRECTORY = 'react-native-pdf-api';

export function normalizePdfSource(source: TypePdfSource) {
  return typeof source === 'string' ? { uri: source } : source;
}

export function isRemotePdfUri(uri: string) {
  return /^https?:\/\//i.test(uri);
}

function getCacheDirectory() {
  const directory = new Directory(Paths.cache, PDF_CACHE_DIRECTORY);
  directory.create({ idempotent: true, intermediates: true });
  return directory;
}

function getSafeFileName(source: ReturnType<typeof normalizePdfSource>) {
  const explicitName = source.fileName ?? source.cacheKey;

  if (explicitName) {
    return explicitName.replace(/[^a-zA-Z0-9._-]/g, '_');
  }

  const fileName = source.uri.split('/').pop()?.split('?')[0] ?? 'document.pdf';
  const decodedFileName = (() => {
    try {
      return decodeURIComponent(fileName);
    } catch {
      return fileName;
    }
  })();
  const normalized = decodedFileName.replace(/[^a-zA-Z0-9._-]/g, '_');

  return normalized.toLowerCase().endsWith('.pdf')
    ? normalized
    : `${normalized}.pdf`;
}

export async function preparePdfSourceAsync(
  source: TypePdfSource,
): Promise<IPreparedPdfSource> {
  const normalizedSource = normalizePdfSource(source);

  if (!isRemotePdfUri(normalizedSource.uri)) {
    return { uri: normalizedSource.uri, fromCache: false };
  }

  const cacheDirectory = getCacheDirectory();
  const file = new File(cacheDirectory, getSafeFileName(normalizedSource));
  const fromCache = file.exists;

  if (!fromCache) {
    await File.downloadFileAsync(normalizedSource.uri, file, {
      headers: normalizedSource.headers,
      idempotent: true,
    });
  }

  return { uri: file.uri, fromCache };
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
    return NativePdfApi.renderPageAsync(this.documentId, pageIndex, options);
  }

  async getThumbnailAsync(pageIndex: number, options?: IPdfRenderOptions) {
    return this.renderPageAsync(pageIndex, {
      maxPixels: 1_048_576,
      scale: 0.4,
      ...options,
    });
  }

  async getTextAsync(pageIndex?: number) {
    return NativePdfApi.getTextAsync(this.documentId, pageIndex);
  }

  async searchTextAsync(query: string, options?: IPdfSearchOptions) {
    return NativePdfApi.searchTextAsync(this.documentId, query, options);
  }

  async closeAsync() {
    if (this.closed) return;

    this.closed = true;
    await NativePdfApi.closeDocumentAsync(this.documentId);
  }
}

export async function openDocumentAsync(
  source: TypePdfSource,
): Promise<IPdfDocument> {
  const preparedSource = await preparePdfSourceAsync(source);
  const result = await NativePdfApi.openDocumentAsync(preparedSource.uri);

  return new PdfDocument(result);
}

export async function clearPdfCacheAsync() {
  await NativePdfApi.closeAllDocumentsAsync();
  await NativePdfApi.clearPdfCacheAsync();
}
