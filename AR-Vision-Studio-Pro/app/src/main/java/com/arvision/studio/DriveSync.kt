package com.arvision.studio

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One-version-per-bundle publication protocol, built for reliable last-known-good offline use. */
object DriveSync {
    const val BUNDLE = "ARVISION_PUBLISHED.zip"
    data class Result(val changed: Boolean, val message: String)
    private const val MAX_ZIP = 25 * 1024 * 1024
    private const val MAX_EXPANDED = 70 * 1024 * 1024L

    private fun request(url: String, maxBytes: Int): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500; readTimeout = 9000
            requestMethod = "GET"; setRequestProperty("Accept", "application/json, application/octet-stream")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != 200) throw IllegalStateException("Drive HTTP ${connection.responseCode}")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var n: Int
                while (input.read(buffer).also { n = it } != -1) {
                    if (output.size() + n > maxBytes) throw IllegalStateException("Paket sınırı aşıldı")
                    output.write(buffer, 0, n)
                }
            }
            return output.toByteArray()
        } finally { connection.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun check(store: ProjectStore, onDownloading: suspend () -> Unit): Result = withContext(Dispatchers.IO) {
        val apiKey = store.driveApiKey().ifBlank { BuildConfig.DRIVE_API_KEY }
        if (apiKey.isBlank()) return@withContext Result(false, "Drive API anahtarı yapılandırılmadı; yerel içerikler kullanılabilir")
        val folder = store.activeFolderId()
        require(folder.isNotBlank()) { "Başlangıç klasörü yapılandırılmadı" }
        val q = "'$folder' in parents and name = '$BUNDLE' and trashed = false"
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=${enc(q)}&fields=${enc("files(id,name,md5Checksum,modifiedTime,size),nextPageToken")}&orderBy=${enc("modifiedTime desc")}&pageSize=10&key=${enc(apiKey)}"
        val items = JSONObject(String(request(listUrl, 100_000), Charsets.UTF_8)).getJSONArray("files")
        if (items.length() == 0) return@withContext Result(false, "Henüz yayınlanmış AR paketi bulunamadı")
        val item = items.getJSONObject(0)
        val fileId = item.getString("id")
        val version = item.optString("md5Checksum").ifBlank { fileId + ":" + item.optString("modifiedTime") }
        if (version == store.publishedVersion()) return@withContext Result(false, "Güncel")
        if (item.optLong("size", 0) > MAX_ZIP) throw IllegalStateException("Paket 25 MB sınırını aşıyor")
        onDownloading()
        val data = request("https://www.googleapis.com/drive/v3/files/${enc(fileId)}?alt=media&key=${enc(apiKey)}", MAX_ZIP)
        val expectedMd5 = item.optString("md5Checksum")
        if (expectedMd5.isNotBlank()) {
            val got = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
            if (!got.equals(expectedMd5, ignoreCase = true)) throw IllegalStateException("İndirilen dosya bütünlüğü doğrulanamadı")
        }
        val scenes = extractVerifiedBundle(store, data, version)
        store.replacePublished(scenes, version)
        Result(true, "${scenes.size} AR tanımı güncellendi")
    }

    private fun extractVerifiedBundle(store: ProjectStore, data: ByteArray, version: String): List<ArScene> {
        val base = File(store.directory(), "sync-${version.replace(Regex("[^a-zA-Z0-9_-]"), "").take(48)}").apply { mkdirs() }
        val contents = mutableMapOf<String, File>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val item: ZipEntry = zip.nextEntry ?: break
                val name = item.name
                if (item.isDirectory) continue
                require(name == "manifest.json" || Regex("refs/[a-zA-Z0-9_.-]{1,128}\\.(jpg|jpeg|png)").matches(name)) { "Paket içerisinde geçersiz yol" }
                require(!contents.containsKey(name)) { "Aynı dosya birden fazla kez kullanılmış" }
                require(contents.size < 250) { "Paket çok fazla dosya içeriyor" }
                val target = File(base, name)
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (zip.read(buf).also { n = it } != -1) {
                        total += n
                        if (total > MAX_EXPANDED) throw IllegalStateException("Açılmış paket fazla büyük")
                        out.write(buf, 0, n)
                    }
                }
                contents[name] = target
            }
        }
        val json = JSONObject(requireNotNull(contents["manifest.json"]) { "manifest.json eksik" }.readText())
        require(json.optInt("schema") == 1) { "Desteklenmeyen paket sürümü" }
        val array = json.getJSONArray("scenes")
        require(array.length() <= 200) { "Çok fazla görsel" }
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val rel = obj.getString("reference")
            val image = requireNotNull(contents[rel]) { "Referans görsel eksik" }
            require(image.length() in 1..(8L * 1024 * 1024)) { "Görsel boyutu geçersiz" }
            val bitmap = requireNotNull(BitmapFactory.decodeFile(image.absolutePath)) { "Görsel okunamadı" }
            require(bitmap.width >= 64 && bitmap.height >= 64) { "Referans görsel çok küçük" }
            bitmap.recycle()
            val width = obj.optDouble("widthMeters", 0.2).toFloat()
            require(width in 0.01f..10f) { "Geçersiz referans genişliği" }

            val effect = obj.optString("effect", "CARD")
            require(effect in setOf("CARD", "CUBE", "ARROW")) { "Geçersiz AR efekt türü" }
            val color = obj.optString("color", "#38BDF8")
            require(Regex("#[0-9a-fA-F]{6}").matches(color)) { "Geçersiz renk" }
            val size = obj.optDouble("sizeMeters", 0.12).toFloat()
            val height = obj.optDouble("heightMeters", 0.08).toFloat()
            require(size in 0.02f..2f && height in -1f..2f) { "3B boyutları geçersiz" }
            ArScene(obj.getString("id"), obj.optString("title").take(100),
                obj.optString("displayText").take(500), image.absolutePath, width, "drive", effect, color, size, height)
        }
    }

    /** Authorized Admin can import a valid publication ZIP without Drive/API access. */
    suspend fun importBundle(store: ProjectStore, data: ByteArray): Int = withContext(Dispatchers.IO) {
        require(data.size <= MAX_ZIP) { "Paket çok büyük" }
        val version = "manual-" + MessageDigest.getInstance("SHA-256")
            .digest(data).joinToString("") { "%02x".format(it) }.take(32)
        val scenes = extractVerifiedBundle(store, data, version)
        store.replacePublished(scenes, version)
        scenes.size
    }

    /** Export local admin drafts for selection of target Google Drive folder via Android's file picker. */
    fun exportDrafts(store: ProjectStore): File {
        val output = File(store.directory(), "draft-export.zip")
        val list = store.drafts()
        val manifest = JSONObject().apply {
            put("schema", 1)
            put("scenes", org.json.JSONArray().apply {
                list.forEach { s -> put(JSONObject().apply {
                    put("id", s.id); put("title", s.title); put("displayText", s.displayText)
                    put("widthMeters", s.widthMeters)
                    put("effect", s.effect); put("color", s.color)
                    put("sizeMeters", s.sizeMeters); put("heightMeters", s.heightMeters)
                    put("reference", "refs/${s.id}.jpg")
                }) }
            })
        }
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            list.forEach { scene ->
                zip.putNextEntry(ZipEntry("refs/${scene.id}.jpg"))
                File(scene.imagePath).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output
    }
}
