package com.example.urduphotodesigner.common.utils

import android.icu.lang.UCharacter
import com.example.urduphotodesigner.common.canvas.model.ColorItem
import com.example.urduphotodesigner.common.canvas.model.EmojiMeta
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import java.util.Locale

object Constants {

    const val BASE_URL = "https://dashboard.urdufonts.com/api/"
    const val X_API_KEY = "21|kxJ7qhe4kjxjhfzQs4JWG34Pv8DeuIy0ZACTFe7Y5672dc67"
    const val BASE_URL_GLIDE = "https://dashboard.urdufonts.com/"
    private val EMOTICONS = 0x1F600..0x1F64F
    private val SUPP_EMOTICONS = 0x1F910..0x1F91F
    private val ANIMAL_FACES = 0x1F400..0x1F43F
    private val WEATHER_NATURE = 0x1F300..0x1F32F
    private val FOOD_DRINK = 0x1F34F..0x1F37F
    private val SPORTS_LEISURE = 0x1F3A0..0x1F3FF
    private val TRANSPORT_MAP = 0x1F680..0x1F6FF
    private val MISC_OBJECTS = 0x1F4A0..0x1F4FF
    private val ALCHEMICAL_SYMBOLS = 0x1F700..0x1F77F
    private val GEOMETRIC_SHAPES = 0x1F780..0x1F7FF
    private val SUPP_ARROWS_C = 0x1F800..0x1F8FF
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
    private val COUNTRY_CODES = listOf(
        "AF","AX","AL","DZ","AS","AD","AO","AI","AQ","AG",
        "AR","AM","AW","AU","AT","AZ","BS","BH","BD","BB",
        "BY","BE","BZ","BJ","BM","BT","BO","BQ","BA","BW",
        "BV","BR","IO","BN","BG","BF","BI","CV","KH","CM",
        "CA","KY","CF","TD","CL","CN","CX","CC","CO","KM",
        "CG","CD","CK","CR","CI","HR","CU","CW","CY","CZ",
        "DK","DJ","DM","DO","EC","EG","SV","GQ","ER","EE",
        "ET","FK","FO","FJ","FI","FR","GF","PF","TF","GA",
        "GM","GE","DE","GH","GI","GR","GL","GD","GP","GU",
        "GT","GG","GN","GW","GY","HT","HM","VA","HN","HK",
        "HU","IS","IN","ID","IR","IQ","IE","IM","IL","IT",
        "JM","JP","JE","JO","KZ","KE","KI","KP","KR","KW",
        "KG","LA","LV","LB","LS","LR","LY","LI","LT","LU",
        "MO","MK","MG","MW","MY","MV","ML","MT","MH","MQ",
        "MR","MU","YT","MX","FM","MD","MC","MN","ME","MS",
        "MA","MZ","MM","NA","NR","NP","NL","NC","NZ","NI",
        "NE","NG","NU","NF","MP","NO","OM","PK","PW","PS",
        "PA","PG","PY","PE","PH","PN","PL","PT","PR","QA",
        "RE","RO","RU","RW","BL","SH","KN","LC","MF","PM",
        "VC","WS","SM","ST","SA","SN","RS","SC","SL","SG",
        "SX","SK","SI","SB","SO","ZA","GS","SS","ES","LK",
        "SD","SR","SJ","SE","CH","SY","TW","TJ","TZ","TH",
        "TL","TG","TK","TO","TT","TN","TR","TM","TC","TV",
        "UG","UA","AE","GB","US","UM","UY","UZ","VU","VE",
        "VN","VG","VI","WF","EH","YE","ZM","ZW"
    )

    // 2. Helper to flatten any number of ranges into Strings
    private fun flatten(vararg ranges: IntRange): List<String> =
        ranges.flatMap { range ->
            range.mapNotNull { cp ->
                runCatching { String(Character.toChars(cp)) }.getOrNull()
            }
        }

    // 3. Public lists by category
    val EMOJI_EMOTICONS: List<String> by lazy { flatten(EMOTICONS, SUPP_EMOTICONS) }
    val EMOJI_ANIMALS: List<String> by lazy { flatten(ANIMAL_FACES) }
    val EMOJI_NATURE: List<String> by lazy { flatten(WEATHER_NATURE) }
    val EMOJI_FOOD: List<String> by lazy { flatten(FOOD_DRINK) }
    val EMOJI_SPORTS: List<String> by lazy { flatten(SPORTS_LEISURE) }
    val EMOJI_TRANSPORT: List<String> by lazy { flatten(TRANSPORT_MAP) }
    val EMOJI_OBJECTS: List<String> by lazy { flatten(MISC_OBJECTS) }
    val EMOJI_ALCHEMY: List<String> by lazy { flatten(ALCHEMICAL_SYMBOLS) }
    val EMOJI_SHAPES: List<String> by lazy { flatten(GEOMETRIC_SHAPES) }
    val EMOJI_ARROWS: List<String> by lazy { flatten(SUPP_ARROWS_C) }
    val EMOJI_FLAGS: List<String> by lazy { COUNTRY_CODES.map(::countryCodeToFlag) }
    val EMOJI_LETTERS: List<String> by lazy { flatten(REGIONAL_INDICATORS) }

    // 5. Build name-mapped lists per category
    private fun List<String>.withNames(): List<EmojiMeta> = mapNotNull { ch ->
        val cp = ch.codePointAt(0)
        runCatching {
            val raw = UCharacter.getName(cp) ?: return@mapNotNull null
            EmojiMeta(ch, raw.toLowerCase(Locale.ROOT).replace('_', ' '))
        }.getOrNull()
    }

    val META_EMOTICONS: List<EmojiMeta> by lazy { EMOJI_EMOTICONS.withNames() }
    val META_ANIMALS: List<EmojiMeta> by lazy { EMOJI_ANIMALS.withNames() }
    val META_NATURE: List<EmojiMeta> by lazy { EMOJI_NATURE.withNames() }
    val META_FOOD: List<EmojiMeta> by lazy { EMOJI_FOOD.withNames() }
    val META_SPORTS: List<EmojiMeta> by lazy { EMOJI_SPORTS.withNames() }
    val META_TRANSPORT: List<EmojiMeta> by lazy { EMOJI_TRANSPORT.withNames() }
    val META_OBJECTS: List<EmojiMeta> by lazy { EMOJI_OBJECTS.withNames() }
    val META_ALCHEMY: List<EmojiMeta> by lazy { EMOJI_ALCHEMY.withNames() }
    val META_SHAPES: List<EmojiMeta> by lazy { EMOJI_SHAPES.withNames() }
    val META_ARROWS: List<EmojiMeta> by lazy { EMOJI_ARROWS.withNames() }
    val META_FLAGS: List<EmojiMeta> by lazy {
        EMOJI_FLAGS.mapIndexed { i, flag ->
            val country = Locale("", COUNTRY_CODES[i]).displayCountry
            EmojiMeta(flag, "Flag of $country")
        }
    }
    val META_LETTERS: List<EmojiMeta> by lazy {
        EMOJI_LETTERS.mapNotNull { ch ->
            // getName("REGIONAL INDICATOR SYMBOL LETTER A") → "LETTER A"
            val raw = UCharacter.getName(ch.codePointAt(0)) ?: return@mapNotNull null
            val letter = raw.substringAfterLast(' ')    // "A"
            EmojiMeta(ch, letter)
        }
    }

    private fun countryCodeToFlag(code: String): String {
        val base = 0x1F1E6
        return code
            .uppercase(Locale.ROOT)
            .map { char ->
                String(Character.toChars(base + (char - 'A')))
            }
            .joinToString("")
    }

    val colorList = listOf(
        "#FFFFFF", "#000000", "#FF0000", "#00FF00", "#0000FF",
        "#FFFF00", "#00FFFF", "#FF00FF", "#C0C0C0", "#808080",
        "#800000", "#808000", "#008000", "#800080", "#008080",
        "#000080", "#FFA07A", "#FA8072", "#E9967A", "#F08080",
        "#CD5C5C", "#DC143C", "#B22222", "#8B0000", "#FF4500",
        "#FF6347", "#FF7F50", "#FF8C00", "#FFA500", "#FFD700",
        "#FFFFE0", "#FFFACD", "#FAFAD2", "#FFEFD5", "#FFE4B5",
        "#FFDAB9", "#EEE8AA", "#F0E68C", "#BDB76B", "#E6E6FA",
        "#D8BFD8", "#DDA0DD", "#EE82EE", "#DA70D6", "#FF00FF",
        "#BA55D3", "#9370DB", "#8A2BE2", "#9400D3", "#9932CC",
        "#8B008B", "#800080", "#4B0082", "#6A5ACD", "#483D8B",
        "#7B68EE", "#ADFF2F", "#7FFF00", "#7CFC00", "#00FF00",
        "#32CD32", "#98FB98", "#90EE90", "#00FA9A", "#00FF7F",
        "#3CB371", "#2E8B57", "#228B22", "#008000", "#006400",
        "#9ACD32", "#6B8E23", "#556B2F", "#66CDAA", "#8FBC8F",
        "#20B2AA", "#008B8B", "#008080", "#00FFFF", "#00CED1",
        "#40E0D0", "#48D1CC", "#00BFFF", "#1E90FF", "#6495ED",
        "#4682B4", "#4169E1", "#0000FF", "#0000CD", "#00008B",
        "#191970", "#87CEFA", "#87CEEB", "#ADD8E6", "#B0C4DE",
        "#708090", "#778899", "#A9A9A9", "#696969", "#2F4F4F"
    ).map { ColorItem(it) }

    val shadowColorList = listOf(
        "#000000",
        "#808080",
        "#2F4F4F",
        "#4B0082",
        "#483D8B",
        "#6A5ACD",
        "#708090",
        "#8B0000",
        "#B22222",
        "#8B008B",
        "#556B2F",
        "#8FBC8F",
        "#00008B",
        "#191970",
        "#2E8B57",
        "#800000",
        "#A52A2A",
        "#D2691E",
        "#B22222",
        "#000080",
        "#2C3E50",
        "#3B3B3B",
        "#708090",
        "#4B0082"
    ).map { ColorItem(it) }
}