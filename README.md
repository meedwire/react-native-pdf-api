# @meedwire/react-native-pdf-api

Modulo Nitro para abrir, visualizar, renderizar, extrair texto e buscar em PDFs nativos no React Native.

O pacote expoe uma API imperativa para trabalhar com documentos PDF e um componente `PdfView` para renderizacao nativa em iOS e Android. Fontes remotas `http`/`https` sao baixadas para cache com `expo-file-system` antes de serem entregues aos renderizadores nativos.

## Recursos

- Visualizador nativo com scroll vertical, zoom e pagina inicial.
- Modo continuo ou pagina unica.
- Abertura imperativa de documentos para metadados, informacoes de pagina, render de pagina e thumbnails.
- Extracao de texto por pagina ou do documento inteiro.
- Busca textual com limites (`bounds`) para destacar e focar resultados no visualizador.
- Cache local para PDFs remotos e imagens renderizadas.
- Implementacao nativa via `react-native-nitro-modules`.

## Suporte por plataforma

| Recurso | iOS | Android | Web |
| --- | --- | --- | --- |
| Visualizar PDF | Sim, via PDFKit | Sim, via `PdfRenderer` | Nao, fallback para `View` |
| Metadados | Sim | Parcial: retorna contagem de paginas e campos nulos | Nao |
| Renderizar pagina | Sim | Sim | Nao |
| Extrair texto | Sim | Android 15/API 35+ | Retorna `null` |
| Buscar texto | Sim | Android 15/API 35+ | Retorna `[]` |
| Links/anotacoes | Capabilities indicam suporte a links/anotacoes no iOS, mas nao ha API publica dedicada | Nao | Nao |
| PDFs protegidos por senha | Nao suportado | Nao suportado | Nao suportado |

Use `document.capabilities`, retornado por `openDocumentAsync()` ou `ref.current.openDocumentAsync()`, para decidir se a plataforma atual suporta texto e busca.

## Requisitos

- React Native com suporte a Nitro Modules.
- `react-native-nitro-modules` instalado no app.
- `expo-file-system` instalado no app, mesmo fora de projetos Expo, porque o wrapper JS usa `File`, `Directory` e `Paths`.
- iOS com PDFKit disponivel.
- Android com `minSdkVersion`, `compileSdkVersion`, `targetSdkVersion` e `ndkVersion` definidos no projeto host. O pacote usa CMake, Kotlin, prefab e Nitro.

## Instalacao

```sh
yarn add @meedwire/react-native-pdf-api react-native-nitro-modules expo-file-system
```

ou:

```sh
npm install @meedwire/react-native-pdf-api react-native-nitro-modules expo-file-system
```

Depois instale os pods:

```sh
cd ios
pod install
```

### Expo

Este pacote contem codigo nativo. Ele nao funciona no Expo Go. Use um development build:

```sh
npx expo install expo-file-system
yarn add @meedwire/react-native-pdf-api react-native-nitro-modules
npx expo prebuild
npx expo run:ios
npx expo run:android
```

Sempre gere um novo build nativo depois de instalar ou atualizar o pacote.

### React Native CLI

Em apps React Native CLI, a integracao usa autolinking. Depois da instalacao:

```sh
cd ios && pod install
cd ..
yarn ios
yarn android
```

No Android, garanta que o projeto host tenha o modulo `react-native-nitro-modules` linkado e que as propriedades esperadas pelo Gradle existam. O `android/build.gradle` do pacote le `ndkVersion`, `compileSdkVersion`, `minSdkVersion` e `targetSdkVersion` de `rootProject.ext` ou das propriedades `NitroPdfApi_*`.

## Setup Nitro

O pacote declara os objetos Nitro em `nitro.json`:

- `PdfApi`: Hybrid Object nativo em Swift/Kotlin.
- `PdfView`: Hybrid View nativa em Swift/Kotlin.

Os artefatos gerados ficam em `nitrogen/generated`. Ao modificar specs Nitro durante o desenvolvimento do pacote, rode:

```sh
yarn specs
```

No app consumidor, mantenha `react-native-nitro-modules` instalado e faca rebuild nativo. Se o app usa New Architecture no Android, o pacote tambem aplica o plugin React quando `newArchEnabled=true`.

## Uso rapido com `PdfView`

```tsx
import { useRef } from 'react';
import { Button, StyleSheet, View } from 'react-native';
import {
  PdfView,
  type IPdfViewRef,
  type IPdfSearchResult,
} from '@meedwire/react-native-pdf-api';

export function PdfScreen() {
  const pdfRef = useRef<IPdfViewRef>(null);

  async function findFirstMatch() {
    const results: IPdfSearchResult[] =
      await pdfRef.current?.searchTextAsync('contrato', {
        maxResults: 20,
        resultIndex: 0,
        highlight: true,
        focus: true,
      }) ?? [];

    console.log(results[0]);
  }

  return (
    <View style={styles.container}>
      <PdfView
        ref={pdfRef}
        source="https://example.com/documento.pdf"
        style={styles.pdf}
        initialPage={0}
        maxZoom={5}
        pageSpacing={16}
        maxPageResolution={2048}
        backgroundColor="#f4f4f1"
        onLoad={({ nativeEvent }) => {
          console.log('Paginas:', nativeEvent.pageCount);
        }}
        onPageChange={({ nativeEvent }) => {
          console.log('Pagina atual:', nativeEvent.currentPage);
        }}
        onError={({ nativeEvent }) => {
          console.warn(nativeEvent.code, nativeEvent.message);
        }}
      />

      <Button title="Buscar" onPress={findFirstMatch} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  pdf: {
    flex: 1,
  },
});
```

> Importante: `currentPage`, `initialPage`, `pageIndex` e `resultIndex` usam indice baseado em zero.

## Fontes de PDF

`source` aceita uma string ou um objeto:

```ts
type TypePdfSource =
  | string
  | {
      uri: string;
      headers?: Record<string, string>;
      cacheKey?: string;
      fileName?: string;
    };
```

Exemplos:

```tsx
<PdfView source="file:///var/mobile/Containers/Data/document.pdf" />

<PdfView
  source={{
    uri: 'https://example.com/private.pdf',
    headers: { Authorization: `Bearer ${token}` },
    cacheKey: 'private-document-v1.pdf',
  }}
/>
```

Comportamento:

- URIs `http` e `https` sao baixadas para `Paths.cache/react-native-pdf-api` via `expo-file-system`.
- `headers` so sao usados no download remoto feito pelo JS.
- `cacheKey` ou `fileName` definem o nome seguro do arquivo em cache.
- URIs locais sao repassadas diretamente ao nativo.
- Android tambem resolve `content://`, `file://` e caminhos sem scheme.
- iOS resolve caminhos absolutos e URIs validas aceitas por `URL`.

## Props do `PdfView`

| Prop | Tipo | Padrao | Descricao |
| --- | --- | --- | --- |
| `source` | `TypePdfSource` | Obrigatoria | PDF local ou remoto. |
| `style` | `StyleProp<ViewStyle>` | - | Estilo do container nativo. Use dimensoes reais, como `flex: 1` ou `height`. |
| `backgroundColor` | `string` | `#f4f4f1` no viewer | Cor de fundo do visualizador. Se ausente, o componente tenta usar `style.backgroundColor`. |
| `initialPage` | `number` | `0` | Pagina inicial, baseada em zero. |
| `pageSpacing` | `number` | `16` | Espacamento entre paginas. No Android o valor e convertido por densidade. |
| `maxZoom` | `number` | `5` | Zoom maximo. Valores menores que `1` sao normalizados para `1`. |
| `maxPageResolution` | `number` | `2048` | Android: maior lado do bitmap renderizado no viewer. iOS expoe a prop, mas PDFKit renderiza o viewer. |
| `singlePage` | `boolean` | `false` | Renderiza apenas a pagina inicial em modo pagina unica. |
| `onLoad` | `(event) => void` | - | Chamado quando o documento e carregado. |
| `onPageChange` | `(event) => void` | - | Chamado quando a pagina atual muda. |
| `onError` | `(event) => void` | - | Chamado em erro de fonte, abertura, pagina ou renderizacao. |

## Ref do `PdfView`

Use `ref` para chamar operacoes associadas ao documento exibido:

```tsx
const ref = useRef<IPdfViewRef>(null);

const document = await ref.current?.openDocumentAsync();
const text = await ref.current?.getTextAsync(0);
const results = await ref.current?.searchTextAsync('assinatura');
ref.current?.clearSearchAsync();
await ref.current?.closeDocumentAsync();
```

### `openDocumentAsync()`

Abre a fonte atual e retorna um `IPdfDocument`. O wrapper reutiliza o documento enquanto a assinatura de `source` nao mudar.

### `getTextAsync(pageIndex?)`

Retorna texto da pagina informada ou do documento inteiro. Pode retornar `null` quando a plataforma nao suporta texto. No Android, texto e busca exigem API 35+; em versoes anteriores a chamada nativa rejeita com `ERR_PDF_TEXT_UNSUPPORTED`.

### `searchTextAsync(query, options?)`

Busca texto e retorna uma lista de resultados. Quando `highlight` ou `focus` nao sao `false`, o `PdfView` destaca/foca o resultado indicado por `resultIndex`.

```ts
await ref.current?.searchTextAsync('total', {
  pageIndex: 2,
  caseSensitive: false,
  maxResults: 50,
  resultIndex: 0,
  highlight: true,
  focus: true,
});
```

### `clearSearchAsync()`

Remove destaque e selecao/foco de busca do visualizador.

### `closeDocumentAsync()`

Fecha o documento aberto pelo wrapper JS. O componente tambem fecha o documento preparado ao desmontar ou trocar `source`.

## API imperativa

Tambem e possivel usar a API sem renderizar o componente:

```ts
import {
  openDocumentAsync,
  clearPdfCacheAsync,
} from '@meedwire/react-native-pdf-api';

const document = await openDocumentAsync({
  uri: 'https://example.com/documento.pdf',
  headers: { Authorization: `Bearer ${token}` },
  cacheKey: 'documento.pdf',
});

try {
  const metadata = await document.getMetadataAsync();
  const firstPage = await document.getPageInfoAsync(0);
  const rendered = await document.renderPageAsync(0, {
    format: 'png',
    width: 1200,
    backgroundColor: '#ffffff',
    maxPixels: 16_777_216,
  });
  const thumbnail = await document.getThumbnailAsync(0);
  const text = await document.getTextAsync();
  const matches = await document.searchTextAsync('cliente', {
    maxResults: 10,
  });

  console.log({ metadata, firstPage, rendered, thumbnail, text, matches });
} finally {
  await document.closeAsync();
}

await clearPdfCacheAsync();
```

### Exports

```ts
export {
  PdfView,
  openDocumentAsync,
  clearPdfCacheAsync,
  isRemotePdfUri,
  normalizePdfSource,
  preparePdfSourceAsync,
};
```

Tipos publicos:

- `TypePdfSource`
- `IPdfDocument`
- `IPdfViewRef`
- `IPdfViewProps`
- `IPdfCapabilities`
- `IPdfMetadata`
- `IPdfPageInfo`
- `IPdfRenderOptions`
- `IPdfRenderedPage`
- `IPdfSearchOptions`
- `IPdfSearchBounds`
- `IPdfSearchResult`
- `IPdfViewerSearchHighlight`
- `INativeOpenDocumentResult`
- `IPdfPageChangeEvent`
- `IPdfErrorEvent`
- `IPreparedPdfSource`

## `IPdfDocument`

Um documento aberto contem:

| Campo/metodo | Descricao |
| --- | --- |
| `documentId` | Identificador nativo do documento aberto. |
| `pageCount` | Total de paginas. |
| `sourceUri` | URI final usada pelo nativo. Em PDFs remotos, normalmente e a URI do arquivo em cache. |
| `capabilities` | Recursos suportados pela plataforma atual. |
| `getMetadataAsync()` | Retorna metadados do documento. |
| `getPageInfoAsync(pageIndex)` | Retorna dimensoes, rotacao, label e quantidade de anotacoes quando disponiveis. |
| `renderPageAsync(pageIndex, options?)` | Renderiza uma pagina para arquivo PNG/JPEG em cache. |
| `getThumbnailAsync(pageIndex, options?)` | Atalho de render com `scale: 0.4` e `maxPixels: 1_048_576`, sobrescrevivel por `options`. |
| `getTextAsync(pageIndex?)` | Extrai texto da pagina ou do documento. |
| `searchTextAsync(query, options?)` | Busca texto e retorna bounds por resultado. |
| `closeAsync()` | Fecha o documento no store nativo. |

## Renderizacao de paginas

`renderPageAsync` aceita:

```ts
type IPdfRenderOptions = {
  format?: 'png' | 'jpeg';
  quality?: number;
  width?: number;
  height?: number;
  scale?: number;
  backgroundColor?: string;
  maxPixels?: number;
};
```

Regras principais:

- `format` padrao: `png`. Use `jpeg` para gerar `.jpg`.
- `quality` padrao: `0.9`. No Android valores `0..1` viram porcentagem `0..100`; valores acima de `1` sao tratados como porcentagem.
- `width` ou `height` preservam a proporcao quando apenas uma dimensao e informada.
- Sem `width`/`height`, o tamanho vem de `scale`.
- `maxPixels` padrao: `16_777_216`. Se `width * height` exceder o limite, a chamada falha com `ERR_PDF_RENDER_TOO_LARGE`.
- `backgroundColor` preenche o fundo antes de desenhar a pagina.

O retorno:

```ts
type IPdfRenderedPage = {
  uri: string;
  width: number;
  height: number;
  scale: number;
  pageIndex: number;
};
```

## Busca e texto

`searchTextAsync` aceita:

```ts
type IPdfSearchOptions = {
  pageIndex?: number;
  caseSensitive?: boolean;
  maxResults?: number;
  focus?: boolean;
  highlight?: boolean;
  resultIndex?: number;
};
```

Detalhes:

- `query` vazio, apenas espacos ou `maxResults: 0` retorna `[]`.
- `maxResults` padrao: `100`.
- `caseSensitive` padrao: `false`.
- `pageIndex` restringe a busca a uma pagina.
- `highlight` e `focus` sao usados pelo `PdfView` para atualizar a UI; a API nativa ignora esses campos na busca.
- `resultIndex` escolhe qual resultado deve ser destacado/focado no `PdfView`.

Resultado:

```ts
type IPdfSearchResult = {
  pageIndex: number;
  text?: string;
  bounds: Array<{
    x: number;
    y: number;
    width: number;
    height: number;
  }>;
};
```

Os `bounds` usam coordenadas da pagina PDF. O visualizador converte esses valores para o tamanho exibido.

## Eventos

### `onLoad`

```ts
{
  nativeEvent: {
    documentId: string;
    pageCount: number;
    sourceUri: string;
    capabilities: IPdfCapabilities;
  }
}
```

No `PdfView` iOS, o `documentId` do evento pode vir vazio porque o viewer usa `PDFView` diretamente. Para operacoes imperativas, chame `ref.current.openDocumentAsync()`.

### `onPageChange`

```ts
{
  nativeEvent: {
    currentPage: number;
    pageCount: number;
  }
}
```

`currentPage` e baseado em zero.

### `onError`

```ts
{
  nativeEvent: {
    code: string;
    message: string;
  }
}
```

Codigos observados na implementacao:

- `ERR_PDF_SOURCE`: URI invalida, arquivo inexistente, download remoto ou scheme nao suportado.
- `ERR_PDF_OPEN`: falha ao abrir o PDF.
- `ERR_PDF_LOCKED`: PDF protegido por senha ou bloqueado.
- `ERR_PDF_PAGE_OUT_OF_BOUNDS`: indice de pagina invalido.
- `ERR_PDF_PAGE`: iOS nao conseguiu carregar a pagina.
- `ERR_PDF_DOCUMENT_NOT_FOUND`: documento fechado ou inexistente no store nativo.
- `ERR_PDF_RENDER_TOO_LARGE`: render solicitado excede `maxPixels`.
- `ERR_PDF_RENDER`: falha ao codificar imagem renderizada.
- `ERR_PDF_TEXT_UNSUPPORTED`: Android abaixo de API 35 para extracao de texto.
- `ERR_PDF_SEARCH_UNSUPPORTED`: Android abaixo de API 35 para busca.
- `ERR_PDF_CONTEXT`: contexto React Android ainda nao disponivel.
- `ERR_PDF_RESULT`: resultado nativo iOS inesperado.

## Cache

`preparePdfSourceAsync(source)` baixa PDFs remotos para o cache do app em `react-native-pdf-api` e retorna:

```ts
type IPreparedPdfSource = {
  uri: string;
  fromCache: boolean;
};
```

`clearPdfCacheAsync()` fecha todos os documentos nativos e limpa o cache nativo usado pelo pacote. Para renovar um PDF remoto especifico, altere `cacheKey`/`fileName` ou limpe o cache antes de abrir novamente.

## Troubleshooting

### Tela cinza ou vazia

Checklist:

1. Garanta que o `PdfView` tem tamanho. Use `style={{ flex: 1 }}` dentro de um pai com altura, ou defina `height`.
2. Verifique `onError`. Um PDF remoto pode falhar no download antes de chegar ao nativo e retornar `ERR_PDF_SOURCE`.
3. Para URLs privadas, passe `headers` no objeto `source`.
4. Em Android, confirme se o arquivo remoto foi baixado e se a URI final e `file://` ou caminho local. O renderizador nativo nao abre `http` diretamente.
5. Verifique se o PDF nao e protegido por senha. O pacote retorna `ERR_PDF_LOCKED`.
6. Diminua `maxPageResolution` no Android se paginas grandes consumirem muita memoria.
7. Depois de instalar ou atualizar o pacote, faca rebuild nativo completo. Reload JS nao recompila Nitro, CMake, Pods ou Kotlin.
8. Em iOS, rode `pod install` novamente e limpe DerivedData se o host component Nitro nao for encontrado.
9. Em Android, rode uma build limpa se houver erro de prefab/CMake: `cd android && ./gradlew clean`.

### Busca ou texto nao funcionam no Android

Extracao de texto e busca usam APIs disponiveis a partir do Android 15/API 35. Em versoes anteriores, `capabilities.supportsPageText` e `capabilities.supportsSearch` serao `false`, e as chamadas podem rejeitar com `ERR_PDF_TEXT_UNSUPPORTED` ou `ERR_PDF_SEARCH_UNSUPPORTED`.

### `PdfApi is not available on web`

O pacote possui fallback web apenas para evitar crash de importacao. Operacoes nativas nao estao disponiveis no web.

### PDF remoto nao atualiza

PDFs remotos sao cacheados pelo nome seguro gerado a partir da URL, `cacheKey` ou `fileName`. Troque o `cacheKey` quando o conteudo mudar, ou chame `clearPdfCacheAsync()`.

## Desenvolvimento do pacote

Comandos declarados em `package.json`:

```sh
yarn typecheck
yarn specs
```

Equivalentes com npm:

```sh
npm run typecheck
npm run specs
```

O comando `specs` executa TypeScript e depois `nitrogen --log-level debug`, regenerando artefatos Nitro a partir de `src/specs/PdfApi.nitro.ts`.

Arquivos publicos importantes:

- `src/index.ts`: exports publicos.
- `src/types.ts`: tipos TypeScript publicos.
- `src/PdfApi.ts`: wrapper JS, cache remoto e classe `IPdfDocument`.
- `src/PdfView.tsx`: componente React e API por ref.
- `src/specs/PdfApi.nitro.ts`: contrato Nitro compartilhado.
- `ios/`: implementacao Swift com PDFKit.
- `android/src/main/java/...`: implementacao Kotlin com `PdfRenderer`.
- `nitro.json`: configuracao de autolinking Nitro.
- `NitroPdfApi.podspec`: integracao iOS.
- `android/build.gradle`: integracao Android.

Como este pacote usa codigo nativo, valide alteracoes com pelo menos:

```sh
yarn typecheck
yarn specs
```

E, quando houver mudanca nativa, rode builds iOS/Android no app consumidor.

## Limitacoes conhecidas

- Nao ha suporte a PDFs protegidos por senha.
- Web nao implementa renderizacao nem API nativa.
- Android abaixo de API 35 nao suporta texto/busca.
- Metadados Android sao limitados pelo `PdfRenderer` usado atualmente.
- `maxPageResolution` afeta o viewer Android; no iOS, o viewer usa PDFKit.
- A API publica nao expoe navegacao programatica de pagina sem usar busca/foco ou alterar `initialPage` com nova montagem.
