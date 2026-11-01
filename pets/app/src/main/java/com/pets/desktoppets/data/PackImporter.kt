package com.pets.desktoppets.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Imports pet packs chosen by the user with the system folder picker. A pack is a folder with a pack.json and one
 * sub-folder per pet (built by tools/build_videos.py --pack). The picked folder may be one pack or a folder of packs.
 * Files are copied into app storage so playback never depends on the picker's permission or the original location.
 */
object PackImporter {
    data class Result(val imported: List<String>, val error: String?)

    fun import(context: Context, tree: Uri, onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit): Result {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return Result(emptyList(), "Cannot open that folder.")
        val packs = if (root.findFile("pack.json") != null) listOf(root)
        else root.listFiles().filter { it.isDirectory && it.findFile("pack.json") != null }
        if (packs.isEmpty()) return Result(emptyList(), "No pack.json found. Pick the unzipped pack folder.")

        val total = packs.sumOf { sizeOf(it) }
        var done = 0L
        val imported = mutableListOf<String>()
        for (p in packs) {
            val name = safeName(p.name ?: "pack")
            val tmp = File(CharacterRepository.packsDir(context), ".$name.tmp").apply { deleteRecursively(); mkdirs() }
            try {
                copyTree(context, p, tmp) { n -> done += n; onProgress(done, total) }
                val target = File(CharacterRepository.packsDir(context), name)
                target.deleteRecursively()
                if (!tmp.renameTo(target)) throw java.io.IOException("rename failed")
                imported += name
            } catch (e: Exception) {
                tmp.deleteRecursively()
                return Result(imported, "Import stopped (${e.message ?: "error"}). Is there enough free space?")
            }
        }
        return Result(imported, null)
    }

    fun remove(context: Context, packName: String) {
        File(CharacterRepository.packsDir(context), safeName(packName)).deleteRecursively()
    }

    private fun safeName(n: String) = n.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim('.', ' ').ifEmpty { "pack" }

    private fun sizeOf(d: DocumentFile): Long = if (d.isDirectory) d.listFiles().sumOf { sizeOf(it) } else d.length()

    private fun copyTree(context: Context, src: DocumentFile, dst: File, onBytes: (Long) -> Unit) {
        for (f in src.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) copyTree(context, f, File(dst, safeName(name)).apply { mkdirs() }, onBytes)
            else if (name.endsWith(".mp4") || name.endsWith(".jpg") || name.endsWith(".json")) {
                context.contentResolver.openInputStream(f.uri)?.use { input ->
                    File(dst, name).outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); onBytes(n.toLong()) }
                    }
                }
            }
        }
    }
}
