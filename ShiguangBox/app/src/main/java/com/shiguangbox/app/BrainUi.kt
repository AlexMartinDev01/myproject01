package com.shiguangbox.app

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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BrainCream = Color(0xFFFFF9F0)
private val BrainCard = Color(0xFFFFFDFC)
private val BrainWarm = Color(0xFFF4B860)
private val BrainSage = Color(0xFFA8B99A)
private val BrainDark = Color(0xFF49392C)
private val BrainMuted = Color(0xFF8A7969)

@Composable
fun GlobalSearchScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onNote: (Long) -> Unit,
    onTask: (Long) -> Unit,
    onFavorite: (Long) -> Unit
) {
    val notes by db.noteDao().observeAll().collectAsState(initial = emptyList())
    val tasks by db.taskDao().observeAll().collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeAll().collectAsState(initial = emptyList())
    val summaries by db.dailySummaryDao().observeAll().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }

    val q = query.trim()
    val matchedNotes = remember(notes, q) {
        if (q.isBlank()) emptyList()
        else notes.filter {
            it.content.contains(q, true) || it.category.contains(q, true)
        }.take(20)
    }
    val matchedTasks = remember(tasks, q) {
        if (q.isBlank()) emptyList()
        else tasks.filter {
            it.title.contains(q, true) ||
                it.priority.contains(q, true) ||
                it.repeatType.contains(q, true)
        }.take(20)
    }
    val matchedFavorites = remember(favorites, q) {
        if (q.isBlank()) emptyList()
        else favorites.filter {
            listOf(
                it.title,
                it.rawText,
                it.note,
                it.aiSummary,
                it.aiTags,
                it.platform,
                it.imageAnalysis,
                it.topic,
                it.suggestedTopic
            ).any { text -> text.contains(q, true) }
        }.take(20)
    }
    val matchedSummaries = remember(summaries, q) {
        if (q.isBlank()) emptyList()
        else summaries.filter { it.content.contains(q, true) }.take(20)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BrainCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TopRow("搜索我的拾光盒", onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜记录、待办、收藏、AI摘要……") },
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
        }

        if (q.isBlank()) {
            item {
                BrainCardBox {
                    Icon(Icons.Outlined.Search, null, tint = BrainSage)
                    Spacer(Modifier.height(10.dp))
                    Text("输入一个关键词，就能同时搜索你自己的记录、任务、收藏和 AI 总结。", color = BrainMuted)
                }
            }
        } else {
            val total =
                matchedNotes.size + matchedTasks.size + matchedFavorites.size + matchedSummaries.size

            item {
                Text("找到 $total 条相关内容", color = BrainMuted, fontSize = 13.sp)
            }

            if (matchedNotes.isNotEmpty()) {
                item { SectionTitle("记录", Icons.Outlined.EditNote) }
                items(matchedNotes, key = { "n" + it.id }) { note ->
                    SearchResultCard(
                        title = note.content.take(80),
                        subtitle = note.category + " · " + formatBrainTime(note.createdAt),
                        icon = Icons.Outlined.EditNote,
                        onClick = { onNote(note.id) }
                    )
                }
            }

            if (matchedTasks.isNotEmpty()) {
                item { SectionTitle("待办", Icons.Outlined.CheckCircleOutline) }
                items(matchedTasks, key = { "t" + it.id }) { task ->
                    SearchResultCard(
                        title = task.title,
                        subtitle = if (task.completed) "已完成" else "待完成",
                        icon = Icons.Outlined.CheckCircleOutline,
                        onClick = { onTask(task.id) }
                    )
                }
            }

            if (matchedFavorites.isNotEmpty()) {
                item { SectionTitle("收藏", Icons.Outlined.BookmarkBorder) }
                items(matchedFavorites, key = { "f" + it.id }) { favorite ->
                    SearchResultCard(
                        title = favorite.title,
                        subtitle = buildString {
                            append(favorite.platform)
                            if (favorite.topic.isNotBlank()) append(" · " + favorite.topic)
                            if (favorite.knowledgeType == "图文") append(" · 图文")
                            if (favorite.aiSummary.isNotBlank()) append(" · 已整理")
                        },
                        icon = Icons.Outlined.BookmarkBorder,
                        onClick = { onFavorite(favorite.id) }
                    )
                }
            }

            if (matchedSummaries.isNotEmpty()) {
                item { SectionTitle("每日总结", Icons.Outlined.AutoAwesome) }
                items(matchedSummaries, key = { "s" + it.dateKey }) { summary ->
                    BrainCardBox {
                        Text(summary.dateKey, color = BrainMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(summary.content.take(220), color = BrainDark, maxLines = 6)
                    }
                }
            }

            if (total == 0) {
                item {
                    BrainCardBox {
                        Text("暂时没找到。可以换一个更短的关键词试试。", color = BrainMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun AskMyBoxScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onAiSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes by db.noteDao().observeAll().collectAsState(initial = emptyList())
    val tasks by db.taskDao().observeAll().collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeAll().collectAsState(initial = emptyList())
    val summaries by db.dailySummaryDao().observeAll().collectAsState(initial = emptyList())

    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var sourcePreview by remember { mutableStateOf<List<String>>(emptyList()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BrainCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TopRow("问问拾光盒", onBack)
            Text(
                "它先查你的资料，再让 DeepSeek 基于这些资料回答。",
                color = BrainMuted
            )
        }

        item {
            BrainCardBox {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    placeholder = {
                        Text("例如：我以前收藏过哪些关于直播开场的内容？")
                    },
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        val key = SecureApiKeyStore.load(context)
                        if (key.isNullOrBlank()) {
                            onAiSettings()
                            return@Button
                        }

                        val docs = buildMemoryDocuments(notes, tasks, favorites, summaries)
                        val ranked = rankMemory(question, docs).take(18)
                        sourcePreview = ranked.take(6)
                        loading = true
                        error = ""
                        answer = ""

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DeepSeekClient.answerFromMemory(
                                    apiKey = key,
                                    question = question.trim(),
                                    contextItems = ranked
                                )
                            }
                            loading = false
                            if (result.success) {
                                answer = result.content
                            } else {
                                error = result.error
                            }
                        }
                    },
                    enabled = question.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "正在查找和回答…" else "从我的资料里回答")
                }

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }

        if (answer.isNotBlank()) {
            item {
                BrainCardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Psychology, null, tint = BrainWarm)
                        Spacer(Modifier.width(8.dp))
                        Text("回答", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(answer, color = BrainDark, lineHeight = 23.sp)
                }
            }
        }

        if (sourcePreview.isNotEmpty()) {
            item {
                BrainCardBox {
                    Text("本次检索到的主要依据", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    sourcePreview.forEach {
                        Text("• " + it.take(180), color = BrainMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onDay: (String) -> Unit
) {
    val notes by db.noteDao().observeAll().collectAsState(initial = emptyList())
    val tasks by db.taskDao().observeAll().collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeAll().collectAsState(initial = emptyList())
    val summaries by db.dailySummaryDao().observeAll().collectAsState(initial = emptyList())

    val days = remember(notes, tasks, favorites, summaries) {
        buildSet {
            notes.forEach { add(millisToBrainDate(it.createdAt).toString()) }
            tasks.forEach { task ->
                task.dueAt?.let { add(millisToBrainDate(it).toString()) }
                task.completedAt?.let { add(millisToBrainDate(it).toString()) }
            }
            favorites.forEach { add(millisToBrainDate(it.createdAt).toString()) }
            summaries.forEach { add(it.dateKey) }
        }.sortedDescending()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BrainCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TopRow("历史回顾", onBack)
            Text("按天回看你记录过、做过、收藏过什么。", color = BrainMuted)
        }

        if (days.isEmpty()) {
            item {
                BrainCardBox {
                    Text("还没有可以回顾的历史。", color = BrainMuted)
                }
            }
        } else {
            items(days, key = { it }) { dateKey ->
                val date = runCatching { LocalDate.parse(dateKey) }.getOrNull()
                if (date != null) {
                    val bounds = brainDayBounds(date)
                    val noteCount = notes.count { it.createdAt in bounds.first..bounds.second }
                    val taskCount = tasks.count {
                        it.dueAt?.let { due -> due in bounds.first..bounds.second } == true
                    }
                    val favoriteCount = favorites.count {
                        it.createdAt in bounds.first..bounds.second
                    }
                    val hasSummary = summaries.any { it.dateKey == dateKey }

                    BrainCardBox(
                        modifier = Modifier.clickable { onDay(dateKey) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("M月d日")),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    "记录 $noteCount · 待办 $taskCount · 收藏 $favoriteCount",
                                    color = BrainMuted,
                                    fontSize = 13.sp
                                )
                            }
                            if (hasSummary) {
                                AssistChip(
                                    onClick = { onDay(dateKey) },
                                    label = { Text("已总结") },
                                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = BrainMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDayScreen(
    db: AppDatabase,
    dateKey: String,
    onBack: () -> Unit
) {
    val date = remember(dateKey) {
        runCatching { LocalDate.parse(dateKey) }.getOrElse { LocalDate.now() }
    }
    val bounds = remember(dateKey) { brainDayBounds(date) }

    val notes by db.noteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val tasks by db.taskDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val summary by db.dailySummaryDao().observeByDate(dateKey)
        .collectAsState(initial = null)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BrainCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TopRow(
                date.format(DateTimeFormatter.ofPattern("M月d日")) + " 的拾光盒",
                onBack
            )
        }

        summary?.let {
            item {
                BrainCardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = BrainWarm)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 每日总结", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(it.content, color = BrainDark, lineHeight = 22.sp)
                }
            }
        }

        item {
            BrainCardBox {
                Text("待办", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (tasks.isEmpty()) {
                    Text("这一天没有待办。", color = BrainMuted)
                } else {
                    tasks.forEach {
                        Text(
                            (if (it.completed) "✓ " else "○ ") + it.title,
                            color = if (it.completed) BrainMuted else BrainDark
                        )
                    }
                }
            }
        }

        item {
            BrainCardBox {
                Text("记录", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (notes.isEmpty()) {
                    Text("这一天没有记录。", color = BrainMuted)
                } else {
                    notes.forEach {
                        Text("• " + it.content, color = BrainDark)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        item {
            BrainCardBox {
                Text("收藏", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (favorites.isEmpty()) {
                    Text("这一天没有收藏。", color = BrainMuted)
                } else {
                    favorites.forEach {
                        Text("• [" + it.platform + "] " + it.title, color = BrainDark)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

private fun buildMemoryDocuments(
    notes: List<NoteEntity>,
    tasks: List<TaskEntity>,
    favorites: List<FavoriteEntity>,
    summaries: List<DailySummaryEntity>
): List<String> {
    val docs = mutableListOf<String>()

    notes.forEach {
        docs += "记录 | " + formatBrainTime(it.createdAt) + " | " +
            it.category + " | " + it.content
    }

    tasks.forEach {
        docs += "待办 | " +
            (it.dueAt?.let(::formatBrainTime) ?: "未设时间") +
            " | " + (if (it.completed) "已完成" else "未完成") +
            " | " + it.title
    }

    favorites.forEach {
        docs += buildString {
            append("知识卡 | ")
            append(formatBrainTime(it.createdAt))
            append(" | ")
            append(it.platform)
            append(" | ")
            append(it.title)
            if (it.topic.isNotBlank()) append(" | 专题：" + it.topic)
            if (it.note.isNotBlank()) append(" | 我的备注：" + it.note)
            if (it.aiSummary.isNotBlank()) append(" | AI整理：" + it.aiSummary.take(4200))
            if (it.imageAnalysis.isNotBlank() && it.imageAnalysis != it.aiSummary) {
                append(" | 图文分析：" + it.imageAnalysis.take(2600))
            }
            if (it.aiTags.isNotBlank()) append(" | 标签：" + it.aiTags)
        }
    }

    summaries.forEach {
        docs += "每日总结 | " + it.dateKey + " | " + it.content
    }

    return docs
}

private fun rankMemory(question: String, docs: List<String>): List<String> {
    val tokens = memoryTokens(question)
    if (tokens.isEmpty()) return docs.take(18)

    val scored = docs.mapIndexed { index, doc ->
        val lower = doc.lowercase()
        var score = 0
        tokens.forEach { token ->
            if (lower.contains(token)) score += if (token.length >= 3) 3 else 1
        }
        if (lower.contains(question.trim().lowercase())) score += 8
        Triple(score, -index, doc)
    }

    val matched = scored
        .filter { it.first > 0 }
        .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }
            .thenByDescending { it.second })
        .map { it.third }

    return if (matched.isNotEmpty()) matched else docs.take(18)
}

private fun memoryTokens(text: String): Set<String> {
    val normalized = text.lowercase()
        .replace(Regex("[，。！？、；：,.!?;:]"), " ")
        .trim()

    val tokens = mutableSetOf<String>()

    Regex("[a-z0-9_+-]{2,}", RegexOption.IGNORE_CASE)
        .findAll(normalized)
        .forEach { tokens += it.value }

    val chinese = normalized.filter { it.code in 0x4E00..0x9FFF }
    if (chinese.length >= 2) {
        chinese.windowed(2).forEach { tokens += it }
    }
    if (chinese.length >= 3) {
        chinese.windowed(3).forEach { tokens += it }
    }

    return tokens
}

@Composable
private fun SearchResultCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    BrainCardBox(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BrainSage)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = BrainDark, fontWeight = FontWeight.Medium)
                Text(subtitle, color = BrainMuted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = BrainMuted)
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BrainWarm, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun TopRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, "返回")
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BrainCardBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrainCard),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            content = content
        )
    }
}

private fun millisToBrainDate(ms: Long): LocalDate {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun formatBrainTime(ms: Long): String {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"))
}

private fun brainDayBounds(date: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}
