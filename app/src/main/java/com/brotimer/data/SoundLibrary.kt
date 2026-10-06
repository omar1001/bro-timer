package com.brotimer.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Where alarm sounds come from, and where imported ones live.
 *
 * A stored `soundUri` is one of three things:
 *
 * | value | meaning |
 * |---|---|
 * | `null` | the phone's default alarm sound |
 * | `content://...` | a sound the phone itself manages (a system ringtone) |
 * | `file:///data/.../files/sounds/<name>` | a sound **imported into BroTimer** |
 *
 * **Everything Omar picks from outside the system ringtones is copied in, never referenced.** A
 * Zedge download, a WhatsApp voice note or a file in Downloads can be deleted, moved, or lose its
 * read permission at any time — and an alarm that silently loses its sound is the worst failure
 * this app can have. A private copy cannot disappear. Ringtones are small, so the cost is nothing.
 */
object SoundLibrary {

    private const val TAG = "BroTimer"
    private const val DIR = "sounds"

    /** Generous for a ringtone, small enough that a mis-picked 2-hour podcast is refused. */
    const val MAX_IMPORT_BYTES = 30L * 1024 * 1024

    private val durations = ConcurrentHashMap<String, Long>()

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Imported sounds, newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun uriOf(file: File): String = Uri.fromFile(file).toString()

    fun fileOf(context: Context, soundUri: String?): File? {
        if (soundUri == null || !soundUri.startsWith("file:")) return null
        val path = Uri.parse(soundUri).path ?: return null
        val file = File(path)
        return if (file.parentFile?.canonicalPath == dir(context).canonicalPath) file else null
    }

    // -- importing -------------------------------------------------------------------------------

    class ImportFailed(message: String) : IOException(message)

    /**
     * Copies any readable audio [source] into the library and returns the copy. Re-importing the
     * same file (same name, same size) returns the existing copy instead of duplicating it.
     */
    suspend fun import(context: Context, source: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(
                source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        if (size > MAX_IMPORT_BYTES) throw ImportFailed("That file is too big for an alarm sound (over 30 MB).")

        val fileName = sanitize(name ?: source.lastPathSegment ?: "sound")
        val dir = dir(context)
        var target = File(dir, fileName)
        if (target.exists() && size >= 0 && target.length() == size) return@withContext target

        val stem = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 2
        while (target.exists()) target = File(dir, "$stem ($n)$ext").also { n++ }

        // Copy to a hidden temp file first, so a half-copied file never shows up as a sound.
        val part = File(dir, ".${target.name}.part")
        try {
            val input = resolver.openInputStream(source)
                ?: throw ImportFailed("Could not open that file.")
            input.use { inp ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = inp.read(buf)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) {
                            throw ImportFailed("That file is too big for an alarm sound (over 30 MB).")
                        }
                        out.write(buf, 0, read)
                    }
                }
            }
            if (!isPlayable(context, Uri.fromFile(part))) {
                throw ImportFailed("That file is not audio the phone can play.")
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
            }
        } finally {
            part.delete()
        }
        target
    }

    /** Keeps the original name readable (Arabic included) and strips only what a path cannot hold. */
    private fun sanitize(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimStart('.')
        return cleaned.take(80).ifBlank { "sound" }
    }

    private fun isPlayable(context: Context, uri: Uri): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            mp.setDataSource(context, uri)
            mp.prepare()
            true
        } catch (_: Exception) {
            false
        } finally {
            mp.release()
        }
    }

    /**
     * Deletes an imported sound. Any alarm or timer that used it goes back to the phone's default
     * alarm sound, so nothing is left pointing at a missing file.
     */
    fun delete(context: Context, file: File) {
        val uri = uriOf(file)
        file.delete()
        durations.remove(uri)
        Store.replaceSound(uri, null)
    }

    // -- describing a sound -----------------------------------------------------------------------

    /**
     * System ringtone URIs are often "canonical" (`.../media/270?title=X&canonical=1`): the number
     * is only a hint and the title is the truth. **On this phone the stored default alarm said 270
     * while the real file was 272** — 270 had become `charging.ogg`. Resolve before use.
     */
    fun resolve(context: Context, uri: Uri): Uri {
        if (uri.scheme != "content") return uri
        return try {
            context.contentResolver.uncanonicalize(uri) ?: uri
        } catch (e: Exception) {
            Log.w(TAG, "could not resolve $uri", e)
            uri
        }
    }

    /** Blocking — call off the main thread. */
    fun displayName(context: Context, soundUri: String?): String {
        if (soundUri == null) {
            val actual = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            val title = actual?.let { titleOf(context, it) }
            return if (title.isNullOrBlank()) "Phone's default alarm" else "Default ($title)"
        }
        fileOf(context, soundUri)?.let { return it.name.substringBeforeLast('.', it.name) }
        return titleOf(context, Uri.parse(soundUri)) ?: "Phone sound"
    }

    private fun titleOf(context: Context, uri: Uri): String? {
        // A canonical ringtone URI carries its own title, so no media query (and no permission)
        // is needed. Ringtone.getTitle is the fallback; when it cannot read the media database it
        // quietly returns the URI's last segment — the bare "270" seen on 2026-10-06 — so a
        // purely numeric answer is treated as "unknown".
        uri.getQueryParameter("title")?.let { return it.replace('_', ' ') }
        return try {
            RingtoneManager.getRingtone(context, resolve(context, uri))?.getTitle(context)
                ?.replace('_', ' ')
                ?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) && it != uri.toString() }
        } catch (e: Exception) {
            Log.w(TAG, "no title for $uri", e)
            null
        }
    }

    /**
     * Length of one play of the sound, or 0 if unknown. Blocking — call off the main thread.
     *
     * Measured with a MediaPlayer, not MediaMetadataRetriever: for the default alarm
     * (`content://settings/system/alarm_alert`) the retriever fails with "could not access"
     * (seen on this phone, 2026-10-06), while MediaPlayer falls back to the system's cached copy
     * of the ringtone — the same path the alarm itself plays through.
     */
    fun durationMs(context: Context, soundUri: String?): Long {
        val key = soundUri ?: "default"
        durations[key]?.let { return it }
        val uri = if (soundUri == null) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            resolve(context, Uri.parse(soundUri))
        }
        val mp = MediaPlayer()
        val ms = try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            mp.setDataSource(context, uri)
            mp.prepare()
            mp.duration.toLong().coerceAtLeast(0L)
        } catch (e: Exception) {
            Log.w(TAG, "no duration for $uri", e)
            0L
        } finally {
            runCatching { mp.release() }
        }
        if (ms > 0) durations[key] = ms
        return ms
    }

    // -- audio already on the phone -----------------------------------------------------------------

    data class PhoneAudio(
        val uri: Uri,
        val name: String,
        val durationMs: Long,
        /** e.g. `Ringtones`, `Download`, `WhatsApp Voice Notes` */
        val folder: String,
        val addedAtSec: Long,
    )

    val audioPermission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    /**
     * Every audio file on the phone, **newest first** — which is what makes Zedge easy: whatever
     * Omar downloaded a minute ago is the first row. Blocking — call off the main thread.
     */
    fun queryPhoneAudio(context: Context, limit: Int = 400): List<PhoneAudio> {
        if (!hasAudioPermission(context)) return emptyList()
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        val folderColumn =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.RELATIVE_PATH
            } else {
                @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
            }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            folderColumn,
        )
        val out = ArrayList<PhoneAudio>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.DURATION} >= ?",
                arrayOf("300"),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    val display = c.getString(1).orEmpty()
                    val title = c.getString(2).orEmpty()
                    val name = title.ifBlank { display.substringBeforeLast('.', display) }
                    // RELATIVE_PATH is already a folder; the pre-Android-10 DATA column is a full
                    // file path, so drop the file name from it.
                    val rawFolder = c.getString(5).orEmpty().let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it else it.substringBeforeLast('/')
                    }
                    out += PhoneAudio(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name.ifBlank { "Audio $id" },
                        durationMs = c.getLong(3),
                        folder = shortFolder(rawFolder),
                        addedAtSec = c.getLong(4),
                    )
                }
            }
        }
        return out
    }

    /** `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202640/` → `WhatsApp Voice Notes` */
    private fun shortFolder(raw: String): String {
        val parts = raw.trim('/').split('/').filter { it.isNotBlank() }
        val meaningful = parts.lastOrNull { seg -> seg.any { !it.isDigit() } }
        return meaningful ?: parts.lastOrNull().orEmpty()
    }
}
