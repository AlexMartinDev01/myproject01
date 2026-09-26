package com.shiguangbox.app

enum class PetMotion(
    val displayName: String,
    val durationSeconds: Double,
    val size: MotionSize
) {
    NONE(
        displayName = "无",
        durationSeconds = 0.0,
        size = MotionSize.MICRO
    ),
    LOOK_AROUND(
        displayName = "左右看看",
        durationSeconds = 3.0,
        size = MotionSize.MEDIUM
    ),
    HEAD_TILT(
        displayName = "歪头",
        durationSeconds = 2.35,
        size = MotionSize.MICRO
    ),
    LOOK_UP(
        displayName = "抬头看",
        durationSeconds = 2.15,
        size = MotionSize.MICRO
    ),
    STRETCH(
        displayName = "伸懒腰",
        durationSeconds = 2.7,
        size = MotionSize.MEDIUM
    ),
    SMALL_JUMP(
        displayName = "小跳一下",
        durationSeconds = 1.45,
        size = MotionSize.BIG
    ),
    DOZE_NOD(
        displayName = "困倦点头",
        durationSeconds = 3.2,
        size = MotionSize.MEDIUM
    ),
    SHY(
        displayName = "害羞缩一下",
        durationSeconds = 2.45,
        size = MotionSize.MEDIUM
    ),
    SEEK_ATTENTION(
        displayName = "主动求关注",
        durationSeconds = 3.35,
        size = MotionSize.BIG
    )
}

enum class MotionSize {
    MICRO,
    MEDIUM,
    BIG
}

data class MotionModifier(
    val intensity: Float = 1f,
    val speed: Float = 1f,
    val direction: Float = 1f
)

data class PetMotionProfile(
    val headCenterU: Double,
    val headCenterV: Double,
    val headRadiusU: Double,
    val headRadiusV: Double,
    val headTiltDegrees: Double,
    val lookDistance: Double,
    val stretchAmount: Double,
    val jumpHeight: Double,
    val personalityBounce: Double
)

object PetMotionProfiles {
    fun forPet(
        kind: PetKind
    ): PetMotionProfile =
        when (kind) {
            PetKind.ORANGE ->
                PetMotionProfile(
                    headCenterU = 0.50,
                    headCenterV = 0.40,
                    headRadiusU = 0.30,
                    headRadiusV = 0.27,
                    headTiltDegrees = 8.5,
                    lookDistance = 0.020,
                    stretchAmount = 0.030,
                    jumpHeight = 0.050,
                    personalityBounce = 1.10
                )

            PetKind.YAYA ->
                PetMotionProfile(
                    headCenterU = 0.51,
                    headCenterV = 0.40,
                    headRadiusU = 0.31,
                    headRadiusV = 0.30,
                    headTiltDegrees = 6.2,
                    lookDistance = 0.014,
                    stretchAmount = 0.022,
                    jumpHeight = 0.034,
                    personalityBounce = 0.78
                )

            PetKind.YUTUAN ->
                PetMotionProfile(
                    headCenterU = 0.50,
                    headCenterV = 0.41,
                    headRadiusU = 0.32,
                    headRadiusV = 0.31,
                    headTiltDegrees = 6.8,
                    lookDistance = 0.016,
                    stretchAmount = 0.024,
                    jumpHeight = 0.036,
                    personalityBounce = 0.86
                )
        }
}
