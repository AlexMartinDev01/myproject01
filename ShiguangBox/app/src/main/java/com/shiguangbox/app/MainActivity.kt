package com.shiguangbox.app

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

private val Cream = Color(0xFFFFF9F0)
private val WarmOrange = Color(0xFFF4B860)
private val Sage = Color(0xFFA8B99A)
private val DarkBrown = Color(0xFF49392C)
private val Muted = Color(0xFF8A7969)
private val CardColor = Color(0xFFFFFDFC)
private val PaleOrange = Color(0xFFFFEBC7)
private val PaleGreen = Color(0xFFEAF1E4)

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
        setContent { ShiguangBoxApp() }
    }
}

@Composable
fun ShiguangBoxApp() {
    var onboardingDone by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WarmOrange,
            secondary = Sage,
            background = Cream,
            surface = CardColor,
            onPrimary = Color.White,
            onBackground = DarkBrown,
            onSurface = DarkBrown
        )
    ) {
        if (!onboardingDone) {
            SetupScreen(onStart = { onboardingDone = true })
        } else {
            MainShell()
        }
    }
}

@Composable
private fun SetupScreen(onStart: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("苏打水") }
    var city by rememberSaveable { mutableStateOf("西安") }
    var summaryTime by rememberSaveable { mutableStateOf("22:30") }

    Surface(color = Cream) {
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
                        .background(PaleOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Inventory2, null, tint = WarmOrange, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("把生活慢慢收进来", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DarkBrown)
                Spacer(Modifier.height(8.dp))
                Text("记录想法、安排事情、收藏好内容，\n剩下的交给我整理。", color = Muted, lineHeight = 24.sp)
            }

            item { SetupField("你的称呼", "这会是你在拾光盒里的名字", name) { name = it } }
            item { SetupField("常用城市", "用于天气与本地提醒", city) { city = it } }
            item { SetupField("每日总结时间", "到点提醒你回顾今天", summaryTime) { summaryTime = it } }

            item {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("开始使用", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ArrowForward, null)
                }
                Spacer(Modifier.height(8.dp))
                Text("第一版数据保存在本机，更轻更安心。", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SetupField(title: String, hint: String, value: String, onValue: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = DarkBrown)
            Text(hint, color = Muted, fontSize = 13.sp)
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
}

@Composable
private fun MainShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            if (currentRoute in navItems.map { it.route }) {
                NavigationBar(containerColor = CardColor) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
                            navController.navigate("favorites")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    FloatingActionButton(
                        onClick = { quickAddOpen = !quickAddOpen },
                        containerColor = Sage,
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
                    onSummary = { navController.navigate("summary") },
                    onQuickNote = { navController.navigate("quick_note") },
                    onNewTask = { navController.navigate("new_task") }
                )
            }
            composable("notes") { NotesScreen(onNew = { navController.navigate("quick_note") }) }
            composable("tasks") { TasksScreen(onNew = { navController.navigate("new_task") }) }
            composable("favorites") { FavoritesScreen() }
            composable("mine") { MineScreen() }
            composable("quick_note") { QuickNoteScreen(onBack = { navController.popBackStack() }) }
            composable("new_task") { NewTaskScreen(onBack = { navController.popBackStack() }) }
            composable("summary") { SummaryScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = CardColor,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = WarmOrange)
            Spacer(Modifier.width(8.dp))
            Text(label, color = DarkBrown)
        }
    }
}

@Composable
private fun TodayScreen(onSummary: () -> Unit, onQuickNote: () -> Unit, onNewTask: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("下午好，苏打水 ☀️", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkBrown)
            Text("9月22日 · 星期二", color = Muted)
        }

        item {
            WarmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbSunny, null, tint = WarmOrange, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("24°C  晴", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("18～27°C · 下午有点晒", color = Muted)
                        Spacer(Modifier.height(6.dp))
                        Text("今天适合出门，记得防晒。", color = DarkBrown)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "今天要做",
                icon = Icons.Outlined.Checklist,
                trailing = "3/5 已完成"
            ) {
                TaskRow(true, "完成组会 PPT")
                TaskRow(true, "回复咨询消息")
                TaskRow(false, "18:00 直播")
                TaskRow(false, "整理视频素材")
                TextButton(onClick = onNewTask) { Text("＋ 新建待办") }
            }
        }

        item {
            SectionCard(title = "今天记下了", icon = Icons.Outlined.EditNote, trailing = "4 条") {
                NoteRow("10:38", "想到一期视频：0 实习到底应该先补什么？")
                NoteRow("14:12", "导师让我看看 MoS₂ 的相关论文。")
                TextButton(onClick = onQuickNote) { Text("＋ 记一下") }
            }
        }

        item {
            SectionCard(title = "今天收进来了", icon = Icons.Outlined.Bookmarks, trailing = "4 条") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagPill("B站 ×2")
                    TagPill("抖音 ×1")
                    TagPill("微信读书 ×1")
                }
                Spacer(Modifier.height(12.dp))
                Text("为什么大学生找工作越来越难？", fontWeight = FontWeight.Bold)
                Text("就业 · 大学生 · 职业规划", color = Muted, fontSize = 13.sp)
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
                Text("总结我的今天", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Text(
                "根据今天的记录、待办与收藏生成总结",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun NotesScreen(onNew: () -> Unit) {
    val notes = listOf(
        "今天第一次直播比想象中顺利，后半段慢慢找到节奏了。",
        "想到一期新视频：0实习到底应该先补什么。",
        "导师让我继续看看 MoS₂ 的相关文章。",
        "今天咨询里反复出现“没有项目怎么办”这个问题。"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("记录", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("把此刻的想法，慢慢收进来。", color = Muted)
        }
        items(notes) { note ->
            WarmCard {
                Text(note, color = DarkBrown, lineHeight = 22.sp)
                Spacer(Modifier.height(10.dp))
                Row {
                    TagPill("生活")
                    Spacer(Modifier.width(8.dp))
                    Text("今天", color = Muted, fontSize = 12.sp)
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
private fun TasksScreen(onNew: () -> Unit) {
    val tasks = remember {
        mutableStateListOf(
            Pair("完成组会 PPT", true),
            Pair("18:00 直播", false),
            Pair("回复咨询消息", true),
            Pair("整理视频素材", false),
            Pair("阅读 1 篇论文", false)
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("待办 ☀️", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("把想做的事，一件件变成真实的生活。", color = Muted)
        }
        items(tasks.indices.toList()) { index ->
            val item = tasks[index]
            WarmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.second,
                        onCheckedChange = { checked -> tasks[index] = item.copy(second = checked) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.first, fontWeight = FontWeight.SemiBold)
                        Text(if (item.first.contains("18:00")) "今天 18:00" else "今天", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.MoreHoriz, null, tint = Muted)
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
private fun FavoritesScreen() {
    val favorites = listOf(
        Triple("普通人怎么做好第一次直播？", "抖音", "直播 · 自媒体 · 表达"),
        Triple("从零理解 Transformer Attention", "B站", "AI · 深度学习 · Transformer"),
        Triple("纳瓦尔宝典", "微信读书", "成长 · 思考")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("收藏", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("把有价值的内容，留在时光里 🌿", color = Muted)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索我的收藏") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                readOnly = true,
                shape = RoundedCornerShape(18.dp)
            )
        }
        items(favorites) { item ->
            WarmCard {
                Text(item.first, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(item.second, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("AI 摘要：这是一条示例收藏，后续阶段会接入真实分享与 AI 自动整理。", color = DarkBrown)
                Spacer(Modifier.height(10.dp))
                Text(item.third, color = WarmOrange, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MineScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Cream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("我的", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("拾光盒 · V0.1 原型版", color = Muted)
        }
        item { SettingRow(Icons.Outlined.LocationOn, "常用城市", "西安") }
        item { SettingRow(Icons.Outlined.Schedule, "每日总结时间", "22:30") }
        item { SettingRow(Icons.Outlined.NotificationsNone, "通知提醒", "已开启") }
        item { SettingRow(Icons.Outlined.Lock, "数据保存", "当前仅保存在本机") }
        item { SettingRow(Icons.Outlined.AutoAwesome, "AI 整理", "下一阶段接入") }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String) {
    WarmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Sage)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(value, color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun QuickNoteScreen(onBack: () -> Unit) {
    var text by rememberSaveable {
        mutableStateOf("今天第一次直播比想象中顺利，但是开场还是有一点紧张。")
    }
    SimpleTopScreen("记录", onBack) {
        Text("现在在想什么？", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("把此刻的想法，放进拾光盒吧。", color = Muted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(190.dp),
            shape = RoundedCornerShape(20.dp)
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagPill("生活")
            TagPill("工作")
            TagPill("学习")
            TagPill("灵感")
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("保存", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NewTaskScreen(onBack: () -> Unit) {
    var naturalText by rememberSaveable { mutableStateOf("明天下午三点提醒我给老师发材料") }
    SimpleTopScreen("新建待办", onBack) {
        Text("要做什么？", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("用自然的语言告诉我，我来帮你整理。", color = Muted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = naturalText,
            onValueChange = { naturalText = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(14.dp))
        SectionCard(title = "系统识别", icon = Icons.Outlined.AutoAwesome, trailing = "") {
            InfoLine("任务内容", "给老师发材料")
            InfoLine("日期", "明天")
            InfoLine("时间", "15:00")
            InfoLine("提醒", "到点提醒")
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("创建待办", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryScreen(onBack: () -> Unit) {
    SimpleTopScreen("AI 今日总结", onBack) {
        Text("今天辛苦啦 ☀️", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("2026年9月22日 · 星期二", color = Muted)
        Spacer(Modifier.height(16.dp))

        SummaryBlock("今天完成了什么", Icons.Outlined.CheckCircle) {
            Text("今天共完成 3 项任务，主要集中在学习、自媒体和咨询工作。")
        }
        SummaryBlock("今天发生了什么", Icons.Outlined.EditNote) {
            Text("今天的记录里，直播准备和研究生学习出现得最多，整体节奏比较充实。")
        }
        SummaryBlock("今天学到了什么", Icons.Outlined.Lightbulb) {
            Text("收藏内容主要集中在直播表达与 AI 学习，有两条内容值得之后继续深入整理。")
        }
        SummaryBlock("明天可以先做", Icons.Outlined.ListAlt) {
            Text("• 整理视频素材\n• 阅读并整理 1 篇 MoS₂ 相关论文")
        }

        Spacer(Modifier.height(10.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("保存为今日日记")
        }
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("复制今日总结")
        }
    }
}

@Composable
private fun SimpleTopScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Column(content = content)
    }
}

@Composable
private fun SummaryBlock(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title, icon, "") { content() }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Muted, modifier = Modifier.width(84.dp))
        Text(value, color = DarkBrown, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WarmCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
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
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(PaleGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Sage)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (trailing.isNotBlank()) Text(trailing, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun TaskRow(done: Boolean, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            null,
            tint = if (done) Sage else Muted
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (done) Muted else DarkBrown)
    }
}

@Composable
private fun NoteRow(time: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(time, color = Muted, modifier = Modifier.width(56.dp), fontSize = 13.sp)
        Text(text, color = DarkBrown)
    }
}

@Composable
private fun TagPill(text: String) {
    Surface(
        color = PaleOrange,
        shape = RoundedCornerShape(50)
    ) {
        Text(text, color = DarkBrown, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
