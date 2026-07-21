# WsCareAds SDK — Task Tracker

## Phase 1 — Skeleton + Core Engine
- [x] Create new repo folder `C:\Users\WebsCare\Documents\GitHub\WebsCareAds\`
- [x] Set up Gradle multi-module structure (`webscare-ads` + `sample`)
- [x] Create `WsCareAds.kt` facade (public API surface) - implemented as `WebsCareAds.kt`
- [x] Create `AdConfig.kt` DSL
- [x] Create `NativeSize.kt`, `CollapsiblePosition.kt` enums
- [x] Implement `AdManager.kt` core (premium gate, routing, lifecycle callbacks)
- [x] Implement `ConsentManager.kt` (UMP integration)
- [x] Implement `FrequencyCap.kt`
- [x] Implement `NetworkCheck.kt`, `AdLogger.kt`, `TestAdIds.kt`

## Phase 2 — Banner + Interstitial Handlers
- [x] Implement `BannerAdHandler.kt` (adaptive + collapsible + inline)
- [x] Implement `InterstitialHandler.kt` (with frequency cap + lifecycle safety)
- [x] Implement `WebsCareBannerView.kt` (XML custom view)
- [x] Implement `AdPreloader.kt`
- [x] Implement `RetryPolicy.kt`
- [x] Create `webscare_banner_container.xml`
- [x] Create `webscare_attrs.xml` for custom view attributes

## Phase 3 — Native Ads
- [x] Design + build `webscare_native_small.xml`
- [x] Design + build `webscare_native_medium.xml`
- [x] Design + build `webscare_native_full.xml`
- [x] Design + build `webscare_native_recycler_item.xml`
- [x] Design + build all shimmer placeholders (`webscare_shimmer_*.xml`)
- [x] Implement `NativeAdHandler.kt`
- [x] Implement `NativeAdPopulator.kt`
- [x] Implement `WebsCareNativeView.kt` (XML custom view with shimmer)
- [x] Create `webscare_colors.xml` + `values-night/webscare_colors.xml`
- [x] Create `webscare_styles.xml`, `webscare_dimens.xml`
- [x] Create drawable resources (ad badge, CTA button, card background)

## Phase 3.5 — Hardening & Core Architecture Rules
- [x] Implement WebsCareAds.setPremium(enabled) helper and hide active banners/native views
- [x] Implement post-AppOpen cooldown timer (interstitial lock for 60-120s)
- [x] Implement placement-specific remote configuration toggle flags in AdConfig
- [x] Implement network-change listener and collapse custom views to GONE when offline
- [x] Implement background preloading and 4-hour caching expiry for Interstitial and Rewarded ads
- [x] Implement non-blocking fallback flow for interstitials/rewarded ads (actions proceed instantly)

## Phase 4 — RecyclerView + Rewarded + App Open
- [x] Implement `AdNativeRecyclerAdapter.kt`
- [x] Implement batch loading via `AdLoader.loadAds()`
- [x] Implement `RewardedHandler.kt` (Rewarded + Rewarded Interstitial)
- [x] Implement `AppOpenAdHandler.kt` with `ProcessLifecycleOwner`
- [x] Implement `AdExpiryTracker.kt`
- [x] Implement `LifecycleObserver.kt`
- [x] Implement `loadNativeOrEmpty()`

## Phase 5 — Sample App + Testing
- [x] Build sample app with screens for each ad type
- [x] Test all 6 ad types with Google test IDs
- [x] Test premium bypass
- [x] Test frequency capping
- [x] Test lifecycle edge cases
- [x] Test dark mode
- [x] Test RecyclerView adapter

## Phase 6 — Publish + Integrate
- [ ] Write `README.md`
- [ ] Add `consumer-rules.pro`
- [ ] Add `jitpack.yml`
- [ ] Create `feature/ads-integration` branch in UrduPhotoDesigner
- [ ] Tag v1.0.0 → verify JitPack build
- [ ] Integrate into UrduPhotoDesigner
- [ ] Add ad containers to relevant layouts
- [ ] Test on physical device
