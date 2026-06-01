import {
  type ComponentType,
  default as React,
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import { StyleSheet } from 'react-native';
import { callback } from 'react-native-nitro-modules';

import {
  normalizePdfSource,
  openDocumentAsync,
  preparePdfSourceAsync,
} from './PdfApi';
import { NativePdfView } from './NativePdfApi';

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

const NativePdfViewComponent =
  NativePdfView as ComponentType<Record<string, unknown>>;

export const PdfView = forwardRef<IPdfViewRef, IPdfViewProps>(function PdfView(
  { source, backgroundColor, onError, onLoad, onPageChange, style, ...props },
  ref,
) {
  const normalizedSource = useMemo(() => normalizePdfSource(source), [source]);
  const sourceSignature = useMemo(
    () => JSON.stringify(normalizedSource),
    [normalizedSource],
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

  const closePreparedDocumentAsync = useCallback(async () => {
    const document = documentRef.current;
    documentRef.current = null;
    documentSourceSignatureRef.current = null;
    await document?.closeAsync();
  }, []);

  const updateSearchHighlight = useCallback(
    (
      result?: IPdfSearchResult | null,
      options?: { focus?: boolean; highlight?: boolean },
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
    [],
  );

  useEffect(() => {
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
        onError?.({
          nativeEvent: {
            code: 'ERR_PDF_SOURCE',
            message: error instanceof Error ? error.message : String(error),
          },
        });
      });

    return () => {
      isMounted = false;
    };
  }, [sourceSignature, onError]);

  useEffect(() => {
    return () => {
      void closePreparedDocumentAsync();
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
    (nativeEvent: INativeOpenDocumentResult) => {
      onLoad?.({ nativeEvent });
    },
    [onLoad],
  );

  const handlePageChange = useCallback(
    (nativeEvent: IPdfPageChangeEvent) => {
      onPageChange?.({ nativeEvent });
    },
    [onPageChange],
  );

  const handleError = useCallback(
    (nativeEvent: IPdfErrorEvent) => {
      onError?.({ nativeEvent });
    },
    [onError],
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
    [closePreparedDocumentAsync, ensureDocumentAsync, updateSearchHighlight],
  );

  return (
    <NativePdfViewComponent
      {...props}
      onError={callback(handleError)}
      onLoad={callback(handleLoad)}
      onPageChange={callback(handlePageChange)}
      source={preparedUri}
      searchHighlight={searchHighlight ?? undefined}
      style={style}
      viewerBackgroundColor={nativeBackgroundColor}
    />
  );
});
