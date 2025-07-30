package com.example.urduphotodesigner.common.utils

class KashidaProcessor(private val insertionFreq: Int = 3, private val insertionContrast: Double = 0.8) {

    // Main function to process text and insert Kashida characters
    fun process(nudeText: String?): String {
        if (nudeText == null) return ""

        var modifiedText = remove(nudeText) // Ensure text is "nude"
        var occurrencesArr: List<MatchResult>
        var distribution: List<Int> = emptyList()

        val reg = Regex("[يئهشسقفغعـضصنمكظطخحجثتب][يئهشسقفغعضصنمكظطخوـحجثتبلدرا]")

        // Match Arabic words and insert Kashida
        modifiedText = modifiedText.replace(Regex("[\\u0600-\\u06FF]+")) { match ->
            var wordWithKashida = match.value
            occurrencesArr = _legacyMatch(wordWithKashida, reg)

            if (occurrencesArr.isEmpty()) return@replace wordWithKashida

            val occurrences = occurrencesArr.size

            // Determine insertions distribution
            if (insertionContrast == 0.0) {
                // Evenly distribute the Kashida insertions across the occurrences
                distribution = List(occurrences) { insertionFreq }
            } else {
                // Adjust the frequency for each occurrence
                distribution = List(occurrences) { insertionFreq }
            }

            var countOfAddedKashida = 0
            for (i in occurrencesArr.indices) {
                val frequency = distribution[i]
                if (frequency <= 0) continue // Skip if no Kashida should be added

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