# Walkthrough — Monetization UI & Export Rewarded Unlock Integration

All requested monetization fixes, rewarded ad export unlock flows, settings banner clipping fixes, and native ad recyclerview feeds have been implemented and verified.

---

## 1. Summary of Changes Implemented

### A. Export Screen Premium Detection & Rewarded Ad Unlock (Images 1 & 2)
* **[fragment_export.xml](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/res/layout/fragment_export.xml)**:
  - Added **"Watch Video Ad to Export"** button (`btnWatchAdToExport`) with play icon (`ic_video_ad.xml`) directly inside the `premiumAssets` card.
* **[ExportFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/editor/export/ExportFragment.kt)**:
  - Added session-level unlock state (`isSessionExportUnlocked`).
  - Preloaded rewarded & interstitial ads on `onViewCreated()`.
  - Updated `updatePremiumBannerVisibility()`:
    - Renders the `premiumAssets` card whenever a Premium Template, Premium Canvas Asset, Premium Format (`PNG`), or Premium Resolution (`1920x1080`) is selected.
    - Set up `btnWatchAdToExport` click listener: plays Rewarded Ad via `WebsCareAds.showRewarded()`, sets `isSessionExportUnlocked = true`, updates UI, and automatically initiates file export.
  - Updated `startExport()`:
    - Free/Standard export: plays Interstitial Ad via `WebsCareAds.showInterstitial()` (respecting 3-min cooldown) before starting file render.

---

### B. Settings Screen Banner Overlap & Clipping Fix (Image 3)
* **[fragment_settings.xml](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/res/layout/fragment_settings.xml)**:
  - Increased `WebsCareBannerView` margin bottom to `24dp` and ensured `SpringNestedScrollView` has `paddingBottom="160dp"` and `clipToPadding="false"`.
  - Banner now sits cleanly **above** the floating bottom navigation bar with zero text or icon clipping.

---

### C. Templates RecyclerView Native Ads
* **[TemplateCategoriesFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/navigation/templates/TemplateCategoriesFragment.kt)**:
  - Wrapped category and template adapters with `WebsCareAds.wrapWithNativeAds(adapter, BuildConfig.AD_NATIVE_CATEGORIES, interval = 6)`.
* **[TemplatesListFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/navigation/templates/TemplatesListFragment.kt)**:
  - Wrapped subcategory grid adapters with `WebsCareAds.wrapWithNativeAds(adapter, BuildConfig.AD_NATIVE_TEMPLATES, interval = 6)`.

---

## 2. Build & Verification Results

* **WebsCareAds Library Release AAR Build**:
  ```bash
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew :webscare-ads:assembleRelease
  # Result: BUILD SUCCESSFUL
  ```
* **UrduPhotoDesigner App Debug Build**:
  ```bash
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew :app:assembleDebug
  # Result: BUILD SUCCESSFUL (31s)
  ```
