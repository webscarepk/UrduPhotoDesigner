package com.example.urduphotodesigner.common.utils

class KashidaProcessor(private val insertionFreq: Int = 3, private val insertionContrast: Double = 0.8) {

    // Main function to process text and insert Kashida characters
    fun process(nudeText: String?): String {
        if (nudeText == null) return ""

        var modifiedText = remove(nudeText) // Ensure text is "nude"
        val occurrencesArr: List<MatchResult>
        var distribution: List<Int> = emptyList()
        var a: Double
        var b: Double
        var min: Double
        val reg = Regex("[يئهشسقفغعـضصنمكظطخحجثتب][يئهشسقفغعضصنمكظطخوـحجثتبلدرا]")

        // Matches Arabic words
        modifiedText = modifiedText.replace(Regex("[\\u0600-\\u06FF]+")) { match ->
            var wordWithKashida = match.value
            occurrencesArr = _legacyMatch(wordWithKashida, reg)

            if (occurrencesArr.isEmpty()) return@replace wordWithKashida

            val occurrences = occurrencesArr.size

            // Determine insertions distribution
            if (insertionContrast == 0.0) {
                distribution = List(occurrences) { (insertionFreq / occurrences).toInt() }
            } else {
                if (insertionContrast == 1.0) {
                    distribution = listOf(insertionFreq)
                } else {
                    min = insertionFreq / occurrences.toDouble()
                    a = min + (insertionFreq - min) * insertionContrast
                    b = insertionFreq / a

                    distribution = List(occurrences) { (-(a / b * it + a)).toInt() }
                }
            }

            var countOfAddedKashida = 0
            for (i in occurrencesArr.indices) {
                val frequency = distribution[i]
                if (frequency < 0) continue

                val occurrence = occurrencesArr[i]
                val index = occurrence.range.first

                val beforeKashida = wordWithKashida.substring(0, index + countOfAddedKashida + 1)
                val kashidaInsert = "ـ".repeat(frequency)
                val afterKashida = wordWithKashida.substring(index + 1 + countOfAddedKashida)

                wordWithKashida = beforeKashida + kashidaInsert + afterKashida
                countOfAddedKashida += frequency
            }

            wordWithKashida
        }

        return modifiedText
    }

    // Legacy function for regex matching
    private fun _legacyMatch(str: String, regex: Regex): List<MatchResult> {
        return regex.findAll(str).toList()
    }

    // Function to remove Kashida character
    fun remove(kashidaText: String): String {
        return kashidaText.replace("ـ", "")
    }
}
