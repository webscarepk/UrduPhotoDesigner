package com.webscare.urducanvas.di

import android.content.Context
import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import com.webscare.urducanvas.common.utils.SvgDecoder
import com.webscare.urducanvas.common.utils.SvgDrawableTranscoder
import java.io.InputStream

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry
            .register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder())
            .append(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDiskCache(
            com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory(
                context,
                500 * 1024 * 1024L // 500MB
            )
        )
        builder.setMemoryCache(
            com.bumptech.glide.load.engine.cache.LruResourceCache(
                50 * 1024 * 1024L // 50MB
            )
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}