// androidx.media is deprecated in favor of Media3, but it is still the simplest
// way to control other apps' platform sessions. Media3's MediaController is the
// migration path if it ever stops working.
@file:Suppress("DEPRECATION")

package io.github.krnelpan1c.droidpod.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import android.support.v4.media.MediaMetadataCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Album art comes either as a bitmap inside the metadata (most players) or as a
 * URI we may or may not be allowed to read. Remote (http) URIs are skipped: we
 * have no network permission.
 */
class ArtLoader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLoaded: () -> Unit,
) {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 6
    }
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private var lastBitmap: Bitmap? = null
    private var lastImage: ImageBitmap? = null

    fun artFor(metadata: MediaMetadataCompat?): ImageBitmap? {
        metadata ?: return null
        bitmapFrom(metadata)?.let { bitmap ->
            if (bitmap !== lastBitmap) {
                lastBitmap = bitmap
                lastImage = bitmap.asImageBitmap()
            }
            return lastImage
        }
        val uri = uriFrom(metadata) ?: return null
        cache[uri]?.let { return it }
        if (uri !in pending && uri !in failed && LOCAL_SCHEMES.any { uri.startsWith(it) }) {
            pending += uri
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { runCatching { decode(uri.toUri()) }.getOrNull() }
                pending -= uri
                if (bitmap != null) {
                    cache[uri] = bitmap.asImageBitmap()
                    onLoaded()
                } else {
                    failed += uri
                }
            }
        }
        return null
    }

    private fun decode(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            if (w > MAX_SIZE || h > MAX_SIZE) {
                val scale = MAX_SIZE.toFloat() / maxOf(w, h)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    companion object {
        private const val MAX_SIZE = 600
        private val LOCAL_SCHEMES = listOf("content:", "android.resource:", "file:")

        val BITMAP_KEYS = listOf(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
            MediaMetadataCompat.METADATA_KEY_ART,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
        )
        val URI_KEYS = listOf(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadataCompat.METADATA_KEY_ART_URI,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
        )

        fun bitmapFrom(m: MediaMetadataCompat): Bitmap? =
            BITMAP_KEYS.firstNotNullOfOrNull { m.getBitmap(it) } ?: m.description?.iconBitmap

        fun uriFrom(m: MediaMetadataCompat): String? =
            URI_KEYS.firstNotNullOfOrNull { key -> m.getString(key)?.takeIf { it.isNotBlank() } }
                ?: m.description?.iconUri?.toString()
    }
}
