# WebsCare Ads SDK — Complete Implementation Plan

> [!NOTE]
> This plan leaves **zero use cases behind**. Every ad type, every layout variant, every edge case, every policy constraint, and every placement pattern is accounted for.

---

## 1. Why a Library (And Why Your Instinct is Right)

Your approach: *"Call a function, pass an ad ID, done"* — is correct. But a production-grade library must handle **17 hidden concerns** behind that simple API call:

| # | Hidden Concern | What Happens Without It |
|---|---------------|------------------------|
| 1 | **Premium gate** | Ads still load (wasting bandwidth/battery) for paying users |
| 2 | **Lifecycle safety** | Crash: `Activity destroyed before ad callback fires` |
| 3 | **Frequency capping** | Users rage-quit from interstitial spam |
| 4 | **Ad expiry** | App Open Ads expire after 4 hours; showing stale ads = blank screen |
| 5 | **GDPR consent (UMP)** | Google limits ad fill or rejects your app in EU/EEA |
| 6 | **Network pre-check** | Loading ads with no internet = silent failures + wasted time |
| 7 | **Retry with backoff** | One failed load = no ads for that session |
| 8 | **Preloading** | User taps "Export" → 3-second wait for ad to load → terrible UX |
| 9 | **Shimmer placeholders** | Native ad space shows blank white box while loading |
| 10 | **RecyclerView injection** | Inserting ads into lists is 200+ lines of fragile boilerplate |
| 11 | **Dark mode support** | Ad layout is white-on-white in dark mode |
| 12 | **Ad attribution ("Ad" label)** | AdMob policy violation → account suspension |
| 13 | **Accidental click prevention** | Content shifts when ad loads → accidental clicks → policy violation |
| 14 | **Test mode auto-detection** | Developer tests with live ads → account suspension |
| 15 | **Revenue tracking** | No Firebase Analytics integration = flying blind on eCPM |
| 16 | **Collapsible banner state** | Collapsible banner expands over content without proper height management |
| 17 | **Rewarded ad user consent** | Showing rewarded ad without user opt-in = policy violation |

**All 17 are handled internally by the library. The calling app still does a one-liner.**

---

## 2. Complete Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    YOUR APP (Any WebsCare App)                           │
│                                                                         │
│  // INIT (Application.onCreate — one time)                              │
│  WebsCareAds.init(this) {                                               │
│      premiumCheck = { billingManager.isSubscribed.value }                │
│      testMode = BuildConfig.DEBUG                                       │
│      interstitialCooldown = 3.minutes                                   │
│      rewardedCooldown = 1.minutes                                       │
│  }                                                                      │
│                                                                         │
│  // USE (anywhere — one line each)                                      │
│  WebsCareAds.showAppOpen(activity, AD_ID)                               │
│  WebsCareAds.loadBanner(activity, container, AD_ID)                     │
│  WebsCareAds.loadCollapsibleBanner(activity, container, AD_ID)          │
│  WebsCareAds.loadNative(activity, container, AD_ID)                     │
│  WebsCareAds.loadNative(activity, container, AD_ID, NativeSize.SMALL)   │
│  WebsCareAds.showInterstitial(activity, AD_ID) { proceed() }           │
│  WebsCareAds.showRewarded(activity, AD_ID) { reward -> grant() }       │
│  WebsCareAds.wrapWithNativeAds(adapter, AD_ID, every = 6)              │
│                                                                         │
│  // XML (drop-in views — zero Kotlin code needed for banners)           │
│  <com.webscare.ads.WebsCareBannerView ad_unit_id="ca-..." />           │
│  <com.webscare.ads.WebsCareNativeView  ad_unit_id="ca-..." />          │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │ dependency (JitPack)
┌──────────────────────────▼──────────────────────────────────────────────┐
│                webscare-ads (Library Module)                             │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   PUBLIC LAYER                                    │   │
│  │  WebsCareAds.kt          — Static facade (the ONE class)         │   │
│  │  AdConfig.kt              — DSL config builder                   │   │
│  │  NativeSize.kt            — Enum: SMALL, MEDIUM, FULL            │   │
│  │  WebsCareBannerView.kt   — Custom View (XML-declarable)         │   │
│  │  WebsCareNativeView.kt   — Custom View (XML-declarable)         │   │
│  │  AdNativeRecyclerAdapter.kt — Wrapper for RecyclerView          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                           │                                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   INTERNAL ENGINE                                 │   │
│  │  AdManager.kt           — Core: cache, premium gate, router      │   │
│  │  ConsentManager.kt      — Google UMP wrapper                     │   │
│  │  FrequencyCap.kt        — Per-format cooldown tracker            │   │
│  │  AdPreloader.kt         — Background preloading                  │   │
│  │  AdExpiryTracker.kt     — 4-hour expiry for loaded ads           │   │
│  │  LifecycleObserver.kt   — ProcessLifecycleOwner integration      │   │
│  │  RetryPolicy.kt         — Exponential backoff for loads          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                           │                                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   AD HANDLERS (one per format)                    │   │
│  │  AppOpenAdHandler.kt                                              │   │
│  │  BannerAdHandler.kt      (adaptive + collapsible + inline)       │   │
│  │  NativeAdHandler.kt      (load, populate, destroy lifecycle)     │   │
│  │  InterstitialHandler.kt  (with frequency cap gate)               │   │
│  │  RewardedHandler.kt      (rewarded + rewarded interstitial)      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                           │                                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   UTILITIES                                       │   │
│  │  AdLogger.kt             — Tagged logging (auto-off release)     │   │
│  │  NetworkCheck.kt         — Connectivity pre-check                │   │
│  │  TestAdIds.kt            — All Google test ad unit IDs           │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                           │                                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   LAYOUTS & RESOURCES                              │   │
│  │  res/layout/                                                      │   │
│  │    webscare_native_small.xml        — Compact inline native      │   │
│  │    webscare_native_medium.xml       — Card-style native          │   │
│  │    webscare_native_full.xml         — Full-width with media      │   │
│  │    webscare_native_recycler_item.xml— For RecyclerView slots     │   │
│  │    webscare_shimmer_small.xml       — Loading placeholder        │   │
│  │    webscare_shimmer_medium.xml      — Loading placeholder        │   │
│  │    webscare_shimmer_full.xml        — Loading placeholder        │   │
│  │    webscare_banner_container.xml    — Self-contained banner      │   │
│  │  res/values/                                                      │   │
│  │    webscare_attrs.xml               — Custom view attributes     │   │
│  │    webscare_colors.xml              — Themed ad colors            │   │
│  │    webscare_styles.xml              — Ad text/button styles       │   │
│  │    webscare_dimens.xml              — Consistent spacing          │   │
│  │  res/values-night/                                                │   │
│  │    webscare_colors.xml              — Dark mode overrides         │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                           │                                             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   PROGUARD                                        │   │
│  │  consumer-rules.pro     — Auto-applied to consuming apps         │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Every Ad Type — Design, Layout, Placement Rules, and Constraints

### 3.1 App Open Ad

| Aspect | Detail |
|--------|--------|
| **Format** | Full-screen overlay with branding + close button |
| **When to show** | After splash completes (cold start) OR on foreground resume (warm start) |
| **Layout** | No custom layout — Google renders it |
| **Preloading** | Load in `Application.onCreate()`, reload after each show |
| **Expiry** | **4-hour rule** — loaded ads expire. `AdExpiryTracker` checks timestamp before show |
| **Cold start** | Load ad *parallel* to splash video. If ad not ready when video ends → skip (never block user) |
| **Warm start** | Only show if user was in background >= `minBackgroundSeconds` (configurable, default 30s) |
| **Conflicts** | NEVER show over another ad. `AdManager` tracks `isFullScreenAdShowing` flag |
| **Premium** | Skip entirely if `premiumCheck() == true` |

**Handler API:**
```kotlin
internal class AppOpenAdHandler {
    fun preload(context: Context, adUnitId: String)
    fun showIfReady(activity: Activity, onDismissed: () -> Unit)
    fun isAdAvailable(): Boolean  // checks loaded + not expired
}
```

---

### 3.2 Banner Ads (3 Variants)

#### 3.2.1 Anchored Adaptive Banner
| Aspect | Detail |
|--------|--------|
| **Size** | Auto-calculated: full screen width x optimal height |
| **Placement** | Bottom of screen (Settings, utility screens) |
| **Layout** | Inflated into a `FrameLayout` container provided by the app |
| **Height reservation** | Container must have `wrap_content` height — ad calculates its own |
| **Lifecycle** | `pause()` in `onPause()`, `resume()` in `onResume()`, `destroy()` in `onDestroyView()` |
| **Auto-refresh** | AdMob handles refresh automatically (30-120s configurable in AdMob console) |

#### 3.2.2 Collapsible Banner
| Aspect | Detail |
|--------|--------|
| **Size** | Initially expanded (larger overlay), user can collapse to standard adaptive |
| **Placement** | Bottom of static screens only — NOT on scrolling content |
| **Implementation** | Same as adaptive + `extras.putString("collapsible", "bottom")` in `AdRequest` |
| **Constraint** | Do NOT request collapsible on every impression — use flag `collapsibleShownThisSession` to show max 1x per session |
| **Height management** | Container animates height change when user collapses |

#### 3.2.3 Inline Adaptive Banner (for ScrollViews/RecyclerViews)
| Aspect | Detail |
|--------|--------|
| **Size** | `AdSize.getInlineAdaptiveBannerAdSize(width, maxHeight)` |
| **Placement** | Between content sections in a ScrollView |
| **Key difference** | Unlike anchored, inline banners scroll with content |

**WebsCareBannerView — XML Custom View:**
```xml
<!-- App developer just drops this into their layout XML -->
<com.webscare.ads.WebsCareBannerView
    android:id="@+id/settingsBanner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:webscare_ad_unit_id="ca-app-pub-xxx/yyy"
    app:webscare_banner_type="adaptive"
    app:layout_constraintBottom_toBottomOf="parent" />
```

**Custom attributes defined in `webscare_attrs.xml`:**
```xml
<declare-styleable name="WebsCareBannerView">
    <attr name="webscare_ad_unit_id" format="string" />
    <attr name="webscare_banner_type" format="enum">
        <enum name="adaptive" value="0" />
        <enum name="collapsible_bottom" value="1" />
        <enum name="collapsible_top" value="2" />
        <enum name="inline" value="3" />
    </attr>
</declare-styleable>
```

The view internally handles:
- Auto-loading when attached to window
- Lifecycle binding (pause/resume/destroy)
- Premium check (self-hides if premium)
- Shimmer placeholder until ad loads
- Graceful collapse to 0dp height if ad fails

---

### 3.3 Native Advanced Ads (3 Size Variants + RecyclerView Adapter)

#### 3.3.1 Native Layout Variants

**SMALL** (`webscare_native_small.xml`) — For tight spaces:
```
┌─────────────────────────────────────────┐
│ [Icon] Headline              [Ad] [CTA] │
│        Advertiser                        │
└─────────────────────────────────────────┘
Height: ~72dp
```

**MEDIUM** (`webscare_native_medium.xml`) — Card-style with media:
```
┌─────────────────────────────────────────┐
│ [Icon] Headline                    [Ad] │
│        Advertiser                        │
│ ┌─────────────────────────────────────┐ │
│ │           MediaView (16:9)          │ │
│ └─────────────────────────────────────┘ │
│ Body text (2 lines max)         [CTA →] │
└─────────────────────────────────────────┘
Height: ~280dp
```

**FULL** (`webscare_native_full.xml`) — Maximum visibility:
```
┌─────────────────────────────────────────┐
│ [Icon] Headline                    [Ad] │
│        Advertiser                        │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │         MediaView (4:3)             │ │
│ │                                     │ │
│ └─────────────────────────────────────┘ │
│ Body text (3 lines max)                  │
│ ★★★★★  (StarRating if available)        │
│ Store badge        [Install / CTA  →]   │
└─────────────────────────────────────────┘
Height: ~380dp
```

#### 3.3.2 Shimmer Loading Placeholders

Every native layout has a matching shimmer placeholder (`webscare_shimmer_*.xml`):
- Shimmer animation plays while ad loads
- Fixed height matching the real ad layout (prevents content jump / accidental clicks)
- Automatically swaps to real ad via crossfade animation

#### 3.3.3 Native Ad Layout Requirements (AdMob Policy Compliance)

| Element | Required? | View Type | ID |
|---------|-----------|-----------|-----|
| `NativeAdView` | **YES** | Root wrapper | `@+id/webscare_native_ad_view` |
| Headline | **YES** | `TextView` | `@+id/ad_headline` |
| MediaView | **YES** | `MediaView` | `@+id/ad_media` |
| "Ad" attribution | **YES** | `TextView` | `@+id/ad_attribution` |
| Icon | Recommended | `ImageView` | `@+id/ad_app_icon` |
| Body | Recommended | `TextView` | `@+id/ad_body` |
| CTA button | Recommended | `Button` | `@+id/ad_call_to_action` |
| Advertiser | Optional | `TextView` | `@+id/ad_advertiser` |
| Star rating | Optional | `RatingBar` | `@+id/ad_stars` |
| Store | Optional | `TextView` | `@+id/ad_store` |
| Price | Optional | `TextView` | `@+id/ad_price` |

#### 3.3.4 Dark Mode Support

Two color resource files shipped:

**`res/values/webscare_colors.xml`** (light mode) and **`res/values-night/webscare_colors.xml`** (dark mode).

Apps can override any colors in their own `colors.xml` to match their brand.

#### 3.3.5 WebsCareNativeView — XML Custom View

```xml
<com.webscare.ads.WebsCareNativeView
    android:id="@+id/homeNativeAd"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:webscare_ad_unit_id="ca-app-pub-xxx/yyy"
    app:webscare_native_size="medium"
    app:webscare_show_shimmer="true" />
```

Internally:
1. Shows shimmer placeholder immediately (fixed height = prevents content jump)
2. Loads native ad in background
3. Crossfades from shimmer to real ad
4. If load fails → collapses to 0dp with animation (no blank space left behind)
5. If premium → never inflates at all (View.GONE)
6. Calls `nativeAd.destroy()` on `onDetachedFromWindow()`

#### 3.3.6 AdNativeRecyclerAdapter — RecyclerView Integration

```kotlin
// App code — ONE LINE to inject native ads into any RecyclerView
val adAdapter = WebsCareAds.wrapWithNativeAds(
    originalAdapter = myTemplatesAdapter,
    adUnitId = "ca-app-pub-xxx/yyy",
    interval = 6,
    nativeSize = NativeSize.MEDIUM,
    startOffset = 3
)
recyclerView.adapter = adAdapter
```

Key constraints:
- Pre-loads a batch of 5 ads via `AdLoader.Builder().loadAds(AdRequest, 5)`
- Reloads next batch when 3 remaining
- Calls `nativeAd.destroy()` in `onViewRecycled()` to prevent leaks
- Proxies `DiffUtil` notifications from the wrapped adapter
- If premium → acts as transparent passthrough (no ads injected)
- If no ads loaded → positions collapse (no empty slots)

---

### 3.4 Interstitial Ads

| Aspect | Detail |
|--------|--------|
| **Format** | Full-screen overlay |
| **When to show** | On navigation transitions, action completions (export, save) |
| **Preloading** | Auto-preloads next ad after each show |
| **Frequency cap** | Configurable (default 3 minutes). `FrequencyCap` blocks if cooldown active |
| **Fallback** | If ad not loaded → callback fires immediately (never blocks user flow) |
| **Policy** | NEVER show on back press, NEVER show during content interaction |

**Handler internal flow:**
```
App calls showInterstitial(activity, id, onDismissed)
    ├─ premiumCheck() == true  → onDismissed() immediately, return
    ├─ frequencyCap.canShow(id) == false → onDismissed() immediately, return
    ├─ cachedAd == null → onDismissed() immediately, preload for next time
    ├─ activity.isFinishing → onDismissed() immediately, return
    └─ Show ad
         ├─ onAdDismissedFullScreenContent → onDismissed(), preloadNext()
         └─ onAdFailedToShowFullScreenContent → onDismissed(), preloadNext()
```

**Lifecycle-safe callbacks** — all callbacks check `lifecycle.currentState.isAtLeast(STARTED)` before firing.

---

### 3.5 Rewarded Ads

| Aspect | Detail |
|--------|--------|
| **Format** | Full-screen video, user must watch to earn reward |
| **User consent** | **MANDATORY** — user must explicitly opt in (tap "Watch Ad" button) |
| **Preloading** | Preload when entering a screen that may need it |
| **Loading state** | If user taps "Watch Ad" but ad isn't loaded → show progress dialog |
| **Reward callback** | Only fires if `onUserEarnedReward` is triggered by SDK |
| **Frequency cap** | Configurable (default 1 minute) |
| **Fallback** | If ad unavailable → show toast "Ad not available, try again later" |

**Two sub-types:**

| Type | Use Case |
|------|----------|
| **Rewarded** | User explicitly chooses to watch. "Watch ad to unlock AI Background Removal" |
| **Rewarded Interstitial** | Hybrid — shows reward screen but can be more interstitial-like |

---

### 3.6 Empty State Replacement

```kotlin
WebsCareAds.loadNativeOrEmpty(
    activity = requireActivity(),
    container = binding.emptyStateContainer,
    adUnitId = AD_ID,
    nativeSize = NativeSize.FULL,
    emptyView = binding.defaultEmptyState
)
```

Logic:
1. If content exists → hide container entirely
2. If content empty AND premium → show `emptyView`
3. If content empty AND NOT premium → load native ad into container
4. If native ad fails to load → fall back to `emptyView`

---

## 4. Complete Project Structure

```
WebsCareAds/                              ← New GitHub repo
├── webscare-ads/                         ← Library module
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/webscare/ads/
│       │   ├── WebsCareAds.kt                     ← PUBLIC: Static facade
│       │   ├── AdConfig.kt                        ← PUBLIC: Configuration DSL
│       │   ├── NativeSize.kt                      ← PUBLIC: Enum (SMALL/MEDIUM/FULL)
│       │   ├── WebsCareBannerView.kt              ← PUBLIC: Drop-in banner View
│       │   ├── WebsCareNativeView.kt              ← PUBLIC: Drop-in native View
│       │   ├── AdNativeRecyclerAdapter.kt         ← PUBLIC: RecyclerView wrapper
│       │   └── internal/
│       │       ├── AdManager.kt
│       │       ├── ConsentManager.kt
│       │       ├── FrequencyCap.kt
│       │       ├── AdPreloader.kt
│       │       ├── AdExpiryTracker.kt
│       │       ├── LifecycleObserver.kt
│       │       ├── RetryPolicy.kt
│       │       ├── NativeAdPopulator.kt
│       │       ├── handlers/
│       │       │   ├── AdHandler.kt
│       │       │   ├── AppOpenAdHandler.kt
│       │       │   ├── BannerAdHandler.kt
│       │       │   ├── NativeAdHandler.kt
│       │       │   ├── InterstitialHandler.kt
│       │       │   └── RewardedHandler.kt
│       │       └── utils/
│       │           ├── AdLogger.kt
│       │           ├── NetworkCheck.kt
│       │           └── TestAdIds.kt
│       └── res/
│           ├── layout/
│           │   ├── webscare_native_small.xml
│           │   ├── webscare_native_medium.xml
│           │   ├── webscare_native_full.xml
│           │   ├── webscare_native_recycler_item.xml
│           │   ├── webscare_shimmer_small.xml
│           │   ├── webscare_shimmer_medium.xml
│           │   ├── webscare_shimmer_full.xml
│           │   ├── webscare_banner_container.xml
│           │   └── webscare_loading_dialog.xml
│           ├── values/
│           │   ├── webscare_attrs.xml
│           │   ├── webscare_colors.xml
│           │   ├── webscare_styles.xml
│           │   └── webscare_dimens.xml
│           ├── values-night/
│           │   └── webscare_colors.xml
│           └── drawable/
│               ├── webscare_ad_badge_bg.xml
│               ├── webscare_cta_bg.xml
│               ├── webscare_card_bg.xml
│               └── webscare_shimmer_block.xml
│
├── sample/                               ← Demo app
│   ├── build.gradle.kts
│   └── src/main/java/com/webscare/ads/sample/
│       ├── SampleApp.kt
│       ├── MainActivity.kt
│       ├── BannerDemoActivity.kt
│       ├── NativeDemoActivity.kt
│       ├── RecyclerDemoActivity.kt
│       └── RewardedDemoActivity.kt
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── jitpack.yml
└── README.md
```

---

## 5. The Complete Public API

```kotlin
object WebsCareAds {

    // ── INITIALIZATION ────────────────────────────────────────────────
    fun init(application: Application, config: AdConfig.() -> Unit = {})

    // ── APP OPEN ──────────────────────────────────────────────────────
    fun preloadAppOpen(context: Context, adUnitId: String)
    fun showAppOpen(activity: Activity, adUnitId: String, onDismissed: () -> Unit = {})
    fun enableAutoAppOpen(adUnitId: String, minBackgroundSeconds: Int = 30)

    // ── BANNER ────────────────────────────────────────────────────────
    fun loadBanner(activity: Activity, container: ViewGroup, adUnitId: String)
    fun loadCollapsibleBanner(activity: Activity, container: ViewGroup, adUnitId: String, position: CollapsiblePosition = CollapsiblePosition.BOTTOM)
    fun loadInlineBanner(activity: Activity, container: ViewGroup, adUnitId: String, maxHeightDp: Int = 250)

    // ── NATIVE ────────────────────────────────────────────────────────
    fun loadNative(activity: Activity, container: ViewGroup, adUnitId: String, size: NativeSize = NativeSize.MEDIUM)
    fun loadNative(activity: Activity, container: ViewGroup, adUnitId: String, customLayoutResId: Int)
    fun loadNativeOrEmpty(activity: Activity, container: ViewGroup, adUnitId: String, size: NativeSize, emptyView: View)

    // ── NATIVE RECYCLERVIEW ───────────────────────────────────────────
    fun <VH : RecyclerView.ViewHolder> wrapWithNativeAds(
        originalAdapter: RecyclerView.Adapter<VH>,
        adUnitId: String,
        interval: Int = 6,
        startOffset: Int = 3,
        nativeSize: NativeSize = NativeSize.MEDIUM
    ): RecyclerView.Adapter<*>

    // ── INTERSTITIAL ──────────────────────────────────────────────────
    fun preloadInterstitial(context: Context, adUnitId: String)
    fun showInterstitial(activity: Activity, adUnitId: String, onDismissed: () -> Unit = {})

    // ── REWARDED ──────────────────────────────────────────────────────
    fun preloadRewarded(context: Context, adUnitId: String)
    fun showRewarded(
        activity: Activity, adUnitId: String,
        onRewarded: (rewardType: String, rewardAmount: Int) -> Unit,
        onDismissed: () -> Unit = {},
        onNotReady: () -> Unit = {}
    )
    fun showRewardedInterstitial(
        activity: Activity, adUnitId: String,
        onRewarded: (rewardType: String, rewardAmount: Int) -> Unit,
        onDismissed: () -> Unit = {},
        onNotReady: () -> Unit = {}
    )

    // ── UTILITIES ─────────────────────────────────────────────────────
    fun setTestDeviceIds(vararg deviceIds: String)
    fun isAdLoaded(adUnitId: String): Boolean
    fun destroyAllAds()
    fun showConsentForm(activity: Activity)
    fun openAdInspector(activity: Activity)
}
```

---

## 6. Integration into UrduPhotoDesigner

> [!IMPORTANT]
> **Before ANY code changes**, a new branch `feature/ads-integration` will be created from the current `main`/`master` branch. All ads integration work in UrduPhotoDesigner happens on this branch. It will only be merged back after full testing.

### Step 0: Create Branch

```bash
cd C:\Users\WebsCare\Documents\GitHub\UrduPhotoDesigner
git checkout -b feature/ads-integration
```

### Step 1: Add Dependency

**[build.gradle.kts](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/build.gradle.kts):**
```diff
dependencies {
+    implementation("com.github.WebsCare:WebsCareAds:1.0.0")
}
```

### Step 2: Init in Application

**[MyApplication.kt](file:///c:/Users/WebsCare/Documents/GitHub/UrduPhotoDesigner/app/src/main/java/com/webscare/urducanvas/MyApplication.kt):**
```diff
+import com.webscare.ads.WebsCareAds
+import kotlin.time.Duration.Companion.minutes

 @HiltAndroidApp
 class MyApplication : Application() {
+    @Inject lateinit var billingManager: BillingManager

     override fun onCreate() {
         super.onCreate()
+        WebsCareAds.init(this) {
+            testMode = BuildConfig.DEBUG
+            premiumCheck = { billingManager.isSubscribed.value }
+            interstitialCooldown = 3.minutes
+            rewardedCooldown = 1.minutes
+            enableConsent = true
+            onAdRevenue = { type, valueMicros, currency ->
+                // Log to Firebase Analytics for eCPM tracking
+            }
+        }
     }
 }
```

### Step 3: Ad Unit ID Mapping

Your 9 ad units from AdMob, mapped to screens:

| Ad Unit Name | Format | Ad Unit ID | Screen | BuildConfig Field |
|-------------|--------|-----------|--------|-------------------|
| Splash App Open | App Open | `ca-app-pub-4379805490947109/2348663407` | Splash | `AD_APP_OPEN_SPLASH` |
| Home Native | Native Advanced | `ca-app-pub-4379805490947109/5002746903` | Home | `AD_NATIVE_HOME` |
| Categories Native | Native Advanced | `ca-app-pub-4379805490947109/1115472783` | Template Categories | `AD_NATIVE_CATEGORIES` |
| Templates Native | Native Advanced | `ca-app-pub-4379805490947109/9281023402` | Templates List | `AD_NATIVE_TEMPLATES` |
| Empty State Native | Native Advanced | `ca-app-pub-4379805490947109/9852014833` | My Files (empty) | `AD_NATIVE_EMPTY_STATE` |
| AI Background Removal Rewarded | Rewarded | `ca-app-pub-4379805490947109/5341778394` | AI BG Removal | `AD_REWARDED_BG_REMOVAL` |
| Export Interstitial | Interstitial | `ca-app-pub-4379805490947109/7489309447` | Export Settings | `AD_INTERSTITIAL_EXPORT` |
| Export Success Native | Native Advanced | `ca-app-pub-4379805490947109/2081336029` | Export Success | `AD_NATIVE_EXPORT_SUCCESS` |
| Settings Banner | Banner | `ca-app-pub-4379805490947109/9089451712` | Settings | `AD_BANNER_SETTINGS` |

### Step 4: Add Ads to Each Screen

| Screen | Code to Add |
|--------|------------|
| **Splash** | `WebsCareAds.showAppOpen(activity, BuildConfig.AD_APP_OPEN_SPLASH) { navigateToHome() }` |
| **Home** | `WebsCareAds.loadNative(activity, binding.adContainer, BuildConfig.AD_NATIVE_HOME, NativeSize.MEDIUM)` |
| **Template Categories** | `WebsCareAds.wrapWithNativeAds(adapter, BuildConfig.AD_NATIVE_CATEGORIES, interval=5, startOffset=3)` |
| **Templates List** | `WebsCareAds.wrapWithNativeAds(adapter, BuildConfig.AD_NATIVE_TEMPLATES, interval=7)` |
| **My Files (empty)** | `WebsCareAds.loadNativeOrEmpty(activity, container, BuildConfig.AD_NATIVE_EMPTY_STATE, NativeSize.FULL, emptyView)` |
| **Create Canvas** | No ads |
| **Editor** | No ads |
| **AI BG Removal** | `WebsCareAds.showRewarded(activity, BuildConfig.AD_REWARDED_BG_REMOVAL, onRewarded = { ... })` |
| **Export Settings** | `WebsCareAds.showInterstitial(activity, BuildConfig.AD_INTERSTITIAL_EXPORT) { startExport() }` |
| **Export Success** | `WebsCareAds.loadNative(activity, binding.adContainer, BuildConfig.AD_NATIVE_EXPORT_SUCCESS, NativeSize.MEDIUM)` |
| **Settings** | `WebsCareAds.loadBanner(activity, binding.bannerContainer, BuildConfig.AD_BANNER_SETTINGS)` |
| **Subscription** | No ads |

---

## 7. Test Ad IDs vs. Real Ad IDs Strategy

> [!CAUTION]
> Using live ad IDs during development and accidentally clicking ads **will get your AdMob account permanently suspended**. The library makes this impossible.

### How It Works

```kotlin
WebsCareAds.init(this) {
    testMode = BuildConfig.DEBUG   // ← THIS IS THE KEY LINE
}
```

| Build Type | `BuildConfig.DEBUG` | `testMode` | Ad IDs Used |
|-----------|--------------------|-----------|-----------:|
| **Debug** (dev/testing) | `true` | `true` | Google's official test IDs (hardcoded in library) |
| **Release** (Play Store) | `false` | `false` | Your real ad unit IDs (passed in code) |

### What Happens Internally

```kotlin
internal fun resolveAdUnitId(passedId: String, adType: AdType): String {
    if (config.testMode) {
        return when (adType) {
            AdType.APP_OPEN       -> TestAdIds.APP_OPEN
            AdType.BANNER         -> TestAdIds.BANNER
            AdType.COLLAPSIBLE    -> TestAdIds.COLLAPSIBLE
            AdType.INTERSTITIAL   -> TestAdIds.INTERSTITIAL
            AdType.REWARDED       -> TestAdIds.REWARDED
            AdType.REWARDED_INTER -> TestAdIds.REWARDED_INTER
            AdType.NATIVE         -> TestAdIds.NATIVE
        }
    }
    return passedId  // Real ID used in release
}
```

### Where Real Ad IDs Live in UrduPhotoDesigner

All 9 ad unit IDs stored as `BuildConfig` fields in one place:

```kotlin
// In app/build.gradle.kts:
android {
    defaultConfig {
        // App Open
        buildConfigField("String", "AD_APP_OPEN_SPLASH",       '"ca-app-pub-4379805490947109/2348663407"')
        // Native Advanced
        buildConfigField("String", "AD_NATIVE_HOME",            '"ca-app-pub-4379805490947109/5002746903"')
        buildConfigField("String", "AD_NATIVE_CATEGORIES",      '"ca-app-pub-4379805490947109/1115472783"')
        buildConfigField("String", "AD_NATIVE_TEMPLATES",       '"ca-app-pub-4379805490947109/9281023402"')
        buildConfigField("String", "AD_NATIVE_EMPTY_STATE",     '"ca-app-pub-4379805490947109/9852014833"')
        buildConfigField("String", "AD_NATIVE_EXPORT_SUCCESS",  '"ca-app-pub-4379805490947109/2081336029"')
        // Interstitial
        buildConfigField("String", "AD_INTERSTITIAL_EXPORT",    '"ca-app-pub-4379805490947109/7489309447"')
        // Rewarded
        buildConfigField("String", "AD_REWARDED_BG_REMOVAL",    '"ca-app-pub-4379805490947109/5341778394"')
        // Banner
        buildConfigField("String", "AD_BANNER_SETTINGS",        '"ca-app-pub-4379805490947109/9089451712"')
    }
}
```

> [!NOTE]
> **AdMob App ID** (for `AndroidManifest.xml`): Each consuming app declares its own:
> ```xml
> <meta-data
>     android:name="com.google.android.gms.ads.APPLICATION_ID"
>     android:value="ca-app-pub-4379805490947109~YOUR_APP_ID" />
> ```

---

## 8. Placement Rules & Constraints Matrix

Every rule is enforced **inside the library**, not left for the consuming app to remember:

| Rule | Enforcement |
|------|-------------|
| Premium users see zero ads | `premiumCheck()` called before every operation. If true → skip + invoke callback immediately |
| No two full-screen ads overlap | `isFullScreenAdShowing` flag in AdManager. Second call queued or dropped |
| Interstitials frequency-capped | `FrequencyCap` blocks calls within cooldown window |
| Rewarded ads need explicit user action | Library never auto-shows. Documentation enforces "call from user tap" |
| App Open Ads expire after 4 hours | `AdExpiryTracker` stores load timestamp. `isAdAvailable()` checks before show |
| Collapsible banners max 1x/session | `collapsibleShownThisSession` boolean flag |
| Native ads destroy on recycle | `onViewRecycled()` calls `nativeAd.destroy()` |
| Banner ads pause/resume with lifecycle | `WebsCareBannerView` implements `DefaultLifecycleObserver` |
| No ads on destroyed Activity | All callbacks check `lifecycle.currentState.isAtLeast(STARTED)` |
| No ads without network | `NetworkCheck.isConnected()` before every load |
| Test ads in debug builds | `testMode` swaps all unit IDs to Google's official test IDs |
| "Ad" attribution visible | Built into every native layout XML — not removable by consumer |
| Content doesn't shift on ad load | Shimmer placeholders reserve exact height before ad loads |
| Consent obtained before personalized ads | `ConsentManager` runs during `init()` |
| Failed loads retry with backoff | `RetryPolicy` retries 3x with exponential delay (1s → 2s → 4s) |

---

## 9. Dependencies the Library Brings

```kotlin
// build.gradle.kts of webscare-ads module
dependencies {
    // Google Mobile Ads SDK (required)
    api("com.google.android.gms:play-services-ads:24.4.0")

    // UMP Consent SDK (required for GDPR)
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // Lifecycle (for ProcessLifecycleOwner - App Open Ad)
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.9.1")

    // Shimmer (for native ad loading placeholders)
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Kotlin Coroutines (for retry/preloading)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

---

## 10. Development Phases

### Phase 1 — Skeleton + Core Engine (3-4 days)
- [x] Create new GitHub repo `WebsCareAds`
- [x] Set up Gradle multi-module structure (`webscare-ads` + `sample`)
- [x] Implement `WebsCareAds.kt` facade (public API surface)
- [x] Implement `AdConfig.kt` DSL
- [x] Implement `AdManager.kt` core (premium gate, routing, lifecycle callbacks)
- [x] Implement `ConsentManager.kt` (UMP integration)
- [x] Implement `FrequencyCap.kt`
- [x] Implement `NetworkCheck.kt`, `AdLogger.kt`, `TestAdIds.kt`

### Phase 2 — Banner + Interstitial Handlers (2-3 days)
> The `resolveAdUnitId()` test/release switching is built into every handler from day one.
- [x] Implement `BannerAdHandler.kt` (adaptive + collapsible + inline)
- [x] Implement `InterstitialHandler.kt` (with frequency cap + lifecycle safety)
- [x] Implement `WebsCareBannerView.kt` (XML custom view)
- [x] Implement `AdPreloader.kt`
- [x] Implement `RetryPolicy.kt`
- [x] Create `webscare_banner_container.xml`
- [x] Create `webscare_attrs.xml` for custom view attributes

### Phase 3 — Native Ads (3-4 days)
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

### Phase 3.5 — Hardening & Core Architecture Rules (2-3 days)
- [ ] **Dynamic Premium Toggling:** Add direct `WebsCareAds.setPremium(enabled)` helper that updates `AdManager`, immediately destroys cached ads, releases resources, and hides active banner/native containers.
- [ ] **Warm Start & Launch Cooldowns:** Implement a post-AppOpen cooldown timer (e.g. block interstitials for 60-120 seconds after an App Open ad is shown, avoiding double-ad bombardment).
- [ ] **Remote Config Flags:** Build runtime-mutable toggle flags inside `AdConfig` (e.g. `home_native_enabled`, `templates_native_enabled`, `export_interstitial_enabled`, `settings_banner_enabled`, `rewarded_enabled`, `app_open_enabled`) so apps can enable/disable individual placements dynamically.
- [ ] **Offline Handling & Collapsing UI:** If network is unavailable:
  - Return false/cancel load instantly (no AdMob request sent).
  - Force active custom containers (`WebsCareBannerView`, `WebsCareNativeView`) to immediately collapse to `GONE` with a smooth height transition to preserve visual design.
- [ ] **Preload Caching & Expiry Handling:**
  - Standardize background preloading lifecycle for Interstitial and Rewarded ads.
  - Implement caching timers — automatically drop/expire cached ads after 4 hours and fetch fresh ones.
- [ ] **Non-Blocking Fallback:** Ensure that if an interstitial/rewarded ad is not cached, not ready, or capped, the requesting action proceeds instantly (never delay users or show loader overlays indefinitely).

### Phase 4 — RecyclerView + Rewarded + App Open (3-4 days)
- [ ] Implement `AdNativeRecyclerAdapter.kt`
- [ ] Implement batch loading via `AdLoader.loadAds()`
- [ ] Implement `RewardedHandler.kt` (Rewarded + Rewarded Interstitial)
- [ ] Implement `AppOpenAdHandler.kt` with `ProcessLifecycleOwner`
- [ ] Implement `AdExpiryTracker.kt`
- [ ] Implement `LifecycleObserver.kt`
- [ ] Implement `loadNativeOrEmpty()`

### Phase 5 — Sample App + Testing (2-3 days)
- [ ] Build sample app with screens for each ad type
- [ ] Test all 6 ad types with Google test IDs
- [ ] Test premium bypass
- [ ] Test frequency capping
- [ ] Test lifecycle edge cases
- [ ] Test dark mode
- [ ] Test RecyclerView adapter

### Phase 6 — Publish + Integrate (2 days)
- [ ] Write `README.md`
- [ ] Add `consumer-rules.pro`
- [ ] Add `jitpack.yml`
- [ ] **Create `feature/ads-integration` branch in UrduPhotoDesigner**
- [ ] Tag v1.0.0 → verify JitPack build
- [ ] Integrate into UrduPhotoDesigner (on `feature/ads-integration` branch)
- [ ] Add ad containers to relevant layouts
- [ ] Test on physical device

---

## 11. Verification Plan

### Automated Tests
```bash
./gradlew :webscare-ads:testDebugUnitTest

# Tests cover:
# - FrequencyCap: cooldown math, edge cases
# - AdExpiryTracker: 4-hour expiry
# - AdConfig: DSL builder
# - RetryPolicy: backoff timing
# - AdNativeRecyclerAdapter: position mapping, item count math
```

### Manual Verification Checklist
- [ ] Run sample app → all 6 ad types render with Google test ads
- [ ] Set `premiumCheck = { true }` or `setPremium(true)` → verify absolutely zero ads appear
- [ ] Rapid-fire `showInterstitial()` 5x → only 1st shows, rest are bypassed
- [ ] Background app for 30s+ → App Open Ad shows on return
- [ ] Background app for 5s → App Open Ad does NOT show
- [ ] Rotate device while interstitial is showing → no crash
- [ ] Kill app process → restart → App Open Ad still works
- [ ] Enable dark mode → all native layouts render correctly
- [ ] RecyclerView with 0 items → no ads injected (no crash)
- [ ] RecyclerView with 3 items + interval=6 → no ads (not enough items)
- [ ] RecyclerView with 20 items + interval=6 → ads at correct positions
- [ ] Turn off WiFi/data → ads fail gracefully, no crashes (views collapse to 0dp)
- [ ] Simulate EEA locale → UMP consent dialog appears
- [ ] Tap "Privacy" in Settings → consent form re-shows

---

## 12. Resolved Decisions

| # | Decision | Answer |
|---|----------|--------|
| 1 | **Repo location** | ✅ `C:\Users\WebsCare\Documents\GitHub\WebsCareAds\` |
| 2 | **Package name** | ✅ `com.webscare.ads` |
| 3 | **minSdk** | ✅ `minSdk = 23` — does NOT affect any app features. UrduPhotoDesigner (minSdk 24) works perfectly |
| 4 | **AdMob App ID** | ✅ Each consuming app provides its own via manifest merging |
| 5 | **Repo/Library name** | ✅ `WebsCareAds` (module: `webscare-ads`, resources: `webscare_*`) |

