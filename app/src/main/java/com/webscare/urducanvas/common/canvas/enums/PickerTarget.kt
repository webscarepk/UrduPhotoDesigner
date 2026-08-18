package com.webscare.urducanvas.common.canvas.enums

import com.google.gson.annotations.SerializedName

enum class PickerTarget {
  @SerializedName("EyeDropperBackground")
  EYE_DROPPER_BACKGROUND,
  @SerializedName("EyeDropperOverlay")
  EYE_DROPPER_OVERLAY,

  @SerializedName("EyeDropperTextFill")
  EYE_DROPPER_TEXT_FILL,

  @SerializedName("EyeDropperTextStroke")
  EYE_DROPPER_TEXT_STROKE,

  @SerializedName("EyeDropperShadow")
  EYE_DROPPER_SHADOW,

  @SerializedName("EyeDropperImageStroke")
  EYE_DROPPER_IMAGE_STROKE,

  @SerializedName("EyeDropperLabel")
  EYE_DROPPER_LABEL,

  @SerializedName("EyeDropperDrawStroke")
  EYE_DROPPER_DRAW_STROKE,

  @SerializedName("EyeDropperDrawFill")
  EYE_DROPPER_DRAW_FILL,

  @SerializedName("EyeDropperShapeStroke")
  EYE_DROPPER_SHAPE_STROKE,

  @SerializedName("EyeDropperShapeFill")
  EYE_DROPPER_SHAPE_FILL,

  @SerializedName("EyeDropperGradient")
  EYE_DROPPER_GRADIENT,

  @SerializedName("ColorPickerBackground")
  COLOR_PICKER_BACKGROUND,
  @SerializedName("ColorPickerOverlay")
  COLOR_PICKER_OVERLAY,

  @SerializedName("ColorPickerTextFill")
  COLOR_PICKER_TEXT_FILL,

  @SerializedName("ColorPickerTextStroke")
  COLOR_PICKER_TEXT_STROKE,

  @SerializedName("ColorPickerImageStroke")
  COLOR_PICKER_IMAGE_STROKE,

  @SerializedName("ColorPickerShadow")
  COLOR_PICKER_SHADOW,
  @SerializedName("ColorPickerImageShadow")
  COLOR_PICKER_IMAGE_SHADOW,

  @SerializedName("ColorPickerLabel")
  COLOR_PICKER_LABEL,

  @SerializedName("ColorPickerDrawStroke")
  COLOR_PICKER_DRAW_STROKE,

  @SerializedName("ColorPickerDrawFill")
  COLOR_PICKER_DRAW_FILL,

  @SerializedName("ColorPickerShapeStroke")
  COLOR_PICKER_SHAPE_STROKE,

  @SerializedName("ColorPickerShapeFill")
  COLOR_PICKER_SHAPE_FILL,
  @SerializedName("ColorPickerGradient")
  COLOR_PICKER_GRADIENT,

  @SerializedName("EyeDropperTableFill")
  EYE_DROPPER_TABLE_FILL,
  @SerializedName("EyeDropperTableStroke")
  EYE_DROPPER_TABLE_STROKE,
  @SerializedName("EyeDropperTableTextColor")
  EYE_DROPPER_TABLE_TEXT_COLOR,

  @SerializedName("ColorPickerTableFill")
  COLOR_PICKER_TABLE_FILL,
  @SerializedName("ColorPickerTableStroke")
  COLOR_PICKER_TABLE_STROKE,
  @SerializedName("ColorPickerTableTextColor")
  COLOR_PICKER_TABLE_TEXT_COLOR,

  @SerializedName("EyeDropperGlow")
  EYE_DROPPER_GLOW,
  @SerializedName("ColorPickerGlow")
  COLOR_PICKER_GLOW
}
