package com.shiguangbox.app

enum class PetKind(
    val id: String,
    val displayName: String,
    val emoji: String,
    val subtitle: String,
    val moodId: String
) {
    ORANGE(
        id = "orange",
        displayName = "橘团",
        emoji = "☀️",
        subtitle = "开心系小橘猫 · 活泼、热情、会摇尾巴",
        moodId = "happy"
    ),
    YAYA(
        id = "yaya",
        displayName = "芽芽",
        emoji = "🌿",
        subtitle = "平静系垂耳兔 · 温柔、安静、会轻轻晃耳朵",
        moodId = "calm"
    ),
    YUTUAN(
        id = "yutuan",
        displayName = "雨团",
        emoji = "🌧️",
        subtitle = "雨天系云朵小狗 · 安静、敏感、会轻轻垂耳陪伴",
        moodId = "sad"
    )
}

data class PetVisualResources(
    val idle: Int,
    val tired: Int,
    val sleep: Int,
    val wake: Int
)

object PetProfiles {
    fun fromId(id: String?): PetKind {
        return when (id) {
            PetKind.YAYA.id -> PetKind.YAYA
            PetKind.YUTUAN.id -> PetKind.YUTUAN
            else -> PetKind.ORANGE
        }
    }

    fun resources(kind: PetKind): PetVisualResources {
        return when (kind) {
            PetKind.ORANGE ->
                PetVisualResources(
                    idle = R.drawable.pet_orange_idle,
                    tired = R.drawable.pet_orange_tired,
                    sleep = R.drawable.pet_orange_sleep,
                    wake = R.drawable.pet_orange_wake
                )

            PetKind.YAYA ->
                PetVisualResources(
                    idle = R.drawable.pet_yaya_idle,
                    tired = R.drawable.pet_yaya_tired,
                    sleep = R.drawable.pet_yaya_sleep,
                    wake = R.drawable.pet_yaya_wake
                )

            PetKind.YUTUAN ->
                PetVisualResources(
                    idle = R.drawable.pet_yutuan_idle,
                    tired = R.drawable.pet_yutuan_idle,
                    sleep = R.drawable.pet_yutuan_idle,
                    wake = R.drawable.pet_yutuan_idle
                )
        }
    }
}
