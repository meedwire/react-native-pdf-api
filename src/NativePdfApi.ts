import { Platform, View } from 'react-native';
import {
  NitroModules,
  getHostComponent,
} from 'react-native-nitro-modules';

import PdfViewConfig from '../nitrogen/generated/shared/json/PdfViewConfig.json';

import type {
  PdfApi,
  PdfViewMethods,
  PdfViewProps,
} from './specs/PdfApi.nitro';
import type { ComponentProps, ComponentType } from 'react';

export type INativePdfViewProps = PdfViewProps & {
  style?: ComponentProps<typeof View>['style'];
};

const unsupportedCapabilities = {
  supportsMetadata: false,
  supportsPageText: false,
  supportsSearch: false,
  supportsLinks: false,
  supportsForms: false,
  supportsAnnotations: false,
};

const webModule = {
  getCapabilities() {
    return unsupportedCapabilities;
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
} satisfies Partial<PdfApi>;

const nativeModule =
  Platform.OS === 'web'
    ? (webModule as unknown as PdfApi)
    : NitroModules.createHybridObject<PdfApi>('PdfApi');

export const NativePdfView =
  Platform.OS === 'web'
    ? (View as ComponentType<INativePdfViewProps>)
    : getHostComponent<PdfViewProps, PdfViewMethods>(
        'PdfView',
        () => PdfViewConfig as never,
      );

export default nativeModule;
