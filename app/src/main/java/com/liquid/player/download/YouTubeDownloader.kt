package com.liquid.player.download

import android.content.Context
import android.util.Log
import com.liquid.player.data.MusicRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class VideoItem(
    val id: String,
    val title: String,
    val uploader: String,
    val durationSec: Int,
    val thumbnail: String,
    val url: String,
)

object YouTubeDownloader {
    private const val TAG = "YouTubeDownloader"

    @Volatile
    var ready: Boolean = false
        private set

    /** Extracts the bundled python / yt-dlp / ffmpeg. Call off the main thread. */
    @Synchronized
    fun init(context: Context) {
        if (ready) return
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            ready = true
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
        }
    }

    /**
     * Updates yt-dlp to the newest stable build (YouTube changes often, so old
     * versions stop working). Done through reflection so a library API change
     * can never break compilation; failure is harmless.
     */
    fun updateYtDlp(context: Context) {
        try {
            val yd = YoutubeDL.getInstance()
            val method = yd.javaClass.methods.firstOrNull {
                it.name == "updateYoutubeDL" && it.parameterTypes.size == 2
            } ?: return
            val channelClass = method.parameterTypes[1]
            val stable: Any? = findStableChannel(channelClass)
            if (stable != null) {
                method.invoke(yd, context.applicationContext, stable)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp update skipped", t)
        }
    }

    private fun findStableChannel(cls: Class<*>): Any? {
        // Static field style: UpdateChannel.STABLE / _STABLE
        for (name in listOf("STABLE", "_STABLE")) {
            runCatching { cls.getField(name).get(null) }.getOrNull()?.let { return it }
        }
        // Kotlin `companion object`
        runCatching {
            val companion = cls.getField("Companion").get(null)
            for (getter in listOf("getSTABLE", "get_STABLE")) {
                runCatching { companion.javaClass.getMethod(getter).invoke(companion) }
                    .getOrNull()?.let { return it }
            }
        }
        // Kotlin `object STABLE`
        runCatching {
            val nested = Class.forName(cls.name + "\$STABLE")
            return nested.getField("INSTANCE").get(null)
        }
        // Enum style
        cls.enumConstants?.firstOrNull { it.toString().contains("STABLE", ignoreCase = true) }
            ?.let { return it }
        return null
    }

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.startsWith("http://") || t.startsWith("https://") ||
            t.contains("youtu.be/") || t.contains("youtube.com/")
    }

    /** Text query -> up to 12 results; link -> single item. */
    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        check(ready) { "yt-dlp ещё запускается, подожди пару секунд" }
        val q = query.trim()
        val isUrl = looksLikeUrl(q)
        val request = YoutubeDLRequest(if (isUrl) q else "ytsearch12:$q")
        request.addOption("--dump-json")
        if (isUrl) request.addOption("--no-playlist") else request.addOption("--flat-playlist")
        request.addOption("--no-warnings")
        val response = YoutubeDL.getInstance().execute(request, null) { _, _, _ -> }
        val items = ArrayList<VideoItem>()
        for (line in response.out.lines()) {
            val s = line.trim()
            if (!s.startsWith("{")) continue
            runCatching {
                val j = JSONObject(s)
                val id = j.optString("id")
                if (id.isNotBlank()) {
                    items.add(
                        VideoItem(
                            id = id,
                            title = j.optString("title").ifBlank { "Без названия" },
                            uploader = j.optString("uploader").ifBlank {
                                j.optString("channel").ifBlank { "YouTube" }
                            },
                            durationSec = j.optDouble("duration", 0.0).toInt(),
                            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                            url = "https://www.youtube.com/watch?v=$id",
                        )
                    )
                }
            }
        }
        items
    }

    /** Downloads [item] as MP3 into the app music folder. Returns the resulting file. */
    suspend fun downloadMp3(
        context: Context,
        item: VideoItem,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        check(ready) { "yt-dlp ещё запускается, подожди пару секунд" }
        val dir = MusicRepository.downloadsDir(context)
        val startedAt = System.currentTimeMillis() - 2000

        val request = YoutubeDLRequest(item.url)
        request.addOption("-f", "bestaudio/best")
        request.addOption("-x")
        request.addOption("--audio-format", "mp3")
        request.addOption("--audio-quality", "0")
        request.addOption("--no-playlist")
        request.addOption("--embed-metadata")
        request.addOption("--no-mtime")
        request.addOption("--trim-filenames", "120")
        request.addOption("-o", dir.absolutePath + "/%(title)s.%(ext)s")

        YoutubeDL.getInstance().execute(request, item.id) { progress, _, _ ->
            onProgress((progress / 100f).coerceIn(0f, 1f))
        }

        val file = dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("mp3", true) && it.lastModified() >= startedAt }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("Файл не создан. Проверь интернет или обнови приложение")

        // Cover art next to the file (non-fatal if it fails).
        runCatching {
            val cover = File(dir, file.nameWithoutExtension + ".jpg")
            val conn = URL(item.thumbnail).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.use { input -> cover.outputStream().use { input.copyTo(it) } }
        }
        file
    }
}
