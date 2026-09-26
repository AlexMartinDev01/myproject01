package com.shiguangbox.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

enum class PetWorldEventType {
    QUIET_OBSERVE,
    STRETCH_BREAK,
    DAYDREAM,
    SEEK_TOUCH,
    CHECK_TASKS,
    INVITE_NOTE,
    INVITE_MOOD,
    TASK_MEMORY,
    MOOD_COMPANY,
    RARE_DISCOVERY
}

enum class PetEventRarity {
    COMMON,
    UNCOMMON,
    RARE,
    ULTRA_RARE
}

enum class PetDiscovery(
    val id: String,
    val displayName: String,
    val emoji: String,
    val owner: PetKind,
    val rarity: PetEventRarity
) {
    ORANGE_STAR(
        id = "orange_star",
        displayName = "橘团捡到的小星星",
        emoji = "⭐",
        owner = PetKind.ORANGE,
        rarity = PetEventRarity.RARE
    ),
    ORANGE_BUTTON(
        id = "orange_button",
        displayName = "橘团找到的小纽扣",
        emoji = "🟠",
        owner = PetKind.ORANGE,
        rarity = PetEventRarity.UNCOMMON
    ),
    ORANGE_FEATHER(
        id = "orange_feather",
        displayName = "橘团追到的小羽毛",
        emoji = "🪶",
        owner = PetKind.ORANGE,
        rarity = PetEventRarity.ULTRA_RARE
    ),
    YAYA_LEAF(
        id = "yaya_leaf",
        displayName = "芽芽收好的小叶子",
        emoji = "🍃",
        owner = PetKind.YAYA,
        rarity = PetEventRarity.UNCOMMON
    ),
    YAYA_PETAL(
        id = "yaya_petal",
        displayName = "芽芽遇见的小花瓣",
        emoji = "🌸",
        owner = PetKind.YAYA,
        rarity = PetEventRarity.RARE
    ),
    YAYA_SEED(
        id = "yaya_seed",
        displayName = "芽芽藏起来的小种子",
        emoji = "🌱",
        owner = PetKind.YAYA,
        rarity = PetEventRarity.ULTRA_RARE
    ),
    YUTUAN_DROP(
        id = "yutuan_drop",
        displayName = "雨团留下的小水珠",
        emoji = "💧",
        owner = PetKind.YUTUAN,
        rarity = PetEventRarity.UNCOMMON
    ),
    YUTUAN_CLOUD(
        id = "yutuan_cloud",
        displayName = "雨团收藏的小云朵",
        emoji = "☁️",
        owner = PetKind.YUTUAN,
        rarity = PetEventRarity.RARE
    ),
    YUTUAN_RAINBOW(
        id = "yutuan_rainbow",
        displayName = "雨团记住的小彩虹",
        emoji = "🌈",
        owner = PetKind.YUTUAN,
        rarity = PetEventRarity.ULTRA_RARE
    );

    companion object {
        fun fromId(
            id: String
        ): PetDiscovery? =
            entries.firstOrNull {
                it.id == id
            }
    }
}

enum class PetBondStage(
    val label: String,
    val description: String
) {
    NEW(
        label = "初识",
        description = "它还在慢慢认识你的节奏"
    ),
    FAMILIAR(
        label = "熟悉",
        description = "已经开始记住你常见的互动方式"
    ),
    CLOSE(
        label = "亲近",
        description = "会更自然地回应最近发生过的事情"
    ),
    IN_SYNC(
        label = "默契",
        description = "很多陪伴不需要说太多也能接得上"
    )
}

data class PetMemory(
    val id: String,
    val type: String,
    val petId: String,
    val title: String,
    val detail: String,
    val createdAt: Long
)

data class PetWorldContext(
    val petKind: PetKind,
    val clinginessLevel: Int,
    val hour: Int,
    val pendingTasks: Int,
    val completedTasks: Int,
    val mood: String,
    val affection: Int,
    val idleMs: Long,
    val quietNight: Boolean
)

data class PetWorldEvent(
    val id: String,
    val type: PetWorldEventType,
    val title: String,
    val message: String,
    val sequence: PetBehaviorSequence,
    val rarity: PetEventRarity = PetEventRarity.COMMON,
    val cooldownMs: Long = 35L * 60L * 1000L,
    val dailyLimit: Int = 3,
    val targetRoute: String? = null,
    val actionLabel: String? = null,
    val discovery: PetDiscovery? = null,
    val interactive: Boolean = false
)

object PetWorldStore {

    private const val PREFS =
        "shiguangbox_settings"

    private const val MEMORY_KEY =
        "pet_world_memories"

    private const val DISCOVERY_KEY =
        "pet_world_discoveries"

    private const val MAX_MEMORIES =
        40

    fun prefs(
        context: Context
    ): SharedPreferences =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    fun bondStage(
        affection: Int
    ): PetBondStage =
        when {
            affection >= 80 ->
                PetBondStage.IN_SYNC
            affection >= 35 ->
                PetBondStage.CLOSE
            affection >= 12 ->
                PetBondStage.FAMILIAR
            else ->
                PetBondStage.NEW
        }

    fun recentMemories(
        prefs: SharedPreferences,
        limit: Int = 8
    ): List<PetMemory> {
        val raw =
            prefs.getString(
                MEMORY_KEY,
                "[]"
            ) ?: "[]"

        return runCatching {
            val array =
                JSONArray(
                    raw
                )

            buildList {
                for (
                    i in
                    0 until
                    array.length()
                ) {
                    val item =
                        array.optJSONObject(
                            i
                        ) ?: continue

                    add(
                        PetMemory(
                            id =
                                item.optString(
                                    "id"
                                ),
                            type =
                                item.optString(
                                    "type"
                                ),
                            petId =
                                item.optString(
                                    "petId"
                                ),
                            title =
                                item.optString(
                                    "title"
                                ),
                            detail =
                                item.optString(
                                    "detail"
                                ),
                            createdAt =
                                item.optLong(
                                    "createdAt"
                                )
                        )
                    )
                }
            }
                .sortedByDescending {
                    it.createdAt
                }
                .take(
                    limit
                )
        }
            .getOrDefault(
                emptyList()
            )
    }

    fun addMemory(
        prefs: SharedPreferences,
        memory: PetMemory
    ) {
        val existing =
            recentMemories(
                prefs,
                MAX_MEMORIES
            )
                .filterNot {
                    it.id ==
                        memory.id
                }

        val merged =
            (
                listOf(
                    memory
                ) +
                    existing
                )
                .take(
                    MAX_MEMORIES
                )

        val array =
            JSONArray()

        merged.forEach {
            array.put(
                JSONObject()
                    .put(
                        "id",
                        it.id
                    )
                    .put(
                        "type",
                        it.type
                    )
                    .put(
                        "petId",
                        it.petId
                    )
                    .put(
                        "title",
                        it.title
                    )
                    .put(
                        "detail",
                        it.detail
                    )
                    .put(
                        "createdAt",
                        it.createdAt
                    )
            )
        }

        prefs.edit()
            .putString(
                MEMORY_KEY,
                array.toString()
            )
            .apply()
    }

    fun discoveredIds(
        prefs: SharedPreferences
    ): Set<String> =
        prefs.getStringSet(
            DISCOVERY_KEY,
            emptySet()
        )
            ?.toSet()
            ?: emptySet()

    fun discoveries(
        prefs: SharedPreferences
    ): List<PetDiscovery> =
        discoveredIds(
            prefs
        )
            .mapNotNull(
                PetDiscovery::fromId
            )
            .sortedWith(
                compareBy<PetDiscovery> {
                    it.owner.ordinal
                }
                    .thenBy {
                        it.rarity.ordinal
                    }
            )

    fun collectDiscovery(
        prefs: SharedPreferences,
        discovery: PetDiscovery
    ): Boolean {
        val before =
            discoveredIds(
                prefs
            )

        if (
            discovery.id in
            before
        ) {
            return false
        }

        prefs.edit()
            .putStringSet(
                DISCOVERY_KEY,
                before +
                    discovery.id
            )
            .apply()

        addMemory(
            prefs,
            PetMemory(
                id =
                    "discovery_" +
                        discovery.id,
                type =
                    "discovery",
                petId =
                    discovery.owner.id,
                title =
                    discovery.emoji +
                        " " +
                        discovery.displayName,
                detail =
                    "这是一件只属于你和" +
                        discovery.owner
                            .displayName +
                        "的小发现。",
                createdAt =
                    System.currentTimeMillis()
            )
        )

        return true
    }

    fun eventCountToday(
        prefs: SharedPreferences,
        eventId: String
    ): Int =
        prefs.getInt(
            eventDayKey(
                eventId
            ),
            0
        )

    fun lastEventAt(
        prefs: SharedPreferences,
        eventId: String
    ): Long =
        prefs.getLong(
            "pet_world_event_at_" +
                eventId,
            0L
        )

    fun markEvent(
        prefs: SharedPreferences,
        event: PetWorldEvent
    ) {
        val dayKey =
            eventDayKey(
                event.id
            )

        prefs.edit()
            .putInt(
                dayKey,
                prefs.getInt(
                    dayKey,
                    0
                ) +
                    1
            )
            .putLong(
                "pet_world_event_at_" +
                    event.id,
                System.currentTimeMillis()
            )
            .putString(
                "pet_world_last_event",
                event.id
            )
            .putLong(
                "pet_world_last_event_at",
                System.currentTimeMillis()
            )
            .apply()

        if (
            event.type in
            setOf(
                PetWorldEventType.SEEK_TOUCH,
                PetWorldEventType.CHECK_TASKS,
                PetWorldEventType.INVITE_NOTE,
                PetWorldEventType.INVITE_MOOD,
                PetWorldEventType.TASK_MEMORY,
                PetWorldEventType.MOOD_COMPANY,
                PetWorldEventType.RARE_DISCOVERY
            )
        ) {
            addMemory(
                prefs,
                PetMemory(
                    id =
                        "event_" +
                            event.id +
                            "_" +
                            System.currentTimeMillis(),
                    type =
                        event.type.name
                            .lowercase(),
                    petId =
                        when {
                            event.discovery !=
                                null ->
                                event.discovery.owner.id
                            else ->
                                prefs.getString(
                                    "pet_selected_id",
                                    PetKind.ORANGE.id
                                ) ?:
                                    PetKind.ORANGE.id
                        },
                    title =
                        event.title,
                    detail =
                        event.message,
                    createdAt =
                        System.currentTimeMillis()
                )
            )
        }
    }

    fun wasEventRecent(
        prefs: SharedPreferences,
        event: PetWorldEvent
    ): Boolean =
        System.currentTimeMillis() -
            lastEventAt(
                prefs,
                event.id
            ) <
            event.cooldownMs

    fun canRunToday(
        prefs: SharedPreferences,
        event: PetWorldEvent
    ): Boolean =
        eventCountToday(
            prefs,
            event.id
        ) <
            event.dailyLimit

    private fun eventDayKey(
        eventId: String
    ): String =
        "pet_world_day_" +
            LocalDate.now()
                .toString() +
            "_" +
            eventId
}

object PetWorldEngine {

    fun choose(
        prefs: SharedPreferences,
        context: PetWorldContext
    ): PetWorldEvent? {
        if (
            context.quietNight
        ) {
            return if (
                Random.nextInt(
                    100
                ) <
                55
            ) {
                quietEvent(
                    context
                )
            } else {
                null
            }
        }

        // 约 20% 的调度轮次刻意什么都不发生。
        if (
            Random.nextInt(
                100
            ) <
            20
        ) {
            return null
        }

        val candidates =
            buildList {
                add(
                    quietEvent(
                        context
                    )
                )

                add(
                    stretchEvent(
                        context
                    )
                )

                if (
                    context.hour in
                    11..22
                ) {
                    add(
                        daydreamEvent(
                            context
                        )
                    )
                }

                if (
                    context.idleMs >
                    18L *
                        60L *
                        1000L
                ) {
                    add(
                        seekTouchEvent(
                            context
                        )
                    )
                }

                if (
                    context.pendingTasks >
                    0 &&
                    context.hour in
                    8..21
                ) {
                    add(
                        taskEvent(
                            context
                        )
                    )
                }

                if (
                    context.completedTasks >
                    0
                ) {
                    add(
                        taskMemoryEvent(
                            context
                        )
                    )
                }

                if (
                    context.mood in
                    setOf(
                        "sad",
                        "tired",
                        "quiet"
                    )
                ) {
                    add(
                        moodCompanyEvent(
                            context
                        )
                    )
                }

                if (
                    context.hour in
                    10..22
                ) {
                    add(
                        noteInviteEvent(
                            context
                        )
                    )
                }

                if (
                    context.hour in
                    9..22
                ) {
                    add(
                        moodInviteEvent(
                            context
                        )
                    )
                }

                rareDiscoveryEvent(
                    prefs,
                    context
                )
                    ?.let(
                        ::add
                    )
            }
                .filter {
                    PetWorldStore
                        .canRunToday(
                            prefs,
                            it
                        ) &&
                        !PetWorldStore
                            .wasEventRecent(
                                prefs,
                                it
                            )
                }

        if (
            candidates.isEmpty()
        ) {
            return null
        }

        val weighted =
            candidates.flatMap {
                event ->
                val weight =
                    weightFor(
                        context,
                        event
                    )

                List(
                    weight
                        .coerceAtLeast(
                            1
                        )
                ) {
                    event
                }
            }

        return weighted
            .randomOrNull()
    }

    fun forceRare(
        prefs: SharedPreferences,
        context: PetWorldContext
    ): PetWorldEvent? =
        rareDiscoveryEvent(
            prefs,
            context,
            force = true
        )

    fun bondStage(
        affection: Int
    ): PetBondStage =
        PetWorldStore
            .bondStage(
                affection
            )

    private fun weightFor(
        context: PetWorldContext,
        event: PetWorldEvent
    ): Int {
        var weight =
            when (
                event.type
            ) {
                PetWorldEventType.QUIET_OBSERVE ->
                    16
                PetWorldEventType.STRETCH_BREAK ->
                    12
                PetWorldEventType.DAYDREAM ->
                    9
                PetWorldEventType.SEEK_TOUCH ->
                    8
                PetWorldEventType.CHECK_TASKS ->
                    8
                PetWorldEventType.INVITE_NOTE ->
                    6
                PetWorldEventType.INVITE_MOOD ->
                    5
                PetWorldEventType.TASK_MEMORY ->
                    7
                PetWorldEventType.MOOD_COMPANY ->
                    9
                PetWorldEventType.RARE_DISCOVERY ->
                    1
            }

        when (
            context.petKind
        ) {
            PetKind.ORANGE -> {
                if (
                    event.type ==
                    PetWorldEventType.SEEK_TOUCH
                ) {
                    weight +=
                        8
                }
            }

            PetKind.YAYA -> {
                if (
                    event.type in
                    setOf(
                        PetWorldEventType.QUIET_OBSERVE,
                        PetWorldEventType.DAYDREAM
                    )
                ) {
                    weight +=
                        6
                }
            }

            PetKind.YUTUAN -> {
                if (
                    event.type in
                    setOf(
                        PetWorldEventType.MOOD_COMPANY,
                        PetWorldEventType.QUIET_OBSERVE
                    )
                ) {
                    weight +=
                        5
                }
            }
        }

        if (
            context.clinginessLevel >=
            2 &&
            event.interactive
        ) {
            weight +=
                5
        }

        if (
            context.clinginessLevel ==
            0 &&
            event.interactive
        ) {
            weight =
                (
                    weight *
                        0.45
                    )
                    .toInt()
                    .coerceAtLeast(
                        1
                    )
        }

        when (
            PetWorldStore
                .bondStage(
                    context.affection
                )
        ) {
            PetBondStage.NEW ->
                Unit

            PetBondStage.FAMILIAR -> {
                if (
                    event.interactive
                ) {
                    weight +=
                        1
                }
            }

            PetBondStage.CLOSE -> {
                if (
                    event.type in
                    setOf(
                        PetWorldEventType.SEEK_TOUCH,
                        PetWorldEventType.TASK_MEMORY,
                        PetWorldEventType.MOOD_COMPANY
                    )
                ) {
                    weight +=
                        3
                }
            }

            PetBondStage.IN_SYNC -> {
                if (
                    event.type in
                    setOf(
                        PetWorldEventType.TASK_MEMORY,
                        PetWorldEventType.MOOD_COMPANY,
                        PetWorldEventType.RARE_DISCOVERY
                    )
                ) {
                    weight +=
                        4
                }
            }
        }

        return weight
    }

    private fun quietEvent(
        context: PetWorldContext
    ): PetWorldEvent {
        val kind =
            context.petKind

        return PetWorldEvent(
            id =
                kind.id +
                    "_quiet_observe",
            type =
                PetWorldEventType.QUIET_OBSERVE,
            title =
                kind.displayName +
                    " · 自己待一会儿",
            message =
                when (kind) {
                    PetKind.ORANGE ->
                        "刚刚东看看西看看，好像发现了什么，又没发现。"
                    PetKind.YAYA ->
                        "它只是安安静静看了一会儿周围，没有想打扰你。"
                    PetKind.YUTUAN ->
                        "雨声轻轻的，它抬头看了一会儿。"
                },
            sequence =
                when (kind) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_world_observe",
                            PetMotion.LOOK_AROUND,
                            PetMotion.HEAD_TILT
                        )
                    PetKind.YAYA ->
                        sequence(
                            "yaya_world_observe",
                            PetMotion.LOOK_UP,
                            PetMotion.LOOK_AROUND
                        )
                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_world_observe",
                            PetMotion.LOOK_UP,
                            PetMotion.SHY
                        )
                },
            cooldownMs =
                22L *
                    60L *
                    1000L,
            dailyLimit =
                6,
            interactive =
                false
        )
    }

    private fun stretchEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_stretch_break",
            type =
                PetWorldEventType.STRETCH_BREAK,
            title =
                context.petKind.displayName +
                    " · 活动一下",
            message =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        "站久啦，伸个懒腰继续陪你。"
                    PetKind.YAYA ->
                        "轻轻舒展一下，又可以安静待着了。"
                    PetKind.YUTUAN ->
                        "云也要伸伸懒腰，雨点跟着晃了一下。"
                },
            sequence =
                sequence(
                    context.petKind.id +
                        "_world_stretch",
                    PetMotion.STRETCH,
                    PetMotion.HEAD_TILT
                ),
            cooldownMs =
                50L *
                    60L *
                    1000L,
            dailyLimit =
                3
        )

    private fun daydreamEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_daydream",
            type =
                PetWorldEventType.DAYDREAM,
            title =
                context.petKind.displayName +
                    " · 发一会儿呆",
            message =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        "它盯着一个地方看了半天，也不知道脑袋里在想什么。"
                    PetKind.YAYA ->
                        "芽芽安静发了一会儿呆，耳朵偶尔轻轻动一下。"
                    PetKind.YUTUAN ->
                        "雨团看着雨点发了一会儿呆，什么都没说。"
                },
            sequence =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_daydream",
                            PetMotion.LOOK_UP,
                            PetMotion.HEAD_TILT
                        )
                    PetKind.YAYA ->
                        sequence(
                            "yaya_daydream",
                            PetMotion.LOOK_UP,
                            PetMotion.DOZE_NOD
                        )
                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_daydream",
                            PetMotion.LOOK_UP,
                            PetMotion.SHY
                        )
                },
            cooldownMs =
                70L *
                    60L *
                    1000L,
            dailyLimit =
                2,
            interactive =
                false
        )

    private fun seekTouchEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_seek_touch",
            type =
                PetWorldEventType.SEEK_TOUCH,
            title =
                context.petKind.displayName +
                    " · 来找你啦",
            message =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        "我在旁边晃半天啦，真的不摸一下吗？"
                    PetKind.YAYA ->
                        "忙完一点了吗？我就在这里。"
                    PetKind.YUTUAN ->
                        "这里安静了好一会儿，要不要陪我听一下雨？"
                },
            sequence =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_world_seek",
                            PetMotion.LOOK_AROUND,
                            PetMotion.SEEK_ATTENTION,
                            PetMotion.HEAD_TILT
                        )
                    PetKind.YAYA ->
                        sequence(
                            "yaya_world_seek",
                            PetMotion.LOOK_UP,
                            PetMotion.HEAD_TILT
                        )
                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_world_seek",
                            PetMotion.LOOK_UP,
                            PetMotion.SEEK_ATTENTION
                        )
                },
            cooldownMs =
                when (
                    context.clinginessLevel
                ) {
                    3 ->
                        14L *
                            60L *
                            1000L
                    2 ->
                        24L *
                            60L *
                            1000L
                    else ->
                        50L *
                            60L *
                            1000L
                },
            dailyLimit =
                when (
                    context.clinginessLevel
                ) {
                    3 -> 8
                    2 -> 5
                    else -> 3
                },
            interactive =
                true
        )

    private fun taskEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_check_tasks",
            type =
                PetWorldEventType.CHECK_TASKS,
            title =
                context.petKind.displayName +
                    " · 看看今天",
            message =
                "今天还有 " +
                    context.pendingTasks +
                    " 件待办，要不要挑一件最小的先做？",
            sequence =
                sequence(
                    context.petKind.id +
                        "_world_task",
                    PetMotion.LOOK_UP,
                    PetMotion.HEAD_TILT
                ),
            cooldownMs =
                95L *
                    60L *
                    1000L,
            dailyLimit =
                2,
            targetRoute =
                "tasks",
            actionLabel =
                "去看看",
            interactive =
                true
        )

    private fun noteInviteEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_invite_note",
            type =
                PetWorldEventType.INVITE_NOTE,
            title =
                context.petKind.displayName +
                    " · 留下一点今天",
            message =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        "刚刚是不是闪过一个想法？趁没跑掉，记一下？"
                    PetKind.YAYA ->
                        "如果今天有一句想留下来的话，可以先收进盒子里。"
                    PetKind.YUTUAN ->
                        "雨声里想到的东西，也可以先留下一小句。"
                },
            sequence =
                sequence(
                    context.petKind.id +
                        "_world_note",
                    PetMotion.HEAD_TILT,
                    PetMotion.LOOK_UP
                ),
            cooldownMs =
                4L *
                    60L *
                    60L *
                    1000L,
            dailyLimit =
                1,
            targetRoute =
                "quick_note",
            actionLabel =
                "记一下",
            interactive =
                true
        )

    private fun moodInviteEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_invite_mood",
            type =
                PetWorldEventType.INVITE_MOOD,
            title =
                context.petKind.displayName +
                    " · 今天感觉怎样",
            message =
                "今天还没认真问过你：现在是什么心情？",
            sequence =
                sequence(
                    context.petKind.id +
                        "_world_mood",
                    PetMotion.LOOK_UP,
                    PetMotion.SHY
                ),
            cooldownMs =
                8L *
                    60L *
                    60L *
                    1000L,
            dailyLimit =
                1,
            targetRoute =
                "journal_mood",
            actionLabel =
                "记心情",
            interactive =
                true
        )

    private fun taskMemoryEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_task_memory",
            type =
                PetWorldEventType.TASK_MEMORY,
            title =
                context.petKind.displayName +
                    " · 还记得刚才",
            message =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        "今天已经完成 " +
                            context.completedTasks +
                            " 件啦，我可都看见了。"
                    PetKind.YAYA ->
                        "今天已经往前走了 " +
                            context.completedTasks +
                            " 步，不用急着一下走完。"
                    PetKind.YUTUAN ->
                        "已经收好 " +
                            context.completedTasks +
                            " 件事，云好像也轻了一点。"
                },
            sequence =
                PetBehaviorLibrary
                    .forLifeEvent(
                        context.petKind,
                        PetLifeEvent.AFTER_TASK
                    ),
            cooldownMs =
                75L *
                    60L *
                    1000L,
            dailyLimit =
                2
        )

    private fun moodCompanyEvent(
        context: PetWorldContext
    ): PetWorldEvent =
        PetWorldEvent(
            id =
                context.petKind.id +
                    "_mood_company",
            type =
                PetWorldEventType.MOOD_COMPANY,
            title =
                context.petKind.displayName +
                    " · 陪你一下",
            message =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        "今天不用一直有精神，我就在旁边晃悠。"
                    PetKind.YAYA ->
                        "不用马上变好，安静待一会儿也可以。"
                    PetKind.YUTUAN ->
                        "今天如果有点累，就让雨声再轻一点。"
                },
            sequence =
                PetBehaviorLibrary
                    .forLifeEvent(
                        context.petKind,
                        PetLifeEvent.MOOD_COMPANY
                    ),
            cooldownMs =
                90L *
                    60L *
                    1000L,
            dailyLimit =
                2,
            interactive =
                true
        )

    private fun rareDiscoveryEvent(
        prefs: SharedPreferences,
        context: PetWorldContext,
        force: Boolean = false
    ): PetWorldEvent? {
        if (
            !force &&
            !prefs.getBoolean(
                "pet_rare_events_enabled",
                true
            )
        ) {
            return null
        }

        val undiscovered =
            PetDiscovery.entries
                .filter {
                    it.owner ==
                        context.petKind &&
                        it.id !in
                        PetWorldStore
                            .discoveredIds(
                                prefs
                            )
                }

        if (
            undiscovered.isEmpty()
        ) {
            return null
        }

        val roll =
            Random.nextInt(
                10_000
            )

        val eligible =
            undiscovered.filter {
                force ||
                    roll <
                    when (
                        it.rarity
                    ) {
                        PetEventRarity.UNCOMMON ->
                            260
                        PetEventRarity.RARE ->
                            90
                        PetEventRarity.ULTRA_RARE ->
                            25
                        else ->
                            400
                    }
            }

        val discovery =
            eligible
                .randomOrNull()
                ?: return null

        return PetWorldEvent(
            id =
                "discovery_" +
                    discovery.id,
            type =
                PetWorldEventType.RARE_DISCOVERY,
            title =
                context.petKind.displayName +
                    " · 好像发现了什么",
            message =
                when (
                    discovery
                ) {
                    PetDiscovery.ORANGE_STAR ->
                        "我刚刚追着亮亮的东西跑，结果捡到一颗小星星。送你！"
                    PetDiscovery.ORANGE_BUTTON ->
                        "这里滚过来一个小纽扣，我先替你捡起来啦。"
                    PetDiscovery.ORANGE_FEATHER ->
                        "追了半天终于追到这根小羽毛！这个要好好收着。"
                    PetDiscovery.YAYA_LEAF ->
                        "刚刚有一片很轻的叶子落下来，我觉得它很好看。"
                    PetDiscovery.YAYA_PETAL ->
                        "这里多了一片小花瓣，我想把它留给你。"
                    PetDiscovery.YAYA_SEED ->
                        "找到一颗很小的种子。也许以后会长出什么呢。"
                    PetDiscovery.YUTUAN_DROP ->
                        "雨停了一点，这颗水珠居然一直没有掉下来。"
                    PetDiscovery.YUTUAN_CLOUD ->
                        "刚刚有一小团云没有跟着飘走，我把它记下来啦。"
                    PetDiscovery.YUTUAN_RAINBOW ->
                        "这次彩虹很小很小，但刚好被我们看见了。"
                },
            sequence =
                when (
                    context.petKind
                ) {
                    PetKind.ORANGE ->
                        sequence(
                            "orange_discovery",
                            PetMotion.LOOK_AROUND,
                            PetMotion.SMALL_JUMP,
                            PetMotion.LOOK_UP
                        )
                    PetKind.YAYA ->
                        sequence(
                            "yaya_discovery",
                            PetMotion.LOOK_UP,
                            PetMotion.HEAD_TILT,
                            PetMotion.SHY
                        )
                    PetKind.YUTUAN ->
                        sequence(
                            "yutuan_discovery",
                            PetMotion.LOOK_UP,
                            PetMotion.STRETCH,
                            PetMotion.HEAD_TILT
                        )
                },
            rarity =
                discovery.rarity,
            cooldownMs =
                20L *
                    60L *
                    60L *
                    1000L,
            dailyLimit =
                1,
            actionLabel =
                "收进回忆盒",
            discovery =
                discovery,
            interactive =
                true
        )
    }

    private fun sequence(
        id: String,
        vararg motions: PetMotion
    ): PetBehaviorSequence =
        PetBehaviorSequence(
            id =
                id,
            steps =
                motions.mapIndexed {
                    index,
                    motion ->
                    PetBehaviorStep(
                        motion =
                            motion,
                        intensity =
                            1f,
                        speed =
                            1f,
                        direction =
                            if (
                                index %
                                    2 ==
                                0
                            ) {
                                1f
                            } else {
                                -1f
                            },
                        pauseAfterMs =
                            180L
                    )
                },
            settleAfterMs =
                180L
        )
}
