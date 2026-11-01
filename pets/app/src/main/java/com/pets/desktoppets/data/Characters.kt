package com.pets.desktoppets.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import org.json.JSONObject
import java.io.File

/**
 * One pet = one looping video. Bundled pets live in assets (assets/pets/manifest.json, built by tools/build_videos.py);
 * imported pets live in app storage (filesDir/packs/<pack>/, see PackImporter). [packDir] is null for bundled pets.
 */
data class CharacterInfo(
    val id: String,
    val name: String,
    val videoPath: String,
    val posterPath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val hasAudio: Boolean,
    /** The characters shown in the clip (best effort, see tools/pet_names.json). */
    val characters: List<String> = emptyList(),
    val packDir: String? = null,
    val packName: String? = null,
) {
    val aspect: Float get() = width.toFloat() / height
    val videoUri: String get() = if (packDir == null) "asset:///$videoPath" else Uri.fromFile(File(packDir, videoPath)).toString()
}

data class PackInfo(val name: String, val dir: File, val petCount: Int, val bytes: Long)

/** Manifest-driven repository for bundled and imported pets. Only metadata is held in memory. */
class CharacterRepository(private val context: Context) {
    @Volatile private var all: List<CharacterInfo> = load()
    val characters: List<CharacterInfo> get() = all

    /** Re-reads bundled + imported pets (call after importing or removing a pack). */
    fun reload() { all = load() }

    fun byId(id: String?): CharacterInfo? = all.firstOrNull { it.id == id }
    fun resolve(id: String?): CharacterInfo? = byId(id) ?: all.firstOrNull()

    fun packs(): List<PackInfo> = packsDir(context).listFiles { f -> f.isDirectory && File(f, "pack.json").exists() }
        .orEmpty().sortedBy { it.name }
        .map { d -> PackInfo(d.name, d, all.count { it.packDir == d.path }, d.walkTopDown().filter { it.isFile }.sumOf { it.length() }) }

    private val posters = object : LruCache<String, Bitmap>(64) {}

    /** Already-decoded poster or null (never blocks). */
    fun cachedPoster(id: String): Bitmap? = posters.get(id)

    fun poster(info: CharacterInfo): Bitmap? {
        posters.get(info.id)?.let { return it }
        return try {
            val o = BitmapFactory.Options().apply { inSampleSize = 2 }   // 360 px wide is plenty for cards
            val bmp = if (info.packDir == null) context.assets.open(info.posterPath).use { BitmapFactory.decodeStream(it, null, o) }
            else BitmapFactory.decodeFile(File(info.packDir, info.posterPath).path, o)
            bmp?.also { posters.put(info.id, it) }
        } catch (e: Exception) {
            null   // missing / damaged poster: UI shows a placeholder
        }
    }

    private fun parse(arr: org.json.JSONArray, packDir: File?): List<CharacterInfo> = buildList {
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val pack = packDir?.name
                val info = CharacterInfo(
                    id = (if (pack != null) "$pack/" else "") + o.getString("id"), name = o.getString("name"),
                    videoPath = o.getString("video"), posterPath = o.getString("poster"),
                    width = o.getInt("width"), height = o.getInt("height"),
                    durationMs = o.getLong("durationMs"), hasAudio = o.optBoolean("hasAudio", true),
                    characters = o.optJSONArray("characters")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                    packDir = packDir?.path, packName = pack,
                )
                // skip entries whose video is missing instead of crashing later
                if (packDir == null) context.assets.openFd(info.videoPath).close() else require(File(packDir, info.videoPath).isFile)
                add(info)
            }
        }
    }

    private fun load(): List<CharacterInfo> {
        val bundled = try {
            val root = JSONObject(context.assets.open("pets/manifest.json").bufferedReader().use { it.readText() })
            parse(root.getJSONArray("pets"), null)
        } catch (e: Exception) { emptyList() }
        val imported = packsDir(context).listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.flatMap { d ->
            runCatching { parse(JSONObject(File(d, "pack.json").readText()).getJSONArray("pets"), d) }.getOrDefault(emptyList())
        }
        return bundled + imported
    }

    companion object {
        fun packsDir(context: Context) = File(context.filesDir, "packs").apply { mkdirs() }
    }
}
