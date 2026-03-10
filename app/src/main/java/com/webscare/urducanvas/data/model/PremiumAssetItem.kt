package com.webscare.urducanvas.data.model

import com.webscare.urducanvas.common.canvas.enums.ElementType

data class PremiumAssetItem(
    val elementId: String,
    val type: ElementType,        // TEXT or IMAGE/STICKER
    val fontId: String? = null,   // for TEXT
    val bitmapData: String? = null, // for IMAGE/STICKER (base64)
    val fontImageBase64: String? = null, // local font thumbnail
    val fontImageUrl: String? = null    // remote font image_url
)