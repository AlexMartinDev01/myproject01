package com.shiguangbox.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PetSpeechBubbleView(
    context: Context,
    private val petKind: PetKind = PetKind.ORANGE
) : LinearLayout(context) {

    enum class Tone {
        NORMAL,
        REMINDER,
        SUCCESS,
        MOOD
    }

    private val yayaTheme =
        petKind == PetKind.YAYA

    private val yutuanTheme =
        petKind == PetKind.YUTUAN

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.25f)
        }

    private val sizingPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val bubblePath = Path()

    private var tailAtTop = false
    private var tailCenter = 0f
    private var tone = Tone.NORMAL
    private var actionsRow:
        LinearLayout? = null

    private var boundTitle = ""
    private var boundMessage = ""
    private var actionCount = 0

    private val outerInset = dp(7f)
    private val tailHeight = dp(12f)
    private val tailHalfWidth = dp(12f)
    private val corner = dp(22f)

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setWillNotDraw(false)
        setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        elevation = dp(10f)
        updatePadding()
        applyTone(Tone.NORMAL)
    }

    fun isYayaTheme(): Boolean =
        yayaTheme

    fun isDecorativeTheme(): Boolean =
        yayaTheme ||
            yutuanTheme

    fun minimumOverlayHeightPx(): Int =
        dpInt(
            when {
                yayaTheme ->
                    if (actionCount > 0) {
                        176
                    } else {
                        142
                    }

                yutuanTheme ->
                    if (actionCount > 0) {
                        170
                    } else {
                        138
                    }

                else -> 72
            }
        )

    fun tailAnchorFraction(): Float =
        when {
            yayaTheme -> 0.47f
            yutuanTheme -> 0.54f
            else -> 0.50f
        }

    fun preferredWidthPx(
        screenWidthPx: Int
    ): Int {
        if (
            !yayaTheme &&
            !yutuanTheme
        ) {
            return dpInt(248)
        }

        val minWidth =
            dpInt(
                if (yutuanTheme) {
                    180
                } else {
                    184
                }
            )

        val maxWidth =
            minOf(
                dpInt(
                    if (yutuanTheme) {
                        278
                    } else {
                        286
                    }
                ),
                (
                    screenWidthPx *
                        if (yutuanTheme) {
                            0.70f
                        } else {
                            0.72f
                        }
                    ).toInt()
            ).coerceAtLeast(minWidth)

        sizingPaint.textSize =
            sp(
                if (yutuanTheme) {
                    12.8f
                } else {
                    13.0f
                }
            )

        sizingPaint.typeface =
            if (yayaTheme) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }

        val widestMessage =
            boundMessage
                .split("\n")
                .maxOfOrNull {
                    sizingPaint
                        .measureText(it)
                } ?: 0f

        sizingPaint.textSize =
            sp(
                if (yutuanTheme) {
                    9.8f
                } else {
                    10.0f
                }
            )

        sizingPaint.typeface =
            Typeface.DEFAULT_BOLD

        val titleWidth =
            sizingPaint
                .measureText(boundTitle)

        var desired =
            maxOf(
                minWidth,
                (
                    widestMessage +
                        dp(
                            if (yutuanTheme) {
                                50f
                            } else {
                                52f
                            }
                        )
                    ).toInt(),
                (
                    titleWidth +
                        dp(
                            if (yutuanTheme) {
                                46f
                            } else {
                                48f
                            }
                        )
                    ).toInt()
            )

        val length =
            boundMessage.length

        desired =
            when {
                actionCount > 0 ->
                    maxOf(
                        desired,
                        dpInt(
                            if (yutuanTheme) {
                                250
                            } else {
                                258
                            }
                        )
                    )

                length >= 34 ->
                    maxWidth

                length >= 22 ->
                    maxOf(
                        desired,
                        dpInt(
                            if (yutuanTheme) {
                                244
                            } else {
                                252
                            }
                        )
                    )

                length >= 12 ->
                    maxOf(
                        desired,
                        dpInt(
                            if (yutuanTheme) {
                                214
                            } else {
                                220
                            }
                        )
                    )

                else -> desired
            }

        return desired
            .coerceIn(
                minWidth,
                maxWidth
            )
    }

    fun bind(
        title: String,
        message: String,
        tone: Tone = Tone.NORMAL
    ) {
        removeAllViews()
        actionsRow = null
        actionCount = 0
        boundTitle = title
        boundMessage = message
        this.tone = tone
        applyTone(tone)

        val badge =
            TextView(context).apply {
                text = title
                textSize =
                    when {
                        yayaTheme -> 10.0f
                        yutuanTheme -> 9.8f
                        else -> 11.5f
                    }

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setTextColor(
                    accentColor(tone)
                )

                setPadding(
                    dpInt(
                        if (
                            yayaTheme ||
                            yutuanTheme
                        ) {
                            8
                        } else {
                            9
                        }
                    ),
                    dpInt(
                        if (
                            yayaTheme ||
                            yutuanTheme
                        ) {
                            3
                        } else {
                            4
                        }
                    ),
                    dpInt(
                        if (
                            yayaTheme ||
                            yutuanTheme
                        ) {
                            8
                        } else {
                            9
                        }
                    ),
                    dpInt(
                        if (
                            yayaTheme ||
                            yutuanTheme
                        ) {
                            3
                        } else {
                            4
                        }
                    )
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable
                                .RECTANGLE

                        cornerRadius =
                            dp(
                                when {
                                    yayaTheme -> 11.5f
                                    yutuanTheme -> 11f
                                    else -> 11f
                                }
                            )

                        setColor(
                            accentPaleColor(
                                tone
                            )
                        )
                    }
            }

        addView(
            badge,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        val messageView =
            TextView(context).apply {
                text = message

                textSize =
                    when {
                        yayaTheme -> 13.0f
                        yutuanTheme -> 12.8f
                        else -> 14f
                    }

                setTextColor(
                    when {
                        yayaTheme ->
                            Color.rgb(
                                58,
                                88,
                                42
                            )

                        yutuanTheme ->
                            Color.rgb(
                                57,
                                79,
                                103
                            )

                        else ->
                            Color.rgb(
                                77,
                                59,
                                48
                            )
                    }
                )

                if (yayaTheme) {
                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

                setLineSpacing(
                    0f,
                    when {
                        yayaTheme -> 1.15f
                        yutuanTheme -> 1.14f
                        else -> 1.14f
                    }
                )

                maxLines =
                    if (
                        yayaTheme ||
                        yutuanTheme
                    ) {
                        5
                    } else {
                        4
                    }
            }

        addView(
            messageView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dpInt(
                        if (
                            yayaTheme ||
                            yutuanTheme
                        ) {
                            8
                        } else {
                            9
                        }
                    )
            }
        )

        requestLayout()
    }

    fun addAction(
        label: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ) {
        var row = actionsRow

        if (row == null) {
            row =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER
                }

            actionsRow = row

            addView(
                row,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin =
                        dpInt(
                            if (
                                yayaTheme ||
                                yutuanTheme
                            ) {
                                9
                            } else {
                                10
                            }
                        )
                }
            )
        }

        val button =
            Button(context).apply {
                text = label
                isAllCaps = false
                textSize =
                    when {
                        yayaTheme -> 10.7f
                        yutuanTheme -> 10.5f
                        else -> 12f
                    }
                minimumHeight = 0
                minHeight = 0

                setTextColor(
                    if (primary) {
                        Color.WHITE
                    } else if (yayaTheme) {
                        Color.rgb(
                            66,
                            97,
                            48
                        )
                    } else if (yutuanTheme) {
                        Color.rgb(
                            58,
                            91,
                            122
                        )
                    } else {
                        Color.rgb(
                            95,
                            67,
                            48
                        )
                    }
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable
                                .RECTANGLE

                        cornerRadius =
                            dp(
                                if (
                                    yayaTheme ||
                                    yutuanTheme
                                ) {
                                    13f
                                } else {
                                    13f
                                }
                            )

                        setColor(
                            if (primary) {
                                accentColor(tone)
                            } else if (yayaTheme) {
                                Color.rgb(
                                    238,
                                    248,
                                    216
                                )
                            } else if (yutuanTheme) {
                                Color.rgb(
                                    231,
                                    244,
                                    255
                                )
                            } else {
                                Color.rgb(
                                    255,
                                    239,
                                    218
                                )
                            }
                        )
                    }

                setOnClickListener {
                    onClick()
                }
            }

        row.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                dpInt(
                    when {
                        yayaTheme -> 34
                        yutuanTheme -> 34
                        else -> 38
                    }
                ),
                1f
            ).apply {
                marginEnd =
                    if (
                        (
                            actionsRow
                                ?.childCount
                                ?: 0
                            ) == 0
                    ) {
                        dpInt(6)
                    } else {
                        0
                    }
            }
        )

        actionCount += 1
        requestLayout()
    }

    fun setTail(
        atTop: Boolean,
        centerPx: Float
    ) {
        tailAtTop = atTop
        tailCenter = centerPx

        if (!yayaTheme) {
            updatePadding()
        }

        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        if (
            yayaTheme ||
            yutuanTheme
        ) {
            val minHeight =
                minimumOverlayHeightPx()

            if (measuredHeight < minHeight) {
                setMeasuredDimension(
                    measuredWidth,
                    minHeight
                )
            }
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        if (yayaTheme) {
            drawYayaBubble(canvas)
            super.onDraw(canvas)
            return
        }

        if (yutuanTheme) {
            drawYutuanBubble(canvas)
            super.onDraw(canvas)
            return
        }

        bubblePath.reset()

        val left = outerInset
        val right =
            width - outerInset

        val top =
            outerInset +
                if (tailAtTop) {
                    tailHeight
                } else {
                    0f
                }

        val bottom =
            height -
                outerInset -
                if (tailAtTop) {
                    0f
                } else {
                    tailHeight
                }

        val body =
            RectF(
                left,
                top,
                right,
                bottom
            )

        bubblePath.addRoundRect(
            body,
            corner,
            corner,
            Path.Direction.CW
        )

        val safeCenter =
            tailCenter.coerceIn(
                left + corner,
                right - corner
            )

        if (tailAtTop) {
            bubblePath.moveTo(
                safeCenter -
                    tailHalfWidth,
                top
            )

            bubblePath.lineTo(
                safeCenter,
                outerInset
            )

            bubblePath.lineTo(
                safeCenter +
                    tailHalfWidth,
                top
            )
        } else {
            bubblePath.moveTo(
                safeCenter -
                    tailHalfWidth,
                bottom
            )

            bubblePath.lineTo(
                safeCenter,
                height - outerInset
            )

            bubblePath.lineTo(
                safeCenter +
                    tailHalfWidth,
                bottom
            )
        }

        bubblePath.close()

        fillPaint.setShadowLayer(
            dp(12f),
            0f,
            dp(4f),
            Color.argb(
                42,
                64,
                43,
                26
            )
        )

        canvas.drawPath(
            bubblePath,
            fillPaint
        )

        fillPaint.clearShadowLayer()

        canvas.drawPath(
            bubblePath,
            strokePaint
        )

        super.onDraw(canvas)
    }

    private fun drawYayaBubble(
        canvas: Canvas
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()

        val tailSpace =
            dp(14f)

        val body =
            RectF(
                dp(5f),
                if (tailAtTop) {
                    tailSpace
                } else {
                    dp(5f)
                },
                w - dp(5f),
                if (tailAtTop) {
                    h - dp(5f)
                } else {
                    h - tailSpace
                }
            )

        val cloud =
            buildYayaCloudPath(
                body
            )

        val fill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.rgb(
                        255,
                        250,
                        239
                    )

                setShadowLayer(
                    dp(8f),
                    0f,
                    dp(3f),
                    Color.argb(
                        42,
                        79,
                        104,
                        38
                    )
                )
            }

        canvas.drawPath(
            cloud,
            fill
        )

        fill.clearShadowLayer()

        val outer =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND

                strokeWidth =
                    dp(4.6f)

                color =
                    Color.rgb(
                        92,
                        143,
                        45
                    )
            }

        canvas.drawPath(
            cloud,
            outer
        )

        val middle =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND

                strokeWidth =
                    dp(2.7f)

                color =
                    Color.rgb(
                        168,
                        207,
                        86
                    )
            }

        canvas.drawPath(
            cloud,
            middle
        )

        val inner =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND

                strokeWidth =
                    dp(1.25f)

                color =
                    Color.argb(
                        225,
                        255,
                        255,
                        245
                    )
            }

        canvas.drawPath(
            cloud,
            inner
        )

        drawYayaTail(
            canvas,
            body
        )

        drawYayaDecorations(
            canvas,
            body
        )
    }

    private fun drawYutuanBubble(
        canvas: Canvas
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        val tailSpace =
            dp(14f)

        val body =
            RectF(
                dp(5f),
                if (tailAtTop) {
                    tailSpace
                } else {
                    dp(5f)
                },
                w - dp(5f),
                if (tailAtTop) {
                    h - dp(5f)
                } else {
                    h - tailSpace
                }
            )

        val cloud =
            buildYayaCloudPath(
                body
            )

        val fill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.rgb(
                        252,
                        253,
                        255
                    )

                setShadowLayer(
                    dp(8f),
                    0f,
                    dp(3f),
                    Color.argb(
                        38,
                        69,
                        115,
                        160
                    )
                )
            }

        canvas.drawPath(
            cloud,
            fill
        )

        fill.clearShadowLayer()

        val glow =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND

                strokeWidth =
                    dp(4.5f)

                color =
                    Color.argb(
                        120,
                        171,
                        211,
                        247
                    )
            }

        canvas.drawPath(
            cloud,
            glow
        )

        val middle =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND

                strokeWidth =
                    dp(2.4f)

                color =
                    Color.rgb(
                        112,
                        169,
                        225
                    )
            }

        canvas.drawPath(
            cloud,
            middle
        )

        val inner =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeJoin =
                    Paint.Join.ROUND

                strokeCap =
                    Paint.Cap.ROUND

                strokeWidth =
                    dp(0.9f)

                color =
                    Color.argb(
                        220,
                        235,
                        247,
                        255
                    )
            }

        canvas.drawPath(
            cloud,
            inner
        )

        drawYutuanTail(
            canvas,
            body
        )

        drawYutuanDecorations(
            canvas,
            body
        )
    }

    private fun drawYutuanTail(
        canvas: Canvas,
        body: RectF
    ) {
        val anchor =
            tailCenter.coerceIn(
                body.left +
                    dp(42f),
                body.right -
                    dp(42f)
            )

        val direction =
            if (tailAtTop) {
                -1f
            } else {
                1f
            }

        val baseY =
            if (tailAtTop) {
                body.top +
                    dp(2f)
            } else {
                body.bottom -
                    dp(2f)
            }

        val tipY =
            baseY +
                direction *
                    dp(13f)

        val tail =
            Path().apply {
                moveTo(
                    anchor -
                        dp(13f),
                    baseY
                )

                cubicTo(
                    anchor -
                        dp(6f),
                    baseY +
                        direction *
                            dp(1f),
                    anchor -
                        dp(4f),
                    tipY -
                        direction *
                            dp(2f),
                    anchor,
                    tipY
                )

                cubicTo(
                    anchor +
                        dp(4f),
                    tipY -
                        direction *
                            dp(2f),
                    anchor +
                        dp(7f),
                    baseY +
                        direction *
                            dp(1f),
                    anchor +
                        dp(13f),
                    baseY
                )

                close()
            }

        val fill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.rgb(
                        248,
                        252,
                        255
                    )
            }

        val stroke =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(2.1f)

                strokeJoin =
                    Paint.Join.ROUND

                color =
                    Color.rgb(
                        112,
                        169,
                        225
                    )
            }

        canvas.drawPath(
            tail,
            fill
        )

        canvas.drawPath(
            tail,
            stroke
        )
    }

    private fun drawYutuanDecorations(
        canvas: Canvas,
        body: RectF
    ) {
        val w =
            body.width()

        val h =
            body.height()

        val topLeftX =
            body.left +
                w *
                    0.145f

        val topLeftY =
            body.top +
                h *
                    0.10f

        drawYutuanCloud(
            canvas,
            topLeftX,
            topLeftY,
            dp(17f)
        )

        drawRainDrop(
            canvas,
            topLeftX +
                dp(2f),
            topLeftY +
                dp(24f),
            dp(5.8f)
        )

        drawRainDrop(
            canvas,
            topLeftX +
                dp(18f),
            topLeftY +
                dp(18f),
            dp(3.3f)
        )

        drawWaterBubble(
            canvas,
            body.left +
                dp(27f),
            body.top +
                h *
                    0.70f,
            dp(7f)
        )

        drawWaterBubble(
            canvas,
            body.left +
                dp(46f),
            body.top +
                h *
                    0.77f,
            dp(3.6f)
        )

        val rightX =
            body.right -
                w *
                    0.115f

        val rightY =
            body.top +
                h *
                    0.63f

        drawYutuanCloud(
            canvas,
            rightX,
            rightY,
            dp(14f)
        )

        drawRainDrop(
            canvas,
            rightX +
                dp(7f),
            rightY +
                dp(21f),
            dp(5.2f)
        )

        drawRainDrop(
            canvas,
            rightX +
                dp(20f),
            rightY +
                dp(25f),
            dp(3.0f)
        )

        drawWaterBubble(
            canvas,
            body.right -
                dp(28f),
            body.top +
                h *
                    0.24f,
            dp(5.8f)
        )

        drawBlueSparkle(
            canvas,
            body.left +
                w *
                    0.49f,
            body.top +
                dp(12f),
            dp(4.2f)
        )

        drawBlueSparkle(
            canvas,
            body.right -
                dp(24f),
            body.top +
                h *
                    0.49f,
            dp(3.2f)
        )
    }

    private fun drawYutuanCloud(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val fill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.rgb(
                        250,
                        252,
                        255
                    )
            }

        val outline =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(1.2f)

                color =
                    Color.rgb(
                        153,
                        191,
                        229
                    )
            }

        val circles =
            arrayOf(
                floatArrayOf(
                    centerX -
                        radius *
                            0.62f,
                    centerY +
                        radius *
                            0.08f,
                    radius *
                        0.62f
                ),
                floatArrayOf(
                    centerX,
                    centerY -
                        radius *
                            0.24f,
                    radius *
                        0.82f
                ),
                floatArrayOf(
                    centerX +
                        radius *
                            0.70f,
                    centerY +
                        radius *
                            0.07f,
                    radius *
                        0.57f
                )
            )

        circles.forEach {
            canvas.drawCircle(
                it[0],
                it[1],
                it[2],
                fill
            )

            canvas.drawCircle(
                it[0],
                it[1],
                it[2],
                outline
            )
        }
    }

    private fun drawRainDrop(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val drop =
            Path().apply {
                moveTo(
                    centerX,
                    centerY -
                        radius *
                            1.45f
                )

                cubicTo(
                    centerX +
                        radius *
                            0.90f,
                    centerY -
                        radius *
                            0.35f,
                    centerX +
                        radius *
                            0.82f,
                    centerY +
                        radius *
                            0.82f,
                    centerX,
                    centerY +
                        radius
                )

                cubicTo(
                    centerX -
                        radius *
                            0.82f,
                    centerY +
                        radius *
                            0.82f,
                    centerX -
                        radius *
                            0.90f,
                    centerY -
                        radius *
                            0.35f,
                    centerX,
                    centerY -
                        radius *
                            1.45f
                )

                close()
            }

        val fill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        190,
                        169,
                        214,
                        246
                    )
            }

        val stroke =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(1.1f)

                color =
                    Color.rgb(
                        88,
                        153,
                        218
                    )
            }

        canvas.drawPath(
            drop,
            fill
        )

        canvas.drawPath(
            drop,
            stroke
        )

        val highlight =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        220,
                        255,
                        255,
                        255
                    )
            }

        canvas.drawCircle(
            centerX -
                radius *
                    0.28f,
            centerY -
                radius *
                    0.35f,
            radius *
                0.22f,
            highlight
        )
    }

    private fun drawWaterBubble(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val fill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        150,
                        172,
                        216,
                        246
                    )
            }

        val stroke =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(1f)

                color =
                    Color.rgb(
                        107,
                        164,
                        222
                    )
            }

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            fill
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            stroke
        )

        val highlight =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        220,
                        255,
                        255,
                        255
                    )
            }

        canvas.drawCircle(
            centerX -
                radius *
                    0.30f,
            centerY -
                radius *
                    0.34f,
            radius *
                0.24f,
            highlight
        )
    }

    private fun drawBlueSparkle(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val sparkle =
            Path().apply {
                moveTo(
                    centerX,
                    centerY -
                        radius
                )

                lineTo(
                    centerX +
                        radius *
                            0.25f,
                    centerY -
                        radius *
                            0.24f
                )

                lineTo(
                    centerX +
                        radius,
                    centerY
                )

                lineTo(
                    centerX +
                        radius *
                            0.25f,
                    centerY +
                        radius *
                            0.24f
                )

                lineTo(
                    centerX,
                    centerY +
                        radius
                )

                lineTo(
                    centerX -
                        radius *
                            0.25f,
                    centerY +
                        radius *
                            0.24f
                )

                lineTo(
                    centerX -
                        radius,
                    centerY
                )

                lineTo(
                    centerX -
                        radius *
                            0.25f,
                    centerY -
                        radius *
                            0.24f
                )

                close()
            }

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        205,
                        153,
                        197,
                        239
                    )
            }

        canvas.drawPath(
            sparkle,
            paint
        )
    }

    private fun buildYayaCloudPath(
        body: RectF
    ): Path {
        val path = Path()

        val l = body.left
        val t = body.top
        val r = body.right
        val b = body.bottom

        val w =
            body.width()

        val h =
            body.height()

        path.moveTo(
            l + w * 0.09f,
            t + h * 0.17f
        )

        path.cubicTo(
            l + w * 0.035f,
            t + h * 0.20f,
            l + w * 0.015f,
            t + h * 0.36f,
            l + w * 0.02f,
            t + h * 0.52f
        )

        path.cubicTo(
            l + w * 0.015f,
            t + h * 0.70f,
            l + w * 0.055f,
            t + h * 0.84f,
            l + w * 0.13f,
            t + h * 0.86f
        )

        path.cubicTo(
            l + w * 0.16f,
            b + h * 0.015f,
            l + w * 0.28f,
            b + h * 0.025f,
            l + w * 0.34f,
            b - h * 0.035f
        )

        path.cubicTo(
            l + w * 0.40f,
            b + h * 0.035f,
            l + w * 0.50f,
            b + h * 0.04f,
            l + w * 0.56f,
            b - h * 0.035f
        )

        path.cubicTo(
            l + w * 0.63f,
            b + h * 0.025f,
            l + w * 0.76f,
            b + h * 0.015f,
            l + w * 0.80f,
            b - h * 0.055f
        )

        path.cubicTo(
            l + w * 0.90f,
            b - h * 0.02f,
            l + w * 0.975f,
            b - h * 0.16f,
            l + w * 0.975f,
            t + h * 0.55f
        )

        path.cubicTo(
            l + w * 0.985f,
            t + h * 0.35f,
            l + w * 0.95f,
            t + h * 0.18f,
            l + w * 0.88f,
            t + h * 0.16f
        )

        path.cubicTo(
            l + w * 0.84f,
            t - h * 0.015f,
            l + w * 0.69f,
            t - h * 0.035f,
            l + w * 0.61f,
            t + h * 0.09f
        )

        path.cubicTo(
            l + w * 0.55f,
            t - h * 0.005f,
            l + w * 0.43f,
            t - h * 0.01f,
            l + w * 0.38f,
            t + h * 0.085f
        )

        path.cubicTo(
            l + w * 0.32f,
            t + h * 0.005f,
            l + w * 0.19f,
            t - h * 0.005f,
            l + w * 0.15f,
            t + h * 0.10f
        )

        path.cubicTo(
            l + w * 0.13f,
            t + h * 0.13f,
            l + w * 0.11f,
            t + h * 0.16f,
            l + w * 0.09f,
            t + h * 0.17f
        )

        path.close()

        return path
    }

    private fun drawYayaDecorations(
        canvas: Canvas,
        body: RectF
    ) {
        val w =
            body.width()

        val h =
            body.height()

        val leftX =
            body.left +
                w *
                0.115f

        val topY =
            body.top +
                h *
                0.12f

        drawLeaf(
            canvas,
            leftX -
                dp(6f),
            topY -
                dp(4f),
            dp(39f),
            dp(19f),
            -48f
        )

        drawLeaf(
            canvas,
            leftX -
                dp(19f),
            topY +
                dp(2f),
            dp(25f),
            dp(13f),
            -150f
        )

        drawLeaf(
            canvas,
            leftX +
                dp(12f),
            topY +
                dp(3f),
            dp(25f),
            dp(13f),
            -20f
        )

        drawFlower(
            canvas,
            leftX,
            topY +
                dp(14f),
            dp(8f)
        )

        drawDewBubble(
            canvas,
            leftX -
                dp(25f),
            topY +
                dp(25f),
            dp(9f)
        )

        drawDewBubble(
            canvas,
            leftX +
                dp(22f),
            topY +
                dp(17f),
            dp(4f)
        )

        val rightX =
            body.right -
                w *
                0.115f

        val rightY =
            body.top +
                h *
                0.13f

        drawLeaf(
            canvas,
            rightX +
                dp(3f),
            rightY -
                dp(2f),
            dp(30f),
            dp(15f),
            42f
        )

        drawLeaf(
            canvas,
            rightX +
                dp(18f),
            rightY +
                dp(9f),
            dp(23f),
            dp(12f),
            118f
        )

        drawFlower(
            canvas,
            rightX,
            rightY +
                dp(16f),
            dp(6.2f)
        )

        drawDewBubble(
            canvas,
            rightX +
                dp(23f),
            rightY +
                dp(9f),
            dp(7f)
        )

        drawSparkle(
            canvas,
            body.left +
                dp(23f),
            body.top +
                h *
                0.49f,
            dp(7f)
        )

        drawSparkle(
            canvas,
            body.left +
                dp(34f),
            body.top +
                h *
                0.55f,
            dp(3.5f)
        )

        drawLeaf(
            canvas,
            body.left +
                dp(28f),
            body.top +
                h *
                0.66f,
            dp(27f),
            dp(13f),
            -145f
        )

        drawSparkle(
            canvas,
            body.right -
                dp(28f),
            body.top +
                h *
                0.54f,
            dp(5f)
        )

        drawLeaf(
            canvas,
            body.right -
                dp(26f),
            body.top +
                h *
                0.66f,
            dp(24f),
            dp(12f),
            -35f
        )
    }

    private fun drawYayaTail(
        canvas: Canvas,
        body: RectF
    ) {
        val anchor =
            tailCenter.coerceIn(
                body.left +
                    dp(48f),
                body.right -
                    dp(48f)
            )

        val y =
            if (tailAtTop) {
                body.top -
                    dp(3f)
            } else {
                body.bottom +
                    dp(4f)
            }

        drawLeaf(
            canvas,
            anchor,
            y,
            dp(32f),
            dp(16f),
            if (tailAtTop) {
                -90f
            } else {
                92f
            }
        )
    }

    private fun drawLeaf(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        leafWidth: Float,
        leafHeight: Float,
        rotation: Float
    ) {
        canvas.save()

        canvas.rotate(
            rotation,
            centerX,
            centerY
        )

        val halfW =
            leafWidth /
                2f

        val halfH =
            leafHeight /
                2f

        val leaf =
            Path().apply {
                moveTo(
                    centerX -
                        halfW,
                    centerY
                )

                cubicTo(
                    centerX -
                        halfW *
                        0.28f,
                    centerY -
                        halfH *
                        1.15f,
                    centerX +
                        halfW *
                        0.55f,
                    centerY -
                        halfH,
                    centerX +
                        halfW,
                    centerY
                )

                cubicTo(
                    centerX +
                        halfW *
                        0.45f,
                    centerY +
                        halfH,
                    centerX -
                        halfW *
                        0.35f,
                    centerY +
                        halfH *
                        1.05f,
                    centerX -
                        halfW,
                    centerY
                )

                close()
            }

        val leafFill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.rgb(
                        150,
                        198,
                        74
                    )
            }

        canvas.drawPath(
            leaf,
            leafFill
        )

        val leafStroke =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(1.2f)

                color =
                    Color.rgb(
                        87,
                        137,
                        44
                    )
            }

        canvas.drawPath(
            leaf,
            leafStroke
        )

        val vein =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(0.75f)

                color =
                    Color.argb(
                        135,
                        76,
                        124,
                        45
                    )
            }

        canvas.drawLine(
            centerX -
                halfW *
                0.62f,
            centerY,
            centerX +
                halfW *
                0.70f,
            centerY,
            vein
        )

        canvas.restore()
    }

    private fun drawFlower(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        petalRadius: Float
    ) {
        val petal =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.rgb(
                        255,
                        253,
                        246
                    )

                setShadowLayer(
                    dp(1.8f),
                    0f,
                    dp(0.8f),
                    Color.argb(
                        30,
                        92,
                        114,
                        48
                    )
                )
            }

        for (i in 0 until 5) {
            val angle =
                Math.toRadians(
                    (
                        i *
                            72 -
                            90
                        ).toDouble()
                )

            val px =
                centerX +
                    kotlin.math.cos(
                        angle
                    ).toFloat() *
                    petalRadius *
                    0.95f

            val py =
                centerY +
                    kotlin.math.sin(
                        angle
                    ).toFloat() *
                    petalRadius *
                    0.95f

            canvas.drawCircle(
                px,
                py,
                petalRadius *
                    0.74f,
                petal
            )
        }

        petal.clearShadowLayer()

        val center =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.rgb(
                        248,
                        177,
                        39
                    )
            }

        canvas.drawCircle(
            centerX,
            centerY,
            petalRadius *
                0.48f,
            center
        )
    }

    private fun drawDewBubble(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val bubble =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    Color.argb(
                        165,
                        196,
                        225,
                        105
                    )
            }

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            bubble
        )

        val rim =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(1f)

                color =
                    Color.rgb(
                        105,
                        158,
                        54
                    )
            }

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            rim
        )

        val highlight =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        225,
                        255,
                        255,
                        245
                    )
            }

        canvas.drawCircle(
            centerX -
                radius *
                0.32f,
            centerY -
                radius *
                0.34f,
            radius *
                0.24f,
            highlight
        )
    }

    private fun drawSparkle(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float
    ) {
        val star =
            Path().apply {
                moveTo(
                    centerX,
                    centerY -
                        radius
                )

                lineTo(
                    centerX +
                        radius *
                        0.28f,
                    centerY -
                        radius *
                        0.27f
                )

                lineTo(
                    centerX +
                        radius,
                    centerY
                )

                lineTo(
                    centerX +
                        radius *
                        0.28f,
                    centerY +
                        radius *
                        0.27f
                )

                lineTo(
                    centerX,
                    centerY +
                        radius
                )

                lineTo(
                    centerX -
                        radius *
                        0.28f,
                    centerY +
                        radius *
                        0.27f
                )

                lineTo(
                    centerX -
                        radius,
                    centerY
                )

                lineTo(
                    centerX -
                        radius *
                        0.28f,
                    centerY -
                        radius *
                        0.27f
                )

                close()
            }

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.rgb(
                        247,
                        184,
                        55
                    )
            }

        canvas.drawPath(
            star,
            paint
        )
    }

    private fun updatePadding() {
        if (yayaTheme) {
            setPadding(
                dpInt(22),
                dpInt(24),
                dpInt(22),
                dpInt(34)
            )
            return
        }

        if (yutuanTheme) {
            setPadding(
                dpInt(22),
                dpInt(23),
                dpInt(22),
                dpInt(33)
            )
            return
        }

        val horizontal = dpInt(21)
        val bodyVertical = dpInt(15)
        val tail = dpInt(12)

        setPadding(
            horizontal,
            bodyVertical +
                if (tailAtTop) {
                    tail
                } else {
                    0
                },
            horizontal,
            bodyVertical +
                if (tailAtTop) {
                    0
                } else {
                    tail
                }
        )
    }

    private fun applyTone(
        tone: Tone
    ) {
        fillPaint.style =
            Paint.Style.FILL

        fillPaint.color =
            when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        255,
                        251,
                        245
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        255,
                        247,
                        231
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        248,
                        251,
                        239
                    )

                Tone.MOOD ->
                    Color.rgb(
                        250,
                        247,
                        252
                    )
            }

        strokePaint.color =
            when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        239,
                        216,
                        190
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        239,
                        187,
                        121
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        201,
                        220,
                        169
                    )

                Tone.MOOD ->
                    Color.rgb(
                        220,
                        207,
                        232
                    )
            }
    }

    private fun accentColor(
        tone: Tone
    ): Int {
        if (yayaTheme) {
            return when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        84,
                        126,
                        50
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        109,
                        137,
                        47
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        65,
                        128,
                        62
                    )

                Tone.MOOD ->
                    Color.rgb(
                        91,
                        126,
                        70
                    )
            }
        }

        if (yutuanTheme) {
            return when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        73,
                        139,
                        205
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        66,
                        129,
                        198
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        83,
                        151,
                        177
                    )

                Tone.MOOD ->
                    Color.rgb(
                        99,
                        133,
                        185
                    )
            }
        }

        return when (tone) {
            Tone.NORMAL ->
                Color.rgb(
                    221,
                    145,
                    76
                )

            Tone.REMINDER ->
                Color.rgb(
                    224,
                    127,
                    44
                )

            Tone.SUCCESS ->
                Color.rgb(
                    116,
                    148,
                    79
                )

            Tone.MOOD ->
                Color.rgb(
                    142,
                    116,
                    166
                )
        }
    }

    private fun accentPaleColor(
        tone: Tone
    ): Int {
        if (yayaTheme) {
            return when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        239,
                        248,
                        218
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        247,
                        246,
                        211
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        227,
                        245,
                        216
                    )

                Tone.MOOD ->
                    Color.rgb(
                        235,
                        245,
                        224
                    )
            }
        }

        if (yutuanTheme) {
            return when (tone) {
                Tone.NORMAL ->
                    Color.rgb(
                        229,
                        243,
                        255
                    )

                Tone.REMINDER ->
                    Color.rgb(
                        222,
                        239,
                        255
                    )

                Tone.SUCCESS ->
                    Color.rgb(
                        226,
                        245,
                        247
                    )

                Tone.MOOD ->
                    Color.rgb(
                        232,
                        239,
                        251
                    )
            }
        }

        return when (tone) {
            Tone.NORMAL ->
                Color.rgb(
                    255,
                    237,
                    215
                )

            Tone.REMINDER ->
                Color.rgb(
                    255,
                    229,
                    193
                )

            Tone.SUCCESS ->
                Color.rgb(
                    232,
                    241,
                    215
                )

            Tone.MOOD ->
                Color.rgb(
                    239,
                    231,
                    246
                )
        }
    }

    private fun dp(
        value: Float
    ): Float {
        return value *
            resources
                .displayMetrics
                .density
    }

    private fun dpInt(
        value: Int
    ): Int {
        return (
            value *
                resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    private fun sp(
        value: Float
    ): Float {
        return value *
            resources
                .displayMetrics
                .scaledDensity
    }
}
