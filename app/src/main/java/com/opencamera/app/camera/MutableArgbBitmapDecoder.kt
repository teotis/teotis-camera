package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal object MutableArgbBitmapDecoder {
    fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return null
        if (decoded.isMutable && decoded.config == Bitmap.Config.ARGB_8888) {
            return decoded
        }
        val mutable = decoded.copy(Bitmap.Config.ARGB_8888, true)
        if (mutable !== decoded) {
            decoded.recycle()
        }
        return mutable
    }
}
