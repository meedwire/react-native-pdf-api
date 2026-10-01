import {
  type ComponentType,
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import { StyleSheet } from 'react-native';

import {
  normalizePdfSource,
  openDocumentAsync,
  preparePdfSourceAsync,
} from './PdfApi';
import NativePdfViewComponent from './PdfViewNativeComponent';

import type {
  INativeOpenDocumentResult,
  IPdfDocument,
  IPdfErrorEvent,
  IPdfPageChangeEvent,
  IPdfSearchResult,
  IPdfViewerSearchHighlight,
  IPdfViewProps,
  IPdfViewRef,
} from './types';

const NativePdfView = NativePdfViewComponent as unknown as ComponentType<
  Record<string, unknown>
>;

export const PdfView = forwardRef<IPdfViewRef, IPdfViewProps>(function PdfView(
  { source, backgroundColor, onError, onLoad, onPageChange, style, ...props },
  ref
) {
  const normalizedSource = useMemo(() => normalizePdfSource(source), [source]);
  const sourceSignature = useMemo(
    () => JSON.stringify(normalizedSource),
    [normalizedSource]
  );
  const latestSourceSignatureRef = useRef(sourceSignature);
  const documentRef = useRef<IPdfDocument | null>(null);
  const documentSourceSignatureRef = useRef<string | null>(null);
  const searchHighlightRequestIdRef = useRef(0);
  const [preparedUri, setPreparedUri] = useState<string | undefined>();
  const [searchHighlight, setSearchHighlight] =
    useState<IPdfViewerSearchHighlight | null>(null);
  const flattenedStyle = useMemo(() => StyleSheet.flatten(style), [style]);
  const nativeBackgroundColor =
    backgroundColor ??
    (typeof flattenedStyle?.backgroundColor === 'string'
      ? flattenedStyle.backgroundColor
      : undefined);

  latestSourceSignatureRef.current = sourceSignature;

  // Keep the latest onError in a ref so the source-preparation effect does NOT
  // depend on it. Consumers usually pass an inline handler (new identity every
  // render); depending on it would re-run the effect each render, toggling
  // `preparedUri` and reloading the native document in a tight loop.
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  const closePreparedDocumentAsync = useCallback(async () => {
    const document = documentRef.current;
    documentRef.current = null;
    documentSourceSignatureRef.current = null;
    await document?.closeAsync();
  }, []);

  const updateSearchHighlight = useCallback(
    (
      result?: IPdfSearchResult | null,
      options?: { focus?: boolean; highlight?: boolean }
    ) => {
      searchHighlightRequestIdRef.current += 1;

      const shouldHighlight = options?.highlight !== false;
      const shouldFocus = options?.focus !== false;

      setSearchHighlight({
        requestId: searchHighlightRequestIdRef.current,
        pageIndex: result?.pageIndex,
        bounds: shouldHighlight ? result?.bounds : undefined,
        focusBounds: shouldFocus ? result?.bounds : undefined,
      });
    },
    []
  );

  useEffect(() => {
    // False once this source is no longer current: the component unmounted or
    // `source` changed. A late result for it must neither replace the current
    // document nor surface as an error for the source being shown.
    let isMounted = true;
    const nextSource = JSON.parse(sourceSignature) as ReturnType<
      typeof normalizePdfSource
    >;

    setPreparedUri(undefined);

    preparePdfSourceAsync(nextSource)
      .then(({ uri }) => {
        if (isMounted) setPreparedUri(uri);
      })
      .catch((error) => {
        if (!isMounted) return;

        onErrorRef.current?.({
          nativeEvent: {
            code: 'ERR_PDF_SOURCE',
            message: error instanceof Error ? error.message : String(error),
          },
        });
      });

    return () => {
      isMounted = false;
    };
  }, [sourceSignature]);

  useEffect(() => {
    return () => {
      // Best effort: nothing is waiting on this close.
      closePreparedDocumentAsync().catch(() => {});
    };
  }, [sourceSignature, closePreparedDocumentAsync]);

  const ensureDocumentAsync = useCallback(async () => {
    if (
      documentRef.current &&
      documentSourceSignatureRef.current === sourceSignature
    ) {
      return documentRef.current;
    }

    await closePreparedDocumentAsync();

    const preparedSourceUri =
      preparedUri ?? (await preparePdfSourceAsync(normalizedSource)).uri;
    const document = await openDocumentAsync(preparedSourceUri);

    if (latestSourceSignatureRef.current !== sourceSignature) {
      await document.closeAsync();
      throw new Error('PDF source changed before the document was ready.');
    }

    documentRef.current = document;
    documentSourceSignatureRef.current = sourceSignature;

    return document;
  }, [
    closePreparedDocumentAsync,
    normalizedSource,
    preparedUri,
    sourceSignature,
  ]);

  const handleLoad = useCallback(
    (event: { nativeEvent: INativeOpenDocumentResult }) => {
      onLoad?.(event);
    },
    [onLoad]
  );

  const handlePageChange = useCallback(
    (event: { nativeEvent: IPdfPageChangeEvent }) => {
      onPageChange?.(event);
    },
    [onPageChange]
  );

  const handleError = useCallback(
    (event: { nativeEvent: IPdfErrorEvent }) => {
      onError?.(event);
    },
    [onError]
  );

  useImperativeHandle(
    ref,
    () => ({
      openDocumentAsync: ensureDocumentAsync,
      async getTextAsync(pageIndex) {
        const document = await ensureDocumentAsync();

        return document.getTextAsync(pageIndex);
      },
      async searchTextAsync(query, options) {
        const document = await ensureDocumentAsync();
        const results = await document.searchTextAsync(query, options);
        const shouldHighlight = options?.highlight !== false;
        const shouldFocus = options?.focus !== false;

        if (shouldHighlight || shouldFocus) {
          const resultIndex = Math.max(0, options?.resultIndex ?? 0);
          updateSearchHighlight(results[resultIndex] ?? null, options);
        }

        return results;
      },
      clearSearchAsync() {
        updateSearchHighlight(null);
      },
      closeDocumentAsync: closePreparedDocumentAsync,
    }),
    [closePreparedDocumentAsync, ensureDocumentAsync, updateSearchHighlight]
  );

  return (
    <NativePdfView
      {...props}
      onError={handleError}
      onLoad={handleLoad}
      onPageChange={handlePageChange}
      source={preparedUri}
      searchHighlight={searchHighlight ?? undefined}
      style={style}
      viewerBackgroundColor={nativeBackgroundColor}
    />
  );
});
