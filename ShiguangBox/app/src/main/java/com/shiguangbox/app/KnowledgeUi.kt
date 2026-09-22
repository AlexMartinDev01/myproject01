package com.shiguangbox.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

private val KnowledgeCream = Color(0xFFFFF9F0)
private val KnowledgeCardColor = Color(0xFFFFFDFC)
private val KnowledgeWarm = Color(0xFFF4B860)
private val KnowledgeSage = Color(0xFFA8B99A)
private val KnowledgeDark = Color(0xFF49392C)
private val KnowledgeMuted = Color(0xFF8A7969)
private val KnowledgePale = Color(0xFFEAF1E4)

@Composable
fun KnowledgeHomeScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onCard: (Long) -> Unit,
    onTopic: (String) -> Unit,
    onImageImport: () -> Unit,
    onWeekly: () -> Unit
) {
    val all by db.favoriteDao().observeAll().collectAsState(initial = emptyList())

    val topics = remember(all) {
        all.mapNotNull { it.topic.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    val unclassified = all.count { it.topic.isBlank() }
    val pendingSuggestions = all.count { it.topic.isBlank() && it.suggestedTopic.isNotBlank() }
    val reviewItem = remember(all) {
        val weekAgo = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        all.firstOrNull { it.createdAt < weekAgo } ?: all.lastOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(KnowledgeCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KnowledgeTopRow("我的知识库", onBack)
            Text(
                "把收藏、截图和图文资料整理成可以长期搜索和提问的知识。",
                color = KnowledgeMuted
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KnowledgeMetric(
                    modifier = Modifier.weight(1f),
                    value = all.size.toString(),
                    label = "知识卡"
                )
                KnowledgeMetric(
                    modifier = Modifier.weight(1f),
                    value = topics.size.toString(),
                    label = "专题"
                )
                KnowledgeMetric(
                    modifier = Modifier.weight(1f),
                    value = unclassified.toString(),
                    label = "待分类"
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImageImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Spacer(Modifier.width(6.dp))
                    Text("收图文")
                }
                OutlinedButton(
                    onClick = onWeekly,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("本周拾光")
                }
            }
        }

        if (pendingSuggestions > 0) {
            item {
                KnowledgeBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = KnowledgeWarm)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("有 $pendingSuggestions 条知识等待你确认专题", fontWeight = FontWeight.Bold)
                            Text(
                                "AI 只做建议，不会擅自替你分类。",
                                color = KnowledgeMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        if (topics.isNotEmpty()) {
            item {
                Text("专题", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            items(topics, key = { it.first }) { pair ->
                KnowledgeBox(
                    modifier = Modifier.clickable { onTopic(pair.first) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, tint = KnowledgeSage)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pair.first, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(pair.second.toString() + " 条知识", color = KnowledgeMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = KnowledgeMuted)
                    }
                }
            }
        }

        reviewItem?.let { itemToReview ->
            item {
                Text("今天回顾一下", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            item {
                KnowledgeBox(
                    modifier = Modifier.clickable { onCard(itemToReview.id) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.HistoryEdu, null, tint = KnowledgeWarm)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(itemToReview.title, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(
                                formatKnowledgeTime(itemToReview.createdAt) +
                                    if (itemToReview.topic.isNotBlank()) " · " + itemToReview.topic else "",
                                color = KnowledgeMuted,
                                fontSize = 12.sp
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = KnowledgeMuted)
                    }
                }
            }
        }

        item {
            Text("最近知识", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (all.isEmpty()) {
            item {
                KnowledgeBox {
                    Text("还没有知识卡。可以先收藏一条网页，或者导入一张截图。", color = KnowledgeMuted)
                }
            }
        } else {
            items(all.take(20), key = { it.id }) { card ->
                KnowledgeListCard(card = card, onClick = { onCard(card.id) })
            }
        }
    }
}

@Composable
fun KnowledgeCardScreen(
    db: AppDatabase,
    favoriteId: Long,
    onBack: () -> Unit,
    onCard: (Long) -> Unit,
    onTopic: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item by db.favoriteDao().observeById(favoriteId).collectAsState(initial = null)
    val all by db.favoriteDao().observeAll().collectAsState(initial = emptyList())

    var topicText by rememberSaveable(favoriteId) { mutableStateOf("") }
    var noteText by rememberSaveable(favoriteId) { mutableStateOf("") }
    var initialized by rememberSaveable(favoriteId) { mutableStateOf(false) }
    var message by rememberSaveable(favoriteId) { mutableStateOf("") }

    LaunchedEffect(item?.id) {
        val current = item
        if (!initialized && current != null) {
            topicText = current.topic
            noteText = current.note
            initialized = true
        }
    }

    val current = item
    val related = remember(current?.id, current?.topic, current?.aiTags, current?.suggestedTopic, all) {
        current?.let { findRelatedKnowledge(it, all).take(4) } ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(KnowledgeCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { KnowledgeTopRow("知识卡片", onBack) }

        if (current == null) {
            item { Text("正在读取…", color = KnowledgeMuted) }
        } else {
            item {
                KnowledgeBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = KnowledgePale,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (current.knowledgeType == "图文") Icons.Outlined.Image
                                else Icons.Outlined.BookmarkBorder,
                                null,
                                tint = KnowledgeSage,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                current.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = KnowledgeDark
                            )
                            Text(
                                current.platform + " · " + formatKnowledgeTime(current.createdAt),
                                color = KnowledgeMuted,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    db.favoriteDao().update(
                                        current.copy(
                                            important = !current.important,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(
                                if (current.important) Icons.Outlined.Star
                                else Icons.Outlined.StarBorder,
                                "重要",
                                tint = if (current.important) KnowledgeWarm else KnowledgeMuted
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    AssistChip(
                        onClick = {},
                        label = { Text(current.processingStatus) },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }
                    )
                }
            }

            val imagePaths = ImageStorage.decodePaths(current.imagePaths)
            if (imagePaths.isNotEmpty()) {
                item {
                    KnowledgeBox {
                        Text("原始图片", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        imagePaths.forEach { path ->
                            val image = remember(path) {
                                BitmapFactory.decodeFile(path)?.asImageBitmap()
                            }
                            if (image != null) {
                                Image(
                                    bitmap = image,
                                    contentDescription = "知识图片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            if (current.aiSummary.isNotBlank()) {
                item {
                    KnowledgeBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = KnowledgeWarm)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 知识整理", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(current.aiSummary, color = KnowledgeDark, lineHeight = 23.sp)
                        if (current.aiTags.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("标签：" + current.aiTags, color = KnowledgeWarm, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (current.rawText.isNotBlank()) {
                item {
                    KnowledgeBox {
                        Text("原始文字", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(current.rawText, color = KnowledgeDark, lineHeight = 22.sp)
                    }
                }
            }

            item {
                KnowledgeBox {
                    Text("我的想法", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                        placeholder = { Text("留下你的理解、用途或补充……") }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                db.favoriteDao().update(
                                    current.copy(
                                        note = noteText.trim(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                message = "备注已保存"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存备注")
                    }
                }
            }

            if (current.suggestedTopic.isNotBlank() && current.topic.isBlank()) {
                item {
                    KnowledgeBox {
                        Text("AI 建议专题", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = {},
                                label = { Text(current.suggestedTopic) },
                                leadingIcon = { Icon(Icons.Outlined.Folder, null) }
                            )
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    scope.launch {
                                        db.favoriteDao().update(
                                            current.copy(
                                                topic = current.suggestedTopic,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        )
                                        topicText = current.suggestedTopic
                                        message = "已加入「" + current.suggestedTopic + "」"
                                    }
                                }
                            ) {
                                Text("加入")
                            }
                        }
                        Text(
                            "只是建议，你也可以在下面自己改。",
                            color = KnowledgeMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                KnowledgeBox {
                    Text("所属专题", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topicText,
                        onValueChange = { topicText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：秋招 / 自媒体 / AI学习") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    db.favoriteDao().update(
                                        current.copy(
                                            topic = topicText.trim(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    message = "专题已保存"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("保存专题")
                        }
                        if (current.topic.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onTopic(current.topic) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("进入专题")
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        KnowledgeOrganizeWorker.enqueue(context, current.id)
                        message = "已经重新交给 AI 整理"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新 AI 整理")
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = KnowledgeSage, fontSize = 12.sp)
                }
            }

            if (related.isNotEmpty()) {
                item {
                    Text("与你这条相关", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                items(related, key = { it.id }) { relatedItem ->
                    KnowledgeListCard(
                        card = relatedItem,
                        onClick = { onCard(relatedItem.id) }
                    )
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
                TextButton(
                    onClick = {
                        scope.launch {
                            ImageStorage.deletePaths(current.imagePaths)
                            db.favoriteDao().delete(current)
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("删除这张知识卡")
                }
            }
        }
    }
}

@Composable
fun TopicDetailScreen(
    db: AppDatabase,
    topic: String,
    onBack: () -> Unit,
    onCard: (Long) -> Unit,
    onAsk: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by db.favoriteDao().observeAll().collectAsState(initial = emptyList())
    val itemsInTopic = all.filter { it.topic == topic }

    var summary by rememberSaveable(topic) { mutableStateOf("") }
    var loading by rememberSaveable(topic) { mutableStateOf(false) }
    var error by rememberSaveable(topic) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(KnowledgeCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KnowledgeTopRow(topic, onBack)
            Text(itemsInTopic.size.toString() + " 条知识", color = KnowledgeMuted)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAsk,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Psychology, null)
                    Spacer(Modifier.width(6.dp))
                    Text("问这个专题")
                }
                OutlinedButton(
                    onClick = {
                        val key = SecureApiKeyStore.load(context)
                        if (key.isNullOrBlank()) {
                            error = "请先配置 DeepSeek API Key"
                            return@OutlinedButton
                        }
                        loading = true
                        error = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DeepSeekClient.summarizeTopic(
                                    apiKey = key,
                                    topic = topic,
                                    items = itemsInTopic.map(::knowledgeMaterial)
                                )
                            }
                            loading = false
                            if (result.success) summary = result.content else error = result.error
                        }
                    },
                    enabled = itemsInTopic.isNotEmpty() && !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (loading) "整理中…" else "总结专题")
                }
            }
        }

        if (error.isNotBlank()) {
            item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }

        if (summary.isNotBlank()) {
            item {
                KnowledgeBox {
                    Text("专题 AI 总结", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(summary, color = KnowledgeDark, lineHeight = 23.sp)
                }
            }
        }

        if (itemsInTopic.isEmpty()) {
            item {
                KnowledgeBox {
                    Text("这个专题还没有知识。", color = KnowledgeMuted)
                }
            }
        } else {
            items(itemsInTopic, key = { it.id }) { card ->
                KnowledgeListCard(card = card, onClick = { onCard(card.id) })
            }
        }
    }
}

@Composable
fun TopicAskScreen(
    db: AppDatabase,
    topic: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by db.favoriteDao().observeAll().collectAsState(initial = emptyList())
    val topicItems = all.filter { it.topic == topic }

    var question by rememberSaveable(topic) { mutableStateOf("") }
    var answer by rememberSaveable(topic) { mutableStateOf("") }
    var loading by rememberSaveable(topic) { mutableStateOf(false) }
    var error by rememberSaveable(topic) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(KnowledgeCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KnowledgeTopRow("问「" + topic + "」", onBack)
            Text("只使用这个专题里的知识卡回答。", color = KnowledgeMuted)
        }

        item {
            KnowledgeBox {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = { Text("例如：这个专题里重复提到最多的观点是什么？") }
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val key = SecureApiKeyStore.load(context)
                        if (key.isNullOrBlank()) {
                            error = "请先配置 DeepSeek API Key"
                            return@Button
                        }
                        loading = true
                        error = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DeepSeekClient.answerFromMemory(
                                    apiKey = key,
                                    question = question.trim(),
                                    contextItems = topicItems.map(::knowledgeMaterial)
                                )
                            }
                            loading = false
                            if (result.success) answer = result.content else error = result.error
                        }
                    },
                    enabled = question.isNotBlank() && !loading && topicItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "正在回答…" else "只从这个专题回答")
                }
            }
        }

        if (error.isNotBlank()) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }

        if (answer.isNotBlank()) {
            item {
                KnowledgeBox {
                    Text("回答", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(answer, color = KnowledgeDark, lineHeight = 23.sp)
                }
            }
        }
    }
}

@Composable
fun WeeklyKnowledgeScreen(
    db: AppDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by db.favoriteDao().observeAll().collectAsState(initial = emptyList())
    val start = remember { System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L }
    val recent = all.filter { it.createdAt >= start }

    var report by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

    val topTopics = recent
        .mapNotNull { it.topic.takeIf(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(KnowledgeCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KnowledgeTopRow("本周拾光", onBack)
            Text("只总结最近7天真实加入的知识。", color = KnowledgeMuted)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KnowledgeMetric(Modifier.weight(1f), recent.size.toString(), "新增知识")
                KnowledgeMetric(
                    Modifier.weight(1f),
                    recent.count { it.knowledgeType == "图文" }.toString(),
                    "图文"
                )
                KnowledgeMetric(
                    Modifier.weight(1f),
                    recent.count { it.aiSummary.isNotBlank() }.toString(),
                    "已整理"
                )
            }
        }

        if (topTopics.isNotEmpty()) {
            item {
                KnowledgeBox {
                    Text("本周专题", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    topTopics.forEach {
                        Text("• " + it.first + "  " + it.second + " 条", color = KnowledgeDark)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val key = SecureApiKeyStore.load(context)
                    if (key.isNullOrBlank()) {
                        error = "请先配置 DeepSeek API Key"
                        return@Button
                    }
                    loading = true
                    error = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            DeepSeekClient.weeklyKnowledgeReport(
                                apiKey = key,
                                items = recent.map(::knowledgeMaterial)
                            )
                        }
                        loading = false
                        if (result.success) report = result.content else error = result.error
                    }
                },
                enabled = recent.isNotEmpty() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "正在生成…" else "生成本周知识报告")
            }
        }

        if (error.isNotBlank()) {
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }

        if (report.isNotBlank()) {
            item {
                KnowledgeBox {
                    Text("AI 周报", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(report, color = KnowledgeDark, lineHeight = 23.sp)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeListCard(
    card: FavoriteEntity,
    onClick: () -> Unit
) {
    KnowledgeBox(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (card.knowledgeType == "图文") Icons.Outlined.Image
                else Icons.Outlined.Description,
                null,
                tint = KnowledgeSage
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    buildString {
                        append(card.platform)
                        if (card.topic.isNotBlank()) append(" · " + card.topic)
                        if (card.processingStatus != "已保存") append(" · " + card.processingStatus)
                    },
                    color = KnowledgeMuted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            if (card.important) {
                Icon(Icons.Outlined.Star, null, tint = KnowledgeWarm)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = KnowledgeMuted)
        }
    }
}

@Composable
private fun KnowledgeMetric(
    modifier: Modifier,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = KnowledgeCardColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KnowledgeDark)
            Text(label, color = KnowledgeMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun KnowledgeTopRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, "返回")
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KnowledgeBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KnowledgeCardColor),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            content = content
        )
    }
}

private fun findRelatedKnowledge(
    current: FavoriteEntity,
    all: List<FavoriteEntity>
): List<FavoriteEntity> {
    val currentTags = splitKnowledgeTerms(current.aiTags)
    val currentWords = splitKnowledgeTerms(current.title + " " + current.suggestedTopic)

    return all
        .asSequence()
        .filter { it.id != current.id }
        .map { candidate ->
            var score = 0
            if (current.topic.isNotBlank() && candidate.topic == current.topic) score += 8
            if (current.suggestedTopic.isNotBlank() &&
                candidate.suggestedTopic == current.suggestedTopic) score += 4

            val tags = splitKnowledgeTerms(candidate.aiTags)
            score += currentTags.intersect(tags).size * 3

            val words = splitKnowledgeTerms(candidate.title + " " + candidate.suggestedTopic)
            score += currentWords.intersect(words).size

            score to candidate
        }
        .filter { it.first > 0 }
        .sortedByDescending { it.first }
        .map { it.second }
        .toList()
}

private fun splitKnowledgeTerms(text: String): Set<String> {
    return text
        .lowercase()
        .split('·', ',', '，', ' ', '/', '|', '：', ':', '-', '—')
        .map { it.trim() }
        .filter { it.length >= 2 }
        .toSet()
}

private fun knowledgeMaterial(item: FavoriteEntity): String {
    return buildString {
        append("标题：" + item.title)
        append("；来源：" + item.platform)
        if (item.topic.isNotBlank()) append("；专题：" + item.topic)
        if (item.rawText.isNotBlank()) append("；原文：" + item.rawText.take(1800))
        if (item.note.isNotBlank()) append("；我的备注：" + item.note.take(800))
        if (item.aiSummary.isNotBlank()) append("；AI整理：" + item.aiSummary.take(3200))
        if (item.aiTags.isNotBlank()) append("；标签：" + item.aiTags)
    }
}

private fun formatKnowledgeTime(ms: Long): String {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}
