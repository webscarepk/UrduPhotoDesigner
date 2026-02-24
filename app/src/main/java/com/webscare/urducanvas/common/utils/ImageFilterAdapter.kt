package com.webscare.urducanvas.common.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter

class ImageFilterAdapter : TypeAdapter<ImageFilter>() {

    override fun write(out: JsonWriter, value: ImageFilter?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.name) // save only the name
        }
    }

    override fun read(reader: JsonReader): ImageFilter {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                ImageFilter.None
            }
            JsonToken.STRING -> {
                val name = reader.nextString()
                ImageFilter.Companion.fromName(name)
            }
            JsonToken.BEGIN_OBJECT -> {
                // Handle old object format: { "name": "Invert" }
                reader.beginObject()
                var name: String? = null
                while (reader.hasNext()) {
                    val fieldName = reader.nextName()
                    if (fieldName == "name" && reader.peek() == JsonToken.STRING) {
                        name = reader.nextString()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                ImageFilter.Companion.fromName(name ?: "None")
            }
            else -> {
                reader.skipValue()
                ImageFilter.None
            }
        }
    }
}

