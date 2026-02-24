package com.webscare.urducanvas.common.utils

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.caverock.androidsvg.SVG
import java.io.InputStream

class SvgDecoder : ResourceDecoder<InputStream, SVG> {
    override fun handles(source: InputStream, options: Options): Boolean = true

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<SVG>? {
        return try {
            val svg = SVG.getFromInputStream(source)
            val viewBox = svg.documentViewBox
            if (viewBox != null && (viewBox.width() <= 0f || viewBox.height() <= 0f)) {
                return null
            }
            SimpleResource(svg)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}