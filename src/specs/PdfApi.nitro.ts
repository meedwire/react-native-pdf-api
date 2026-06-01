import type {
  HybridObject,
  HybridView,
  HybridViewMethods,
  HybridViewProps,
} from 'react-native-nitro-modules';

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

export interface PdfViewerSearchHighlight {
  requestId: number;
  pageIndex?: number;
  bounds?: PdfSearchBounds[];
  focusBounds?: PdfSearchBounds[];
}

export interface PdfOpenDocumentResult {
  documentId: string;
  pageCount: number;
  sourceUri: string;
  capabilities: PdfCapabilities;
}

export interface PdfPageChangeEvent {
  currentPage: number;
  pageCount: number;
}

export interface PdfErrorEvent {
  code: string;
  message: string;
}

export interface PdfApi
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  getCapabilities(): PdfCapabilities;
  openDocumentAsync(uri: string): Promise<PdfOpenDocumentResult>;
  closeDocumentAsync(documentId: string): Promise<void>;
  closeAllDocumentsAsync(): Promise<void>;
  getMetadataAsync(documentId: string): Promise<PdfMetadata>;
  getPageInfoAsync(documentId: string, pageIndex: number): Promise<PdfPageInfo>;
  renderPageAsync(
    documentId: string,
    pageIndex: number,
    options?: PdfRenderOptions,
  ): Promise<PdfRenderedPage>;
  getTextAsync(documentId: string, pageIndex?: number): Promise<string | null>;
  searchTextAsync(
    documentId: string,
    query: string,
    options?: PdfSearchOptions,
  ): Promise<PdfSearchResult[]>;
  clearPdfCacheAsync(): Promise<void>;
}

export interface PdfViewProps extends HybridViewProps {
  source?: string;
  viewerBackgroundColor?: string;
  initialPage?: number;
  pageSpacing?: number;
  maxZoom?: number;
  maxPageResolution?: number;
  singlePage?: boolean;
  searchHighlight?: PdfViewerSearchHighlight;
  onLoad?: (result: PdfOpenDocumentResult) => void;
  onPageChange?: (event: PdfPageChangeEvent) => void;
  onError?: (event: PdfErrorEvent) => void;
}

export interface PdfViewMethods extends HybridViewMethods {}

export type PdfView = HybridView<
  PdfViewProps,
  PdfViewMethods,
  { ios: 'swift'; android: 'kotlin' }
>;
