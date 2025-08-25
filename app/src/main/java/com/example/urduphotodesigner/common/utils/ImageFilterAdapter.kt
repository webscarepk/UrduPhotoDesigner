package com.example.urduphotodesigner.common.utils

import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class ImageFilterAdapter : JsonSerializer<ImageFilter>, JsonDeserializer<ImageFilter> {
    override fun serialize(
        src: ImageFilter?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        // Store only the name
        return JsonPrimitive(src?.javaClass?.simpleName ?: "None")
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ImageFilter {
        return when (json?.asString) {
            "Grayscale" -> ImageFilter.Grayscale
            "Sepia" -> ImageFilter.Sepia
            "Invert" -> ImageFilter.Invert
            "CoolTint" -> ImageFilter.CoolTint
            "WarmTint" -> ImageFilter.WarmTint
            "Vintage" -> ImageFilter.Vintage
            "Film" -> ImageFilter.Film
            "TealOrange" -> ImageFilter.TealOrange
            "HighContrast" -> ImageFilter.HighContrast
            "BlackWhite" -> ImageFilter.BlackWhite
            else -> ImageFilter.None
        }
    }
}
