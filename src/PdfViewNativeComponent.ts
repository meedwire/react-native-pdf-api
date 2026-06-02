import {
  codegenNativeComponent,
  type HostComponent,
  type ViewProps,
} from 'react-native';
import type {
  DirectEventHandler,
  Double,
  Int32,
} from 'react-native/Libraries/Types/CodegenTypes';

type SearchBounds = Readonly<{
  x: Double;
  y: Double;
  width: Double;
  height: Double;
}>;

type OnLoadEvent = Readonly<{
  documentId: string;
  pageCount: Int32;
  sourceUri: string;
  capabilities: Readonly<{
    supportsMetadata: boolean;
    supportsPageText: boolean;
    supportsSearch: boolean;
    supportsLinks: boolean;
    supportsForms: boolean;
    supportsAnnotations: boolean;
  }>;
}>;

type OnPageChangeEvent = Readonly<{
  currentPage: Int32;
  pageCount: Int32;
}>;

type OnErrorEvent = Readonly<{
  code: string;
  message: string;
}>;

export interface NativeProps extends ViewProps {
  /** Local file URI of the PDF (remote sources are pre-fetched in JS). */
  source?: string;
  viewerBackgroundColor?: string;
  initialPage?: Int32;
  pageSpacing?: Double;
  maxZoom?: Double;
  maxPageResolution?: Double;
  singlePage?: boolean;
  searchHighlight?: Readonly<{
    requestId: Double;
    pageIndex?: Int32;
    bounds?: ReadonlyArray<SearchBounds>;
    focusBounds?: ReadonlyArray<SearchBounds>;
  }>;
  onLoad?: DirectEventHandler<OnLoadEvent>;
  onPageChange?: DirectEventHandler<OnPageChangeEvent>;
  onError?: DirectEventHandler<OnErrorEvent>;
}

export default codegenNativeComponent<NativeProps>(
  'PdfView'
) as HostComponent<NativeProps>;
