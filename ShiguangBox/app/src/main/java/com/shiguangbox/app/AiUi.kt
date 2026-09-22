package com.shiguangbox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val AiCream = Color(0xFFFFF9F0)
private val AiWarm = Color(0xFFF4B860)
private val AiSage = Color(0xFFA8B99A)
private val AiDark = Color(0xFF49392C)
private val AiMuted = Color(0xFF8A7969)
private val AiCard = Color(0xFFFFFDFC)

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(SecureApiKeyStore.exists(context)) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var success by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AiCream)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, "返回")
            }
            Text("DeepSeek AI", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = AiCard),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = AiWarm)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("AI 接口设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (saved) "本机已保存一个 API Key" else "还没有保存 API Key",
                            color = AiMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (saved) "输入新 Key 可替换现有 Key" else "DeepSeek API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation =
                        if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff
                                else Icons.Outlined.Visibility,
                                "显示或隐藏"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (key.isNotBlank()) {
                            SecureApiKeyStore.save(context, key)
                            key = ""
                            saved = true
                            success = true
                            message = "已加密保存在这台手机上"
                        }
                    },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("安全保存")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val apiKey = SecureApiKeyStore.load(context)
                        if (apiKey.isNullOrBlank()) {
                            success = false
                            message = "请先保存 API Key"
                            return@OutlinedButton
                        }

                        testing = true
                        message = ""

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DeepSeekClient.testConnection(apiKey)
                            }
                            testing = false
                            success = result.success
                            message =
                                if (result.success)
                                    "连接成功 · 模型：" + DeepSeekClient.MODEL
                                else
                                    result.error
                        }
                    },
                    enabled = saved && !testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.WifiTethering, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (testing) "正在测试…" else "测试连接")
                }

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        color = if (success) AiSage else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = AiCard),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("这一版 AI 能做什么", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("• 整理收藏内容", color = AiDark)
                Text("• 根据真实记录生成每日总结", color = AiDark)
                Text("• 严格基于已有文字，不假装看过拿不到正文的视频", color = AiDark)
                Spacer(Modifier.height(12.dp))
                Text(
                    "模型：" + DeepSeekClient.MODEL + " · 非思考模式，优先速度和成本。",
                    color = AiMuted,
                    fontSize = 12.sp
                )
            }
        }

        if (saved) {
            Spacer(Modifier.height(14.dp))
            TextButton(
                onClick = {
                    SecureApiKeyStore.clear(context)
                    saved = false
                    success = true
                    message = "已从本机移除 API Key"
                }
            ) {
                Icon(Icons.Outlined.DeleteOutline, null)
                Spacer(Modifier.width(6.dp))
                Text("移除本机 API Key")
            }
        }
    }
}

@Composable
fun AiDailySummarySection(
    db: AppDatabase,
    completedTasks: List<String>,
    pendingTasks: List<String>,
    notes: List<String>,
    favorites: List<String>,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateKey = remember {
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val saved by db.dailySummaryDao()
        .observeByDate(dateKey)
        .collectAsState(initial = null)

    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = AiCard),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = AiWarm)
                Spacer(Modifier.width(8.dp))
                Text("DeepSeek 今日总结", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }

            Spacer(Modifier.height(12.dp))

            val summary = saved
            if (summary != null) {
                Text(
                    summary.content,
                    color = AiDark,
                    lineHeight = 23.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val key = SecureApiKeyStore.load(context)
                        if (key.isNullOrBlank()) {
                            onOpenSettings()
                            return@OutlinedButton
                        }

                        loading = true
                        error = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DeepSeekClient.dailySummary(
                                    apiKey = key,
                                    date = dateKey,
                                    completedTasks = completedTasks,
                                    pendingTasks = pendingTasks,
                                    notes = notes,
                                    favorites = favorites
                                )
                            }
                            loading = false
                            if (result.success) {
                                db.dailySummaryDao().upsert(
                                    DailySummaryEntity(
                                        dateKey = dateKey,
                                        content = result.content
                                    )
                                )
                            } else {
                                error = result.error
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "正在重新整理…" else "重新生成")
                }
            } else {
                Text(
                    "把今天真实完成的任务、记录和收藏交给 AI 整理成一份日记/工作总结。",
                    color = AiMuted
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val key = SecureApiKeyStore.load(context)
                        if (key.isNullOrBlank()) {
                            onOpenSettings()
                            return@Button
                        }

                        loading = true
                        error = ""
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DeepSeekClient.dailySummary(
                                    apiKey = key,
                                    date = dateKey,
                                    completedTasks = completedTasks,
                                    pendingTasks = pendingTasks,
                                    notes = notes,
                                    favorites = favorites
                                )
                            }
                            loading = false
                            if (result.success) {
                                db.dailySummaryDao().upsert(
                                    DailySummaryEntity(
                                        dateKey = dateKey,
                                        content = result.content
                                    )
                                )
                            } else {
                                error = result.error
                            }
                        }
                    },
                    enabled = !loading,
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
                    Text(if (loading) "正在整理…" else "AI 生成今日总结")
                }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}
