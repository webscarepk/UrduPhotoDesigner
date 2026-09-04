package com.webscare.urducanvas.common.canvas.model

/**
 * A named material swatch: a colour *and* the finish it is shown in.
 *
 * The 3D panel already offers 52 abstract finishes (Glossy, Chrome, Velvet…) tinted by
 * whatever colour is current. That answers "what is it made of" but not "which grey" —
 * and the two are not independent. Pearl is not just a pale grey, it is a pale grey with
 * a sweep sheen; Onyx is black marble; Silver is brushed metal; Denim is a blue with a
 * woven grain. Pairing the two is what makes these read as materials rather than colours.
 *
 * The finishes stay available on their own; this is the curated shortcut through them.
 */
data class Text3DSwatch(
    val name: String,
    val hex: String,
    /** id of a [Text3DSurface] in [Text3DData.SURFACES]. */
    val surfaceId: String
)

data class Text3DSwatchFamily(
    val name: String,
    val swatches: List<Text3DSwatch>
)

object Text3DPalette {

    val FAMILIES: List<Text3DSwatchFamily> = listOf(
        Text3DSwatchFamily(
            "Neutrals", listOf(
                Text3DSwatch("Pearl", "#EFEAE4", "pearl"),
                Text3DSwatch("Silver", "#C3C7CB", "brushed"),
                Text3DSwatch("Dove", "#BFBCB6", "suede"),
                Text3DSwatch("Mist", "#CFD1CC", "linen"),
                Text3DSwatch("Ash", "#B2B3AE", "concrete"),
                Text3DSwatch("Slate", "#4A5158", "granite"),
                Text3DSwatch("Smoke", "#8A8782", "matte"),
                Text3DSwatch("Graphite", "#3C3E40", "concrete"),
                Text3DSwatch("Onyx", "#141416", "marble")
            )
        ),
        Text3DSwatchFamily(
            "Blues", listOf(
                Text3DSwatch("Ice", "#CBDCEA", "frosted"),
                Text3DSwatch("Powder", "#C2D2E0", "glossy"),
                Text3DSwatch("Sky", "#9FBBD8", "linen"),
                Text3DSwatch("Azure", "#8CBFC9", "glass"),
                Text3DSwatch("Cornflower", "#7C93C4", "denim"),
                Text3DSwatch("Cobalt", "#1B2C8A", "enamel"),
                Text3DSwatch("Sapphire", "#2B4A9B", "prism"),
                Text3DSwatch("Royal", "#1B2E7A", "velvet"),
                Text3DSwatch("Navy", "#141C33", "suede")
            )
        ),
        Text3DSwatchFamily(
            "Greens", listOf(
                Text3DSwatch("Mint", "#D3DECC", "chalk"),
                Text3DSwatch("Sage", "#A8B48C", "suede"),
                Text3DSwatch("Pistachio", "#B3C07E", "linen"),
                Text3DSwatch("Olive", "#7A7C3C", "velvet"),
                Text3DSwatch("Fern", "#5F7233", "tweed"),
                Text3DSwatch("Emerald", "#1E6B4A", "prism"),
                Text3DSwatch("Jade", "#8AA792", "frosted"),
                Text3DSwatch("Forest", "#22301F", "velvet"),
                Text3DSwatch("Pine", "#16210F", "obsidian")
            )
        ),
        Text3DSwatchFamily(
            "Purples", listOf(
                Text3DSwatch("Wisteria", "#C9AFCB", "chalk"),
                Text3DSwatch("Lavender", "#B18DC4", "suede"),
                Text3DSwatch("Lilac", "#C29FCB", "matte"),
                Text3DSwatch("Orchid", "#B78CB0", "satin"),
                Text3DSwatch("Mauve", "#A5798F", "velvet"),
                Text3DSwatch("Amethyst", "#8B4FA8", "prism"),
                Text3DSwatch("Plum", "#5F1A55", "enamel"),
                Text3DSwatch("Aubergine", "#3A0E31", "velvet"),
                Text3DSwatch("Violet", "#2A1030", "glossy")
            )
        ),
        Text3DSwatchFamily(
            "Pinks", listOf(
                Text3DSwatch("Blush", "#EFCDC2", "chalk"),
                Text3DSwatch("Petal", "#EEC3C2", "suede"),
                Text3DSwatch("Rose", "#DCB4BA", "frosted"),
                Text3DSwatch("Peony", "#E0AAB2", "satin"),
                Text3DSwatch("Pink", "#D9707F", "velvet"),
                Text3DSwatch("Fuchsia", "#CE2C63", "glossy"),
                Text3DSwatch("Raspberry", "#C43A63", "velvet"),
                Text3DSwatch("Magenta", "#A81050", "velvet"),
                Text3DSwatch("Mulberry", "#6B0F33", "glossy")
            )
        ),
        Text3DSwatchFamily(
            "Reds", listOf(
                Text3DSwatch("Salmon", "#E1836C", "matte"),
                Text3DSwatch("Coral", "#E27A63", "linen"),
                Text3DSwatch("Poppy", "#CE1B22", "chalk"),
                Text3DSwatch("Scarlet", "#C81219", "enamel"),
                Text3DSwatch("Crimson", "#A61220", "velvet"),
                Text3DSwatch("Ruby", "#9E0B2C", "prism"),
                Text3DSwatch("Carmine", "#A3132C", "velvet"),
                Text3DSwatch("Garnet", "#6E101F", "velvet"),
                Text3DSwatch("Oxblood", "#3E1418", "suede")
            )
        ),
        Text3DSwatchFamily(
            "Oranges", listOf(
                Text3DSwatch("Apricot", "#F0BC96", "matte"),
                Text3DSwatch("Peach", "#F2B48C", "linen"),
                Text3DSwatch("Melon", "#EDA173", "suede"),
                Text3DSwatch("Tangerine", "#E8801A", "enamel"),
                Text3DSwatch("Marigold", "#E2620F", "chalk"),
                Text3DSwatch("Amber", "#D08B18", "glass"),
                Text3DSwatch("Pumpkin", "#D4602C", "matte"),
                Text3DSwatch("Terracotta", "#B4573A", "sandstone"),
                Text3DSwatch("Rust", "#7C4A33", "rust")
            )
        ),
        Text3DSwatchFamily(
            "Yellows", listOf(
                Text3DSwatch("Vanilla", "#EFE7D2", "matte"),
                Text3DSwatch("Butter", "#EADFB4", "linen"),
                Text3DSwatch("Lemon", "#EFD05A", "matte"),
                Text3DSwatch("Daffodil", "#EDD583", "chalk"),
                Text3DSwatch("Canary", "#F0BC22", "glossy"),
                Text3DSwatch("Honey", "#C88A16", "glass"),
                Text3DSwatch("Mustard", "#CE9A22", "suede"),
                Text3DSwatch("Ochre", "#B0700F", "chalk"),
                Text3DSwatch("Saffron", "#C5711A", "velvet")
            )
        ),
        Text3DSwatchFamily(
            "Browns", listOf(
                Text3DSwatch("Sand", "#CBAA6E", "linen"),
                Text3DSwatch("Fawn", "#C6AE8E", "suede"),
                Text3DSwatch("Camel", "#B4936A", "suede"),
                Text3DSwatch("Caramel", "#9C5A15", "glass"),
                Text3DSwatch("Tan", "#C39A66", "matte"),
                Text3DSwatch("Chestnut", "#6E3320", "leather"),
                Text3DSwatch("Cognac", "#57301B", "wood"),
                Text3DSwatch("Coffee", "#331F14", "granite"),
                Text3DSwatch("Espresso", "#241611", "matte")
            )
        )
    )

    /** Every swatch, flattened, in family order. */
    val ALL: List<Text3DSwatch> by lazy { FAMILIES.flatMap { it.swatches } }

    /**
     * Falls back to the plain finish if a swatch names a surface that has since been
     * renamed, so a bad id shows the colour rather than nothing.
     */
    fun surfaceFor(swatch: Text3DSwatch): Text3DSurface =
        Text3DData.SURFACES.firstOrNull { it.id == swatch.surfaceId }
            ?: Text3DData.SURFACES.first()
}
