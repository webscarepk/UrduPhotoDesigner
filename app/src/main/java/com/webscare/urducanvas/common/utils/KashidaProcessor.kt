package com.webscare.urducanvas.common.utils

import android.graphics.Typeface
import android.text.TextPaint

class KashidaProcessor(
    private val insertionFreq: Int = 1   // default: 1 kashida per slot
) {
    companion object {
        const val KASHIDA = "ـ"

        // Non-connecting (isolated/final) characters → never take Kashida
        private val ISOLFINA = setOf(
            "د","ذ","ڈ","ڌ","ڍ","ډ","ڊ","ڋ","ڎ","ڏ","ڐ","ۮ",
            "ݙ","ݚ","ر","ز","ڑ","ڒ","ړ","ڔ","ڕ","ږ","ڗ","ژ","ڙ","ۯ","ݛ","ݫ","ݬ",
            "ﻻ","ﻹ","و","ۄ","ۊ","ۏ","ؤ","ۅ","ۆ","ۇ","ۈ","ۉ","ۋ","ٷ","ﻷ"
        )

        // Letters that can stretch with Kashida
        private val KASHIDA_ALLOWED = setOf(
            "ب","پ","ت","ٹ","ث",
            "ج","چ","ح","خ",
            "س","ش","ص","ض",
            "ط","ظ",
            "ع","غ",
            "ف","ق",
            "ک","گ",
            "ل","م","ن","ی"
        )
    }

    // ---- Main Processor ----
    fun process(nudeText: String?): String {
        if (nudeText == null) return ""

        val regex = Regex("[\u0600-\u06FF]+")
        return regex.replace(nudeText) { match ->
            insertKasheedaSafely(match.value, insertionFreq)
        }
    }

    // Insert Kashida safely into one word
    private fun insertKasheedaSafely(word: String, freq: Int): String {
        val cleanWord = word.replace(KASHIDA, "")

        for (i in cleanWord.length - 1 downTo 0) {
            val ch = cleanWord[i].toString()

            if (KASHIDA_ALLOWED.contains(ch)) {
                if (i < cleanWord.length - 1) {
                    val nextChar = cleanWord[i + 1].toString()

                    // Agar agla char Alif hai, to bhi Kashida allow karo
                    if (!ISOLFINA.contains(nextChar) || nextChar == "ا") {
                        val kashidaInsert = KASHIDA.repeat(freq)
                        return cleanWord.substring(0, i + 1) + kashidaInsert + cleanWord.substring(i + 1)
                    }
                }
            }
        }
        return cleanWord
    }

    fun remove(kashidaText: String): String {
        return kashidaText.replace(KASHIDA, "")
    }

    // ---- Safety Check for Current Font ----
    private fun isKasheedaSafe(typeface: Typeface): Boolean {
        return try {
            val paint = TextPaint().apply {
                this.typeface = typeface
                textSize = 64f
            }
            val baseWord = "باب"
            val withKasheeda = "با${KASHIDA}ب"

            val widthBase = paint.measureText(baseWord)
            val widthKasheeda = paint.measureText(withKasheeda)

            if (widthKasheeda <= widthBase) return false

            val baseWidths = FloatArray(baseWord.length)
            val kasheedaWidths = FloatArray(withKasheeda.length)
            paint.getTextWidths(baseWord, baseWidths)
            paint.getTextWidths(withKasheeda, kasheedaWidths)

            kasheedaWidths.size >= baseWidths.size
        } catch (e: Exception) {
            false // corrupt font — skip kashida entirely
        }
    }

    // Public safe entry point
    fun processSafe(nudeText: String?, typeface: Typeface): String {
        if (nudeText == null) return ""
        return if (isKasheedaSafe(typeface)) process(nudeText) else nudeText
    }
}
