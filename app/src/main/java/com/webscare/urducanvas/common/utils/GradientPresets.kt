package com.webscare.urducanvas.common.utils

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.model.GradientItem

object GradientPresets {
  /** 30 presets: 10 LINEAR, 10 RADIAL, 10 SWEEP */
  val defaultList: List<GradientItem> = listOf(

    // ───── LINEAR (10) ────────────────────────────────────────────────────────
      GradientItem(
          colors = listOf(Color.parseColor("#FF5F6D"), Color.parseColor("#FFC371")),
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
          colors = listOf(Color.parseColor("#e96443"), Color.parseColor("#904e95")),
          positions = listOf(0f, 1f),
          angle = 180f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.parseColor("#00d2ff"), Color.parseColor("#3a7bd5")),
          positions = listOf(0f, 1f),
          angle = 225f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.parseColor("#f7971e"), Color.parseColor("#ffd200")),
          positions = listOf(0f, 1f),
          angle = 270f,
          type = GradientType.LINEAR
      ),
      GradientItem(
          colors = listOf(Color.parseColor("#11998e"), Color.parseColor("#38ef7d")),
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
          colors = listOf(Color.YELLOW, Color.parseColor("#FFA500"), Color.RED),
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
          colors = listOf(Color.parseColor("#ff9a9e"), Color.parseColor("#fad0c4")),
          positions = listOf(0f, 1f),
          type = GradientType.RADIAL,
          radialRadiusFactor = 0.4f,
          centerX = 0.5f,
          centerY = 0.25f
      ),
      GradientItem(
          colors = listOf(Color.parseColor("#a18cd1"), Color.parseColor("#fbc2eb")),
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
          colors = listOf(Color.parseColor("#ffecd2"), Color.parseColor("#fcb69f")),
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
          colors = listOf(Color.parseColor("#11998e"), Color.parseColor("#38ef7d")),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 135f,
          centerX = 0.25f,
          centerY = 0.75f
      ),
      GradientItem(
          colors = listOf(Color.parseColor("#ee9ca7"), Color.parseColor("#ffdde1")),
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
          colors = listOf(Color.parseColor("#232526"), Color.parseColor("#414345")),
          positions = listOf(0f, 1f),
          type = GradientType.SWEEP,
          sweepStartAngle = 300f,
          centerX = 0.5f,
          centerY = 0.75f
      )
  )
}
