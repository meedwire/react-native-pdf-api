import type { StyleProp, ViewStyle } from 'react-native';

import type {
  PdfCapabilities,
  PdfErrorEvent,
  PdfMetadata,
  PdfOpenDocumentResult,
  PdfPageChangeEvent,
  PdfPageInfo,
  PdfRenderedPage,
  PdfRenderOptions,
  PdfSearchBounds,
  PdfSearchOptions,
  PdfSearchResult,
  PdfViewerSearchHighlight,
} from './specs/PdfApi.nitro';

export type TypePdfSource =
  | string
  | {
      uri: string;
      headers?: Record<string, string>;
      cacheKey?: string;
      fileName?: string;
    };

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
    options?: IPdfRenderOptions,
  ): Promise<IPdfRenderedPage>;
  getThumbnailAsync(
    pageIndex: number,
    options?: IPdfRenderOptions,
  ): Promise<IPdfRenderedPage>;
  getTextAsync(pageIndex?: number): Promise<string | null>;
  searchTextAsync(
    query: string,
    options?: IPdfSearchOptions,
  ): Promise<IPdfSearchResult[]>;
  closeAsync(): Promise<void>;
}

export interface IPdfViewRef {
  openDocumentAsync(): Promise<IPdfDocument>;
  getTextAsync(pageIndex?: number): Promise<string | null>;
  searchTextAsync(
    query: string,
    options?: IPdfSearchOptions,
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
