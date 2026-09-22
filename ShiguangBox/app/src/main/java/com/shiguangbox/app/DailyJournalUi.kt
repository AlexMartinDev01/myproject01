package com.shiguangbox.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DailyJournalScreen(
    db: AppDatabase,
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onOpenSummary: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journal = LocalJournalTheme.current
    val date = remember { LocalDate.now() }
    val bounds = remember { journalDayBounds(date) }

    val allTasks by db.taskDao().observeAll().collectAsState(initial = emptyList())
    val notes by db.noteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val summary by db.dailySummaryDao().observeByDate(date.toString())
        .collectAsState(initial = null)

    val tasks = remember(allTasks, bounds) {
        allTasks.filter { task ->
            val dueToday = task.dueAt?.let { it in bounds.first..bounds.second } == true
            val completedToday = task.completedAt?.let { it in bounds.first..bounds.second } == true
            dueToday || completedToday
        }
    }

    val name = prefs.getString("name", "我") ?: "我"
    var sharing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(journal.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("今天的手账页", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        journal.name + " · " + journal.moodEmoji + " " + journal.moodLabel,
                        color = journal.muted,
                        fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = {
                        sharing = true
                        message = ""
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                JournalShareRenderer.render(
                                    context = context,
                                    profile = journal,
                                    name = name,
                                    date = date,
                                    tasks = tasks,
                                    notes = notes,
                                    favorites = favorites,
                                    summary = summary?.content.orEmpty()
                                )
                            }

                            sharing = false
                            if (file != null) {
                                runCatching {
                                    shareJournalImage(context, file)
                                }.onFailure {
                                    message = it.message ?: "分享失败"
                                }
                            } else {
                                message = "手账长图生成失败"
                            }
                        }
                    },
                    enabled = !sharing
                ) {
                    Icon(Icons.Outlined.Share, "分享")
                }
            }
        }

        item {
            DateStampCard(date = date, name = name)
        }

        item {
            ScrapbookSection(title = "今天的清单", sticker = "✓") {
                if (tasks.isEmpty()) {
                    Text("今天没有写下待办。", color = journal.muted)
                } else {
                    tasks.take(8).forEachIndexed { index, task ->
                        StickyTaskCard(
                            task = task,
                            tilt = if (index % 2 == 0) -1.0f else 1.0f
                        )
                        if (index != tasks.take(8).lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (tasks.size > 8) {
                        Spacer(Modifier.height(6.dp))
                        Text("还有 " + (tasks.size - 8) + " 条收在今天里。", color = journal.muted, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            ScrapbookSection(title = "今天记下", sticker = "✎") {
                if (notes.isEmpty()) {
                    Text("今天还没有留下文字。", color = journal.muted)
                } else {
                    notes.take(6).forEach { note ->
                        Text(
                            "• " + note.content,
                            color = journal.text,
                            lineHeight = 21.sp,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }

        item {
            ScrapbookSection(title = "今天收藏", sticker = "♡") {
                if (favorites.isEmpty()) {
                    Text("今天还没有收藏内容。", color = journal.muted)
                } else {
                    favorites.take(6).forEach { favorite ->
                        Column {
                            Text(
                                favorite.title,
                                fontWeight = FontWeight.SemiBold,
                                color = journal.text,
                                maxLines = 2
                            )
                            Text(
                                favorite.platform +
                                    if (favorite.topic.isNotBlank()) " · " + favorite.topic else "",
                                color = journal.muted,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }

        item {
            ScrapbookSection(title = "今天的 AI 整理", sticker = journal.stickerEmoji) {
                if (summary != null) {
                    Text(
                        summary!!.content,
                        color = journal.text,
                        lineHeight = 22.sp
                    )
                } else {
                    Text(
                        "今天还没有生成 AI 总结。手账页可以先保存，等你想整理时再生成。",
                        color = journal.muted,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Spacer(Modifier.width(7.dp))
                        Text("去整理我的今天")
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    sharing = true
                    message = ""
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            JournalShareRenderer.render(
                                context = context,
                                profile = journal,
                                name = name,
                                date = date,
                                tasks = tasks,
                                notes = notes,
                                favorites = favorites,
                                summary = summary?.content.orEmpty()
                            )
                        }
                        sharing = false
                        if (file != null) {
                            runCatching { shareJournalImage(context, file) }
                                .onFailure { message = it.message ?: "分享失败" }
                        } else {
                            message = "手账长图生成失败"
                        }
                    }
                },
                enabled = !sharing,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(if (sharing) "正在生成手账长图…" else "生成并分享今天的手账", fontWeight = FontWeight.Bold)
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "长图只包含今天的文字摘要，不会把你的 API Key 或本地原图放进去。",
                color = journal.muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DateStampCard(
    date: LocalDate,
    name: String
) {
    val journal = LocalJournalTheme.current
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)

    Card(
        colors = CardDefaults.cardColors(containerColor = journal.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(start = 34.dp, top = 0.dp)
                    .width(84.dp)
                    .height(20.dp)
                    .rotate(-5f)
                    .background(journal.paleSecondary.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = journal.text
                    )
                    Text(
                        weekday + " · " + name + " 的拾光",
                        color = journal.muted,
                        fontSize = 13.sp
                    )
                }

                Surface(
                    color = journal.palePrimary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        journal.moodEmoji + " " + journal.moodLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = journal.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrapbookSection(
    title: String,
    sticker: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val journal = LocalJournalTheme.current

    Card(
        colors = CardDefaults.cardColors(containerColor = journal.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sticker, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = journal.text)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StickyTaskCard(
    task: TaskEntity,
    tilt: Float
) {
    val journal = LocalJournalTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth().rotate(tilt),
        color = if (task.completed) journal.paleSecondary else journal.palePrimary,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (task.completed) "✓" else "○", fontSize = 18.sp, color = journal.primary)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.SemiBold,
                    color = journal.text,
                    maxLines = 2
                )
                task.dueAt?.let {
                    Text(
                        journalFormatTime(it),
                        color = journal.muted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private object JournalShareRenderer {

    fun render(
        context: Context,
        profile: JournalThemeProfile,
        name: String,
        date: LocalDate,
        tasks: List<TaskEntity>,
        notes: List<NoteEntity>,
        favorites: List<FavoriteEntity>,
        summary: String
    ): File? {
        return runCatching {
            val width = 1080
            val height = 3000
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.background.toArgb()
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

            val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.surface.toArgb()
            }
            val palePrimary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.palePrimary.toArgb()
            }
            val paleSecondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.paleSecondary.toArgb()
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.text.toArgb()
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.muted.toArgb()
                textSize = 30f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.text.toArgb()
                textSize = 66f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = profile.text.toArgb()
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Decorative scrapbook dots.
            canvas.drawCircle(950f, 120f, 64f, palePrimary)
            canvas.drawCircle(90f, 2820f, 78f, paleSecondary)

            var y = 120f
            canvas.drawText("拾光盒 · 今日手账", 72f, y, mutedPaint)
            y += 95f
            canvas.drawText(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")), 72f, y, titlePaint)
            y += 58f
            canvas.drawText(
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA) +
                    " · " + name + " · " + profile.moodLabel,
                74f,
                y,
                mutedPaint
            )
            y += 82f

            y = drawRoundedSection(canvas, 58f, y, 1022f, 220f, surface) { top ->
                canvas.drawText(profile.stickerEmoji + "  " + profile.name, 92f, top + 66f, sectionPaint)
                drawWrappedText(
                    canvas,
                    profile.tagline,
                    92f,
                    top + 118f,
                    850f,
                    textPaint.apply { textSize = 32f },
                    46f,
                    2
                )
            } + 34f

            y = drawTextSection(
                canvas, y, "✓  今天的清单", tasks.take(7).map {
                    (if (it.completed) "✓ " else "○ ") + it.title
                }.ifEmpty { listOf("今天没有写下待办") },
                surface, sectionPaint, textPaint, profile
            )

            y = drawTextSection(
                canvas, y, "✎  今天记下", notes.take(5).map { "• " + it.content.take(110) }
                    .ifEmpty { listOf("今天还没有留下文字") },
                surface, sectionPaint, textPaint, profile
            )

            y = drawTextSection(
                canvas, y, "♡  今天收藏", favorites.take(5).map {
                    "• " + it.title.take(90) + "  [" + it.platform + "]"
                }.ifEmpty { listOf("今天还没有收藏内容") },
                surface, sectionPaint, textPaint, profile
            )

            val summaryLines = if (summary.isBlank()) {
                listOf("今天还没有生成 AI 总结。")
            } else {
                summary.replace("\r", "").split("\n").filter { it.isNotBlank() }.take(14)
            }

            y = drawTextSection(
                canvas, y, profile.stickerEmoji + "  今天的 AI 整理",
                summaryLines,
                surface, sectionPaint, textPaint, profile,
                maxSectionHeight = 780f
            )

            val footerY = (height - 120).toFloat()
            canvas.drawText("把今天慢慢收好 · ShiguangBox", 72f, footerY, mutedPaint)

            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "拾光盒手账_" + date + ".png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            file
        }.getOrNull()
    }

    private fun drawRoundedSection(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        paint: Paint,
        drawContent: (Float) -> Unit
    ): Float {
        canvas.drawRoundRect(left, top, right, top + height, 34f, 34f, paint)
        drawContent(top)
        return top + height
    }

    private fun drawTextSection(
        canvas: Canvas,
        top: Float,
        title: String,
        lines: List<String>,
        surface: Paint,
        sectionPaint: Paint,
        textPaint: Paint,
        profile: JournalThemeProfile,
        maxSectionHeight: Float = 540f
    ): Float {
        val estimatedHeight = (150f + lines.size * 78f).coerceAtMost(maxSectionHeight)
        canvas.drawRoundRect(58f, top, 1022f, top + estimatedHeight, 34f, 34f, surface)
        canvas.drawText(title, 92f, top + 68f, sectionPaint)

        var y = top + 126f
        val normal = Paint(textPaint).apply {
            textSize = 31f
            color = profile.text.toArgb()
        }

        lines.forEach { line ->
            if (y > top + estimatedHeight - 48f) return@forEach
            y = drawWrappedText(canvas, line, 96f, y, 840f, normal, 44f, 3) + 16f
        }

        return top + estimatedHeight + 34f
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maxLines: Int
    ): Float {
        if (text.isBlank()) return y

        var current = ""
        var line = 0
        var baseline = y

        for (char in text) {
            val candidate = current + char
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                canvas.drawText(current, x, baseline, paint)
                baseline += lineHeight
                line += 1
                if (line >= maxLines) return baseline
                current = char.toString()
            } else {
                current = candidate
            }
        }

        if (current.isNotEmpty() && line < maxLines) {
            canvas.drawText(current, x, baseline, paint)
            baseline += lineHeight
        }
        return baseline
    }
}

private fun shareJournalImage(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "我的拾光盒今日手账")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "分享今天的手账"))
}

private fun journalDayBounds(date: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}

private fun journalFormatTime(ms: Long): String {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
