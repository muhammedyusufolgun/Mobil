package com.arvision.studio

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ArScene(
    val id: String,
    val title: String,
    val displayText: String,
    val imagePath: String,
    val widthMeters: Float = 0.20f,
    val origin: String = "local",
    val effect: String = "CARD",
    val color: String = "#38BDF8",
    val sizeMeters: Float = 0.12f,
    val heightMeters: Float = 0.08f
)

/** Durable local scene store; admin drafts and verified published scenes are separate. */
class ProjectStore(private val context: Context) {
    private val dir = File(context.filesDir, "arvision").apply { mkdirs() }
    private val draftsFile = File(dir, "drafts.json")
    private val publishedFile = File(dir, "published.json")
    private val prefs = context.getSharedPreferences("arvision-admin", Context.MODE_PRIVATE)

    /** For Google Cloud API keys restricted to this Android package + signing SHA-1.
     * These request headers identify the app; they are not user authentication. */
    @Suppress("DEPRECATION")
    fun driveAndroidIdentity(): Pair<String, String>? = runCatching {
        val pm = context.packageManager
        val packageName = context.packageName
        val bytes = if (android.os.Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            requireNotNull(info.signingInfo).apkContentsSigners.first().toByteArray()
        } else {
            val info = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            requireNotNull(info.signatures).first().toByteArray()
        }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-1")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        packageName to fingerprint
    }.getOrNull()

    fun driveApiKey(): String = prefs.getString("drive_api_key", "") ?: ""
    fun setDriveApiKey(key: String) { prefs.edit().putString("drive_api_key", key.trim()).apply() }
    fun customFolder(): String? = prefs.getString("custom_drive_folder", null)?.takeIf { it.isNotBlank() }
    fun setCustomFolder(url: String?) {
        val before = customFolder()
        if (url.isNullOrBlank()) prefs.edit().remove("custom_drive_folder").commit()
        else prefs.edit().putString("custom_drive_folder", url).commit()
        // Select the matching source cache rather than deleting a previously downloaded publication.
        if (before != customFolder()) prefs.edit().putString("active_source", cacheTag()).apply()
    }
    fun activeFolderId(): String {
        val custom = customFolder() ?: return BuildConfig.BOOTSTRAP_FOLDER_ID
        // Local admin-only setting, never included in exported AR scene payloads.
        return Regex("(?:/folders/|^)([a-zA-Z0-9_-]{10,})").find(custom)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Geçerli Google Drive klasör adresi girin")
    }
    // Each Drive source owns its own last-known-good cache. Switching sources never erases other offline packages.
    private fun cacheTag(): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(activeFolderId().toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) }
    private fun sourceFile() = File(dir, "published-${cacheTag()}.json")
    fun publishedVersion(): String = prefs.getString("published_version_${cacheTag()}", "") ?: ""
    fun markPublishedVersion(version: String) { prefs.edit().putString("published_version_${cacheTag()}", version).apply() }

    private fun read(file: File): List<ArScene> = runCatching {
        val array = JSONArray(file.readText(Charsets.UTF_8))
        (0 until array.length()).map { i ->
            val x = array.getJSONObject(i)
            ArScene(x.getString("id"), x.optString("title"), x.optString("displayText"), x.getString("imagePath"), x.optDouble("widthMeters", 0.2).toFloat(), x.optString("origin", "local"),
                x.optString("effect", "CARD"), x.optString("color", "#38BDF8"),
                x.optDouble("sizeMeters", 0.12).toFloat(), x.optDouble("heightMeters", 0.08).toFloat())
        }
    }.getOrDefault(emptyList())
    fun drafts(): List<ArScene> = read(draftsFile)
    fun published(): List<ArScene> = read(sourceFile())
    fun allVisible(): List<ArScene> = published()
    private fun write(file: File, scenes: List<ArScene>) {
        val json = JSONArray()
        scenes.forEach { s -> json.put(JSONObject().apply {
            put("id", s.id); put("title", s.title); put("displayText", s.displayText)
            put("imagePath", s.imagePath); put("widthMeters", s.widthMeters); put("origin", s.origin)
            put("effect", s.effect); put("color", s.color); put("sizeMeters", s.sizeMeters); put("heightMeters", s.heightMeters)
        }) }
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(json.toString(), Charsets.UTF_8)
        check(tmp.renameTo(file)) { "Dosya kaydedilemedi" }
    }
    fun saveDraft(scene: ArScene) {
        write(draftsFile, drafts().filterNot { it.id == scene.id } + scene)
    }
    fun deleteDraft(id: String) { write(draftsFile, drafts().filterNot { it.id == id }) }
    fun addImageDraft(uri: Uri, title: String, displayText: String, widthMeters: Float,
                      effect: String, color: String, sizeMeters: Float, heightMeters: Float): ArScene {
        val id = UUID.randomUUID().toString()
        val imageDir = File(dir, "draft-images").apply { mkdirs() }
        val dst = File(imageDir, "$id.jpg")
        val src = requireNotNull(context.contentResolver.openInputStream(uri))
        src.use { input -> dst.outputStream().use { output -> input.copyTo(output) } }
        return ArScene(id, title, displayText, dst.absolutePath, widthMeters, "local",
            effect, color, sizeMeters, heightMeters).also { saveDraft(it) }
    }
    fun addBitmapDraft(bitmap: Bitmap, title: String, displayText: String, widthMeters: Float,
                       effect: String, color: String, sizeMeters: Float, heightMeters: Float): ArScene {
        val id = UUID.randomUUID().toString()
        val imageDir = File(dir, "draft-images").apply { mkdirs() }
        val dst = File(imageDir, "$id.jpg")
        dst.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        return ArScene(id, title, displayText, dst.absolutePath, widthMeters, "local",
            effect, color, sizeMeters, heightMeters).also { saveDraft(it) }
    }
    fun replacePublished(scenes: List<ArScene>, version: String) {
        write(sourceFile(), scenes)
        markPublishedVersion(version)
    }
    fun directory(): File = dir
}
