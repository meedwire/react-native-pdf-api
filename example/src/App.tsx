import { useRef, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Platform,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { PdfView, type IPdfViewRef } from '@meedwire/react-native-pdf-api';

const DOCS = [
  {
    key: 'multi',
    label: 'Multi-page',
    source:
      'https://raw.githubusercontent.com/mozilla/pdf.js/master/web/compressed.tracemonkey-pldi-09.pdf',
  },
  {
    key: 'single',
    label: 'Single page',
    source: Image.resolveAssetSource(require('../assets/single-page.pdf')).uri,
  },
  {
    key: 'landscape',
    label: 'Landscape',
    source: Image.resolveAssetSource(
      require('../assets/single-page-landscape.pdf')
    ).uri,
  },
] as const;

export default function App() {
  const pdfRef = useRef<IPdfViewRef>(null);
  const [query, setQuery] = useState('');
  const [docKey, setDocKey] = useState<(typeof DOCS)[number]['key']>('multi');
  const [loading, setLoading] = useState(true);
  const [pageInfo, setPageInfo] = useState({ current: 0, total: 0 });
  const [status, setStatus] = useState<string>('Loading…');

  const source = DOCS.find((doc) => doc.key === docKey)!.source;

  function selectDoc(key: (typeof DOCS)[number]['key']) {
    if (key === docKey) return;
    setDocKey(key);
    setQuery('');
    setLoading(true);
    setPageInfo({ current: 0, total: 0 });
    setStatus('Loading…');
  }

  async function handleSearch() {
    if (!query.trim()) {
      return;
    }
    try {
      const results = await pdfRef.current?.searchTextAsync(query, {
        highlight: true,
        focus: true,
      });
      setStatus(`${results?.length ?? 0} match(es) for "${query}"`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    }
  }

  function handleClear() {
    pdfRef.current?.clearSearchAsync();
    setStatus('Search cleared');
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>react-native-pdf-api</Text>
        <Text style={styles.subtitle}>
          Page {pageInfo.current + 1} / {pageInfo.total || '–'} · {status}
        </Text>
      </View>

      <View style={styles.docRow}>
        {DOCS.map((doc) => {
          const active = doc.key === docKey;
          return (
            <TouchableOpacity
              key={doc.key}
              style={[styles.chip, active && styles.chipActive]}
              onPress={() => selectDoc(doc.key)}
            >
              <Text style={[styles.chipText, active && styles.chipTextActive]}>
                {doc.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <View style={styles.searchRow}>
        <TextInput
          style={styles.input}
          placeholder="Search in PDF…"
          value={query}
          onChangeText={setQuery}
          onSubmitEditing={handleSearch}
          autoCapitalize="none"
          returnKeyType="search"
        />
        <TouchableOpacity style={styles.button} onPress={handleSearch}>
          <Text style={styles.buttonText}>Find</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, styles.buttonGhost]}
          onPress={handleClear}
        >
          <Text style={styles.buttonGhostText}>Clear</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.viewerWrapper}>
        <PdfView
          key={docKey}
          ref={pdfRef}
          source={source}
          style={styles.pdf}
          pageSpacing={12}
          maxZoom={5}
          onLoad={({ nativeEvent }) => {
            setLoading(false);
            setPageInfo({ current: 0, total: nativeEvent.pageCount });
            setStatus(`Loaded ${nativeEvent.pageCount} page(s)`);
          }}
          onPageChange={({ nativeEvent }) => {
            setPageInfo({
              current: nativeEvent.currentPage,
              total: nativeEvent.pageCount,
            });
          }}
          onError={({ nativeEvent }) => {
            setLoading(false);
            setStatus(`${nativeEvent.code}: ${nativeEvent.message}`);
          }}
        />
        {loading ? (
          <View style={styles.loader}>
            <ActivityIndicator size="large" />
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 0) : 44,
  },
  header: { paddingHorizontal: 16, paddingTop: 12, paddingBottom: 8 },
  title: { fontSize: 20, fontWeight: '700', color: '#111' },
  subtitle: { fontSize: 13, color: '#666', marginTop: 2 },
  docRow: {
    flexDirection: 'row',
    paddingHorizontal: 12,
    paddingBottom: 8,
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#d0d5dd',
    backgroundColor: '#fff',
  },
  chipActive: { backgroundColor: '#2563eb', borderColor: '#2563eb' },
  chipText: { color: '#344054', fontWeight: '600', fontSize: 13 },
  chipTextActive: { color: '#fff' },
  searchRow: {
    flexDirection: 'row',
    paddingHorizontal: 12,
    paddingBottom: 10,
    gap: 8,
  },
  input: {
    flex: 1,
    height: 40,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 12,
  },
  button: {
    height: 40,
    paddingHorizontal: 16,
    borderRadius: 8,
    backgroundColor: '#2563eb',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: { color: '#fff', fontWeight: '600' },
  buttonGhost: { backgroundColor: '#eef2ff' },
  buttonGhostText: { color: '#2563eb', fontWeight: '600' },
  viewerWrapper: { flex: 1 },
  pdf: { flex: 1, backgroundColor: '#f4f4f1' },
  loader: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
