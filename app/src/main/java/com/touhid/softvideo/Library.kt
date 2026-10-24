package com.touhid.softvideo

import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

data class Video(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val folder: String,
    val uri: Uri,
    val file: String // file name on the device, e.g. "clip.mp4"
)

fun loadVideos(context: Context): List<Video> {
    val list = mutableListOf<Video>()
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )
    context.contentResolver.query(
        collection, projection, null, null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val bucketCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val name = c.getString(nameCol) ?: "Video"
            list.add(
                Video(
                    id = id,
                    title = name.substringBeforeLast('.'),
                    durationMs = c.getLong(durCol),
                    folder = c.getString(bucketCol) ?: "Unknown",
                    uri = ContentUris.withAppendedId(collection, id),
                    file = name
                )
            )
        }
    }
    return list
}

/**
 * Renames the video file on the device, keeping its extension.
 * Needs write access first (MediaStore.createWriteRequest).
 * Returns the new file name, or null if the rename failed.
 */
fun renameVideo(context: Context, v: Video, title: String): String? {
    val clean = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    val ext = v.file.substringAfterLast('.', "")
    val newFile = if (ext.isEmpty()) clean else "$clean.$ext"
    if (newFile == v.file) return newFile
    val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, newFile) }
    val ok = try {
        context.contentResolver.update(v.uri, values, null, null) > 0
    } catch (e: Exception) {
        false
    }
    return if (ok) newFile else null
}

fun shareVideo(context: Context, v: Video) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(v.uri) ?: "video/*"
        putExtra(Intent.EXTRA_STREAM, v.uri)
        clipData = ClipData.newRawUri(v.title, v.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${v.title}"))
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
