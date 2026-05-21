package com.webscare.urducanvas.common.utils

/**
 * Pexels super-queries for an Urdu graphic design app.
 *
 * ── Key design decisions ──────────────────────────────────────────────────────
 *
 * FEWER SUBCATEGORIES PER QUERY (2-3 max):
 *   Each query returns 80 photos. With 4 subcategories + fallback, each gets
 *   ~16 images — not enough to fill the screen so pagination never fires.
 *   With 2-3 subcategories + fallback, each gets ~25-40 — fills screen,
 *   user scrolls, pagination fires naturally.
 *
 * BROADER KEYWORDS:
 *   Pexels alt_text is written by photographers in plain English.
 *   "jama masjid", "minar", "sultanahmet" rarely appear.
 *   "mosque", "dome", "prayer", "lantern" always appear.
 *   Keep keywords short and universal.
 *
 * MERGED SMALL CATEGORIES:
 *   "Marble", "Wood", "Fabric", "Paper" → one "Texture" fallback.
 *   "Grunge", "Vintage", "Artistic" → one "Artistic" fallback.
 *   Better to have 1 tab with 40 images than 4 tabs with 10 each.
 *
 * ── API budget ────────────────────────────────────────────────────────────────
 *   Launch (SEED_ON_LAUNCH): 8 queries × 80 photos = 640 images
 *   Lazy (on first tab tap): 4 queries × 80 photos = 320 more
 *   Search: only if local Room results < 5
 */
object PexelsCategories {

    data class Subcategory(val tabName: String, val keywords: List<String>)

    data class SuperQuery(
        val query: String,
        val subcategories: List<Subcategory>,
        val fallbackTab: String,
        val lazyLoad: Boolean = false
    )

    val ALL: List<SuperQuery> = listOf(

        // ── 1. MOSQUE & ISLAMIC ARCHITECTURE ─────────────────────────────────
        // Query: "islamic architecture" → 770K photos on Pexels ✅
        // 2 subcategories → ~35-40 photos each → fills screen → pagination fires
        SuperQuery(
            query = "islamic architecture",
            subcategories = listOf(
                Subcategory("Mosque",      listOf("mosque", "masjid", "minaret", "minar", "dome")),
                Subcategory("Holy Places", listOf("kaaba", "mecca", "makkah", "medina", "haram", "kabah"))
            ),
            fallbackTab = "Islamic Architecture"
        ),

        // ── 2. RAMADAN & EID ─────────────────────────────────────────────────
        // Query: "ramadan" → 7.5K photos ✅ (enough for many pages)
        // Keeping 2 subcategories — Ramadan & Eid are distinct enough
        SuperQuery(
            query = "ramadan eid",
            subcategories = listOf(
                Subcategory("Ramadan",     listOf("ramadan", "ramzan", "iftar", "suhoor", "kareem")),
                Subcategory("Eid",         listOf("eid", "eid mubarak", "eid al fitr", "eid ul adha"))
            ),
            fallbackTab = "Islamic"
        ),

        // ── 3. ISLAMIC ART & CALLIGRAPHY ─────────────────────────────────────
        // Query: "arabic calligraphy" → large count ✅
        // Calligraphy and geometric patterns are the two main Islamic art forms
        SuperQuery(
            query = "arabic calligraphy islamic",
            subcategories = listOf(
                Subcategory("Calligraphy", listOf("calligraphy", "arabic calligraphy", "quran", "arabic text", "bismillah", "allah")),
                Subcategory("Islamic Pattern", listOf("arabesque", "islamic pattern", "geometric", "ornament", "moroccan", "mandala"))
            ),
            fallbackTab = "Islamic Art"
        ),

        // ── 4. WEDDING & MEHNDI ──────────────────────────────────────────────
        // Query: "wedding" → 593K photos ✅
        // Mehndi/henna is hugely relevant for South Asian weddings
        SuperQuery(
            query = "wedding",
            subcategories = listOf(
                Subcategory("Wedding Decor", listOf("wedding decor", "bridal", "reception", "wedding flowers", "wedding stage")),
                Subcategory("Mehndi",        listOf("mehndi", "henna", "henna hand", "mehendi", "bridal mehndi"))
            ),
            fallbackTab = "Wedding"
        ),

        // ── 5. GOLD & LUXURY ─────────────────────────────────────────────────
        // Query: "gold background" → 400K+ ✅
        // Gold is #1 design element in Urdu social posts
        // 2 subcategories — gold textures vs glitter/bokeh
        SuperQuery(
            query = "gold background",
            subcategories = listOf(
                Subcategory("Gold Texture", listOf("gold texture", "golden texture", "metallic", "gold foil", "gold leaf")),
                Subcategory("Glitter",      listOf("glitter", "shimmer", "sparkle", "sequin", "bokeh", "glow"))
            ),
            fallbackTab = "Gold"
        ),

        // ── 6. NATURE ────────────────────────────────────────────────────────
        // Query: "nature" → 2M+ ✅
        // Only 2 subcategories — landscape and flowers — rest go to fallback "Nature"
        // This ensures each tab gets 25+ images
        SuperQuery(
            query = "nature",
            subcategories = listOf(
                Subcategory("Landscape",   listOf("mountain", "ocean", "beach", "valley", "forest", "lake", "river", "waterfall")),
                Subcategory("Flowers",     listOf("flower", "rose", "floral", "blossom", "petal", "bouquet", "bloom", "tulip"))
            ),
            fallbackTab = "Nature"
        ),

        // ── 7. SKY & STARS ───────────────────────────────────────────────────
        // Query: "sky stars" → large count ✅
        // Sky/sunset is used in almost every Urdu quote post
        // Stars/galaxy for motivational content
        SuperQuery(
            query = "sky stars",
            subcategories = listOf(
                Subcategory("Sunset & Sky", listOf("sunset", "sunrise", "sky", "cloud", "dawn", "dusk", "golden hour", "horizon")),
                Subcategory("Stars & Galaxy", listOf("star", "galaxy", "milky way", "space", "nebula", "night sky", "cosmos"))
            ),
            fallbackTab = "Night"
        ),

        // ── 8. COLORFUL BACKGROUNDS ──────────────────────────────────────────
        // Query: "colorful background" → 100K+ ✅
        // All color-based backgrounds in one place
        // 2 subcategories max — gradient and pastel
        SuperQuery(
            query = "colorful background",
            subcategories = listOf(
                Subcategory("Gradient",    listOf("gradient", "color blend", "ombre", "smooth", "transition")),
                Subcategory("Pastel",      listOf("pastel", "soft", "baby pink", "mint", "lavender", "peach", "light color"))
            ),
            fallbackTab = "Colors"
        ),

        // ── 9. TEXTURE & SURFACE ─────────────────────────────────────────────
        // Query: "texture" → 1M+ ✅
        // All textures in one query — marble, wood, fabric, paper all go to fallback
        // Only 2 subcategories to ensure each gets enough images
        SuperQuery(
            query = "texture",
            subcategories = listOf(
                Subcategory("Marble & Stone", listOf("marble", "granite", "stone", "slate", "concrete", "rock surface")),
                Subcategory("Fabric & Silk",  listOf("fabric", "silk", "velvet", "satin", "cloth", "linen", "textile"))
            ),
            fallbackTab = "Texture"
        ),

        // ── 10. PEOPLE & PORTRAITS — lazy ────────────────────────────────────
        // Query: "portrait" → 1M+ ✅
        // Relevant for profile frames, greeting cards, social posts
        SuperQuery(
            query = "portrait",
            subcategories = listOf(
                Subcategory("Muslim",      listOf("hijab", "muslim", "hijabi", "abaya", "islamic woman", "niqab")),
                Subcategory("Silhouette",  listOf("silhouette", "shadow", "backlit", "outline person", "sunset silhouette"))
            ),
            fallbackTab = "Portraits",
            lazyLoad = true
        ),

        // ── 11. CELEBRATION & LIGHTS — lazy ──────────────────────────────────
        // Query: "celebration lights" → large count ✅
        // Birthday, party, festive content — very common in Urdu design
        SuperQuery(
            query = "celebration lights",
            subcategories = listOf(
                Subcategory("Fairy Lights", listOf("fairy light", "string light", "twinkle", "bokeh light", "warm light", "candle")),
                Subcategory("Party",        listOf("party", "birthday", "confetti", "balloon", "festive", "celebration", "anniversary"))
            ),
            fallbackTab = "Celebration",
            lazyLoad = true
        ),

        // ── 12. WATERCOLOR & ART — lazy ──────────────────────────────────────
        // Query: "watercolor" → large count ✅
        // Used heavily in Urdu poetry posts and artistic designs
        SuperQuery(
            query = "watercolor",
            subcategories = listOf(
                Subcategory("Watercolor",  listOf("watercolor", "watercolour", "paint splash", "brush stroke", "aquarelle")),
                Subcategory("Vintage",     listOf("vintage", "retro", "old paper", "aged", "antique", "grunge", "worn"))
            ),
            fallbackTab = "Artistic",
            lazyLoad = true
        ),

        // ── 13. CITY & ARCHITECTURE — lazy ───────────────────────────────────
        // Query: "city" → 600K+ ✅
        // Modern urban aesthetics for business/lifestyle posts
        SuperQuery(
            query = "city",
            subcategories = listOf(
                Subcategory("Night City",  listOf("night city", "neon", "city lights", "urban night", "skyline", "light trail")),
                Subcategory("Architecture", listOf("architecture", "building", "skyscraper", "bridge", "interior", "corridor"))
            ),
            fallbackTab = "City",
            lazyLoad = true
        )
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    val SEED_ON_LAUNCH: List<SuperQuery> get() = ALL.filter { !it.lazyLoad }
    val LAZY: List<SuperQuery> get() = ALL.filter { it.lazyLoad }

    fun classify(altText: String?, superQuery: SuperQuery): String {
        if (altText.isNullOrBlank()) return superQuery.fallbackTab
        val lower = altText.lowercase()
        for (sub in superQuery.subcategories) {
            if (sub.keywords.any { lower.contains(it) }) return sub.tabName
        }
        return superQuery.fallbackTab
    }

    val ALL_TAB_NAMES: List<String> by lazy {
        ALL.flatMap { sq -> sq.subcategories.map { it.tabName } + sq.fallbackTab }.distinct()
    }

    val BY_QUERY: Map<String, SuperQuery> by lazy { ALL.associateBy { it.query } }

    fun superQueryForTab(tabName: String): SuperQuery? =
        ALL.firstOrNull { sq ->
            sq.subcategories.any { it.tabName == tabName } || sq.fallbackTab == tabName
        }

    fun isPexelsTab(tabName: String): Boolean = superQueryForTab(tabName) != null

    const val MIN_IMAGES_FOR_OWN_TAB = 5
    const val OTHERS_TAB = "Others"
}