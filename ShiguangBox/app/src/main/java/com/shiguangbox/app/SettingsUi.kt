package com.shiguangbox.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.time.LocalDate

private val SettingsCream = Color(0xFFFFF9F0)
private val SettingsCard = Color(0xFFFFFDFC)
private val SettingsMuted = Color(0xFF8A7969)
private val SettingsSage = Color(0xFFA8B99A)

@Composable
fun SettingsScreen(
    db: AppDatabase,
    onAiSettings: () -> Unit,
    onJournalTheme: () -> Unit,
    onPetSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("shiguangbox_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable {
        mutableStateOf(prefs.getString("name", "我") ?: "我")
    }
    var city by rememberSaveable {
        mutableStateOf(prefs.getString("city", "") ?: "")
    }
    var summaryTime by rememberSaveable {
        mutableStateOf(prefs.getString("summary_time", "22:30") ?: "22:30")
    }
    var autoSummary by rememberSaveable {
        mutableStateOf(prefs.getBoolean("auto_summary", false))
    }
    var autoFavoriteAi by rememberSaveable {
        mutableStateOf(prefs.getBoolean("auto_favorite_ai", false))
    }

    var saveMessage by rememberSaveable { mutableStateOf("") }
    var exporting by rememberSaveable { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exporting = true
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    BackupUtils.buildBackup(context, db)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(data.toByteArray(Charsets.UTF_8))
                    }
                }
                exporting = false
                saveMessage = "备份已导出"
            }
        }
    }

    fun saveSettings() {
        prefs.edit()
            .putString("name", name.ifBlank { "我" })
            .putString("city", city.trim())
            .putString("summary_time", summaryTime.ifBlank { "22:30" })
            .putBoolean("auto_summary", autoSummary)
            .putBoolean("auto_favorite_ai", autoFavoriteAi)
            .apply()

        DailySummaryScheduler.schedule(
            context,
            summaryTime.ifBlank { "22:30" }
        )

        saveMessage = "设置已保存"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsCream),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("我的", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("拾光盒 · V1.1 橘团行为状态机版", color = SettingsMuted)
        }

        item {
            val journal = LocalJournalTheme.current
            SettingsCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(journal.stickerEmoji, fontSize = 32.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("今天的手账", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            journal.name + " · " + journal.moodEmoji + " " + journal.moodLabel,
                            color = SettingsMuted,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onJournalTheme) {
                        Text("换风格")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    journal.tagline,
                    color = SettingsMuted,
                    fontSize = 13.sp
                )
            }
        }

        item {
            SettingsCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Pets, null, tint = SettingsSage)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("我的桌宠 · 橘团", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "悬浮提醒、快速记事、快速加待办",
                            color = SettingsMuted,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onPetSettings) {
                        Text("设置")
                    }
                }
            }
        }

        item {
            SettingsCardBox {
                Text("基础设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("称呼") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("常用城市") },
                    supportingText = { Text("用于首页实时天气") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = summaryTime,
                    onValueChange = { summaryTime = it },
                    label = { Text("每日总结时间") },
                    supportingText = { Text("格式例如 22:30") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { saveSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存基础设置")
                }

                if (saveMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(saveMessage, color = SettingsSage, fontSize = 13.sp)
                }
            }
        }

        item {
            SettingsCardBox {
                Text("自动整理", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("到点自动生成今日总结", fontWeight = FontWeight.Medium)
                        Text(
                            "需要已经配置 DeepSeek API Key",
                            color = SettingsMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoSummary,
                        onCheckedChange = {
                            autoSummary = it
                            saveSettings()
                        }
                    )
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("收藏/图文后自动生成知识卡", fontWeight = FontWeight.Medium)
                        Text(
                            "文字、网页或图片收进来后，在后台生成摘要、标签和专题建议",
                            color = SettingsMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoFavoriteAi,
                        onCheckedChange = {
                            autoFavoriteAi = it
                            saveSettings()
                        }
                    )
                }
            }
        }

        item {
            SettingsCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = SettingsSage)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DeepSeek AI", fontWeight = FontWeight.Bold)
                        Text(
                            if (SecureApiKeyStore.exists(context))
                                "已配置 · 模型 " + DeepSeekClient.MODEL
                            else
                                "尚未配置 API Key",
                            color = SettingsMuted,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onAiSettings) {
                        Text("设置")
                    }
                }
            }
        }

        item {
            SettingsCardBox {
                Text("数据与备份", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "导出记录、待办、收藏、AI摘要、每日总结和基础设置。API Key 不会被导出。",
                    color = SettingsMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val fileName =
                            "拾光盒备份_" + LocalDate.now().toString() + ".json"
                        backupLauncher.launch(fileName)
                    },
                    enabled = !exporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (exporting) "正在导出…" else "导出我的拾光盒")
                }
            }
        }

        item {
            SettingsCardBox {
                Text("隐私说明", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "记录、待办、收藏和导入图片默认保存在本机。启用 AI 整理时，对应文字与所选图片会发送到你配置的 DeepSeek 接口；API Key 不会包含在数据备份中。",
                    color = SettingsMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsCardBox(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SettingsCard),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            content = content
        )
    }
}
