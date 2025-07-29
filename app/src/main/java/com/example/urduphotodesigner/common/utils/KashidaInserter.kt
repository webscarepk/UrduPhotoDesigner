package com.example.urduphotodesigner.common.utils

class KashidaInserter {

    companion object {
        const val KASHIDA = "ـ"
        val ISOLFINA = listOf(
            "ا", "إ", "ٳ", "د", "ذ", "ڈ", "ڌ", "ڍ", "ډ", "ڊ", "ڋ", "ڎ", "ڏ", "ڐ", "ۮ",
            "ݙ", "ݚ", "ر", "ز", "ڑ", "ڒ", "ړ", "ڔ", "ڕ", "ږ", "ڗ", "ژ", "ڙ", "ۯ", "ݛ", "ݫ", "ݬ",
            "ﻻ", "ﻹ", "و", "ۄ", "ۊ", "ۏ", "ؤ", "ۅ", "ۆ", "ۇ", "ۈ", "ۉ", "ۋ", "ٷ", "ﻷ", "ﻹ"
        )

        // Insert Kashida into a word
        fun insertKashidaInWord(word: String): String {
            var wordWithKashida = word.replace(KASHIDA, "")

            val rules = listOf(
                listOf("س", "ص", "ښ", "ڛ", "ش", "ۺ", "ڜ", "ﺺ", "ڝ", "ڞ", "ض", "ݜ", "ݭ"),
                listOf("ه", "ۀ", "ة", "ە", "د", "ذ", "ڈ", "ڌ", "ڍ", "ډ", "ڊ", "ڋ", "ڎ", "ڏ", "ڐ", "ۮ", "ݙ", "ݚ"),
                listOf("ا", "إ", "ٳ", "ب", "پ", "ٻ", "ڀ", "ت", "ٽ", "ث", "ٹ", "ٺ", "ٿ", "ݐ", "ݑ", "ݒ", "ݓ", "ݔ", "ݕ", "ݖ", "ك", "گ", "ڰ", "ڴ", "ڬ", "ڮ", "ڲ", "ڭ", "ڱ", "ڳ", "ل", "ڸ", "ݪ"),
                listOf("ع", "ڠ", "غ", "ف", "ڤ", "ڡ", "ڢ", "ڣ", "ڥ", "ڦ", "ٯ", "ق", "ڧ", "ڨ", "و", "ۄ", "ۊ", "ۏ", "ؤ", "ۅ", "ۆ", "ۇ", "ۈ", "ۉ", "ۋ", "ٷ", "ݝ", "ݞ", "ݟ", "ݠ", "ݡ")
            )

            for (rule in rules) {
                for (i in wordWithKashida.length - 1 downTo 0) {
                    if (rule.contains(wordWithKashida[i].toString()) && i > 0 && !ISOLFINA.contains(wordWithKashida[i - 1].toString())) {
                        return wordWithKashida.substring(0, i + 1) + KASHIDA + wordWithKashida.substring(i + 1)
                    }
                }
            }

            return wordWithKashida
        }

        // Insert Kashida in the entire text
        fun insertKashidaInText(text: String): String {
            val regex = Regex("\\b[\\u0600-\\u06FF]+\\b")
            return regex.replace(text) { match -> insertKashidaInWord(match.value) }
        }
    }
}
