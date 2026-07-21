# Universal Monetization & UI Fixes Implementation Plan (Refined)

This refined plan incorporates your exact feedback:
1. **App-Managed Unlock State**: State management (e.g. `isExportUnlockedForCurrentSession`) is kept inside the host app (`UrduPhotoDesigner`), keeping the **`WebsCareAds`** library 100% clean, stateless, and reusable across all your apps.
2. **Universal Library API**: The library handles core ad preloading, loading, display, and reward callbacks without any domain-specific code (no project IDs in library).
3. **Standard Export Interstitial Ads**: Non-premium designs (standard format/resolution and free assets) show a standard Interstitial Ad upon clicking Export (respecting the 3-minute cooldown), while premium designs offer the Rewarded Video Ad choice.
4. **Settings Screen Banner Overlap/Clipping Fix (Image 3)**: Fixed layout anchoring so the settings banner sits cleanly above the floating bottom navigation bar.
5. **Templates RecyclerView Native Ads**: Embedded native ads across all category and subcategory grid lists in `TemplateCategoriesFragment` and `TemplatesListFragment`.

---

## Refined Architecture & Responsibilities

### 1. `WebsCareAds` SDK Library (Stateless & Universal)
* Pure, feature-agnostic API:
  - `WebsCareAds.showRewarded(activity, adUnitId, onRewarded = { type, amount -> }, onNotReady = { })`
  - `WebsCareAds.showInterstitial(activity, adUnitId, onDismissed = { })`
  - `WebsCareAds.wrapWithNativeAds(adapter, adUnitId, interval = 6)`
* No app-specific state, no project IDs, no domain objects in library.

### 2. `UrduPhotoDesigner` Host Application (App State & UI)

#### Export Screen Flow (`ExportFragment.kt` & `fragment_export.xml`)
* **Standard/Free Design Export:**
  - User taps **Export** → `WebsCareAds.showInterstitial()` runs (respects 3-minute cooldown) → File renders & saves.
* **Premium Design Export (Images 1 & 2):**
  - Premium detection card renders whenever a Premium Template, Premium Asset, Premium Format (`PNG`), or Premium Resolution (`1920x1080`) is selected.
  - Card displays two choices:
    - **Option 1: Subscribe Now** (Green button → Subscriptions Fragment).
    - **Option 2: Watch Ad to Export** (Rewarded Video button).
  - Tapping **Watch Ad to Export** → Calls `WebsCareAds.showRewarded(...)`.
  - Upon reward completion:
    - `ExportFragment` sets its local boolean `isSessionExportUnlocked = true`.
    - Card updates to *"Unlocked via Video Ad"*.
    - Bottom button updates to **"Export Now"** and automatically initiates rendering.
  - If the user leaves the editor and returns later, `isSessionExportUnlocked` resets to `false` (session-scoped unlock).

---

## Component Breakdown & Changes

### Component 1: UrduPhotoDesigner Export Layout & Logic (Images 1 & 2)

#### [MODIFY] [fragment_export.xml](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/res/layout/fragment_export.xml)
- Add `btnWatchAdToExport` button and "OR" divider below the `premiumAssets` detection card.
- Ensure card is shown for ALL premium selections (format, resolution, assets, template).

#### [MODIFY] [ExportFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/editor/export/ExportFragment.kt)
- Add local session unlock state: `private var isSessionExportUnlocked = false`.
- Preload rewarded ad on entrance: `WebsCareAds.preloadRewarded(requireContext(), BuildConfig.AD_REWARDED_BG_REMOVAL)`.
- Update `startExport()`:
  - If `isPremiumLocked()` AND `!isSessionExportUnlocked`:
    - Show `premiumAssets` card with **Watch Ad to Export** button.
  - If not premium locked:
    - Call `WebsCareAds.showInterstitial(requireActivity(), BuildConfig.AD_INTERSTITIAL_EXPORT) { performExportRendering() }`.
- `btnWatchAdToExport` Click Handler:
  - Calls `WebsCareAds.showRewarded(requireActivity(), BuildConfig.AD_REWARDED_BG_REMOVAL, onRewarded = { _, _ -> ... })`.
  - On reward: sets `isSessionExportUnlocked = true`, updates UI, and triggers `performExportRendering()`.

---

### Component 2: Settings Screen Banner Overlap & Clipping Fix (Image 3)

#### [MODIFY] [fragment_settings.xml](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/res/layout/fragment_settings.xml)
- Adjust `WebsCareBannerView` layout anchoring with `android:layout_marginBottom="90dp"` (or above the bottom navigation guidelines) so the banner is displayed cleanly above the floating action bar without text/icon clipping.

---

### Component 3: Native Ads in All Templates RecyclerView Lists

#### [MODIFY] [TemplateCategoriesFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/navigation/templates/TemplateCategoriesFragment.kt)
- Wrap category and template grid adapters using `WebsCareAds.wrapWithNativeAds(adapter, BuildConfig.AD_NATIVE_CATEGORIES, interval = 6)`.

#### [MODIFY] [TemplatesListFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/navigation/templates/TemplatesListFragment.kt)
- Wrap subcategory feed adapters using `WebsCareAds.wrapWithNativeAds(adapter, BuildConfig.AD_NATIVE_TEMPLATES, interval = 6)`.

---

## Verification Plan

### Automated Verification
```bash
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew :webscare-ads:assembleRelease
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew :app:assembleDebug
```

### Manual Verification Checklist
1. **Free Design Export**: Interstitial ad shows on export (respecting cooldown), then file exports.
2. **Premium Design Export (Rewarded Video)**: Tap "Watch Ad to Export" → Rewarded video plays → Upon completion, file exports immediately.
3. **Session Reset**: Exit editor and reopen → Premium export requires watching video ad again.
4. **Settings Banner**: Banner sits cleanly above bottom navigation bar without clipping.
5. **Templates Feeds**: Native ad cards appear every 6 items in category/subcategory grid feeds.
