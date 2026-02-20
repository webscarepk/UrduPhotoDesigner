package com.webscare.urducanvas.data.local

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
    fun fromGradientType(type: com.webscare.urducanvas.common.canvas.enums.GradientType?): String =
        type?.name ?: _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.GradientType.LINEAR.name

    // Convert string back to GradientType enum
    @TypeConverter
    @JvmStatic
    fun toGradientType(name: String?): com.webscare.urducanvas.common.canvas.enums.GradientType =
        name?.let { _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.GradientType.valueOf(it) } ?: _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.GradientType.LINEAR

    @TypeConverter
    fun fromCanvasSize(canvasSize: com.webscare.urducanvas.common.canvas.model.CanvasSize): String {
        return gson.toJson(canvasSize)
    }

    @TypeConverter
    fun toCanvasSize(data: String): com.webscare.urducanvas.common.canvas.model.CanvasSize {
        val type = object : com.google.gson.reflect.TypeToken<com.webscare.urducanvas.common.canvas.model.CanvasSize>() {}.type
        return gson.fromJson(data, type)
    }

    // Convert List<String> <-> JSON
    @TypeConverter
    @JvmStatic
    fun fromStringList(list: List<String>?): String =
        gson.toJson(list ?: emptyList<String>())

    @TypeConverter
    @JvmStatic
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(data, type) ?: emptyList()
    }

}