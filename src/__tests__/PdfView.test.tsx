import * as React from 'react';
import TestRenderer, { act } from 'react-test-renderer';

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

jest.mock('../PdfViewNativeComponent', () => {
  const propsHistory: Array<Record<string, unknown>> = [];
  const Comp = (props: Record<string, unknown>) => {
    propsHistory.push(props);
    return null;
  };
  (Comp as unknown as { propsHistory: typeof propsHistory }).propsHistory =
    propsHistory;
  return { __esModule: true, default: Comp };
});

import NativePdfApiModule from '../NativePdfApi';
import NativePdfViewComponent from '../PdfViewNativeComponent';
import { PdfView } from '../PdfView';
import type { IPdfViewRef } from '../types';

const native = NativePdfApiModule as unknown as {
  [K in keyof NonNullable<typeof NativePdfApiModule>]: jest.Mock;
};
const propsHistory = (
  NativePdfViewComponent as unknown as {
    propsHistory: Array<Record<string, unknown>>;
  }
).propsHistory;

const capabilities = {
  supportsMetadata: true,
  supportsPageText: true,
  supportsSearch: true,
  supportsLinks: true,
  supportsForms: false,
  supportsAnnotations: true,
};

const lastProps = () => propsHistory[propsHistory.length - 1]!;

type PreparedSource = { uri: string; fromCache: boolean };

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((onResolve, onReject) => {
    resolve = onResolve;
    reject = onReject;
  });
  return { promise, resolve, reject };
}

// Lets every pending promise callback (the component's prepare chain) run.
const flushPromises = () =>
  new Promise<void>((resolve) => setImmediate(() => resolve()));

beforeEach(() => {
  jest.clearAllMocks();
  propsHistory.length = 0;
  native.openDocumentAsync.mockImplementation(async (uri: string) => ({
    documentId: 'doc-1',
    pageCount: 2,
    sourceUri: uri,
    capabilities,
  }));
  native.searchTextAsync.mockResolvedValue([
    {
      pageIndex: 1,
      text: 'match',
      bounds: [{ x: 1, y: 2, width: 3, height: 4 }],
    },
  ]);
  native.closeDocumentAsync.mockResolvedValue(undefined);
});

it('forwards the prepared local source and background color to the native view', async () => {
  await act(async () => {
    TestRenderer.create(
      <PdfView source="file:///doc.pdf" backgroundColor="#ffffff" />
    );
  });

  expect(lastProps().source).toBe('file:///doc.pdf');
  expect(lastProps().viewerBackgroundColor).toBe('#ffffff');
});

it('updates the search highlight when searching through the ref', async () => {
  const ref = React.createRef<IPdfViewRef>();

  await act(async () => {
    TestRenderer.create(<PdfView ref={ref} source="file:///doc.pdf" />);
  });

  await act(async () => {
    await ref.current!.searchTextAsync('match');
  });

  const highlight = lastProps().searchHighlight as {
    requestId: number;
    pageIndex?: number;
    bounds?: unknown[];
    focusBounds?: unknown[];
  };
  expect(native.searchTextAsync).toHaveBeenCalledWith('doc-1', 'match', '{}');
  expect(highlight.requestId).toBe(1);
  expect(highlight.pageIndex).toBe(1);
  expect(highlight.bounds).toHaveLength(1);

  await act(async () => {
    ref.current!.clearSearchAsync();
  });

  const cleared = lastProps().searchHighlight as {
    requestId: number;
    pageIndex?: number;
    bounds?: unknown[];
  };
  expect(cleared.requestId).toBe(2);
  expect(cleared.pageIndex).toBeUndefined();
  expect(cleared.bounds).toBeUndefined();
});

describe('onError for remote sources', () => {
  it('fires for the current source when its preparation fails', async () => {
    const prepare = deferred<PreparedSource>();
    native.prepareSourceAsync.mockReturnValueOnce(prepare.promise);
    const onError = jest.fn();

    await act(async () => {
      TestRenderer.create(
        <PdfView source="https://x.com/a.pdf?sig=1" onError={onError} />
      );
    });

    await act(async () => {
      prepare.reject(new Error('Failed to download PDF (HTTP 404).'));
      await flushPromises();
    });

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith({
      nativeEvent: {
        code: 'ERR_PDF_SOURCE',
        message: 'Failed to download PDF (HTTP 404).',
      },
    });
  });

  it('is not called after unmount when the preparation rejects late', async () => {
    const prepare = deferred<PreparedSource>();
    native.prepareSourceAsync.mockReturnValueOnce(prepare.promise);
    const onError = jest.fn();
    let renderer!: ReturnType<typeof TestRenderer.create>;

    await act(async () => {
      renderer = TestRenderer.create(
        <PdfView source="https://x.com/a.pdf?sig=1" onError={onError} />
      );
    });

    await act(async () => {
      renderer.unmount();
    });

    await act(async () => {
      prepare.reject(new Error('Failed to download PDF (HTTP 403).'));
      await flushPromises();
    });

    expect(native.prepareSourceAsync).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
  });

  it('is not called for a source that was replaced before it failed', async () => {
    const prepareA = deferred<PreparedSource>();
    native.prepareSourceAsync
      .mockReturnValueOnce(prepareA.promise)
      .mockResolvedValueOnce({ uri: 'file:///cache/b.pdf', fromCache: false });
    const onError = jest.fn();
    let renderer!: ReturnType<typeof TestRenderer.create>;

    await act(async () => {
      renderer = TestRenderer.create(
        <PdfView source="https://x.com/a.pdf" onError={onError} />
      );
    });

    await act(async () => {
      renderer.update(
        <PdfView source="https://x.com/b.pdf" onError={onError} />
      );
    });

    await act(async () => {
      prepareA.reject(new Error('timeout'));
      await flushPromises();
    });

    expect(native.prepareSourceAsync).toHaveBeenCalledTimes(2);
    expect(onError).not.toHaveBeenCalled();
    expect(lastProps().source).toBe('file:///cache/b.pdf');
  });
});
