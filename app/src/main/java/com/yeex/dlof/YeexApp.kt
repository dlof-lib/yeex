package com.yeex.dlof

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.firebase.FirebaseApp

/**
 * Implements [ImageLoaderFactory] to register Coil's GIF decoder app-wide
 * (coil-gif dependency) — without this, a pasted GIF link preview
 * (LinkPreviewCard) or a GIF Topic cover image just shows a frozen first
 * frame instead of animating. [ImageDecoderDecoder] is used on API 28+
 * (Android's own hardware-accelerated decoder), falling back to the
 * software [GifDecoder] below that.
 */
class YeexApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()
}
