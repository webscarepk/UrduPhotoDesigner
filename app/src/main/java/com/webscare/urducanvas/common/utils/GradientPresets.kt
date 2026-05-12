package com.webscare.urducanvas.common.utils

import androidx.core.graphics.toColorInt
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.model.GradientItem

object GradientPresets {
    /** 100 designer-grade linear gradients — mixed colors, grouped by mood */
    val defaultList: List<GradientItem> = listOf(

        // ── Pitch Black & Deep Dark ───────────────────────────────────────────
        GradientItem(colors = listOf("#0D0D0D".toColorInt(), "#1A1A2E".toColorInt(), "#16213E".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#000000".toColorInt(), "#0F0C29".toColorInt(), "#302B63".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#000000".toColorInt(), "#1A1A1A".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#0D0D0D".toColorInt(), "#2C003E".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#010101".toColorInt(), "#0A0A23".toColorInt(), "#1B1B3A".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Dark & Moody ──────────────────────────────────────────────────────
        GradientItem(colors = listOf("#0F2027".toColorInt(), "#203A43".toColorInt(), "#2C5364".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#141E30".toColorInt(), "#243B55".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#1A1A2E".toColorInt(), "#16213E".toColorInt(), "#0F3460".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#232526".toColorInt(), "#414345".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#16222A".toColorInt(), "#3A6073".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#1F1C2C".toColorInt(), "#928DAB".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#200122".toColorInt(), "#6F0000".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#0F0C29".toColorInt(), "#302B63".toColorInt(), "#24243E".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#000428".toColorInt(), "#004E92".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#2D1B69".toColorInt(), "#11998E".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#373B44".toColorInt(), "#4286F4".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#1A1A2E".toColorInt(), "#E94560".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#0D0D0D".toColorInt(), "#2980B9".toColorInt(), "#6DD5FA".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#2C3E50".toColorInt(), "#4CA1AF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#24243E".toColorInt(), "#302B63".toColorInt(), "#0F0C29".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Navy & Deep Ocean ─────────────────────────────────────────────────
        GradientItem(colors = listOf("#005C97".toColorInt(), "#363795".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#1CB5E0".toColorInt(), "#000046".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#2E3192".toColorInt(), "#1BFFFF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#0052D4".toColorInt(), "#4364F7".toColorInt(), "#6FB1FC".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#4CA1AF".toColorInt(), "#C4E0E5".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#003973".toColorInt(), "#E5E5BE".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#1A3C5E".toColorInt(), "#2980B9".toColorInt(), "#6DD5FA".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Cool Blue & Cyan ──────────────────────────────────────────────────
        GradientItem(colors = listOf("#00D2FF".toColorInt(), "#3A7BD5".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#4FACFE".toColorInt(), "#00F2FE".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#48C6EF".toColorInt(), "#6F86D6".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#89F7FE".toColorInt(), "#66A6FF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#A1C4FD".toColorInt(), "#C2E9FB".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#6DD5FA".toColorInt(), "#2980B9".toColorInt(), "#1A3C5E".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#00C9FF".toColorInt(), "#92FE9D".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#0BD3D3".toColorInt(), "#0099F7".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Purple & Violet ───────────────────────────────────────────────────
        GradientItem(colors = listOf("#DA22FF".toColorInt(), "#9733EE".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#7B2FF7".toColorInt(), "#F107A3".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#667EEA".toColorInt(), "#764BA2".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#5F0A87".toColorInt(), "#A4508B".toColorInt(), "#F6D365".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#8E2DE2".toColorInt(), "#4A00E0".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#4A00E0".toColorInt(), "#8E2DE2".toColorInt(), "#DA22FF".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#B721FF".toColorInt(), "#21D4FD".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Pink & Magenta ────────────────────────────────────────────────────
        GradientItem(colors = listOf("#F093FB".toColorInt(), "#F5576C".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF6CAB".toColorInt(), "#7366FF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FD79A8".toColorInt(), "#A29BFE".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF0099".toColorInt(), "#493240".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#F953C6".toColorInt(), "#B91D73".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF61D2".toColorInt(), "#FE9090".toColorInt(), "#FFD700".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#E96443".toColorInt(), "#904E95".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#A18CD1".toColorInt(), "#FBC2EB".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#C471ED".toColorInt(), "#F64F59".toColorInt(), "#12C2E9".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Red & Orange Fire ─────────────────────────────────────────────────
        GradientItem(colors = listOf("#FF512F".toColorInt(), "#DD2476".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#F83600".toColorInt(), "#F9D423".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FC4A1A".toColorInt(), "#F7B733".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF416C".toColorInt(), "#FF4B2B".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#CB2D3E".toColorInt(), "#EF473A".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF0000".toColorInt(), "#FF6600".toColorInt(), "#FF9900".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#ED213A".toColorInt(), "#93291E".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF5F6D".toColorInt(), "#FFC371".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Warm Amber & Yellow ───────────────────────────────────────────────
        GradientItem(colors = listOf("#F7971E".toColorInt(), "#FFD200".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FFBE76".toColorInt(), "#FF7043".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#F6D365".toColorInt(), "#FDA085".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FFD700".toColorInt(), "#FF8C00".toColorInt(), "#FF4500".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#F7BB97".toColorInt(), "#DD5E89".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FCFF9E".toColorInt(), "#C67700".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Green & Lime ──────────────────────────────────────────────────────
        GradientItem(colors = listOf("#11998E".toColorInt(), "#38EF7D".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#56AB2F".toColorInt(), "#A8E063".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#1D976C".toColorInt(), "#93F9B9".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#00B09B".toColorInt(), "#96C93D".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#43E97B".toColorInt(), "#38F9D7".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#008000".toColorInt(), "#00FF00".toColorInt(), "#ADFF2F".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#76B852".toColorInt(), "#8DC26F".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#134E5E".toColorInt(), "#71B280".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Teal & Mint ───────────────────────────────────────────────────────
        GradientItem(colors = listOf("#00CDAC".toColorInt(), "#8DDAD5".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#96FBC4".toColorInt(), "#F9F586".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#0BD3D3".toColorInt(), "#F8FF00".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#00F260".toColorInt(), "#0575E6".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#43C6AC".toColorInt(), "#191654".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Rainbow & Spectrum ────────────────────────────────────────────────
        GradientItem(colors = listOf("#FF0000".toColorInt(), "#FF7700".toColorInt(), "#FFFF00".toColorInt(), "#00FF00".toColorInt(), "#0000FF".toColorInt(), "#8B00FF".toColorInt()), positions = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF6B6B".toColorInt(), "#FEC89A".toColorInt(), "#FFE66D".toColorInt(), "#A8E6CF".toColorInt(), "#88D8FF".toColorInt()), positions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#F64F59".toColorInt(), "#C471ED".toColorInt(), "#12C2E9".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FC466B".toColorInt(), "#3F5EFB".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#00F5A0".toColorInt(), "#00D9F5".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FBAB7E".toColorInt(), "#F7CE68".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#85FFBD".toColorInt(), "#FFFB7D".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF9A8B".toColorInt(), "#FF6A88".toColorInt(), "#FF99AC".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Sunset & Dusk ─────────────────────────────────────────────────────
        GradientItem(colors = listOf("#FF9A9E".toColorInt(), "#FAD0C4".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FFAFBD".toColorInt(), "#FFC3A0".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FF6E7F".toColorInt(), "#BFE9FF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FDC830".toColorInt(), "#F37335".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FC5C7D".toColorInt(), "#6A3093".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#F7971E".toColorInt(), "#FFD200".toColorInt(), "#FF6B6B".toColorInt()), positions = listOf(0f, 0.5f, 1f), angle = 135f, type = GradientType.LINEAR),

        // ── Soft & Pastel ─────────────────────────────────────────────────────
        GradientItem(colors = listOf("#FFECD2".toColorInt(), "#FCB69F".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FFF1EB".toColorInt(), "#ACE0F9".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FDDB92".toColorInt(), "#D1FDFF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FCCB90".toColorInt(), "#D57EEB".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#E0C3FC".toColorInt(), "#8EC5FC".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#D4FC79".toColorInt(), "#96E6A1".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FFDEE9".toColorInt(), "#B5FFFC".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#E0F7FA".toColorInt(), "#B2EBF2".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#FAD0C4".toColorInt(), "#FFD1FF".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
        GradientItem(colors = listOf("#A1FFCE".toColorInt(), "#FAFFD1".toColorInt()), positions = listOf(0f, 1f), angle = 135f, type = GradientType.LINEAR),
    )
}