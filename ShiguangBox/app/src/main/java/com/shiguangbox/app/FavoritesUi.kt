package com.shiguangbox.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FavCream = Color(0xFFFFF9F0)
private val FavWarmOrange = Color(0xFFF4B860)
private val FavSage = Color(0xFFA8B99A)
private val FavDarkBrown = Color(0xFF49392C)
private val FavMuted = Color(0xFF8A7969)
private val FavCard = Color(0xFFFFFDFC)
private val FavPaleOrange = Color(0xFFFFEBC7)

@Composable
fun CollectionsScreen(
    db: AppDatabase,
    onDetail: (Long) -> Unit,
    onManualAdd: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf("全部") }

    val flow = remember(query) {
        if (query.isBlank()) db.favoriteDao().observeAll()
        else db.favoriteDao().observeSearch(query.trim())
    }
    val allItems by flow.collectAsState(initial = emptyList())

    val shown = remember(allItems, platform) {
        if (platform == "全部") allItems
        else allItems.filter { it.platform == platform }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FavCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("收藏", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = FavDarkBrown)
            Text("看到好的，就先收进来。", color = FavMuted)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索我的收藏") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("全部", "抖音", "B站", "微信读书").forEach { item ->
                    FilterChip(
                        selected = platform == item,
                        onClick = { platform = item },
                        label = { Text(item, fontSize = 12.sp) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("网页", "其他").forEach { item ->
                    FilterChip(
                        selected = platform == item,
                        onClick = { platform = item },
                        label = { Text(item, fontSize = 12.sp) }
                    )
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onManualAdd) {
                    Icon(Icons.Outlined.AddLink, null)
                    Spacer(Modifier.width(4.dp))
                    Text("手动收藏")
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                FavoriteCard {
                    Icon(Icons.Outlined.BookmarkBorder, null, tint = FavSage)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (allItems.isEmpty())
                            "还没有收藏。去抖音、B站、微信读书或浏览器里点“分享 → 收进拾光盒”试试。"
                        else
                            "当前筛选下没有内容。",
                        color = FavMuted
                    )
                }
            }
        } else {
            items(shown, key = { it.id }) { item ->
                FavoriteCard(
                    modifier = Modifier.clickable { onDetail(item.id) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformBadge(item.platform)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.platform,
                            color = FavMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatFavoriteTime(item.createdAt), color = FavMuted, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        item.title,
                        color = FavDarkBrown,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3
                    )

                    if (item.rawText.isNotBlank() && item.rawText != item.title) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.rawText.take(150),
                            color = FavMuted,
                            fontSize = 13.sp,
                            maxLines = 3
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.url.isNotBlank()) {
                            Icon(
                                Icons.Outlined.Link,
                                null,
                                tint = FavWarmOrange,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("已保存原链接", color = FavWarmOrange, fontSize = 12.sp)
                        } else {
                            Text("文字收藏", color = FavSage, fontSize = 12.sp)
                        }

                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, null, tint = FavMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionDetailScreen(
    db: AppDatabase,
    favoriteId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item by db.favoriteDao().observeById(favoriteId).collectAsState(initial = null)

    var note by rememberSaveable(favoriteId) { mutableStateOf("") }
    var title by rememberSaveable(favoriteId) { mutableStateOf("") }
    var initialized by rememberSaveable(favoriteId) { mutableStateOf(false) }
    var aiLoading by rememberSaveable(favoriteId) { mutableStateOf(false) }
    var aiError by rememberSaveable(favoriteId) { mutableStateOf("") }

    LaunchedEffect(item?.id) {
        val current = item
        if (!initialized && current != null) {
            note = current.note
            title = current.title
            initialized = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FavCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "返回")
                }
                Text("收藏详情", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        val current = item
        if (current == null) {
            item { Text("正在读取收藏…", color = FavMuted) }
        } else {
            item {
                FavoriteCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformBadge(current.platform)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(current.platform, fontWeight = FontWeight.Bold)
                            Text(formatFavoriteTime(current.createdAt), color = FavMuted, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("标题") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            if (current.rawText.isNotBlank()) {
                item {
                    FavoriteCard {
                        Text("原始分享内容", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(current.rawText, color = FavDarkBrown, lineHeight = 22.sp)
                    }
                }
            }

            item {
                FavoriteCard {
                    Text("我的备注", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        placeholder = { Text("为什么收藏它？以后想怎么用？") },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                db.favoriteDao().update(
                                    current.copy(
                                        title = title.ifBlank { current.title }.trim(),
                                        note = note.trim(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存修改")
                    }
                }
            }

            if (current.url.isNotBlank()) {
                item {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(current.url))
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("打开原内容")
                    }
                }
            }

            item {
                FavoriteCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = FavWarmOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("DeepSeek AI 整理", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(10.dp))

                    if (current.aiSummary.isNotBlank()) {
                        Text(current.aiSummary, color = FavDarkBrown, lineHeight = 22.sp)
                        if (current.aiTags.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(current.aiTags, color = FavWarmOrange, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Text(
                            "只根据这条收藏现有的分享文字和你的备注整理，不会假装看过无法访问的完整视频。",
                            color = FavMuted,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    Button(
                        onClick = {
                            val apiKey = SecureApiKeyStore.load(context)
                            if (apiKey.isNullOrBlank()) {
                                aiError = "请先到“我的 → DeepSeek AI”保存并测试 API Key"
                                return@Button
                            }

                            aiLoading = true
                            aiError = ""
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    DeepSeekClient.summarizeFavorite(
                                        apiKey = apiKey,
                                        title = current.title,
                                        platform = current.platform,
                                        rawText = current.rawText,
                                        note = note
                                    )
                                }
                                aiLoading = false

                                if (result.success) {
                                    val tags = extractSuggestedTags(result.content)
                                    db.favoriteDao().update(
                                        current.copy(
                                            aiSummary = result.content,
                                            aiTags = tags,
                                            note = note.trim(),
                                            title = title.ifBlank { current.title }.trim(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                } else {
                                    aiError = result.error
                                }
                            }
                        },
                        enabled = !aiLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (aiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (aiLoading) "正在整理…" else if (current.aiSummary.isBlank()) "AI 整理这条收藏" else "重新整理"
                        )
                    }

                    if (aiError.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(aiError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            db.favoriteDao().delete(current)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除这条收藏")
                }
            }
        }
    }
}

@Composable
fun ManualCollectionScreen(
    db: AppDatabase,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var rawText by rememberSaveable { mutableStateOf("") }
    val preview = remember(rawText) {
        if (rawText.isBlank()) null else FavoriteParser.parse(rawText)
    }
    var message by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FavCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "返回")
                }
                Text("手动收藏", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Text("把链接或分享文字粘贴进来。", color = FavMuted)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("粘贴抖音 / B站 / 微信读书 / 网页链接或分享文字") },
                shape = RoundedCornerShape(18.dp)
            )
        }

        preview?.let { parsed ->
            item {
                FavoriteCard {
                    Text("识别结果", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("来源：" + parsed.platform, color = FavMuted)
                    Text("标题：" + parsed.title, color = FavDarkBrown)
                    if (parsed.url.isNotBlank()) {
                        Text("链接：" + parsed.url, color = FavWarmOrange, fontSize = 12.sp)
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item { Text(message, color = FavSage) }
        }

        item {
            Button(
                onClick = {
                    val parsed = preview ?: return@Button
                    scope.launch {
                        val existing = parsed.url
                            .takeIf { it.isNotBlank() }
                            ?.let { db.favoriteDao().findByUrl(it) }

                        if (existing != null) {
                            message = "这个链接已经收藏过啦"
                        } else {
                            db.favoriteDao().insert(parsed)
                            onBack()
                        }
                    }
                },
                enabled = preview != null,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Outlined.BookmarkAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("收进拾光盒", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlatformBadge(platform: String) {
    val icon = when (platform) {
        "抖音" -> Icons.Outlined.PlayCircle
        "B站" -> Icons.Outlined.SmartDisplay
        "微信读书" -> Icons.Outlined.MenuBook
        "网页" -> Icons.Outlined.Language
        else -> Icons.Outlined.BookmarkBorder
    }

    Surface(
        color = FavPaleOrange,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = FavWarmOrange)
        }
    }
}

@Composable
private fun FavoriteCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FavCard),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            content = content
        )
    }
}

private fun extractSuggestedTags(summary: String): String {
    val line = summary.lineSequence()
        .dropWhile { !it.contains("建议标签") }
        .drop(1)
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    return line.removePrefix("：").removePrefix(":").trim()
}

private fun formatFavoriteTime(ms: Long): String {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}
