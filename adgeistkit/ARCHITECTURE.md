AdgeistCore.kt                        # SDK singleton / entry point / factory
ads/
  AdView.kt                           # Public View class publishers place in layouts
  BaseAdView.kt                       # Core rendering ViewGroup (WebView host)
  AdActivity.kt                       # Visibility/impression/click tracking helper
  JsBridge.kt                         # JS<->native bridge (@JavascriptInterface)
  AdListener.kt                       # Ad lifecycle callback abstract class
  AdSize.kt                           # Width/height value object (dp->px)
  AdType.kt                           # enum BANNER / DISPLAY / COMPANION
core/
  TargetingOptions.kt                 # Builds targeting/device metrics map
  device/DeviceIdentifier.kt          # Advertising/device ID
  device/DeviceMeta.kt                # Device metadata + phone-state permission
  device/NetworkUtils.kt              # IP address lookups
data/
  models/CreativeDataModel.kt         # All ad response data models
  models/Event.kt                     # Event logging model
  models/UserDetails.kt              # User details model
  network/FetchCreative.kt            # Ad fetch (OkHttp POST /v2/dsp/ad)
  network/CreativeAnalytics.kt        # Tracking POST /v2/ssp/impression
request/
  AdRequest.kt                        # loadAd() request
  FetchCreativeRequest.kt             # Ad fetch request payload builder
  AnalyticsRequest.kt                 # Impression/click payload builder