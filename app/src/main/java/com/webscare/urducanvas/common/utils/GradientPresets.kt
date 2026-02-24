package com.webscare.urducanvas.common.utils

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.model.GradientItem
import androidx.core.graphics.toColorInt
import com.webscare.urducanvas.common.canvas.enums.GradientType

object GradientPresets {
  /** 30 presets: 10 LINEAR, 10 RADIAL, 10 SWEEP */
  val defaultList: List<GradientItem> = listOf(

    // ───── LINEAR (10) ────────────────────────────────────────────────────────
      GradientItem(
          colors = listOf("#FF5F6D".toColorInt(), "#FFC371".toColorInt()),
          positions = listOf(0f, 1f),
          angle = 0f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.RED, Color.YELLOW, Color.GREEN),
          positions = listOf(0f, 0.5f, 1f),
          angle = 45f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.BLUE, Color.CYAN),
          positions = listOf(0f, 1f),
          angle = 90f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.MAGENTA, Color.TRANSPARENT),
          positions = listOf(0f, 1f),
          angle = 135f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf("#e96443".toColorInt(), "#904e95".toColorInt()),
          positions = listOf(0f, 1f),
          angle = 180f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf("#00d2ff".toColorInt(), "#3a7bd5".toColorInt()),
          positions = listOf(0f, 1f),
          angle = 225f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf("#f7971e".toColorInt(), "#ffd200".toColorInt()),
          positions = listOf(0f, 1f),
          angle = 270f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf("#11998e".toColorInt(), "#38ef7d".toColorInt()),
          positions = listOf(0f, 1f),
          angle = 315f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.DKGRAY, Color.LTGRAY),
          positions = listOf(0f, 1f),
          angle = 30f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.BLACK, Color.WHITE),
          positions = listOf(0f, 1f),
          angle = 120f,
          type = GradientType.LINEAR
      ),

    // ───── RADIAL (10) ────────────────────────────────────────────────────────
      GradientItem(
          colors = listOf(Color.RED, Color.TRANSPARENT),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.5f,
          centerX = 0.5f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf(Color.YELLOW, "#FFA500".toColorInt(), Color.RED),
          positions = listOf(0f, 0.7f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.8f,
          centerX = 0.25f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf(Color.CYAN, Color.BLUE),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 1.0f,
          centerX = 0.75f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf(Color.GREEN, Color.TRANSPARENT),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.6f,
          centerX = 0.25f,
          centerY = 0.75f
      ),
      GradientItem(
          colors = listOf(Color.MAGENTA, Color.YELLOW),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.7f,
          centerX = 0.75f,
          centerY = 0.75f
      ),
      GradientItem(
          colors = listOf("#ff9a9e".toColorInt(), "#fad0c4".toColorInt()),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.4f,
          centerX = 0.5f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf("#a18cd1".toColorInt(), "#fbc2eb".toColorInt()),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.9f,
          centerX = 0.25f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf(Color.DKGRAY, Color.LTGRAY),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.5f,
          centerX = 0.5f,
          centerY = 0.75f
      ),
      GradientItem(
          colors = listOf(Color.BLACK, Color.WHITE),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 1.0f,
          centerX = 0.5f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf("#ffecd2".toColorInt(), "#fcb69f".toColorInt()),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.6f,
          centerX = 0.75f,
          centerY = 0.5f
      ),

    // ───── SWEEP (10) ────────────────────────────────────────────────────────
      GradientItem(
          colors = listOf(Color.RED, Color.YELLOW, Color.GREEN),
          positions = listOf(0f, 0.33f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 0f,
          centerX = 0.5f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf(Color.BLUE, Color.CYAN),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 45f,
          centerX = 0.25f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf(Color.MAGENTA, Color.TRANSPARENT),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 90f,
          centerX = 0.75f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf("#11998e".toColorInt(), "#38ef7d".toColorInt()),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 135f,
          centerX = 0.25f,
          centerY = 0.75f
      ),
      GradientItem(
          colors = listOf("#ee9ca7".toColorInt(), "#ffdde1".toColorInt()),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 180f,
          centerX = 0.75f,
          centerY = 0.75f
      ),
      GradientItem(
          colors = listOf(Color.BLACK, Color.WHITE),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 225f,
          centerX = 0.5f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf(Color.RED, Color.BLACK),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 270f,
          centerX = 0.25f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf(Color.YELLOW, Color.MAGENTA),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 315f,
          centerX = 0.5f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf(Color.GRAY, Color.LTGRAY),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 60f,
          centerX = 0.75f,
          centerY = 0.5f
      ),
      GradientItem(
          colors = listOf("#232526".toColorInt(), "#414345".toColorInt()),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 300f,
          centerX = 0.5f,
          centerY = 0.75f
      )
  )
}
