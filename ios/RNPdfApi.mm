#import "RNPdfApi.h"
#import "RNPdfApi-Swift.h"

@implementation RNPdfApi {
  RNPdfApiImpl *_impl;
}

- (instancetype)init
{
  if (self = [super init]) {
    _impl = [RNPdfApiImpl new];
  }
  return self;
}

- (NSDictionary *)getCapabilities
{
  return [_impl getCapabilities];
}

- (void)prepareSourceAsync:(NSString *)uri
               headersJson:(NSString *)headersJson
                  fileName:(NSString *)fileName
                  cacheKey:(NSString *)cacheKey
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [_impl prepareSource:uri
           headersJson:headersJson
              fileName:fileName
              cacheKey:cacheKey
               resolve:resolve
                reject:reject];
}

- (void)openDocumentAsync:(NSString *)uri
                  resolve:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject
{
  [_impl openDocument:uri resolve:resolve reject:reject];
}

- (void)closeDocumentAsync:(NSString *)documentId
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [_impl closeDocument:documentId resolve:resolve reject:reject];
}

- (void)closeAllDocumentsAsync:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject
{
  [_impl closeAllDocumentsWithResolve:resolve reject:reject];
}

- (void)getMetadataAsync:(NSString *)documentId
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject
{
  [_impl getMetadata:documentId resolve:resolve reject:reject];
}

- (void)getPageInfoAsync:(NSString *)documentId
               pageIndex:(double)pageIndex
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject
{
  [_impl getPageInfo:documentId pageIndex:pageIndex resolve:resolve reject:reject];
}

- (void)renderPageAsync:(NSString *)documentId
              pageIndex:(double)pageIndex
            optionsJson:(NSString *)optionsJson
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject
{
  [_impl renderPage:documentId pageIndex:pageIndex optionsJson:optionsJson resolve:resolve reject:reject];
}

- (void)getTextAsync:(NSString *)documentId
           pageIndex:(double)pageIndex
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  [_impl getText:documentId pageIndex:pageIndex resolve:resolve reject:reject];
}

- (void)searchTextAsync:(NSString *)documentId
                  query:(NSString *)query
            optionsJson:(NSString *)optionsJson
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject
{
  [_impl searchText:documentId query:query optionsJson:optionsJson resolve:resolve reject:reject];
}

- (void)clearPdfCacheAsync:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [_impl clearCacheWithResolve:resolve reject:reject];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativePdfApiSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"RNPdfApi";
}

@end
