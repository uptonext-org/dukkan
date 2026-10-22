require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "DukkanCapture"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.license      = "UNLICENSED"
  s.author       = "Dukkan"
  s.homepage     = "https://dukkan.app"
  s.platforms    = { :ios => "15.1" }
  s.source       = { :path => "." }
  s.source_files = "ios/**/*.{h,m,mm}"
  s.frameworks   = "Speech", "AVFoundation"
  s.requires_arc = true

  install_modules_dependencies(s)
end
