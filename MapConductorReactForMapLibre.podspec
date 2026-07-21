require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name = "MapConductorReactForMapLibre"
  s.version = package["version"]
  s.summary = package["description"]
  s.license = package["license"]
  s.author = package["author"]
  s.homepage = "https://github.com/mapconductor/react-sdk"
  s.source = { :path => __dir__ }
  s.platform = :ios, "15.1"
  s.source_files = "ios/*.{h,m,mm,swift}"
  # MapConductorForMapLibre is a source pod (see ios-sdk/ios-for-maplibre's podspec), not a
  # vendored prebuilt xcframework - see ios-sdk/CLAUDE.md's "iOS Provider Distribution" section.
  # MapLibre itself (the real vendor SDK) stays a normal `s.dependency` either way - it's a
  # dynamic framework, so it was never at risk of being embedded/redistributed by this repo.
  s.dependency "React-Core"
  s.dependency "MapConductorCore"
  s.dependency "MapConductorReactNativeCore"
  s.dependency "MapConductorReactMarkerClustering"
  s.dependency "MapConductorForMapLibre"
  s.dependency "MapLibre", "~> 6.20"
end
