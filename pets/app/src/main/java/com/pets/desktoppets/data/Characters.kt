package com.pets.desktoppets.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONObject

/** One pet = one looping video bundled in assets (see assets/pets/manifest.json, built by tools/build_videos.py). */
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
) {
    val aspect: Float get() = width.toFloat() / height
    val videoUri: String get() = "asset:///$videoPath"
}

/** Manifest-driven asset repository. Only metadata is held in memory. */
class CharacterRepository(private val context: Context) {
    val characters: List<CharacterInfo> by lazy { load() }

    fun byId(id: String?): CharacterInfo? = characters.firstOrNull { it.id == id }
    fun resolve(id: String?): CharacterInfo? = byId(id) ?: characters.firstOrNull()

    private val posters = object : LruCache<String, Bitmap>(16) {}

    fun poster(info: CharacterInfo): Bitmap? {
        posters.get(info.id)?.let { return it }
        return try {
            context.assets.open(info.posterPath).use { BitmapFactory.decodeStream(it) }?.also { posters.put(info.id, it) }
        } catch (e: Exception) {
            null   // missing / damaged poster: UI shows a placeholder
        }
    }

    private fun load(): List<CharacterInfo> = try {
        val root = JSONObject(context.assets.open("pets/manifest.json").bufferedReader().use { it.readText() })
        val arr = root.getJSONArray("pets")
        buildList {
            for (i in 0 until arr.length()) {
                runCatching {
                    val o = arr.getJSONObject(i)
                    val info = CharacterInfo(
                        id = o.getString("id"), name = o.getString("name"),
                        videoPath = o.getString("video"), posterPath = o.getString("poster"),
                        width = o.getInt("width"), height = o.getInt("height"),
                        durationMs = o.getLong("durationMs"), hasAudio = o.optBoolean("hasAudio", true),
                        characters = o.optJSONArray("characters")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                    )
                    context.assets.openFd(info.videoPath).close()   // skip entries whose video is missing
                    add(info)
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
