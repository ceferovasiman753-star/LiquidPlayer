package com.liquid.player.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File

object MusicRepository {

    private val audioExt = setOf("mp3", "m4a", "opus", "ogg", "aac", "wav", "flac", "webm")

    fun downloadsDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "music")
        dir.mkdirs()
        return dir
    }

    /** Reads music from the phone (MediaStore) plus files downloaded by the app. */
    fun scan(context: Context, includeDeviceMusic: Boolean): List<Track> {
        val result = ArrayList<Track>()
        val seenPaths = HashSet<String>()

        if (includeDeviceMusic) {
            runCatching { scanMediaStore(context, result, seenPaths) }
        }
        runCatching { scanDownloads(context, result, seenPaths) }

        return result
    }

    @Suppress("DEPRECATION")
    private fun scanMediaStore(context: Context, out: MutableList<Track>, seen: MutableSet<String>) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000"
        val artBase = Uri.parse("content://media/external/audio/albumart")

        context.contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val artist = c.getString(iArtist).orEmpty().let {
                    if (it == "<unknown>" || it.isBlank()) "Неизвестный исполнитель" else it
                }
                val album = c.getString(iAlbum).orEmpty()
                val albumId = c.getLong(iAlbumId)
                val path = c.getString(iData).orEmpty()
                if (path.isNotEmpty()) seen.add(path)
                out.add(
                    Track(
                        key = "ms:$id",
                        uri = ContentUris.withAppendedId(collection, id),
                        title = c.getString(iTitle).orEmpty().ifBlank { "Без названия" },
                        artist = artist,
                        album = album,
                        durationMs = c.getLong(iDur),
                        addedAt = c.getLong(iAdded) * 1000L,
                        artUri = if (albumId > 0) ContentUris.withAppendedId(artBase, albumId) else null,
                    )
                )
            }
        }
    }

    private fun scanDownloads(context: Context, out: MutableList<Track>, seen: MutableSet<String>) {
        val files = downloadsDir(context).listFiles() ?: return
        for (f in files) {
            if (!f.isFile || f.extension.lowercase() !in audioExt) continue
            if (f.absolutePath in seen) continue
            var title = f.nameWithoutExtension
            var artist = "YouTube"
            var album = ""
            var dur = 0L
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(f.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }?.let { artist = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }?.let { album = it }
                dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Throwable) {
            } finally {
                runCatching { mmr.release() }
            }
            val cover = File(f.parentFile, f.nameWithoutExtension + ".jpg")
            out.add(
                Track(
                    key = "file:${f.name}",
                    uri = f.toUri(),
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = dur,
                    addedAt = f.lastModified(),
                    artUri = if (cover.exists()) cover.toUri() else null,
                    downloaded = true,
                )
            )
        }
    }
}
