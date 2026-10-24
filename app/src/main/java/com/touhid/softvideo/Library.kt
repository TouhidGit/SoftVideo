package com.touhid.softvideo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Video(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val folder: String,
    val uri: Uri
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
                    uri = ContentUris.withAppendedId(collection, id)
                )
            )
        }
    }
    return list
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
