package com.example.urduphotodesigner.data.local

import androidx.room.TypeConverter
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {

    private val gson = Gson()

    // Convert List<Int> to a comma-separated string
    @TypeConverter
    @JvmStatic
    fun fromIntList(list: List<Int>?): String =
        list?.joinToString(",") ?: ""

    // Convert comma-separated string back to List<Int>
    @TypeConverter
    @JvmStatic
    fun toIntList(data: String?): List<Int> =
        data?.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.map { it.toInt() }
            ?: emptyList()

    // Convert List<Float> to a comma-separated string
    @TypeConverter
    @JvmStatic
    fun fromFloatList(list: List<Float>?): String =
        list?.joinToString(",") ?: ""

    // Convert comma-separated string back to List<Float>
    @TypeConverter
    @JvmStatic
    fun toFloatList(data: String?): List<Float> =
        data?.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.map { it.toFloat() }
            ?: emptyList()

    // Convert GradientType enum to a string
    @TypeConverter
    @JvmStatic
    fun fromGradientType(type: GradientType?): String =
        type?.name ?: GradientType.LINEAR.name

    // Convert string back to GradientType enum
    @TypeConverter
    @JvmStatic
    fun toGradientType(name: String?): GradientType =
        name?.let { GradientType.valueOf(it) } ?: GradientType.LINEAR

    @TypeConverter
    fun fromCanvasSize(canvasSize: CanvasSize): String {
        return gson.toJson(canvasSize)
    }

    @TypeConverter
    fun toCanvasSize(data: String): CanvasSize {
        val type = object : TypeToken<CanvasSize>() {}.type
        return gson.fromJson(data, type)
    }
}