import { TurboModuleRegistry, type TurboModule } from 'react-native';

/**
 * Platform PDF capabilities reported by the native module. Use it to decide,
 * at runtime, whether features such as text extraction or search are available
 * on the current device/OS version.
 */
export interface PdfCapabilities {
  supportsMetadata: boolean;
  supportsPageText: boolean;
  supportsSearch: boolean;
  supportsLinks: boolean;
  supportsForms: boolean;
  supportsAnnotations: boolean;
}

export interface PdfMetadata {
  title?: string;
  author?: string;
  subject?: string;
  creator?: string;
  producer?: string;
  keywords?: string[];
  creationDate?: string;
  modificationDate?: string;
  pageCount: number;
  isEncrypted?: boolean;
  isLocked?: boolean;
}

export interface PdfPageInfo {
  pageIndex: number;
  width: number;
  height: number;
  rotation: number;
  label?: string;
  annotationCount?: number;
}

export interface PdfRenderOptions {
  format?: string;
  quality?: number;
  width?: number;
  height?: number;
  scale?: number;
  backgroundColor?: string;
  maxPixels?: number;
}

export interface PdfRenderedPage {
  uri: string;
  width: number;
  height: number;
  scale: number;
  pageIndex: number;
}

export interface PdfSearchOptions {
  pageIndex?: number;
  caseSensitive?: boolean;
  maxResults?: number;
  focus?: boolean;
  highlight?: boolean;
  resultIndex?: number;
}

export interface PdfSearchBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface PdfSearchResult {
  pageIndex: number;
  text?: string;
  bounds: PdfSearchBounds[];
}

export interface PdfOpenDocumentResult {
  documentId: string;
  pageCount: number;
  sourceUri: string;
  capabilities: PdfCapabilities;
}

export interface PdfPreparedSource {
  uri: string;
  fromCache: boolean;
}

export interface Spec extends TurboModule {
  getCapabilities(): PdfCapabilities;
  /**
   * Downloads a remote (`http`/`https`) PDF into the native cache and returns
   * the resulting local file URI. Local URIs are returned untouched.
   *
   * @param headersJson JSON-encoded `Record<string, string>` of request
   *   headers (empty object when none). Passing headers as a JSON string keeps
   *   the codegen spec free of dynamic map types.
   * @param fileName Name of the cached file (sanitized natively, always ending
   *   in `.pdf`). Empty string derives it from the URI's last path segment. It
   *   only names the file; it is never part of the cache identity.
   * @param cacheKey Opaque, stable cache identity. Empty string makes the full
   *   URI (query string included) the identity.
   */
  prepareSourceAsync(
    uri: string,
    headersJson: string,
    fileName: string,
    cacheKey: string
  ): Promise<PdfPreparedSource>;
  openDocumentAsync(uri: string): Promise<PdfOpenDocumentResult>;
  closeDocumentAsync(documentId: string): Promise<void>;
  closeAllDocumentsAsync(): Promise<void>;
  getMetadataAsync(documentId: string): Promise<PdfMetadata>;
  getPageInfoAsync(documentId: string, pageIndex: number): Promise<PdfPageInfo>;
  /** `optionsJson` is a JSON-encoded {@link PdfRenderOptions} (`{}` when none). */
  renderPageAsync(
    documentId: string,
    pageIndex: number,
    optionsJson: string
  ): Promise<PdfRenderedPage>;
  /** `pageIndex` < 0 extracts text from the whole document. */
  getTextAsync(documentId: string, pageIndex: number): Promise<string | null>;
  /** `optionsJson` is a JSON-encoded {@link PdfSearchOptions} (`{}` when none). */
  searchTextAsync(
    documentId: string,
    query: string,
    optionsJson: string
  ): Promise<PdfSearchResult[]>;
  clearPdfCacheAsync(): Promise<void>;
}

export default TurboModuleRegistry.get<Spec>('RNPdfApi');
