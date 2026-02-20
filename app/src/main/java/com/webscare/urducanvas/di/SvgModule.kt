package com.webscare.urducanvas.di

import android.content.Context
import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import com.webscare.urducanvas.common.utils.SvgDecoder
import com.webscare.urducanvas.common.utils.SvgDrawableTranscoder
import java.io.InputStream

@GlideModule
class SvgModule : com.bumptech.glide.module.AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry
            .register(SVG::class.java, PictureDrawable::class.java,
                _root_ide_package_.com.webscare.urducanvas.common.utils.SvgDrawableTranscoder()
            )
            .append(InputStream::class.java, SVG::class.java,
                _root_ide_package_.com.webscare.urducanvas.common.utils.SvgDecoder()
            )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}