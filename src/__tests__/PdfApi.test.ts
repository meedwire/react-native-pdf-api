jest.mock('../NativePdfApi', () => ({
  __esModule: true,
  default: {
    getCapabilities: jest.fn(),
    prepareSourceAsync: jest.fn(),
    openDocumentAsync: jest.fn(),
    closeDocumentAsync: jest.fn(),
    closeAllDocumentsAsync: jest.fn(),
    getMetadataAsync: jest.fn(),
    getPageInfoAsync: jest.fn(),
    renderPageAsync: jest.fn(),
    getTextAsync: jest.fn(),
    searchTextAsync: jest.fn(),
    clearPdfCacheAsync: jest.fn(),
  },
}));

import NativePdfApiModule from '../NativePdfApi';
import {
  clearPdfCacheAsync,
  isRemotePdfUri,
  normalizePdfSource,
  openDocumentAsync,
  preparePdfSourceAsync,
} from '../PdfApi';

const native = NativePdfApiModule as unknown as {
  [K in keyof NonNullable<typeof NativePdfApiModule>]: jest.Mock;
};

const capabilities = {
  supportsMetadata: true,
  supportsPageText: true,
  supportsSearch: true,
  supportsLinks: true,
  supportsForms: false,
  supportsAnnotations: true,
};

beforeEach(() => {
  jest.clearAllMocks();
  native.prepareSourceAsync.mockResolvedValue({
    uri: 'file:///cache/remote.pdf',
    fromCache: false,
  });
  native.openDocumentAsync.mockImplementation(async (uri: string) => ({
    documentId: 'doc-1',
    pageCount: 3,
    sourceUri: uri,
    capabilities,
  }));
  native.closeDocumentAsync.mockResolvedValue(undefined);
  native.closeAllDocumentsAsync.mockResolvedValue(undefined);
  native.clearPdfCacheAsync.mockResolvedValue(undefined);
  native.getMetadataAsync.mockResolvedValue({ pageCount: 3 });
  native.getPageInfoAsync.mockResolvedValue({
    pageIndex: 0,
    width: 100,
    height: 200,
    rotation: 0,
  });
  native.renderPageAsync.mockResolvedValue({
    uri: 'file:///cache/page.png',
    width: 100,
    height: 200,
    scale: 1,
    pageIndex: 0,
  });
  native.getTextAsync.mockResolvedValue('hello world');
  native.searchTextAsync.mockResolvedValue([{ pageIndex: 0, bounds: [] }]);
});

describe('normalizePdfSource', () => {
  it('wraps a string source into an object', () => {
    expect(normalizePdfSource('file:///doc.pdf')).toEqual({
      uri: 'file:///doc.pdf',
    });
  });

  it('returns object sources untouched', () => {
    const source = { uri: 'https://x.com/a.pdf', cacheKey: 'k' };
    expect(normalizePdfSource(source)).toBe(source);
  });
});

describe('isRemotePdfUri', () => {
  it.each([
    ['https://x.com/a.pdf', true],
    ['http://x.com/a.pdf', true],
    ['HTTP://X.com/a.pdf', true],
    ['file:///a.pdf', false],
    ['/var/mobile/a.pdf', false],
    ['content://a/b', false],
  ])('%s -> %s', (uri, expected) => {
    expect(isRemotePdfUri(uri as string)).toBe(expected);
  });
});

describe('preparePdfSourceAsync', () => {
  it('returns local sources without touching native', async () => {
    const result = await preparePdfSourceAsync('file:///local.pdf');
    expect(result).toEqual({ uri: 'file:///local.pdf', fromCache: false });
    expect(native.prepareSourceAsync).not.toHaveBeenCalled();
  });

  it('delegates remote sources to native with serialized headers', async () => {
    const result = await preparePdfSourceAsync({
      uri: 'https://x.com/a.pdf',
      headers: { Authorization: 'Bearer t' },
      cacheKey: 'my-key',
    });

    expect(native.prepareSourceAsync).toHaveBeenCalledWith(
      'https://x.com/a.pdf',
      JSON.stringify({ Authorization: 'Bearer t' }),
      '',
      'my-key'
    );
    expect(result).toEqual({
      uri: 'file:///cache/remote.pdf',
      fromCache: false,
    });
  });

  it('passes an empty headers object, file name and cache key when omitted', async () => {
    await preparePdfSourceAsync('https://x.com/a.pdf');
    expect(native.prepareSourceAsync).toHaveBeenCalledWith(
      'https://x.com/a.pdf',
      '{}',
      '',
      ''
    );
  });

  it('forwards fileName and cacheKey as separate arguments', async () => {
    await preparePdfSourceAsync({
      uri: 'https://x.com/p1/doc.pdf?sig=1',
      fileName: 'laudo.pdf',
      cacheKey: 'exam-42',
    });

    expect(native.prepareSourceAsync).toHaveBeenCalledWith(
      'https://x.com/p1/doc.pdf?sig=1',
      '{}',
      'laudo.pdf',
      'exam-42'
    );
  });

  it('never turns fileName into the cache identity', async () => {
    await preparePdfSourceAsync({
      uri: 'https://x.com/p1/doc.pdf?sig=1',
      fileName: 'laudo.pdf',
    });

    expect(native.prepareSourceAsync).toHaveBeenCalledWith(
      'https://x.com/p1/doc.pdf?sig=1',
      '{}',
      'laudo.pdf',
      ''
    );
  });

  it('forwards cacheKey alone with an empty fileName', async () => {
    await preparePdfSourceAsync({
      uri: 'https://x.com/p1/doc.pdf?sig=1',
      cacheKey: 'exam-42',
    });

    expect(native.prepareSourceAsync).toHaveBeenCalledWith(
      'https://x.com/p1/doc.pdf?sig=1',
      '{}',
      '',
      'exam-42'
    );
  });
});

describe('openDocumentAsync + PdfDocument', () => {
  it('prepares the source then opens the document', async () => {
    const document = await openDocumentAsync('https://x.com/a.pdf');

    expect(native.prepareSourceAsync).toHaveBeenCalled();
    expect(native.openDocumentAsync).toHaveBeenCalledWith(
      'file:///cache/remote.pdf'
    );
    expect(document.documentId).toBe('doc-1');
    expect(document.pageCount).toBe(3);
    expect(document.capabilities.supportsSearch).toBe(true);
  });

  it('delegates document operations using the documentId', async () => {
    const document = await openDocumentAsync('file:///local.pdf');

    await document.getMetadataAsync();
    await document.getPageInfoAsync(2);
    await document.getTextAsync(1);
    await document.searchTextAsync('query', { maxResults: 5 });

    expect(native.getMetadataAsync).toHaveBeenCalledWith('doc-1');
    expect(native.getPageInfoAsync).toHaveBeenCalledWith('doc-1', 2);
    expect(native.getTextAsync).toHaveBeenCalledWith('doc-1', 1);
    expect(native.searchTextAsync).toHaveBeenCalledWith(
      'doc-1',
      'query',
      JSON.stringify({ maxResults: 5 })
    );
  });

  it('getThumbnailAsync applies low-res defaults that options can override', async () => {
    const document = await openDocumentAsync('file:///local.pdf');

    await document.getThumbnailAsync(0);
    expect(native.renderPageAsync).toHaveBeenLastCalledWith(
      'doc-1',
      0,
      JSON.stringify({ maxPixels: 1_048_576, scale: 0.4 })
    );

    await document.getThumbnailAsync(1, { scale: 0.8 });
    expect(native.renderPageAsync).toHaveBeenLastCalledWith(
      'doc-1',
      1,
      JSON.stringify({ maxPixels: 1_048_576, scale: 0.8 })
    );
  });

  it('closeAsync is idempotent', async () => {
    const document = await openDocumentAsync('file:///local.pdf');

    await document.closeAsync();
    await document.closeAsync();

    expect(native.closeDocumentAsync).toHaveBeenCalledTimes(1);
    expect(native.closeDocumentAsync).toHaveBeenCalledWith('doc-1');
  });
});

describe('clearPdfCacheAsync', () => {
  it('closes all documents before clearing the cache', async () => {
    const order: string[] = [];
    native.closeAllDocumentsAsync.mockImplementation(async () => {
      order.push('closeAll');
    });
    native.clearPdfCacheAsync.mockImplementation(async () => {
      order.push('clear');
    });

    await clearPdfCacheAsync();

    expect(order).toEqual(['closeAll', 'clear']);
  });
});
