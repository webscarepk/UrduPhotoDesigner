package com.webscare.urducanvas.data.mapper

import com.webscare.urducanvas.common.utils.Constants.PEXELS_ID_OFFSET
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.PexelsPhoto

fun PexelsPhoto.toImageEntity(subcategory: String): ImageEntity = ImageEntity(
    id = id + PEXELS_ID_OFFSET,
    file_name = "$id.jpg",
    // file_url = medium (940px) — fast to load in grid thumbnails
    // bitmapData = large (1880px) — loaded when user taps to add to canvas
    // This is the key speed fix: grid shows medium, canvas gets large
    file_url = src.medium,
    file_size = "",
    alt_text = alt,
    category = subcategory,
    parent_category = "Backgrounds",
    user_id = 0,
    is_premium = false,
    is_subscribed = false,
    is_recent = false,
    bitmapData = src.large, // reused as "full quality URL" for canvas tap
)
