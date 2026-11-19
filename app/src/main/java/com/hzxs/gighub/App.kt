package com.hzxs.gighub

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

val BrandBlue = Color(0xFF155DFC)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF4E5F80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4F5),
    onSecondaryContainer = Color(0xFF0B1C35),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF11151C),
    surface = Color.White,
    onSurface = Color(0xFF11151C),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF555B66),
    outline = Color(0xFFC4CAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF00276B),
    primaryContainer = Color(0xFF123A8C),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB6C4E0),
    onSecondary = Color(0xFF21314C),
    secondaryContainer = Color(0xFF2A3B58),
    onSecondaryContainer = Color(0xFFDDE4F5),
    background = Color(0xFF0B0F16),
    onBackground = Color(0xFFE3E6EC),
    surface = Color(0xFF121821),
    onSurface = Color(0xFFE3E6EC),
    surfaceVariant = Color(0xFF1B2330),
    onSurfaceVariant = Color(0xFFAEB6C4),
    outline = Color(0xFF3A4456),
)

@Composable
fun GigHubTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val nodeRepo = NodeRepository(app, prefs)
    private val github = GitHubApi()

    var tab by mutableStateOf(0)
    var themeMode by mutableStateOf(prefs.theme)
        private set

    var nodes by mutableStateOf<List<Node>>(emptyList())
        private set
    var selectedNode by mutableStateOf<Node?>(null)
        private set
    var nodeSheetOpen by mutableStateOf(false)
    var nodeSearch by mutableStateOf("")
    var testingAll by mutableStateOf(false)
        private set
    var testingHosts by mutableStateOf<Set<String>>(emptySet())
        private set
    var syncing by mutableStateOf(false)
        private set
    var nodeSource by mutableStateOf("内置")
        private set
    var nodeLastSync by mutableStateOf(prefs.lastSync)
        private set
    var extraNodes by mutableStateOf(prefs.extraNodes)
        private set

    var input by mutableStateOf("")
    var result by mutableStateOf<AccelResult?>(null)
        private set
    var history by mutableStateOf(prefs.history)
        private set

    var repoInput by mutableStateOf("")
    var releases by mutableStateOf<List<Release>>(emptyList())
        private set
    var releasesLoading by mutableStateOf(false)
        private set
    var releasesError by mutableStateOf<String?>(null)
        private set
    var releasesTitle by mutableStateOf("")
        private set
    var hasToken by mutableStateOf(prefs.ghToken.isNotBlank())
        private set

    init {
        refreshNodes()
    }

    fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    fun refreshNodes() {
        viewModelScope.launch {
            val list = nodeRepo.loadNodes()
            nodes = list
            selectedNode = list.firstOrNull { it.value == prefs.selectedNode } ?: list.firstOrNull()
            nodeSource = if (prefs.nodeOverride != null) "在线更新" else "内置"
            extraNodes = prefs.extraNodes
        }
    }

    fun setTheme(mode: Int) {
        themeMode = mode
        prefs.theme = mode
    }

    fun selectNode(node: Node) {
        selectedNode = node
        prefs.selectedNode = node.value
        nodeSheetOpen = false
    }

    fun openNodeSheet() {
        nodeSheetOpen = true
        if (!testingAll && nodes.none { it.latencyMs != null }) testAllLatency()
    }

    fun testAllLatency() {
        if (testingAll) return
        val snapshot = nodes
        if (snapshot.isEmpty()) return
        testingAll = true
        viewModelScope.launch {
            try {
                val results = coroutineScope {
                    val semaphore = Semaphore(10)
                    snapshot.map { node ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit { node.value to nodeRepo.latency(node.value) }
                        }
                    }.awaitAll().toMap()
                }
                nodes = snapshot.map { node ->
                    val ms = results[node.value]
                    if (ms != null) prefs.saveLatency(node.value, ms)
                    node.copy(latencyMs = ms ?: node.latencyMs)
                }.sortedWith(compareBy<Node> { it.latencyMs ?: Long.MAX_VALUE })
                syncSelected()
            } finally {
                testingAll = false
            }
        }
    }

    fun testLatency(node: Node) {
        if (node.value in testingHosts) return
        testingHosts = testingHosts + node.value
        viewModelScope.launch {
            try {
                val ms = nodeRepo.latency(node.value)
                if (ms != null) prefs.saveLatency(node.value, ms)
                nodes = nodes.map { if (it.value == node.value) it.copy(latencyMs = ms ?: it.latencyMs) else it }
                syncSelected()
            } finally {
                testingHosts = testingHosts - node.value
            }
        }
    }

    fun testSpeed(node: Node?) {
        val target = node ?: return toast("请先选择一个节点")
        if (target.value in testingHosts) return
        testingHosts = testingHosts + target.value
        viewModelScope.launch {
            try {
                val mbps = nodeRepo.speed(target.value)
                if (mbps != null) {
                    nodes = nodes.map { if (it.value == target.value) it.copy(speedMbps = mbps) else it }
                    syncSelected()
                    toast("${target.value}：%.1f Mbps".format(mbps))
                } else {
                    toast("测速失败：节点超时或拒绝大文件请求")
                }
            } finally {
                testingHosts = testingHosts - target.value
            }
        }
    }

    fun syncNodes() {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                val count = nodeRepo.updateFromSite()
                if (count != null) {
                    prefs.lastSync = System.currentTimeMillis()
                    nodeLastSync = prefs.lastSync
                    toast("已从官网更新 $count 个节点")
                    refreshNodes()
                } else {
                    toast("更新失败：官网结构可能已变化，稍后再试")
                }
            } finally {
                syncing = false
            }
        }
    }

    fun resetNodes() {
        prefs.nodeOverride = null
        nodeSource = "内置"
        toast("已恢复内置节点")
        refreshNodes()
    }

    fun addCustomNode(raw: String) {
        var host = raw.trim()
        host = host.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        host = host.substringBefore('/').trim()
        if (host.isBlank() || !host.contains('.') || host.contains(' ')) {
            toast("请填写节点域名，例如 gh-proxy.com")
            return
        }
        prefs.addExtra(host)
        toast("已添加节点 $host")
        refreshNodes()
    }

    fun removeCustomNode(host: String) {
        prefs.removeExtra(host)
        refreshNodes()
    }

    fun setToken(token: String) {
        prefs.ghToken = token.trim()
        hasToken = prefs.ghToken.isNotBlank()
        toast(if (hasToken) "Token 已保存" else "Token 已清除")
    }

    private fun syncSelected() {
        selectedNode = nodes.firstOrNull { it.value == selectedNode?.value } ?: selectedNode
    }

    fun onInputChange(value: String) {
        input = value
        if (result != null && result?.original != Proxy.normalize(value)) result = null
    }

    fun clearResult() {
        result = null
    }

    fun pasteFromClipboard() {
        val manager = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        val text = manager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(getApplication())
            ?.toString()
        if (text.isNullOrBlank()) {
            toast("剪贴板里没有文本")
            return
        }
        input = Proxy.extractUrl(text) ?: text.trim()
        result = null
    }

    fun onSharedText(text: String?) {
        val url = Proxy.extractUrl(text.orEmpty()) ?: return
        input = url
        result = null
        tab = 0
    }

    fun generate() {
        val url = Proxy.normalize(input)
        if (url == null) {
            toast("请输入 GitHub 链接")
            return
        }
        val node = selectedNode
        if (node == null) {
            toast("暂无可用节点，请先更新节点列表")
            openNodeSheet()
            return
        }
        val kind = Proxy.kind(url)
        result = AccelResult(
            original = url,
            accelerated = Proxy.build(node.value, url),
            kind = kind,
            node = node.value,
            cloneCommand = if (kind == LinkKind.REPO) Proxy.clone(node.value, url) else null,
        )
        input = url
        prefs.addHistory(url)
        history = prefs.history
    }

    fun copy(text: String) {
        copyToClipboard(getApplication(), text)
        toast("已复制到剪贴板")
    }

    fun openInBrowser(url: String) {
        try {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            toast("没有可用的浏览器")
        }
    }

    fun download(url: String) {
        downloadAs(url, Proxy.fileName(url))
    }

    fun downloadAs(url: String, name: String) {
        val safe = name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(120).ifBlank { "github-download.bin" }
        if (Downloader.enqueue(getApplication(), url, safe, safe)) {
            toast("已加入下载：$safe")
        } else {
            toast("加入下载失败，请改用浏览器打开")
        }
    }

    fun accelUrl(original: String): String? {
        val node = selectedNode ?: run {
            toast("请先选择一个节点")
            return null
        }
        return Proxy.build(node.value, original)
    }

    fun copyAccel(original: String) {
        accelUrl(original)?.let { copy(it) }
    }

    fun downloadAccel(original: String, name: String? = null) {
        val node = selectedNode ?: run {
            toast("请先选择一个节点")
            return
        }
        downloadAs(Proxy.build(node.value, original), name ?: Proxy.fileName(original))
    }

    fun loadReleases() {
        if (releasesLoading) return
        val repo = github.parseRepo(repoInput)
        if (repo == null) {
            toast("请输入 owner/repo 或仓库链接")
            return
        }
        releasesLoading = true
        releasesError = null
        viewModelScope.launch {
            try {
                val list = github.releases(repo.first, repo.second, prefs.ghToken)
                releases = list
                releasesTitle = "${repo.first}/${repo.second}"
                if (list.isEmpty()) releasesError = "该仓库还没有发布 Release"
            } catch (e: ApiException) {
                releases = emptyList()
                releasesError = e.message
            } catch (t: Throwable) {
                releases = emptyList()
                releasesError = "网络错误，请检查网络后重试"
            } finally {
                releasesLoading = false
            }
        }
    }

    fun useHistory(url: String) {
        input = url
        result = null
    }

    fun clearReleases() {
        releases = emptyList()
        releasesError = null
        releasesTitle = ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GigHubApp(vm: AppViewModel) {
    GigHubTheme(vm.themeMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "GigHub 加速",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "GitHub 文件下载加速 · 移动版",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = vm.tab == 0,
                        onClick = { vm.tab = 0 },
                        icon = { Icon(Icons.Filled.Send, contentDescription = null) },
                        label = { Text("加速") },
                    )
                    NavigationBarItem(
                        selected = vm.tab == 1,
                        onClick = { vm.tab = 1 },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("Releases") },
                    )
                    NavigationBarItem(
                        selected = vm.tab == 2,
                        onClick = { vm.tab = 2 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (vm.tab) {
                    0 -> AccelerateScreen(vm)
                    1 -> ReleasesScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
        if (vm.nodeSheetOpen) NodeSheet(vm)
    }
}