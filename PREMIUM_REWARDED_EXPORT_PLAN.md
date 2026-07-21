# Premium Asset Gating & Rewarded Ad Export Unlock Plan

This implementation plan details the strategy for integrating **Rewarded Video Ads for Premium Assets & Export Unlock** in **UrduPhotoDesigner**, allowing non-subscribed users to choose between subscribing or watching a video ad to export designs containing premium fonts, templates, resolution options, or assets.

---

## User Review Required

> [!IMPORTANT]
> **AdMob Compliance & UX Balance**: To ensure 100% compliance with Google AdMob policy (which mandates that rewarded ads must be opt-in), the user will never be forced to watch an ad without clear visual choice. When premium assets/options are detected:
> 1. The export action will display an **Export Unlock Bottom Sheet** presenting two options:
>    - **Option 1: Upgrade to Pro** (Go to Subscription screen for unlimited access).
>    - **Option 2: Watch Short Video Ad** (Watch 1 rewarded video ad to unlock export for this current design).
> 2. Once the user completes watching the rewarded video ad, `onRewarded` callback unlocks export and immediately proceeds with file rendering.
> 3. If the user is already subscribed, zero ads or dialogs will appear (unrestricted export).

---

## Proposed Changes

### Component 1: WebsCareAds SDK Extension & State Management

#### [MODIFY] [WebsCareAds.kt](file:///c:/Users/WebsCare/Documents/GitHub/WebsCareAds/webscare-ads/src/main/java/com/webscare/ads/WebsCareAds.kt)
- Add session-level unlock helper `setSessionExportUnlocked(unlocked: Boolean)` or `isSessionExportUnlocked(): Boolean` to track when a user has earned a temporary export pass for their current project session via rewarded ad.

#### [MODIFY] [RewardedHandler.kt](file:///c:/Users/WebsCare/Documents/GitHub/WebsCareAds/webscare-ads/src/main/java/com/webscare/ads/internal/handlers/RewardedHandler.kt)
- Preload rewarded ad instance automatically on export screen entrance so the ad is ready instantly when the user taps "Watch Video Ad".

---

### Component 2: UrduPhotoDesigner Export Flow & UI Dialogs

#### [NEW] [dialog_export_unlock_sheet.xml](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/res/layout/dialog_export_unlock_sheet.xml)
- Design a modern, glassmorphic Bottom Sheet Dialog presenting:
  - Header: *"Premium Content Detected"* (Icon + Title + Description listing detected premium assets/options).
  - **Primary Action (Pro):** *"Get Urdu Canvas Pro"* button with crown icon (opens Subscription Fragment).
  - **Secondary Action (Rewarded Ad):** *"Watch Video to Export Once"* button with play video icon (`ic_play` / `ic_video`).
  - **Cancel Action:** *"Back to Editor"*.

#### [NEW] [ExportUnlockSheet.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/editor/export/ExportUnlockSheet.kt)
- BottomSheetDialogFragment implementation handling:
  - Binding the premium asset details summary.
  - Tapping **Get Pro**: Navigates to `R.id.subscriptionsFragment` and dismisses sheet.
  - Tapping **Watch Video**: Calls `WebsCareAds.showRewarded(requireActivity(), BuildConfig.AD_REWARDED_BG_REMOVAL, ...)`:
    - On rewarded success: Dismisses sheet, sets session unlocked flag, and invokes `onUnlocked()` callback.
    - On ad not ready: Displays a clean progress indicator or toast and retries.

#### [MODIFY] [ExportFragment.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/ui/editor/export/ExportFragment.kt)
- Update `isPremiumLocked()` helper to check whether `sessionExportUnlocked` is active.
- Update `startExport()`:
  - If `isPremiumLocked()` is true AND user is not subscribed AND session is not unlocked:
    - Launch `ExportUnlockSheet` instead of immediately redirecting to SubscriptionsFragment.
  - Upon sheet `onUnlocked` callback: proceed with `performExportRendering()`.
- Preload rewarded ad on `onViewCreated()` using `WebsCareAds.preloadRewarded(requireContext(), BuildConfig.AD_REWARDED_BG_REMOVAL)`.

---

## Verification Plan

### Automated Tests
- Run unit tests in `WebsCareAds`:
  ```bash
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; ./gradlew :webscare-ads:testDebugUnitTest
  ```

### Manual Verification
1. **Free Design Export**:
   - Create a canvas with only free fonts/assets → Click Export → Interstitial ad respects cooldown, export completes without unlock sheet.
2. **Premium Asset Export (Watch Ad Flow)**:
   - Add a premium font or template → Click Export → `ExportUnlockSheet` displays with "Get Pro" & "Watch Video" buttons.
   - Tap "Watch Video to Export Once" → Complete Google test video ad → Ad closes, export immediately begins and saves image.
3. **Premium Asset Export (Pro Flow)**:
   - Click Export with premium assets → Tap "Get Urdu Canvas Pro" → Navigates to Subscription screen.
4. **Subscribed User Verification**:
   - Enable `debugSetSubscription(true)` → Add premium assets → Click Export → Zero dialogs or ads, instant high-quality export.
