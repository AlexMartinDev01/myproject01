package com.shiguangbox.app

import kotlin.random.Random

enum class PetLifeEvent {
    SILENT_ACTION,
    CHECK_IN,
    MISS_YOU,
    TASK_NUDGE,
    MOOD_COMPANY,
    PROUD_OF_YOU,
    AFTER_TASK,
    WELCOME_BACK
}

data class PetClinginess(
    val level: Int,
    val label: String,
    val minDelayMs: Long,
    val maxDelayMs: Long,
    val missThresholdMs: Long,
    val bubbleChance: Float,
    val bubbleCooldownMs: Long
)

data class PetLifeContext(
    val petKind: PetKind,
    val clinginess: PetClinginess,
    val idleMs: Long,
    val hour: Int,
    val pendingTasks: Int,
    val completedTasks: Int,
    val mood: String,
    val affection: Int,
    val quietNight: Boolean
)

object PetLifeEngine {

    fun clinginess(
        level: Int
    ): PetClinginess =
        when (
            level.coerceIn(0, 3)
        ) {
            0 ->
                PetClinginess(
                    level = 0,
                    label = "安静",
                    minDelayMs = minutes(35),
                    maxDelayMs = minutes(65),
                    missThresholdMs = minutes(120),
                    bubbleChance = 0.18f,
                    bubbleCooldownMs = minutes(42)
                )

            2 ->
                PetClinginess(
                    level = 2,
                    label = "粘人",
                    minDelayMs = minutes(7),
                    maxDelayMs = minutes(15),
                    missThresholdMs = minutes(22),
                    bubbleChance = 0.65f,
                    bubbleCooldownMs = minutes(8)
                )

            3 ->
                PetClinginess(
                    level = 3,
                    label = "超粘人",
                    minDelayMs = minutes(3),
                    maxDelayMs = minutes(8),
                    missThresholdMs = minutes(10),
                    bubbleChance = 0.82f,
                    bubbleCooldownMs = minutes(4)
                )

            else ->
                PetClinginess(
                    level = 1,
                    label = "陪伴",
                    minDelayMs = minutes(15),
                    maxDelayMs = minutes(28),
                    missThresholdMs = minutes(50),
                    bubbleChance = 0.42f,
                    bubbleCooldownMs = minutes(16)
                )
        }

    fun nextDelayMs(
        level: Int,
        petKind: PetKind,
        quietNight: Boolean
    ): Long {
        if (quietNight) {
            return Random.nextLong(
                minutes(38),
                minutes(68) + 1L
            )
        }

        val config =
            clinginess(level)

        val personalityScale =
            when (petKind) {
                PetKind.ORANGE -> 0.86
                PetKind.YAYA -> 1.16
                PetKind.YUTUAN -> 1.00
            }

        val minDelay =
            (
                config.minDelayMs *
                    personalityScale
                )
                .toLong()
                .coerceAtLeast(
                    minutes(2)
                )

        val maxDelay =
            (
                config.maxDelayMs *
                    personalityScale
                )
                .toLong()
                .coerceAtLeast(
                    minDelay + 1L
                )

        return Random.nextLong(
            minDelay,
            maxDelay + 1L
        )
    }

    fun chooseEvent(
        context: PetLifeContext
    ): PetLifeEvent {
        if (context.quietNight) {
            return PetLifeEvent
                .SILENT_ACTION
        }

        val config =
            context.clinginess

        val idleRatio =
            context.idleMs
                .toDouble() /
                config
                    .missThresholdMs
                    .coerceAtLeast(1L)
                    .toDouble()

        if (
            idleRatio >= 1.0 &&
            Random.nextFloat() <
            when (context.petKind) {
                PetKind.ORANGE -> 0.78f
                PetKind.YAYA -> 0.48f
                PetKind.YUTUAN -> 0.64f
            }
        ) {
            return PetLifeEvent
                .MISS_YOU
        }

        if (
            context.completedTasks >= 2 &&
            Random.nextFloat() <
            0.20f
        ) {
            return PetLifeEvent
                .PROUD_OF_YOU
        }

        if (
            context.pendingTasks > 0 &&
            context.hour in 8..21 &&
            Random.nextFloat() <
            0.28f
        ) {
            return PetLifeEvent
                .TASK_NUDGE
        }

        if (
            context.mood in
            setOf(
                "tired",
                "sad",
                "quiet"
            ) &&
            Random.nextFloat() <
            0.34f
        ) {
            return PetLifeEvent
                .MOOD_COMPANY
        }

        return if (
            Random.nextFloat() <
            config.bubbleChance
        ) {
            PetLifeEvent
                .CHECK_IN
        } else {
            PetLifeEvent
                .SILENT_ACTION
        }
    }

    fun titleSuffix(
        event: PetLifeEvent
    ): String =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                "来找你啦"
            PetLifeEvent.TASK_NUDGE ->
                "偷偷提醒"
            PetLifeEvent.MOOD_COMPANY ->
                "陪着你"
            PetLifeEvent.PROUD_OF_YOU ->
                "看到啦"
            PetLifeEvent.AFTER_TASK ->
                "刚才那件事"
            PetLifeEvent.WELCOME_BACK ->
                "欢迎回来"
            else ->
                "陪伴"
        }

    fun message(
        context: PetLifeContext,
        event: PetLifeEvent
    ): String {
        val warm =
            context.affection >= 18

        return when (context.petKind) {
            PetKind.ORANGE ->
                orangeMessage(
                    context,
                    event,
                    warm
                )

            PetKind.YAYA ->
                yayaMessage(
                    context,
                    event,
                    warm
                )

            PetKind.YUTUAN ->
                yutuanMessage(
                    context,
                    event,
                    warm
                )
        }
    }

    private fun orangeMessage(
        context: PetLifeContext,
        event: PetLifeEvent,
        warm: Boolean
    ): String =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                pick(
                    if (warm) {
                        listOf(
                            "你都好久没理我啦～我刚刚一直偷偷看你。",
                            "终于有机会来找你啦，摸摸我嘛。",
                            "我在旁边等了好一会儿，你忙完一点了吗？"
                        )
                    } else {
                        listOf(
                            "忙完了吗～我来露个脸。",
                            "嘿，我还在这里呢。",
                            "休息十秒也行，看看我嘛。"
                        )
                    }
                )

            PetLifeEvent.TASK_NUDGE ->
                pick(
                    listOf(
                        "盒子里还有 ${context.pendingTasks} 件事，要不要先拿最小的一件开工？",
                        "还有 ${context.pendingTasks} 件待办，我陪你先做一件。",
                        "偷偷提醒一下～今天还有 ${context.pendingTasks} 件事没收好。"
                    )
                )

            PetLifeEvent.MOOD_COMPANY ->
                pick(
                    listOf(
                        "今天状态一般也没关系，我就在这儿陪你。",
                        "累的话先松一会儿，我不催你。",
                        "今天慢一点也行，等你想动的时候再动。"
                    )
                )

            PetLifeEvent.PROUD_OF_YOU ->
                pick(
                    listOf(
                        "我可看见啦，你今天已经完成 ${context.completedTasks} 件事了！",
                        "今天已经收掉 ${context.completedTasks} 件啦，挺能干嘛～",
                        "又做完不少事，我先替你开心一下！"
                    )
                )

            PetLifeEvent.AFTER_TASK ->
                pick(
                    listOf(
                        "刚才那件事做完以后，是不是轻松一点啦？",
                        "刚刚完成一件事，我还记得呢～",
                        "那件事已经收好啦，先给自己一点小奖励吧。"
                    )
                )

            PetLifeEvent.WELCOME_BACK ->
                pick(
                    listOf(
                        "你回来啦！我刚刚还在等你。",
                        "终于又看到你啦～",
                        "回来啦回来啦，我还在这儿！"
                    )
                )

            else ->
                pick(
                    listOf(
                        "我过来晃一下～你继续忙你的。",
                        "路过你的屏幕，顺便看看你。",
                        "今天也要记得给自己留一点开心呀。"
                    )
                )
        }

    private fun yayaMessage(
        context: PetLifeContext,
        event: PetLifeEvent,
        warm: Boolean
    ): String =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                pick(
                    if (warm) {
                        listOf(
                            "安静了好一会儿，我还是想来看看你。",
                            "你忙的时候我一直在旁边，不急着回应我。",
                            "好久没和你说话啦，我来陪你待一会儿。"
                        )
                    } else {
                        listOf(
                            "忙了挺久吧，我来安静地陪你一会儿。",
                            "我还在这里，慢慢来就好。",
                            "不用停下来，我只是来看看你。"
                        )
                    }
                )

            PetLifeEvent.TASK_NUDGE ->
                pick(
                    listOf(
                        "今天还有 ${context.pendingTasks} 件事，先做最容易开始的那件吧。",
                        "盒子里还有 ${context.pendingTasks} 件待办，不用一起想完。",
                        "要不要只选一件最小的事，先往前一点点？"
                    )
                )

            PetLifeEvent.MOOD_COMPANY ->
                pick(
                    listOf(
                        "今天不用急着调整状态，我陪你慢一点。",
                        "累的时候，也可以只是安静待一会儿。",
                        "不需要马上变好，先照顾好现在的自己。"
                    )
                )

            PetLifeEvent.PROUD_OF_YOU ->
                pick(
                    listOf(
                        "今天已经完成 ${context.completedTasks} 件事了，做得很稳。",
                        "你已经往前走了 ${context.completedTasks} 步，可以稍微松一松。",
                        "今天做完的事情，我都看见啦。"
                    )
                )

            PetLifeEvent.AFTER_TASK ->
                pick(
                    listOf(
                        "刚才那件事已经完成啦，现在可以稍微松一点。",
                        "我还记得你刚刚做完了一件事，辛苦啦。",
                        "完成以后别急着马上赶下一件，先停一下也好。"
                    )
                )

            PetLifeEvent.WELCOME_BACK ->
                pick(
                    listOf(
                        "欢迎回来，刚刚忙完吗？",
                        "你回来啦，我还在这里。",
                        "又见到你啦，慢慢坐下来吧。"
                    )
                )

            else ->
                pick(
                    listOf(
                        "我就在这里，不打扰你。",
                        "慢一点也没关系。",
                        "今天也陪你安安静静待一会儿。"
                    )
                )
        }

    private fun yutuanMessage(
        context: PetLifeContext,
        event: PetLifeEvent,
        warm: Boolean
    ): String =
        when (event) {
            PetLifeEvent.MISS_YOU ->
                pick(
                    if (warm) {
                        listOf(
                            "这里安静了好久，雨声都变轻了。",
                            "好久没听见你啦，我把雨下小一点等你。",
                            "你忙了很久吧，我还在这片小雨里等你。"
                        )
                    } else {
                        listOf(
                            "今天这里有点安静，我来看看你。",
                            "雨下了一会儿，你还在忙吗？",
                            "我把雨声放轻一点，不吵你。"
                        )
                    }
                )

            PetLifeEvent.TASK_NUDGE ->
                pick(
                    listOf(
                        "雨点数着呢，今天还有 ${context.pendingTasks} 件事。",
                        "还有 ${context.pendingTasks} 件事没收好，挑一件最小的开始吧。",
                        "先做一件吧，做完也许就会放晴一点。"
                    )
                )

            PetLifeEvent.MOOD_COMPANY ->
                pick(
                    listOf(
                        "今天如果有点累，我就陪你下一会儿很轻的雨。",
                        "不想说话也没关系，听一会儿雨就好。",
                        "今天不用很有精神，我安静陪着你。"
                    )
                )

            PetLifeEvent.PROUD_OF_YOU ->
                pick(
                    listOf(
                        "今天已经完成 ${context.completedTasks} 件事啦，云好像轻了一点。",
                        "你做完不少事情了，今天可能会有一点晴。",
                        "我都看见啦，雨也替你开心了一点。"
                    )
                )

            PetLifeEvent.AFTER_TASK ->
                pick(
                    listOf(
                        "刚才那件事做完以后，雨好像也轻了一点。",
                        "我还记得你刚刚完成的那件事，云都松了一点。",
                        "做完一件事以后，听一会儿小雨再继续吧。"
                    )
                )

            PetLifeEvent.WELCOME_BACK ->
                pick(
                    listOf(
                        "你回来啦，刚刚这里下了一会儿小雨。",
                        "又见到你啦，雨声刚好变轻。",
                        "你回来以后，好像没那么阴啦。"
                    )
                )

            else ->
                pick(
                    listOf(
                        "我在听雨，也顺便陪着你。",
                        "今天的雨很轻，不会打扰你。",
                        "我就在这片小雨里待着。"
                    )
                )
        }

    private fun pick(
        lines: List<String>
    ): String =
        lines[
            Random.nextInt(
                lines.size
            )
        ]

    private fun minutes(
        value: Long
    ): Long =
        value *
            60L *
            1000L
}
