package com.example.urduphotodesigner.common.utils

class KashidaInserter {

    companion object {
        const val KASHIDA = "ـ"

        // Non-connecting (isolated/final) characters → never take Kashida
        val ISOLFINA = setOf(
            "ا", "إ", "ٳ", "د", "ذ", "ڈ", "ڌ", "ڍ", "ډ", "ڊ", "ڋ", "ڎ", "ڏ", "ڐ", "ۮ",
            "ݙ", "ݚ", "ر", "ز", "ڑ", "ڒ", "ړ", "ڔ", "ڕ", "ږ", "ڗ", "ژ", "ڙ", "ۯ", "ݛ", "ݫ", "ݬ",
            "ﻻ", "ﻹ", "و", "ۄ", "ۊ", "ۏ", "ؤ", "ۅ", "ۆ", "ۇ", "ۈ", "ۉ", "ۋ", "ٷ", "ﻷ"
        )

        // Valid Kashida candidates (cross-checked with Urdu script rules)
        val KASHIDA_ALLOWED = setOf(
            "ب","پ","ت","ٹ","ث",
            "ج","چ","ح","خ",
            "س","ش","ص","ض",
            "ط","ظ",
            "ع","غ",
            "ف","ق",
            "ک","گ",
            "ل","م","ن","ی"
        )

        // Insert Kashida into a word
        fun insertKashidaInWord(word: String): String {
            var cleanWord = word.replace(KASHIDA, "")

            for (i in cleanWord.length - 1 downTo 0) {
                val ch = cleanWord[i].toString()
                if (KASHIDA_ALLOWED.contains(ch)) {
                    // Ensure it’s not the last character and the next one is connectable
                    if (i < cleanWord.length - 1 &&
                        !ISOLFINA.contains(cleanWord[i + 1].toString())) {
                        return cleanWord.substring(0, i + 1) + KASHIDA + cleanWord.substring(i + 1)
                    }
                }
            }

            return cleanWord
        }

        // Insert Kashida in the entire text
        fun insertKashidaInText(text: String): String {
            val regex = Regex("\\b[\\u0600-\\u06FF]+\\b")
            return regex.replace(text) { match -> insertKashidaInWord(match.value) }
        }
    }
}