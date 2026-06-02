#import "PdfViewComponentView.h"

#import <react/renderer/components/RNPdfApiSpec/ComponentDescriptors.h>
#import <react/renderer/components/RNPdfApiSpec/EventEmitters.h>
#import <react/renderer/components/RNPdfApiSpec/Props.h>
#import <react/renderer/components/RNPdfApiSpec/RCTComponentViewHelpers.h>

#import <React/RCTConversions.h>

#import "RNPdfApi-Swift.h"

using namespace facebook::react;

@interface PdfViewComponentView () <RCTPdfViewViewProtocol>
@end

@implementation PdfViewComponentView {
  PdfViewImpl *_impl;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<PdfViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const PdfViewProps>();
    _props = defaultProps;

    _impl = [[PdfViewImpl alloc] initWithFrame:frame];

    __weak PdfViewComponentView *weakSelf = self;
    _impl.onLoadHandler = ^(NSDictionary *payload) {
      [weakSelf emitOnLoad:payload];
    };
    _impl.onPageChangeHandler = ^(NSDictionary *payload) {
      [weakSelf emitOnPageChange:payload];
    };
    _impl.onErrorHandler = ^(NSDictionary *payload) {
      [weakSelf emitOnError:payload];
    };

    self.contentView = _impl;
  }
  return self;
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps
{
  const auto &oldViewProps = *std::static_pointer_cast<const PdfViewProps>(_props);
  const auto &newViewProps = *std::static_pointer_cast<const PdfViewProps>(props);

  if (newViewProps.viewerBackgroundColor != oldViewProps.viewerBackgroundColor) {
    [_impl setViewerBackgroundColor:RCTNSStringFromStringNilIfEmpty(newViewProps.viewerBackgroundColor)];
  }
  if (newViewProps.initialPage != oldViewProps.initialPage) {
    [_impl setInitialPage:newViewProps.initialPage];
  }
  if (newViewProps.pageSpacing != oldViewProps.pageSpacing) {
    [_impl setPageSpacing:newViewProps.pageSpacing];
  }
  if (newViewProps.maxZoom != oldViewProps.maxZoom) {
    [_impl setMaxZoom:newViewProps.maxZoom];
  }
  if (newViewProps.singlePage != oldViewProps.singlePage) {
    [_impl setSinglePage:newViewProps.singlePage];
  }
  if (newViewProps.searchHighlight.requestId != oldViewProps.searchHighlight.requestId) {
    [_impl setSearchHighlight:[self dictionaryFromHighlight:newViewProps.searchHighlight]];
  }
  if (newViewProps.source != oldViewProps.source) {
    [_impl setSource:RCTNSStringFromStringNilIfEmpty(newViewProps.source)];
  }

  [super updateProps:props oldProps:oldProps];

  [_impl applyPendingSourceIfNeeded];
}

#pragma mark - Event emitters

- (void)emitOnLoad:(NSDictionary *)payload
{
  if (!_eventEmitter) {
    return;
  }
  NSDictionary *caps = payload[@"capabilities"] ?: @{};
  PdfViewEventEmitter::OnLoadCapabilities capabilities{
      [caps[@"supportsMetadata"] boolValue],
      [caps[@"supportsPageText"] boolValue],
      [caps[@"supportsSearch"] boolValue],
      [caps[@"supportsLinks"] boolValue],
      [caps[@"supportsForms"] boolValue],
      [caps[@"supportsAnnotations"] boolValue],
  };
  std::static_pointer_cast<const PdfViewEventEmitter>(_eventEmitter)
      ->onLoad(PdfViewEventEmitter::OnLoad{
          std::string([(payload[@"documentId"] ?: @"") UTF8String]),
          [payload[@"pageCount"] intValue],
          std::string([(payload[@"sourceUri"] ?: @"") UTF8String]),
          capabilities,
      });
}

- (void)emitOnPageChange:(NSDictionary *)payload
{
  if (!_eventEmitter) {
    return;
  }
  std::static_pointer_cast<const PdfViewEventEmitter>(_eventEmitter)
      ->onPageChange(PdfViewEventEmitter::OnPageChange{
          [payload[@"currentPage"] intValue],
          [payload[@"pageCount"] intValue],
      });
}

- (void)emitOnError:(NSDictionary *)payload
{
  if (!_eventEmitter) {
    return;
  }
  std::static_pointer_cast<const PdfViewEventEmitter>(_eventEmitter)
      ->onError(PdfViewEventEmitter::OnError{
          std::string([(payload[@"code"] ?: @"") UTF8String]),
          std::string([(payload[@"message"] ?: @"") UTF8String]),
      });
}

- (NSDictionary *)dictionaryFromHighlight:(const PdfViewSearchHighlightStruct &)highlight
{
  NSMutableArray *bounds = [NSMutableArray new];
  for (const auto &item : highlight.bounds) {
    [bounds addObject:@{@"x" : @(item.x), @"y" : @(item.y), @"width" : @(item.width), @"height" : @(item.height)}];
  }
  NSMutableArray *focusBounds = [NSMutableArray new];
  for (const auto &item : highlight.focusBounds) {
    [focusBounds addObject:@{@"x" : @(item.x), @"y" : @(item.y), @"width" : @(item.width), @"height" : @(item.height)}];
  }
  return @{
    @"requestId" : @(highlight.requestId),
    @"pageIndex" : @(highlight.pageIndex),
    @"bounds" : bounds,
    @"focusBounds" : focusBounds,
  };
}

@end
