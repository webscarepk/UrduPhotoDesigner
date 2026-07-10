package com.webscare.urducanvas.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import com.webscare.urducanvas.common.canvas.model.AdjustmentValues
import com.webscare.urducanvas.common.canvas.model.BrushSettings
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter

object Converters {

    private val gson = Gson()

    // Convert List<Int> to a comma-separated string
    @TypeConverter
    @JvmStatic
    fun fromIntList(list: List<Int>?): String = list?.joinToString(",") ?: ""

    // Convert comma-separated string back to List<Int>
    @TypeConverter
    @JvmStatic
    fun toIntList(data: String?): List<Int> = data?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.toInt() } ?: emptyList()

    // Convert List<Float> to a comma-separated string
    @TypeConverter
    @JvmStatic
    fun fromFloatList(list: List<Float>?): String = list?.joinToString(",") ?: ""

    // Convert comma-separated string back to List<Float>
    @TypeConverter
    @JvmStatic
    fun toFloatList(data: String?): List<Float> = data?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.toFloat() } ?: emptyList()

    // Convert GradientType enum to a string
    @TypeConverter
    @JvmStatic
    fun fromGradientType(type: GradientType?): String = type?.name ?: GradientType.LINEAR.name

    // Convert string back to GradientType enum
    @TypeConverter
    @JvmStatic
    fun toGradientType(name: String?): GradientType = name?.let { GradientType.valueOf(it) } ?: GradientType.LINEAR

    @TypeConverter
    fun fromCanvasSize(canvasSize: CanvasSize?): String? = canvasSize?.let { gson.toJson(it) }

    @TypeConverter
    fun toCanvasSize(data: String?): CanvasSize? {
        if (data.isNullOrBlank()) return null
        val type = object :
            com.google.gson.reflect.TypeToken<CanvasSize>() {}.type
        return gson.fromJson(data, type)
    }

    @TypeConverter
    @JvmStatic
    fun fromGradientItem(item: GradientItem?): String? = item?.let { gson.toJson(it) }

    @TypeConverter
    @JvmStatic
    fun toGradientItem(data: String?): GradientItem? {
        if (data.isNullOrBlank()) return null
        return gson.fromJson(data, GradientItem::class.java)
    }

    // Convert List<String> <-> JSON
    @TypeConverter
    @JvmStatic
    fun fromStringList(list: List<String>?): String = gson.toJson(list ?: emptyList<String>())

    @TypeConverter
    @JvmStatic
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(data, type) ?: emptyList()
    }

    @TypeConverter
    fun fromAdjustmentValues(value: AdjustmentValues?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toAdjustmentValues(data: String?): AdjustmentValues? {
        if (data.isNullOrBlank()) return null
        return gson.fromJson(data, AdjustmentValues::class.java)
    }

    @TypeConverter
    fun fromImageFilter(value: ImageFilter?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toImageFilter(data: String?): ImageFilter? {
        if (data.isNullOrBlank()) return null
        return gson.fromJson(data, ImageFilter::class.java)
    }

    @TypeConverter
    fun fromTextDecorationSet(set: Set<TextDecoration>?): String? = set?.joinToString(",") { it.name }

    @TypeConverter
    fun toTextDecorationSet(data: String?): Set<TextDecoration> {
        if (data.isNullOrBlank()) return emptySet()
        return data.split(",").map { TextDecoration.valueOf(it) }.toSet()
    }

    @TypeConverter
    fun fromStrokeList(list: MutableList<StrokeData>?): String? = list?.let { gson.toJson(it) }

    @TypeConverter
    fun toStrokeList(data: String?): MutableList<StrokeData>? {
        if (data.isNullOrBlank()) return null
        val type = object : TypeToken<MutableList<StrokeData>>() {}.type
        return gson.fromJson(data, type)
    }

    @TypeConverter
    fun fromBrushSettings(value: BrushSettings?): String? = value?.let { gson.toJson(it) }

    @TypeConverter
    fun toBrushSettings(data: String?): BrushSettings? {
        if (data.isNullOrBlank()) return null
        return gson.fromJson(data, BrushSettings::class.java)
    }

    @TypeConverter
    fun fromBlendType(type: BlendType?): String? = type?.name

    @TypeConverter
    fun toBlendType(name: String?): BlendType? = name?.let { BlendType.valueOf(it) }
}
