package com.hzxs.gighub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun labelOf(label: String): String = when (label) {
    "contribute" -> "贡献"
    "search" -> "测绘"
    "custom" -> "自定义"
    else -> label
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun SectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LatencyChip(ms: Long?) {
    val (background, foreground, label) = when {
        ms == null -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "—",
        )
        ms < 500 -> Triple(Color(0xFFE6F8EC), Color(0xFF00893A), "${ms}ms")
        ms < 1000 -> Triple(Color(0xFFFFF3E0), Color(0xFFB25E00), "${ms}ms")
        else -> Triple(Color(0xFFFFECEC), Color(0xFFD32029), "${ms}ms")
    }
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun KindBadge(kind: LinkKind) {
    val color = when (kind) {
        LinkKind.RELEASE -> Color(0xFF155DFC)
        LinkKind.RAW -> Color(0xFF00893A)
        LinkKind.ARCHIVE -> Color(0xFF8A4DFF)
        LinkKind.GIST -> Color(0xFF0B7285)
        LinkKind.REPO -> Color(0xFFB25E00)
        LinkKind.OTHER -> Color(0xFF555B66)
    }
    Tag(kind.title, color)
}

private fun hintOf(kind: LinkKind): String = when (kind) {
    LinkKind.RELEASE -> "Release 资产会通过所选节点中转，大文件建议直接用「下载」。"
    LinkKind.RAW -> "适用于 raw.githubusercontent.com 的文本、图片和小文件。"
    LinkKind.ARCHIVE -> "源码压缩包会经节点中转，下载可能比直连稳定。"
    LinkKind.GIST -> "Gist 文件也可以走加速节点。"
    LinkKind.REPO -> "已生成 git clone 命令，复制到终端即可使用。"
    LinkKind.OTHER -> "这个链接可能不是 GitHub 文件，加速效果不保证。"
}

@Composable
fun AccelerateScreen(vm: AppViewModel) {
    val focus = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "粘贴 Release、Raw、Archive 或仓库链接，选个节点，一键生成加速地址。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionSurface {
            OutlinedTextField(
                value = vm.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text(
                        if (vm.releasesMode) {
                            "owner/repo 或 https://github.com/owner/repo"
                        } else {
                            "https://github.com/owner/repo/releases/download/..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingIcon = {
                    if (vm.input.isBlank()) {
                        TextButton(onClick = vm::pasteFromClipboard) { Text("粘贴") }
                    } else {
                        IconButton(onClick = vm::clearInput) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.generate()
                }),
            )

            if (vm.history.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.history) { url ->
                        AssistChip(
                            onClick = {
                                focus.clearFocus()
                                vm.useHistory(url)
                            },
                            label = {
                                Text(
                                    url.substringAfter("://").take(38),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        focus.clearFocus()
                        vm.openNodeSheet()
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "加速节点",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            vm.selectedNode?.value ?: "点击选择节点",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (vm.testingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        LatencyChip(vm.selectedNode?.latencyMs)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "更换",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "获取 Releases 列表",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (vm.releasesMode) "输入仓库后点附件名，直接走节点下载" else "打开后改为列出仓库的全部 Release",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = vm.releasesMode,
                    onCheckedChange = {
                        focus.clearFocus()
                        vm.updateReleasesMode(it)
                    },
                )
            }

            if (vm.releasesMode) {
                Button(
                    onClick = {
                        focus.clearFocus()
                        vm.loadReleasesFromInput()
                    },
                    enabled = vm.input.isNotBlank() && !vm.releasesLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (vm.releasesLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (vm.releasesLoading) "获取中…" else "获取 Releases 列表",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Button(
                    onClick = {
                        focus.clearFocus()
                        vm.generate()
                    },
                    enabled = vm.input.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        if (vm.result == null) "生成加速链接" else "重新生成",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (vm.releasesMode) {
            ReleasesPanel(vm)
        } else {
            vm.result?.let { ResultCard(vm, it) }
        }
    }
}

@Composable
private fun ResultCard(vm: AppViewModel, result: AccelResult) {
    SectionSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(result.kind)
            Text(
                result.node,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            SelectionContainer {
                Text(
                    result.accelerated,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        if (result.cloneCommand != null) {
            Text(
                "Git 克隆命令",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                SelectionContainer {
                    Text(
                        result.cloneCommand,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { vm.copy(result.cloneCommand ?: result.accelerated) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (result.cloneCommand != null) "复制命令" else "复制")
            }
            if (result.cloneCommand != null) {
                OutlinedButton(
                    onClick = { vm.openInBrowser(result.original) },
                    modifier = Modifier.weight(1f),
                ) { Text("打开仓库") }
            } else {
                OutlinedButton(
                    onClick = { vm.openInBrowser(result.accelerated) },
                    modifier = Modifier.weight(1f),
                ) { Text("浏览器") }
                if (result.kind != LinkKind.OTHER) {
                    Button(
                        onClick = { vm.download(result.accelerated) },
                        modifier = Modifier.weight(1f),
                    ) { Text("下载") }
                }
            }
        }

        Text(
            hintOf(result.kind),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )
    }
}

@Composable
fun ReleasesScreen(vm: AppViewModel) {
    val focus = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "浏览 Releases",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "输入仓库，列出每个 Release 的附件，一键复制加速链接或直接下载。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionSurface {
            OutlinedTextField(
                value = vm.repoInput,
                onValueChange = { vm.repoInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("owner/repo 或 github.com 链接") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.loadReleases()
                }),
            )
            Button(
                onClick = {
                    focus.clearFocus()
                    vm.loadReleases()
                },
                enabled = !vm.releasesLoading && vm.repoInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (vm.releasesLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (vm.releasesLoading) "获取中…" else "获取 Releases")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vm.hasToken) "已配置 Token，额度 5000 次/小时" else "未配置 Token：额度 60 次/小时",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (vm.releases.isNotEmpty()) {
                    TextButton(onClick = vm::clearReleases) { Text("清空") }
                }
            }
        }

        ReleasesPanel(vm)
    }
}

@Composable
private fun ReleasesPanel(vm: AppViewModel) {
    when {
        vm.releasesLoading -> Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在请求 GitHub API…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        vm.releasesError != null -> SectionSurface {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    vm.releasesError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = vm::loadReleases, modifier = Modifier.fillMaxWidth()) {
                Text("重试")
            }
        }

        vm.releases.isNotEmpty() -> {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${vm.releasesTitle} · ${vm.releases.size} 个 Release",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "节点 ${vm.selectedNode?.value ?: "未选择"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            vm.releases.forEachIndexed { index, release ->
                ReleaseCard(vm, release, defaultExpanded = index == 0)
            }
        }

        else -> SectionSurface {
            Text(
                if (vm.releasesMode) "输入仓库，获取 Releases" else "还没有加载数据",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "支持这些写法：\n· microsoft/vscode\n· https://github.com/microsoft/vscode\n· https://github.com/microsoft/vscode/releases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun ReleaseCard(vm: AppViewModel, release: Release, defaultExpanded: Boolean) {
    var expanded by rememberSaveable(release.tag) { mutableStateOf(defaultExpanded) }
    SectionSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        release.tag,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (release.prerelease) {
                        Spacer(Modifier.width(6.dp))
                        Tag("预发布", Color(0xFFB25E00))
                    }
                }
                if (release.name.isNotBlank() && release.name != release.tag) {
                    Text(
                        release.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "发布 ${release.publishedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val fileCount = release.assets.size + if (release.zipUrl != null) 1 else 0
            Text(
                if (expanded) "收起" else "${fileCount} 个文件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp),
            )
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            release.assets.forEach { asset -> AssetRow(vm, asset) }
            release.zipUrl?.let { zipUrl ->
                AssetRow(vm, Asset("源码-${release.tag}.zip", 0L, zipUrl, 0))
            }
            if (release.assets.isEmpty() && release.zipUrl == null) {
                Text(
                    "这个 Release 没有附件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AssetRow(vm: AppViewModel, asset: Asset) {
    val download = { vm.downloadAccel(asset.url, asset.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { download() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                asset.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                if (asset.size > 0) append(Proxy.formatSize(asset.size))
                if (asset.count > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${asset.count} 次下载")
                }
                if (isEmpty()) append(if (asset.url.contains("/zipball/")) "源码包" else "附件")
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { vm.copyAccel(asset.url) },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text("复制") }
        IconButton(onClick = download) {
            Icon(
                painterResource(R.drawable.ic_download),
                contentDescription = "下载 ${asset.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var tokenInput by rememberSaveable { mutableStateOf("") }
    var customInput by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        SectionSurface {
            Text("外观", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "跟随系统", 1 to "深色", 2 to "浅色").forEach { (mode, label) ->
                    FilterChip(
                        selected = vm.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }

        SectionSurface {
            Text("加速节点", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "来源：${vm.nodeSource} · 共 ${vm.nodes.size} 个" +
                    if (vm.extraNodes.isNotEmpty()) "（含 ${vm.extraNodes.size} 个自定义）" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.nodeLastSync > 0) {
                Text(
                    "上次在线更新：${formatTime(vm.nodeLastSync)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::syncNodes,
                    enabled = !vm.syncing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (vm.syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (vm.syncing) "更新中…" else "从官网更新")
                }
                OutlinedButton(onClick = vm::resetNodes, modifier = Modifier.weight(1f)) {
                    Text("恢复内置")
                }
            }
            Text(
                "在线更新会抓取 github.akams.cn 页面的最新节点列表；失败时继续使用内置列表。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Text("添加自定义节点", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("gh-proxy.com") },
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        vm.addCustomNode(customInput)
                        customInput = ""
                    },
                    enabled = customInput.isNotBlank(),
                ) { Text("添加") }
            }
            vm.extraNodes.forEach { host ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        host,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    IconButton(onClick = { vm.removeCustomNode(host) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除节点",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        SectionSurface {
            Text("GitHub Token（可选）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "只用于 Releases 列表接口，把每小时 60 次提升到 5000 次。Token 仅保存在本机，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                placeholder = {
                    Text(if (vm.hasToken) "已保存，粘贴新 Token 可替换" else "ghp_... 或 github_pat_...")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.setToken(tokenInput)
                        tokenInput = ""
                    },
                    enabled = tokenInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
                if (vm.hasToken) {
                    OutlinedButton(onClick = {
                        vm.setToken("")
                        tokenInput = ""
                    }) { Text("清除") }
                }
            }
        }

        SectionSurface {
            Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GigHub加速 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when {
                            vm.checkingUpdate -> "正在检查新版本…"
                            vm.updateInfo != null -> "发现新版本 v${vm.updateInfo?.version}"
                            else -> "从 GitHub Releases 检查更新"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (vm.updateInfo != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                OutlinedButton(onClick = { vm.checkUpdate() }, enabled = !vm.checkingUpdate) {
                    Text(if (vm.checkingUpdate) "检查中…" else "检查更新")
                }
            }
            Text(
                "节点和数据来自 github.akams.cn 公益服务，仅供个人下载加速使用，请勿滥用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.openInBrowser("https://github.akams.cn/") },
                    modifier = Modifier.weight(1f),
                ) { Text("访问官网") }
                OutlinedButton(
                    onClick = { vm.openInBrowser("https://github.com/hzxs163/gighub-android") },
                    modifier = Modifier.weight(1f),
                ) { Text("项目主页") }
            }
        }

        Text(
            "把 GitHub 链接从浏览器「分享」到本应用，可直接填入加速框。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSheet(vm: AppViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.nodeSheetOpen = false },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "选择加速节点",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${vm.nodes.size} 个节点 · 延迟越低越快",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = vm::testAllLatency, enabled = !vm.testingAll) {
                    if (vm.testingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (vm.testingAll) "测速中" else "全部测延迟")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.nodeSearch,
                onValueChange = { vm.nodeSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("搜索节点域名") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vm.nodeSearch.isBlank()) {
                        "点击某一行即可选中"
                    } else {
                        "匹配 ${vm.nodes.count { it.value.contains(vm.nodeSearch, true) }} 个"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { vm.testSpeed(vm.selectedNode) },
                    enabled = vm.selectedNode != null && vm.selectedNode?.value !in vm.testingHosts,
                ) { Text("测当前节点速度") }
            }
            val filtered = vm.nodes.filter {
                vm.nodeSearch.isBlank() ||
                    it.value.contains(vm.nodeSearch, true) ||
                    it.label.contains(vm.nodeSearch, true)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it.value }) { node ->
                    NodeItem(vm, node)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun NodeItem(vm: AppViewModel, node: Node) {
    val testing = node.value in vm.testingHosts
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { vm.selectNode(node) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = vm.selectedNode?.value == node.value,
            onClick = { vm.selectNode(node) },
        )
        Column(Modifier.weight(1f)) {
            Text(
                node.value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    labelOf(node.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (node.custom) Tag("自定义", MaterialTheme.colorScheme.primary)
                node.speedMbps?.let {
                    Text(
                        "%.1f Mbps".format(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00893A),
                    )
                }
            }
        }
        if (testing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            LatencyChip(node.latencyMs)
        }
        IconButton(
            onClick = { vm.testLatency(node) },
            enabled = !testing,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "测试延迟",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}