require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "NitroPdfApi"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/meedwire/react-native-pdf-api"
  s.license      = package["license"]
  s.authors      = "Meedwire"

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/meedwire/react-native-pdf-api.git", :tag => "#{s.version}" }

  s.source_files = [
    "ios/**/*.{swift,h,m,mm,cpp}",
  ]

  s.frameworks = "PDFKit"

  load "nitrogen/generated/ios/NitroPdfApi+autolinking.rb"
  add_nitrogen_files(s)

  current_pod_target_xcconfig = s.attributes_hash["pod_target_xcconfig"] || {}
  s.pod_target_xcconfig = current_pod_target_xcconfig.merge({
    "BUILD_LIBRARY_FOR_DISTRIBUTION" => "NO",
    "OTHER_CPLUSPLUSFLAGS" => "$(inherited) -x objective-c++",
    "SWIFT_VERIFY_EMITTED_MODULE_INTERFACE" => "NO",
  })

  s.dependency "React-jsi"
  s.dependency "React-callinvoker"
  s.dependency "RCT-Folly"
  install_modules_dependencies(s)
end
