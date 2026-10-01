import type { StyleProp, ViewStyle } from 'react-native';

import type {
  PdfCapabilities,
  PdfMetadata,
  PdfOpenDocumentResult,
  PdfPageInfo,
  PdfRenderedPage,
  PdfRenderOptions,
  PdfSearchBounds,
  PdfSearchOptions,
  PdfSearchResult,
} from './NativePdfApi';

/** A PDF source: a URI string, or an object with optional auth/cache control. */
export type TypePdfSource =
  | string
  | {
      /**
       * Remote (`http`/`https`) or local (`file://`, absolute path, Android
       * `content://`) URI.
       */
      uri: string;
      /** Headers sent with the download of a remote `uri`. */
      headers?: Record<string, string>;
      /**
       * Opaque, stable identity of a remote document in the native cache.
       * Without it the identity is the full `uri`, query string included, so a
       * URL that changes on every fetch (e.g. a pre-signed S3 URL) is
       * downloaded again each time. Pass a key that stays the same for the
       * same document and changes when the document does (e.g.
       * `report-42-v3`). It is never used as a file name.
       */
      cacheKey?: string;
      /**
       * Name of the cached file, e.g. what a share sheet shows. It is
       * sanitized (`[A-Za-z0-9._-]`) and always ends in `.pdf`; it defaults to
       * the URI's last path segment. It never affects which document is
       * served from the cache.
       */
      fileName?: string;
    };

export interface PdfPageChangeEvent {
  currentPage: number;
  pageCount: number;
}

export interface PdfErrorEvent {
  code: string;
  message: string;
}

export interface PdfViewerSearchHighlight {
  requestId: number;
  pageIndex?: number;
  bounds?: PdfSearchBounds[];
  focusBounds?: PdfSearchBounds[];
}

export type IPdfCapabilities = PdfCapabilities;
export type IPdfMetadata = PdfMetadata;
export type IPdfPageInfo = PdfPageInfo;
export type IPdfRenderOptions = Omit<PdfRenderOptions, 'format'> & {
  format?: 'png' | 'jpeg';
};
export type IPdfRenderedPage = PdfRenderedPage;
export type IPdfSearchOptions = PdfSearchOptions;
export type IPdfSearchBounds = PdfSearchBounds;
export type IPdfSearchResult = PdfSearchResult;
export type IPdfViewerSearchHighlight = PdfViewerSearchHighlight;
export type INativeOpenDocumentResult = PdfOpenDocumentResult;
export type IPdfPageChangeEvent = PdfPageChangeEvent;
export type IPdfErrorEvent = PdfErrorEvent;

export interface IPreparedPdfSource {
  uri: string;
  fromCache: boolean;
}

export interface IPdfDocument {
  readonly documentId: string;
  readonly pageCount: number;
  readonly sourceUri: string;
  readonly capabilities: IPdfCapabilities;
  getMetadataAsync(): Promise<IPdfMetadata>;
  getPageInfoAsync(pageIndex: number): Promise<IPdfPageInfo>;
  renderPageAsync(
    pageIndex: number,
    options?: IPdfRenderOptions
  ): Promise<IPdfRenderedPage>;
  getThumbnailAsync(
    pageIndex: number,
    options?: IPdfRenderOptions
  ): Promise<IPdfRenderedPage>;
  getTextAsync(pageIndex?: number): Promise<string | null>;
  searchTextAsync(
    query: string,
    options?: IPdfSearchOptions
  ): Promise<IPdfSearchResult[]>;
  closeAsync(): Promise<void>;
}

export interface IPdfViewRef {
  openDocumentAsync(): Promise<IPdfDocument>;
  getTextAsync(pageIndex?: number): Promise<string | null>;
  searchTextAsync(
    query: string,
    options?: IPdfSearchOptions
  ): Promise<IPdfSearchResult[]>;
  clearSearchAsync(): void;
  closeDocumentAsync(): Promise<void>;
}

export interface IPdfViewProps {
  source: TypePdfSource;
  backgroundColor?: string;
  initialPage?: number;
  pageSpacing?: number;
  maxZoom?: number;
  maxPageResolution?: number;
  singlePage?: boolean;
  style?: StyleProp<ViewStyle>;
  onLoad?: (event: { nativeEvent: INativeOpenDocumentResult }) => void;
  onPageChange?: (event: { nativeEvent: IPdfPageChangeEvent }) => void;
  onError?: (event: { nativeEvent: IPdfErrorEvent }) => void;
}
