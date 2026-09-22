package com.shiguangbox.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("today", "今天", Icons.Outlined.Home),
    NavItem("notes", "记录", Icons.Outlined.EditNote),
    NavItem("tasks", "待办", Icons.Outlined.CheckCircleOutline),
    NavItem("favorites", "收藏", Icons.Outlined.BookmarkBorder),
    NavItem("mine", "我的", Icons.Outlined.PersonOutline)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShiguangBoxApp(
                requestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            2001
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun ShiguangBoxApp(requestNotifications: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("shiguangbox_settings", Context.MODE_PRIVATE)
    }
    var onboardingDone by remember {
        mutableStateOf(prefs.getBoolean("onboarding_done", false))
    }
    var journalMood by remember {
        mutableStateOf(prefs.getString("journal_mood", "calm") ?: "calm")
    }
    var journalThemeOffset by remember {
        mutableIntStateOf(prefs.getInt("journal_theme_offset", 0))
    }

    val journalProfile = remember(journalMood, journalThemeOffset) {
        resolveJournalTheme(
            moodId = journalMood,
            date = LocalDate.now(),
            offset = journalThemeOffset
        )
    }

    val selectMood: (String) -> Unit = { moodId ->
        journalMood = moodId
        journalThemeOffset = 0
        prefs.edit()
            .putString("journal_mood", moodId)
            .putInt("journal_theme_offset", 0)
            .putString("journal_mood_date", LocalDate.now().toString())
            .apply()
    }

    val shuffleJournalTheme: () -> Unit = {
        journalThemeOffset += 1
        prefs.edit()
            .putInt("journal_theme_offset", journalThemeOffset)
            .putString("journal_mood_date", LocalDate.now().toString())
            .apply()
    }

    LaunchedEffect(onboardingDone) {
        if (onboardingDone) {
            DailySummaryScheduler.schedule(
                context,
                prefs.getString("summary_time", "22:30") ?: "22:30"
            )
        }
    }

    JournalMaterialTheme(profile = journalProfile) {
        if (!onboardingDone) {
            SetupScreen(
                initialName = prefs.getString("name", "苏打水") ?: "苏打水",
                initialCity = prefs.getString("city", "") ?: "",
                initialSummaryTime = prefs.getString("summary_time", "22:30") ?: "22:30",
                onStart = { name, city, time ->
                    prefs.edit()
                        .putString("name", name.ifBlank { "我" })
                        .putString("city", city.trim())
                        .putString("summary_time", time.ifBlank { "22:30" })
                        .putBoolean("onboarding_done", true)
                        .apply()
                    onboardingDone = true
                    requestNotifications()
                }
            )
        } else {
            MainShell(
                prefs = prefs,
                journalMood = journalMood,
                onSelectMood = selectMood,
                onShuffleTheme = shuffleJournalTheme
            )
        }
    }
}

@Composable
private fun SetupScreen(
    initialName: String,
    initialCity: String,
    initialSummaryTime: String,
    onStart: (String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var city by rememberSaveable { mutableStateOf(initialCity) }
    var summaryTime by rememberSaveable { mutableStateOf(initialSummaryTime) }

    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("把生活慢慢收进来", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("记录想法、安排事情、收藏好内容，\n剩下的慢慢交给拾光盒整理。", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
            }

            item { SetupField("你的称呼", "保存在本机", name) { name = it } }
            item { SetupField("常用城市", "用于首页实时天气；也可以之后再设置", city) { city = it } }
            item { SetupField("每日总结时间", "例如 22:30", summaryTime) { summaryTime = it } }

            item {
                Button(
                    onClick = { onStart(name, city, summaryTime) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("开始使用", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ArrowForward, null)
                }
                Spacer(Modifier.height(8.dp))
                Text("记录和待办会真实保存在手机本地。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SetupField(title: String, hint: String, value: String, onValue: (String) -> Unit) {
    WarmCard {
        Text(title, fontWeight = FontWeight.Bold)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
    }
}

@Composable
private fun MainShell(
    prefs: SharedPreferences,
    journalMood: String,
    onSelectMood: (String) -> Unit,
    onShuffleTheme: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in navItems.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute in navItems.map { it.route }) {
                Column(horizontalAlignment = Alignment.End) {
                    if (quickAddOpen) {
                        QuickAction("记一下", Icons.Outlined.EditNote) {
                            quickAddOpen = false
                            navController.navigate("quick_note")
                        }
                        QuickAction("加待办", Icons.Outlined.AddTask) {
                            quickAddOpen = false
                            navController.navigate("new_task")
                        }
                        QuickAction("收收藏", Icons.Outlined.BookmarkAdd) {
                            quickAddOpen = false
                            navController.navigate("manual_favorite")
                        }
                        QuickAction("收图文", Icons.Outlined.AddPhotoAlternate) {
                            quickAddOpen = false
                            navController.navigate("knowledge_image_import")
                        }
                        QuickAction("晒手账", Icons.Outlined.CollectionsBookmark) {
                            quickAddOpen = false
                            navController.navigate("daily_journal")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    FloatingActionButton(
                        onClick = { quickAddOpen = !quickAddOpen },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Add, "快速添加")
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding)
        ) {
            composable("today") {
                TodayScreen(
                    db = db,
                    prefs = prefs,
                    onSummary = { navController.navigate("summary") },
                    onQuickNote = { navController.navigate("quick_note") },
                    onNewTask = { navController.navigate("new_task") },
                    onTask = { navController.navigate("edit_task/" + it) },
                    onNote = { navController.navigate("edit_note/" + it) },
                    onSearch = { navController.navigate("global_search") },
                    onAsk = { navController.navigate("ask_box") },
                    onHistory = { navController.navigate("history") },
                    onKnowledge = { navController.navigate("knowledge") },
                    onMood = { navController.navigate("journal_mood") },
                    onJournalPage = { navController.navigate("daily_journal") }
                )
            }
            composable("notes") {
                NotesScreen(
                    db = db,
                    onNew = { navController.navigate("quick_note") },
                    onEdit = { navController.navigate("edit_note/" + it) }
                )
            }
            composable("tasks") {
                TasksScreen(
                    db = db,
                    onNew = { navController.navigate("new_task") },
                    onEdit = { navController.navigate("edit_task/" + it) }
                )
            }
            composable("favorites") {
                CollectionsScreen(
                    db = db,
                    onDetail = { navController.navigate("knowledge_detail/" + it) },
                    onManualAdd = { navController.navigate("manual_favorite") }
                )
            }
            composable("manual_favorite") {
                ManualCollectionScreen(
                    db = db,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "favorite_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                CollectionDetailScreen(
                    db = db,
                    favoriteId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("knowledge") {
                KnowledgeHomeScreen(
                    db = db,
                    onBack = { navController.popBackStack() },
                    onCard = { navController.navigate("knowledge_detail/" + it) },
                    onTopic = { navController.navigate("topic/" + Uri.encode(it)) },
                    onImageImport = { navController.navigate("knowledge_image_import") },
                    onWeekly = { navController.navigate("weekly_knowledge") }
                )
            }
            composable("knowledge_image_import") {
                KnowledgeImageImportScreen(
                    db = db,
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate("knowledge_detail/" + id) {
                            popUpTo("knowledge_image_import") { inclusive = true }
                        }
                    }
                )
            }
            composable(
                "knowledge_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                KnowledgeCardScreen(
                    db = db,
                    favoriteId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onCard = { navController.navigate("knowledge_detail/" + it) },
                    onTopic = { navController.navigate("topic/" + Uri.encode(it)) }
                )
            }
            composable(
                "topic/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { entry ->
                val topicName = Uri.decode(entry.arguments?.getString("name").orEmpty())
                TopicDetailScreen(
                    db = db,
                    topic = topicName,
                    onBack = { navController.popBackStack() },
                    onCard = { navController.navigate("knowledge_detail/" + it) },
                    onAsk = { navController.navigate("topic_ask/" + Uri.encode(topicName)) }
                )
            }
            composable(
                "topic_ask/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { entry ->
                TopicAskScreen(
                    db = db,
                    topic = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("weekly_knowledge") {
                WeeklyKnowledgeScreen(
                    db = db,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("journal_mood") {
                MoodJournalScreen(
                    currentMoodId = journalMood,
                    onSelectMood = onSelectMood,
                    onShuffle = onShuffleTheme,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("daily_journal") {
                DailyJournalScreen(
                    db = db,
                    prefs = prefs,
                    onBack = { navController.popBackStack() },
                    onOpenSummary = { navController.navigate("summary") }
                )
            }

            composable("mine") {
                SettingsScreen(
                    db = db,
                    onAiSettings = { navController.navigate("ai_settings") },
                    onJournalTheme = { navController.navigate("journal_mood") },
                    onPetSettings = { navController.navigate("pet_settings") }
                )
            }
            composable("pet_settings") {
                PetSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("ai_settings") {
                AiSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("global_search") {
                GlobalSearchScreen(
                    db = db,
                    onBack = { navController.popBackStack() },
                    onNote = { navController.navigate("edit_note/" + it) },
                    onTask = { navController.navigate("edit_task/" + it) },
                    onFavorite = { navController.navigate("knowledge_detail/" + it) }
                )
            }
            composable("ask_box") {
                AskMyBoxScreen(
                    db = db,
                    onBack = { navController.popBackStack() },
                    onAiSettings = { navController.navigate("ai_settings") }
                )
            }
            composable("history") {
                HistoryScreen(
                    db = db,
                    onBack = { navController.popBackStack() },
                    onDay = { navController.navigate("history_day/" + it) }
                )
            }
            composable(
                "history_day/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { entry ->
                HistoryDayScreen(
                    db = db,
                    dateKey = entry.arguments?.getString("date") ?: LocalDate.now().toString(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("quick_note") {
                NoteEditorScreen(
                    db = db,
                    noteId = null,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "edit_note/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                NoteEditorScreen(
                    db = db,
                    noteId = entry.arguments?.getLong("id"),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("new_task") {
                TaskEditorScreen(
                    db = db,
                    taskId = null,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "edit_task/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                TaskEditorScreen(
                    db = db,
                    taskId = entry.arguments?.getLong("id"),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("summary") {
                SummaryScreen(
                    db = db,
                    onBack = { navController.popBackStack() },
                    onAiSettings = { navController.navigate("ai_settings") }
                )
            }
        }
    }
}

@Composable
private fun TodayScreen(
    db: AppDatabase,
    prefs: SharedPreferences,
    onSummary: () -> Unit,
    onQuickNote: () -> Unit,
    onNewTask: () -> Unit,
    onTask: (Long) -> Unit,
    onNote: (Long) -> Unit,
    onSearch: () -> Unit,
    onAsk: () -> Unit,
    onHistory: () -> Unit,
    onKnowledge: () -> Unit,
    onMood: () -> Unit,
    onJournalPage: () -> Unit
) {
    val bounds = remember { todayBounds() }
    val tasks by db.taskDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val notes by db.noteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val name = prefs.getString("name", "我") ?: "我"
    val city = prefs.getString("city", "") ?: ""
    val date = LocalDate.now()
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)

    var weather by remember(city) { mutableStateOf<WeatherSnapshot?>(null) }
    var weatherLoading by remember(city) { mutableStateOf(true) }
    var weatherError by remember(city) { mutableStateOf("") }

    LaunchedEffect(city) {
        weather = null
        weatherError = ""
        if (city.isBlank()) {
            weatherLoading = false
        } else {
            weatherLoading = true
            val result = withContext(Dispatchers.IO) { WeatherClient.fetch(city) }
            weatherLoading = false
            result.onSuccess { weather = it }
                .onFailure { weatherError = it.message ?: "天气加载失败" }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("你好，" + name + " ☀️", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        date.monthValue.toString() + "月" + date.dayOfMonth + "日 · " + weekday,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, "全局搜索", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Outlined.History, "历史回顾", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        item {
            JournalDayHeader(onOpen = onMood)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onJournalPage,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Outlined.CollectionsBookmark, null)
                Spacer(Modifier.width(8.dp))
                Text("打开今天的完整手账页")
            }
        }

        item {
            WarmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WbSunny,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        when {
                            city.isBlank() -> {
                                Text("还没有设置常用城市", fontWeight = FontWeight.Bold)
                                Text(
                                    "到“我的”里填写城市后，这里会显示实时天气。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                            weatherLoading -> {
                                Text(city + " · 正在获取天气", fontWeight = FontWeight.Bold)
                                Text("稍等一下…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            weather != null -> {
                                val w = weather!!
                                Text(
                                    city + "  " + String.format(Locale.CHINA, "%.0f°C", w.temperature) + "  " + w.condition,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    String.format(
                                        Locale.CHINA,
                                        "%.0f～%.0f°C · 降雨概率 %d%% · 体感 %.0f°C",
                                        w.minTemperature,
                                        w.maxTemperature,
                                        w.rainProbability,
                                        w.apparentTemperature
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(w.advice, color = MaterialTheme.colorScheme.onBackground)
                            }
                            else -> {
                                Text(city + " · 天气暂时没加载出来", fontWeight = FontWeight.Bold)
                                Text(weatherError, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAsk),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Psychology,
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("问问拾光盒", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "从你自己的记录、待办、收藏和总结里找答案",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onKnowledge),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AccountTree,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("我的知识库", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "专题、图文知识卡、相关内容和每周回顾",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            val doneCount = tasks.count { it.completed }
            SectionCard(
                title = "今天要做",
                icon = Icons.Outlined.Checklist,
                trailing = doneCount.toString() + "/" + tasks.size + " 已完成"
            ) {
                if (tasks.isEmpty()) {
                    Text("今天还没有待办，给自己安排一件小事吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tasks.take(5).forEach { task ->
                        DynamicTaskRow(task) { onTask(task.id) }
                    }
                }
                TextButton(onClick = onNewTask) { Text("＋ 新建待办") }
            }
        }

        item {
            SectionCard(
                title = "今天记下了",
                icon = Icons.Outlined.EditNote,
                trailing = notes.size.toString() + " 条"
            ) {
                if (notes.isEmpty()) {
                    Text("今天还没留下记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    notes.take(4).forEach { note ->
                        DynamicNoteRow(note) { onNote(note.id) }
                    }
                }
                TextButton(onClick = onQuickNote) { Text("＋ 记一下") }
            }
        }

        item {
            SectionCard(
                title = "今天收进来了",
                icon = Icons.Outlined.Bookmarks,
                trailing = favorites.size.toString() + " 条"
            ) {
                if (favorites.isEmpty()) {
                    Text("今天还没有收藏。刷到好内容时直接“分享 → 收进拾光盒”。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val douyin = favorites.count { it.platform == "抖音" }
                    val bili = favorites.count { it.platform == "B站" }
                    val weread = favorites.count { it.platform == "微信读书" }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (douyin > 0) TagPill("抖音 ×" + douyin)
                        if (bili > 0) TagPill("B站 ×" + bili)
                        if (weread > 0) TagPill("微信读书 ×" + weread)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        favorites.first().title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
            }
        }

        item {
            Button(
                onClick = onSummary,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("整理我的今天", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Text("搜索、问答、历史回顾和自动总结都已接入。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NotesScreen(
    db: AppDatabase,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val notes by db.noteDao().observeAll().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("记录", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("现在写下的每一条，关掉 App 后也不会消失。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (notes.isEmpty()) {
            item { EmptyCard("还没有记录，先写下第一条吧。") }
        } else {
            items(notes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(note.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(note.content, color = MaterialTheme.colorScheme.onBackground, lineHeight = 22.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagPill(note.category)
                            Spacer(Modifier.width(10.dp))
                            Text(formatDateTime(note.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Outlined.Edit,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, null)
                Spacer(Modifier.width(8.dp))
                Text("写一条新记录")
            }
        }
    }
}

@Composable
private fun NoteEditorScreen(
    db: AppDatabase,
    noteId: Long?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val existing: NoteEntity? = if (noteId != null) {
        db.noteDao().observeById(noteId).collectAsState(initial = null).value
    } else {
        null
    }

    var initialized by rememberSaveable(noteId) { mutableStateOf(noteId == null) }
    var text by rememberSaveable(noteId) { mutableStateOf("") }
    var category by rememberSaveable(noteId) { mutableStateOf("生活") }

    LaunchedEffect(existing?.id) {
        if (!initialized && existing != null) {
            text = existing.content
            category = existing.category
            initialized = true
        }
    }

    SimpleTopScreen(if (noteId == null) "新建记录" else "编辑记录", onBack) {
        Text("现在在想什么？", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("真实保存到手机本地。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(190.dp),
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("写下此刻的想法……") }
        )

        Spacer(Modifier.height(14.dp))
        Text("分类", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("生活", "工作", "学习", "灵感").forEach { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(item) }
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = {
                if (text.isNotBlank()) {
                    scope.launch {
                        if (existing == null) {
                            db.noteDao().insert(
                                NoteEntity(content = text.trim(), category = category)
                            )
                        } else {
                            db.noteDao().update(
                                existing.copy(
                                    content = text.trim(),
                                    category = category,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        onBack()
                    }
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(
                if (noteId == null) "保存记录" else "保存修改",
                fontWeight = FontWeight.Bold
            )
        }

        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        db.noteDao().delete(existing)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text("删除这条记录")
            }
        }
    }
}

@Composable
private fun TasksScreen(
    db: AppDatabase,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by db.taskDao().observeAll().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }

    val today = LocalDate.now()
    val shown = tasks.filter {
        when (tab) {
            0 -> !it.completed &&
                it.dueAt?.let { ms -> millisToDate(ms) == today } == true
            1 -> !it.completed &&
                (it.dueAt == null || millisToDate(it.dueAt) > today)
            else -> it.completed
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("待办 ☀️", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("这次是真的会保存、会提醒。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                listOf("今天", "接下来", "已完成").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) }
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item { EmptyCard("这里暂时没有任务。") }
        } else {
            items(shown, key = { it.id }) { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(task.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    if (checked) {
                                        ReminderScheduler.cancel(context, task.id)
                                        db.taskDao().update(
                                            task.copy(
                                                completed = true,
                                                completedAt = System.currentTimeMillis()
                                            )
                                        )

                                        if (task.repeatType != "不重复") {
                                            val next = nextRecurringTask(task)
                                            val id = db.taskDao().insert(next)
                                            ReminderScheduler.schedule(
                                                context,
                                                next.copy(id = id)
                                            )
                                        }
                                    } else {
                                        val restored = task.copy(
                                            completed = false,
                                            completedAt = null
                                        )
                                        db.taskDao().update(restored)
                                        ReminderScheduler.schedule(context, restored)
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.width(8.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration =
                                    if (task.completed) TextDecoration.LineThrough else null,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
                            )
                            Text(taskSubtitle(task), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }

                        if (task.priority == "重要") {
                            Icon(
                                Icons.Outlined.PriorityHigh,
                                null,
                                tint = Color(0xFFE76F51)
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AddTask, null)
                Spacer(Modifier.width(8.dp))
                Text("新建待办")
            }
        }
    }
}

@Composable
private fun TaskEditorScreen(
    db: AppDatabase,
    taskId: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val existing: TaskEntity? = if (taskId != null) {
        db.taskDao().observeById(taskId).collectAsState(initial = null).value
    } else {
        null
    }

    var initialized by rememberSaveable(taskId) { mutableStateOf(taskId == null) }
    var naturalText by rememberSaveable(taskId) { mutableStateOf("") }
    var title by rememberSaveable(taskId) { mutableStateOf("") }
    var dateText by rememberSaveable(taskId) {
        mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var timeText by rememberSaveable(taskId) { mutableStateOf("18:00") }
    var repeatType by rememberSaveable(taskId) { mutableStateOf("不重复") }
    var priority by rememberSaveable(taskId) { mutableStateOf("普通") }
    var parseMessage by rememberSaveable(taskId) { mutableStateOf("") }

    LaunchedEffect(existing?.id) {
        if (!initialized && existing != null) {
            title = existing.title
            existing.dueAt?.let {
                val dt = Instant.ofEpochMilli(it)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                dateText = dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                timeText = dt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            repeatType = existing.repeatType
            priority = existing.priority
            initialized = true
        }
    }

    SimpleTopScreen(if (taskId == null) "新建待办" else "编辑待办", onBack) {
        Text("要做什么？", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("可以直接输入“明天下午三点提醒我给老师发材料”。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = naturalText,
            onValueChange = { naturalText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自然语言输入（可选）") },
            placeholder = { Text("明天下午三点提醒我给老师发材料") },
            shape = RoundedCornerShape(18.dp)
        )

        TextButton(
            onClick = {
                val parsed = parseNaturalTask(naturalText)
                if (parsed != null) {
                    title = parsed.title
                    dateText = parsed.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    timeText = parsed.time.format(DateTimeFormatter.ofPattern("HH:mm"))
                    parseMessage = "✓ 已识别，可以继续调整"
                } else {
                    parseMessage = "没有完全识别，请在下面手动填写"
                    if (title.isBlank()) title = naturalText.trim()
                }
            },
            enabled = naturalText.isNotBlank()
        ) {
            Icon(Icons.Outlined.AutoAwesome, null)
            Spacer(Modifier.width(6.dp))
            Text("识别一下")
        }

        if (parseMessage.isNotBlank()) {
            Text(parseMessage, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("任务名称") },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("日期 yyyy-MM-dd") },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("时间 HH:mm") },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        Text("重复", fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("不重复", "每天", "每周", "每月").forEach { item ->
                FilterChip(
                    selected = repeatType == item,
                    onClick = { repeatType = item },
                    label = { Text(item) }
                )
            }
        }

        Text("优先级", fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("普通", "重要").forEach { item ->
                FilterChip(
                    selected = priority == item,
                    onClick = { priority = item },
                    label = { Text(item) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val dueAt = parseDateTime(dateText, timeText)

                if (title.isNotBlank() && dueAt != null) {
                    scope.launch {
                        val task = if (existing == null) {
                            TaskEntity(
                                title = title.trim(),
                                dueAt = dueAt,
                                remindAt = dueAt,
                                repeatType = repeatType,
                                priority = priority
                            )
                        } else {
                            existing.copy(
                                title = title.trim(),
                                dueAt = dueAt,
                                remindAt = dueAt,
                                repeatType = repeatType,
                                priority = priority
                            )
                        }

                        if (existing == null) {
                            val id = db.taskDao().insert(task)
                            ReminderScheduler.schedule(
                                context,
                                task.copy(id = id)
                            )
                        } else {
                            ReminderScheduler.cancel(context, existing.id)
                            db.taskDao().update(task)
                            ReminderScheduler.schedule(context, task)
                        }

                        onBack()
                    }
                } else {
                    parseMessage = "请检查任务名称、日期和时间格式"
                }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(
                if (taskId == null) "创建待办并提醒" else "保存修改",
                fontWeight = FontWeight.Bold
            )
        }

        if (existing != null) {
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        ReminderScheduler.cancel(context, existing.id)
                        db.taskDao().delete(existing)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text("删除这个待办")
            }
        }
    }
}

@Composable
private fun FavoritesScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("收藏", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("今天的内容会按你的手账主题轻轻收好。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            EmptyCard("下一版开始：抖音 / B站 / 微信读书 / 网页 → 分享 → 拾光盒。")
        }
    }
}

@Composable
private fun MineScreen(
    prefs: SharedPreferences,
    onAiSettings: () -> Unit
) {
    val context = LocalContext.current
    val notificationsGranted =
        Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("我的", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("拾光盒 · V0.7 心情手账版", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingRow(
                Icons.Outlined.Badge,
                "称呼",
                prefs.getString("name", "我") ?: "我"
            )
        }
        item {
            SettingRow(
                Icons.Outlined.LocationOn,
                "常用城市",
                prefs.getString("city", "") ?: ""
            )
        }
        item {
            SettingRow(
                Icons.Outlined.Schedule,
                "每日总结时间",
                prefs.getString("summary_time", "22:30") ?: "22:30"
            )
        }
        item {
            SettingRow(
                Icons.Outlined.NotificationsNone,
                "通知权限",
                if (notificationsGranted) "已开启" else "未开启，请到系统设置允许通知"
            )
        }
        item {
            SettingRow(Icons.Outlined.Lock, "数据保存", "Room 本地数据库")
        }
        item {
            SettingRow(Icons.Outlined.RestartAlt, "重启后提醒", "会自动恢复未来待办提醒")
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAiSettings)
            ) {
                SettingRow(
                    Icons.Outlined.AutoAwesome,
                    "DeepSeek AI",
                    if (SecureApiKeyStore.exists(context))
                        "已配置 · 点这里测试或更换"
                    else
                        "未配置 · 点这里接入"
                )
            }
        }
    }
}

@Composable
private fun SummaryScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onAiSettings: () -> Unit
) {
    val bounds = remember { todayBounds() }
    val tasks by db.taskDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val notes by db.noteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().observeBetween(bounds.first, bounds.second)
        .collectAsState(initial = emptyList())

    val done = tasks.filter { it.completed }
    val undone = tasks.filter { !it.completed }

    SimpleTopScreen("今日整理", onBack) {
        Text("今天辛苦啦 ☀️", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        AiDailySummarySection(
            db = db,
            completedTasks = done.map { it.title },
            pendingTasks = undone.map { it.title },
            notes = notes.map { it.content },
            favorites = favorites.map { it.title },
            onOpenSettings = onAiSettings
        )

        Spacer(Modifier.height(14.dp))

        SummaryBlock("今天完成了什么", Icons.Outlined.CheckCircle) {
            if (done.isEmpty()) {
                Text("今天还没有勾选完成的任务。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                done.forEach { Text("✓ " + it.title) }
            }
        }

        SummaryBlock("今天记下了什么", Icons.Outlined.EditNote) {
            if (notes.isEmpty()) {
                Text("今天还没有记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("今天一共写了 " + notes.size + " 条记录。")
                notes.take(3).forEach {
                    Text("• " + it.content.take(45))
                }
            }
        }

        SummaryBlock("还没完成", Icons.Outlined.ListAlt) {
            if (undone.isEmpty()) {
                Text("今天的待办都完成啦。", color = MaterialTheme.colorScheme.secondary)
            } else {
                undone.forEach { Text("○ " + it.title) }
            }
        }

        Text(
            "上面保留本地真实汇总作为兜底；DeepSeek 总结只基于这些真实数据生成。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DynamicTaskRow(task: TaskEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (task.completed) Icons.Outlined.CheckCircle
            else Icons.Outlined.RadioButtonUnchecked,
            null,
            tint = if (task.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            task.title,
            color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            textDecoration =
                if (task.completed) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        task.dueAt?.let {
            Text(formatTime(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DynamicNoteRow(note: NoteEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp)
    ) {
        Text(
            formatTime(note.createdAt),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp),
            fontSize = 13.sp
        )
        Text(note.content, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String) {
    WarmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    WarmCard {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun SimpleTopScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "返回")
                }
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Column(content = content)
        }
    }
}

@Composable
private fun SummaryBlock(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard(title, icon, "") { content() }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WarmCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    trailing: String,
    content: @Composable ColumnScope.() -> Unit
) {
    WarmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (trailing.isNotBlank()) {
                Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun TagPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private data class ParsedTask(
    val title: String,
    val date: LocalDate,
    val time: LocalTime
)

private fun parseNaturalTask(input: String): ParsedTask? {
    if (input.isBlank()) return null

    var date = LocalDate.now()

    when {
        input.contains("后天") -> date = date.plusDays(2)
        input.contains("明天") -> date = date.plusDays(1)
    }

    var hour: Int? = null
    var minute = 0

    Regex("(\\d{1,2}):(\\d{2})").find(input)?.let {
        hour = it.groupValues[1].toIntOrNull()
        minute = it.groupValues[2].toIntOrNull() ?: 0
    }

    if (hour == null) {
        val map = mapOf(
            "一" to 1,
            "二" to 2,
            "两" to 2,
            "三" to 3,
            "四" to 4,
            "五" to 5,
            "六" to 6,
            "七" to 7,
            "八" to 8,
            "九" to 9,
            "十" to 10,
            "十一" to 11,
            "十二" to 12
        )

        val token = Regex("([一二两三四五六七八九十]{1,2})点")
            .find(input)
            ?.groupValues
            ?.get(1)

        hour = token?.let { map[it] }

        if (input.contains("半") && hour != null) {
            minute = 30
        }
    }

    if (hour == null) return null

    if ((input.contains("下午") || input.contains("晚上")) && hour!! < 12) {
        hour = hour!! + 12
    }

    if (input.contains("中午") && hour!! < 11) {
        hour = hour!! + 12
    }

    var title = input
        .replace("今天", "")
        .replace("明天", "")
        .replace("后天", "")
        .replace("上午", "")
        .replace("下午", "")
        .replace("中午", "")
        .replace("晚上", "")
        .replace(Regex("\\d{1,2}:\\d{2}"), "")
        .replace(Regex("[一二两三四五六七八九十]{1,2}点(半)?"), "")
        .replace("提醒我", "")
        .replace("记得", "")
        .trim()

    if (title.isBlank()) {
        title = input.trim()
    }

    return ParsedTask(
        title = title,
        date = date,
        time = LocalTime.of(
            hour!!.coerceIn(0, 23),
            minute.coerceIn(0, 59)
        )
    )
}

private fun parseDateTime(dateText: String, timeText: String): Long? {
    return try {
        val date = LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE)
        val time = LocalTime.parse(
            timeText,
            DateTimeFormatter.ofPattern("HH:mm")
        )
        date.atTime(time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

private fun todayBounds(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    val end = LocalDate.now()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli() - 1

    return start to end
}

private fun millisToDate(ms: Long): LocalDate {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun formatTime(ms: Long): String {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatDateTime(ms: Long): String {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}

private fun taskSubtitle(task: TaskEntity): String {
    val due = task.dueAt ?: return "未设置时间"

    val dt = Instant.ofEpochMilli(due)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

    val text = dt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

    return if (task.repeatType == "不重复") {
        text
    } else {
        text + " · " + task.repeatType
    }
}

private fun nextRecurringTask(task: TaskEntity): TaskEntity {
    val due = task.dueAt ?: System.currentTimeMillis()
    val reminder = task.remindAt ?: due
    val zone = ZoneId.systemDefault()

    val dueZoned = Instant.ofEpochMilli(due).atZone(zone)
    val reminderZoned = Instant.ofEpochMilli(reminder).atZone(zone)

    val nextDue = when (task.repeatType) {
        "每天" -> dueZoned.plusDays(1)
        "每周" -> dueZoned.plusWeeks(1)
        "每月" -> dueZoned.plusMonths(1)
        else -> dueZoned
    }

    val nextReminder = when (task.repeatType) {
        "每天" -> reminderZoned.plusDays(1)
        "每周" -> reminderZoned.plusWeeks(1)
        "每月" -> reminderZoned.plusMonths(1)
        else -> reminderZoned
    }

    return task.copy(
        id = 0,
        dueAt = nextDue.toInstant().toEpochMilli(),
        remindAt = nextReminder.toInstant().toEpochMilli(),
        completed = false,
        completedAt = null,
        createdAt = System.currentTimeMillis()
    )
}
