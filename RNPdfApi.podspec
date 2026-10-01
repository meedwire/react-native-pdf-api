require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "RNPdfApi"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/meedwire/react-native-pdf-api.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"

  # Keep the Obj-C++ headers out of the public module umbrella. RNPdfApi.h pulls in
  # <RNPdfApiSpec/...> (the RN Codegen spec) and PdfViewComponentView.h pulls in
  # React-Fabric — both are C++. If they stay public, emitting this pod's Swift
  # module forces Clang to build the aggregate `ReactCodegen` module as plain
  # Objective-C and it fails ("'utility' file not found" / "must be compiled as
  # Obj-C++"). The Swift sources here are React-free and the app resolves these
  # classes by runtime name (TurboModule registry / RCTThirdPartyComponentsProvider),
  # so nothing imports them publicly; the .mm files still see them via Headers/Private.
  s.private_header_files = "ios/**/*.h"

  s.frameworks = "PDFKit", "CryptoKit"

  install_modules_dependencies(s)
end
