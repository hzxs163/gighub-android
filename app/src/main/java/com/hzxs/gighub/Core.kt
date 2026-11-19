package com.hzxs.gighub

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class Node(
    val value: String,
    val label: String = "contribute",
    val latencyMs: Long? = null,
    val speedMbps: Double? = null,
    val custom: Boolean = false,
)

data class Asset(
    val name: String,
    val size: Long,
    val url: String,
    val count: Int,
)

data class Release(
    val tag: String,
    val name: String,
    val prerelease: Boolean,
    val publishedAt: String,
    val assets: List<Asset>,
    val zipUrl: String?,
)

enum class LinkKind(val title: String) {
    RELEASE("Release 文件"),
    RAW("Raw 文件"),
    ARCHIVE("源码压缩包"),
    GIST("Gist 文件"),
    REPO("仓库"),
    OTHER("普通链接"),
}

data class AccelResult(
    val original: String,
    val accelerated: String,
    val kind: LinkKind,
    val node: String,
    val cloneCommand: String? = null,
)

object Proxy {
    private val urlRegex = Regex("""https?://[^\s"'<>()（）]+""")
    private val repoRegex = Regex("""^https?://(www\.)?github\.com/[^/\s]+/[^/\s]+/?$""", RegexOption.IGNORE_CASE)

    fun extractUrl(text: String): String? {
        val match = urlRegex.find(text)?.value?.trimEnd('.', ',', '，', '。', ')', '）', ']', '》')
        if (match != null) return match
        val bare = text.trim()
        if (bare.contains(' ') || bare.isBlank()) return null
        if (Regex("""^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/\S*)?$""").matches(bare)) return "https://$bare"
        return null
    }

    fun normalize(raw: String): String? {
        val line = raw.trim().lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        var url = if (line.startsWith("http://", true) || line.startsWith("https://", true)) line else "https://$line"
        url = url.replace(Regex("kkgithub\\.com", RegexOption.IGNORE_CASE), "github.com")
        url = url.replace(" ", "")
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
        if (host.length < 3 || !host.contains('.')) return null
        return url.takeIf { it.length > 8 }
    }

    fun kind(url: String): LinkKind {
        val plain = url.substringBefore('#').substringBefore('?').trimEnd('/')
        return when {
            url.contains("releases/download/") -> LinkKind.RELEASE
            url.contains("raw.githubusercontent.com") || url.contains("/raw/") -> LinkKind.RAW
            url.contains("gist.github.com") || url.contains("gist.githubusercontent.com") -> LinkKind.GIST
            url.contains("/archive/") -> LinkKind.ARCHIVE
            repoRegex.matches(plain) -> LinkKind.REPO
            else -> LinkKind.OTHER
        }
    }

    fun build(node: String, url: String): String = "https://${node.trim().trim('/')}/$url"

    fun clone(node: String, url: String): String = "git clone https://${node.trim().trim('/')}/$url"

    fun fileName(url: String): String {
        val path = url.substringBefore('#').substringBefore('?')
        val raw = path.substringAfterLast('/')
        if (raw.isBlank()) return "github-download.bin"
        val name = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(120)
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/vnd.rar"
        "tar", "gz", "tgz", "xz" -> "application/gzip"
        "exe", "msi" -> "application/octet-stream"
        "dmg" -> "application/x-apple-diskimage"
        "ipa" -> "application/octet-stream"
        "deb" -> "application/vnd.debian.binary-package"
        "rpm" -> "application/x-rpm"
        "iso", "img" -> "application/octet-stream"
        "jar" -> "application/java-archive"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else "%.1f %s".format(value, units[unit])
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("gighub", Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt("theme", 0)
        set(value) = sp.edit().putInt("theme", value).apply()

    var selectedNode: String?
        get() = sp.getString("selected_node", null)
        set(value) = sp.edit().putString("selected_node", value).apply()

    var ghToken: String
        get() = sp.getString("gh_token", "").orEmpty()
        set(value) = sp.edit().putString("gh_token", value).apply()

    var nodeOverride: String?
        get() = sp.getString("node_override", null)
        set(value) = sp.edit().putString("node_override", value).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(value) = sp.edit().putLong("last_sync", value).apply()

    val history: List<String>
        get() = sp.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }

    fun addHistory(url: String) {
        val list = history.filter { it != url }.toMutableList()
        list.add(0, url)
        sp.edit().putString("history", list.take(8).joinToString("\n")).apply()
    }

    val extraNodes: List<String>
        get() = sp.getString("extra_nodes", "").orEmpty().lines().filter { it.isNotBlank() }

    fun addExtra(host: String) {
        val list = extraNodes.filter { it != host }.toMutableList()
        list.add(0, host)
        sp.edit().putString("extra_nodes", list.joinToString("\n")).apply()
    }

    fun removeExtra(host: String) {
        sp.edit().putString("extra_nodes", extraNodes.filter { it != host }.joinToString("\n")).apply()
    }

    fun latencyCache(): Map<String, Pair<Long, Long>> {
        val map = mutableMapOf<String, Pair<Long, Long>>()
        val now = System.currentTimeMillis()
        sp.getString("latency_cache", "").orEmpty().lines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 3) {
                val ms = parts[1].toLongOrNull() ?: return@forEach
                val ts = parts[2].toLongOrNull() ?: return@forEach
                if (now - ts < 10 * 60 * 1000L) map[parts[0]] = ms to ts
            }
        }
        return map
    }

    fun saveLatency(host: String, ms: Long) {
        val fresh = sp.getString("latency_cache", "").orEmpty().lines()
            .filter { it.isNotBlank() && !it.startsWith("$host\t") }
            .toMutableList()
        fresh.add("$host\t$ms\t${System.currentTimeMillis()}")
        sp.edit().putString("latency_cache", fresh.takeLast(150).joinToString("\n")).apply()
    }
}

class NodeRepository(private val context: Context, private val prefs: Prefs) {

    companion object {
        private const val LATENCY_PROBE =
            "https://raw.githubusercontent.com/microsoft/vscode/refs/heads/main/resources/linux/code.png"
        private const val SPEED_PROBE =
            "https://github.com/pypa/pip/archive/refs/tags/24.3.1.zip"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun loadNodes(): List<Node> = withContext(Dispatchers.IO) {
        val bundled = runCatching {
            context.assets.open("nodes.json").bufferedReader().use { it.readText() }
        }.getOrDefault("[]")
        val base = prefs.nodeOverride?.let { parseList(it) } ?: parseList(bundled)
        val extras = prefs.extraNodes.map { Node(it, "custom", custom = true) }
        val cache = prefs.latencyCache()
        (base + extras).distinctBy { it.value }
            .map { it.copy(latencyMs = cache[it.value]?.first) }
    }

    suspend fun latency(host: String): Long? = withContext(Dispatchers.IO) {
        val url = "https://$host/$LATENCY_PROBE?t=${System.currentTimeMillis()}"
        try {
            val start = SystemClock.elapsedRealtime()
            val request = Request.Builder().url(url).header("User-Agent", UA).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val bytes = body.bytes()
                if (bytes.size < 500) return@withContext null
                SystemClock.elapsedRealtime() - start
            }
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun speed(host: String): Double? = withContext(Dispatchers.IO) {
        val url = "https://$host/$SPEED_PROBE"
        val speedClient = client.newBuilder().callTimeout(14, TimeUnit.SECONDS).build()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Range", "bytes=0-3145727")
                .build()
            val start = SystemClock.elapsedRealtime()
            speedClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@withContext null
                val body = response.body ?: return@withContext null
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total >= 3L * 1024 * 1024) break
                        if (SystemClock.elapsedRealtime() - start > 6000) break
                    }
                }
                val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                if (total < 100 * 1024 || seconds <= 0.0) null
                else total * 8.0 / seconds / 1_000_000.0
            }
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun updateFromSite(): Int? = withContext(Dispatchers.IO) {
        try {
            val home = fetchText("https://github.akams.cn/") ?: return@withContext null
            val chunkPaths = Regex("""/_next/static/chunks/[A-Za-z0-9_\-]+\.js""")
                .findAll(home).map { it.value }.distinct().take(24).toList()
            for (path in chunkPaths) {
                val js = fetchText("https://github.akams.cn$path") ?: continue
                val start = js.indexOf("let j=[")
                if (start < 0) continue
                val end = js.indexOf("}]", start)
                if (end < 0) continue
                val segment = js.substring(start, end + 2)
                val items = Regex("""\{label:"([^"]*)",value:"([^"]*)"}""").findAll(segment)
                    .map { Node(it.groupValues[2], it.groupValues[1]) }
                    .filter { it.value.isNotBlank() }
                    .toList()
                if (items.size >= 10) {
                    prefs.nodeOverride = toJson(items)
                    return@withContext items.size
                }
            }
            null
        } catch (t: Throwable) {
            null
        }
    }

    private fun fetchText(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (t: Throwable) {
        null
    }

    private fun parseList(json: String): List<Node> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val value = obj.optString("value").trim()
            if (value.isBlank()) null else Node(value, obj.optString("label", "contribute"))
        }
    } catch (t: Throwable) {
        emptyList()
    }

    private fun toJson(nodes: List<Node>): String {
        val array = JSONArray()
        nodes.forEach { node ->
            val obj = JSONObject()
            obj.put("label", node.label)
            obj.put("value", node.value)
            array.put(obj)
        }
        return array.toString()
    }
}

class ApiException(message: String) : Exception(message)

class GitHubApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun parseRepo(input: String): Pair<String, String>? {
        var text = input.trim().substringBefore('?').substringBefore('#')
        text = text.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("^(www\\.)?github\\.com/", RegexOption.IGNORE_CASE), "")
        text = text.trimEnd('/')
        val parts = text.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        val valid = Regex("""^[A-Za-z0-9_.-]+$""")
        if (!valid.matches(owner) || !valid.matches(repo)) return null
        return owner to repo
    }

    suspend fun releases(owner: String, repo: String, token: String): List<Release> =
        withContext(Dispatchers.IO) {
            val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=15"
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GigHub-Android")
                .header("X-GitHub-Api-Version", "2022-11-28")
            if (token.isNotBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException(
                        when (response.code) {
                            404 -> "仓库不存在，或该仓库没有 Release"
                            401 -> "GitHub Token 无效，请在设置里重新填写"
                            403, 429 -> "GitHub API 限流了（未登录每小时 60 次）。可在「设置」里填写 Token 提升额度"
                            else -> "GitHub API 返回 HTTP ${response.code}"
                        }
                    )
                }
                parseReleases(body)
            }
        }

    private fun parseReleases(body: String): List<Release> = try {
        val array = JSONArray(body)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val assetsArray = obj.optJSONArray("assets")
            val assets = mutableListOf<Asset>()
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.optJSONObject(i) ?: continue
                    val downloadUrl = asset.optString("browser_download_url")
                    if (downloadUrl.isBlank()) continue
                    assets.add(
                        Asset(
                            name = asset.optString("name", "asset"),
                            size = asset.optLong("size", 0L),
                            url = downloadUrl,
                            count = asset.optInt("download_count", 0),
                        )
                    )
                }
            }
            Release(
                tag = obj.optString("tag_name", "release"),
                name = obj.optString("name", ""),
                prerelease = obj.optBoolean("prerelease", false),
                publishedAt = obj.optString("published_at", "").take(10),
                assets = assets,
                zipUrl = obj.optString("zipball_url").takeIf { it.isNotBlank() },
            )
        }
    } catch (t: Throwable) {
        throw ApiException("GitHub 返回的数据解析失败")
    }
}

object Downloader {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun enqueue(context: Context, url: String, fileName: String, title: String): Boolean = try {
        val manager = context.getSystemService(DownloadManager::class.java)
        val safeName = fileName.ifBlank { "github-download.bin" }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("GigHub加速")
            .setMimeType(Proxy.mimeOf(safeName))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GigHub/$safeName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("User-Agent", UA)
        manager.enqueue(request)
        true
    } catch (t: Throwable) {
        false
    }
}

fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText("GigHub", text))
}