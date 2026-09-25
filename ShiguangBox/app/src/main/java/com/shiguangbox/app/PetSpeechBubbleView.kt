package com.shiguangbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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

    private val orangeTheme =
        petKind == PetKind.ORANGE

    private val yayaTheme =
        petKind == PetKind.YAYA

    private val yutuanTheme =
        petKind == PetKind.YUTUAN

    private val imageBubbleTheme =
        orangeTheme ||
            yayaTheme ||
            yutuanTheme

    private val orangeBubbleBitmap: Bitmap? by lazy {
        if (orangeTheme) {
            runCatching {
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.pet_orange_bubble
                )
            }.getOrNull()
        } else {
            null
        }
    }

    private val yayaBubbleBitmap: Bitmap? by lazy {
        if (yayaTheme) {
            runCatching {
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.pet_yaya_bubble
                )
            }.getOrNull()
        } else {
            null
        }
    }

    private val yutuanBubbleBitmap: Bitmap? by lazy {
        if (yutuanTheme) {
            runCatching {
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.pet_yutuan_bubble
                )
            }.getOrNull()
        } else {
            null
        }
    }

    private val imageBubblePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
        )

    private val yutuanBitmapPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                Paint.FILTER_BITMAP_FLAG
        )

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
    private var compactMode = false
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
        elevation =
            if (imageBubbleTheme) {
                0f
            } else {
                dp(10f)
            }
        updatePadding()
        applyTone(Tone.NORMAL)
    }

    fun isYayaTheme(): Boolean =
        yayaTheme

    fun isDecorativeTheme(): Boolean =
        imageBubbleTheme

    fun setCompactMode(
        enabled: Boolean
    ) {
        if (
            compactMode ==
            enabled
        ) {
            return
        }

        compactMode =
            enabled

        updatePadding()
        requestLayout()
        invalidate()
    }

    fun minimumOverlayHeightPx(): Int =
        dpInt(
            when {
                orangeTheme ->
                    if (actionCount > 0) {
                        if (compactMode) 140 else 152
                    } else {
                        if (compactMode) 104 else 120
                    }

                yayaTheme ->
                    if (actionCount > 0) {
                        if (compactMode) 144 else 156
                    } else {
                        if (compactMode) 108 else 124
                    }

                yutuanTheme ->
                    if (actionCount > 0) {
                        if (compactMode) 140 else 152
                    } else {
                        if (compactMode) 104 else 120
                    }

                else -> 72
            }
        )

    fun tailAnchorFraction(): Float =
        when {
            orangeTheme -> 0.31f
            yayaTheme -> 0.32f
            yutuanTheme -> 0.76f
            else -> 0.50f
        }

    private fun backgroundMirrorX(): Boolean {
        if (!imageBubbleTheme) {
            return false
        }

        val desiredOnRight =
            tailCenter >
                width * 0.5f

        val sourceOnRight =
            yutuanTheme

        return desiredOnRight !=
            sourceOnRight
    }

    private fun backgroundMirrorY(): Boolean =
        imageBubbleTheme &&
            tailAtTop

    private fun withBubbleBackgroundTransform(
        canvas: Canvas,
        draw: () -> Unit
    ) {
        val mirrorX =
            backgroundMirrorX()
        val mirrorY =
            backgroundMirrorY()

        if (
            !mirrorX &&
            !mirrorY
        ) {
            draw()
            return
        }

        val save =
            canvas.save()

        canvas.scale(
            if (mirrorX) -1f else 1f,
            if (mirrorY) -1f else 1f,
            width / 2f,
            height / 2f
        )

        draw()

        canvas.restoreToCount(
            save
        )
    }

    fun preferredWidthPx(
        screenWidthPx: Int
    ): Int {
        if (!imageBubbleTheme) {
            return dpInt(248)
        }

        val minWidth =
            dpInt(
                when {
                    orangeTheme ->
                        if (compactMode) 154 else 166
                    yayaTheme ->
                        if (compactMode) 158 else 170
                    yutuanTheme ->
                        if (compactMode) 154 else 166
                    else ->
                        if (compactMode) 154 else 166
                }
            )

        val maxWidth =
            minOf(
                dpInt(
                    when {
                        orangeTheme ->
                            if (compactMode) 218 else 240
                        yayaTheme ->
                            if (compactMode) 222 else 246
                        yutuanTheme ->
                            if (compactMode) 216 else 238
                        else ->
                            if (compactMode) 218 else 240
                    }
                ),
                (
                    screenWidthPx *
                        when {
                            orangeTheme ->
                                if (compactMode) 0.56f else 0.62f
                            yayaTheme ->
                                if (compactMode) 0.58f else 0.64f
                            yutuanTheme ->
                                if (compactMode) 0.56f else 0.62f
                            else ->
                                if (compactMode) 0.56f else 0.62f
                        }
                    ).toInt()
            ).coerceAtLeast(minWidth)

        sizingPaint.textSize =
            sp(
                when {
                    orangeTheme -> 11.8f
                    yayaTheme -> 12.2f
                    yutuanTheme -> 11.8f
                    else -> 12f
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
                when {
                    orangeTheme -> 9.2f
                    yayaTheme -> 9.4f
                    yutuanTheme -> 9.2f
                    else -> 9.2f
                }
            )

        sizingPaint.typeface =
            Typeface.DEFAULT_BOLD

        val titleWidth =
            sizingPaint
                .measureText(boundTitle)

        val messagePadding =
            dp(
                when {
                    orangeTheme -> 44f
                    yayaTheme -> 44f
                    yutuanTheme -> 42f
                    else -> 44f
                }
            )

        val titlePadding =
            dp(
                when {
                    orangeTheme -> 44f
                    yayaTheme -> 48f
                    yutuanTheme -> 46f
                    else -> 44f
                }
            )

        var desired =
            maxOf(
                minWidth,
                (
                    widestMessage +
                        messagePadding
                    ).toInt(),
                (
                    titleWidth +
                        titlePadding
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
                            when {
                                orangeTheme -> 220
                                yayaTheme -> 228
                                yutuanTheme -> 220
                                else -> 220
                            }
                        )
                    )

                length >= 34 ->
                    maxWidth

                length >= 22 ->
                    maxOf(
                        desired,
                        dpInt(
                            when {
                                orangeTheme -> 218
                                yayaTheme -> 226
                                yutuanTheme -> 218
                                else -> 218
                            }
                        )
                    )

                length >= 12 ->
                    maxOf(
                        desired,
                        dpInt(
                            when {
                                orangeTheme -> 196
                                yayaTheme -> 202
                                yutuanTheme -> 196
                                else -> 196
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
                        orangeTheme -> 9.2f
                        yayaTheme -> 9.4f
                        yutuanTheme -> 9.2f
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
                            imageBubbleTheme
                        ) {
                            7
                        } else {
                            9
                        }
                    ),
                    dpInt(
                        if (
                            imageBubbleTheme
                        ) {
                            2
                        } else {
                            4
                        }
                    ),
                    dpInt(
                        if (
                            imageBubbleTheme
                        ) {
                            7
                        } else {
                            9
                        }
                    ),
                    dpInt(
                        if (
                            imageBubbleTheme
                        ) {
                            2
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
                                    orangeTheme -> 10f
                                    yayaTheme -> 10.5f
                                    yutuanTheme -> 10f
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
                        orangeTheme -> 11.8f
                        yayaTheme -> 12.2f
                        yutuanTheme -> 11.8f
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
                        orangeTheme -> 1.10f
                        yayaTheme -> 1.12f
                        yutuanTheme -> 1.10f
                        else -> 1.14f
                    }
                )

                maxLines =
                    if (imageBubbleTheme) {
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
                            imageBubbleTheme
                        ) {
                            6
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
                                7
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
                        orangeTheme -> 9.8f
                        yayaTheme -> 10.0f
                        yutuanTheme -> 9.8f
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
                                if (imageBubbleTheme) {
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
                        orangeTheme -> 31
                        yayaTheme -> 32
                        yutuanTheme -> 31
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

        updatePadding()
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

        if (imageBubbleTheme) {
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
        if (orangeTheme) {
            drawOrangeBubble(canvas)
            super.onDraw(canvas)
            return
        }

        if (yayaTheme) {
            drawYayaImageBubble(canvas)
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

    private fun drawOrangeBubble(
        canvas: Canvas
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val bitmap =
            orangeBubbleBitmap
                ?: return

        withBubbleBackgroundTransform(
            canvas
        ) {
            drawExactImageBubble(
                canvas = canvas,
                bitmap = bitmap,
                destination =
                    RectF(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat()
                    ),
                sourceLeftFraction = 0.31f,
                sourceRightFraction = 0.69f,
                sourceTopFraction = 0.35f,
                sourceBottomFraction = 0.69f,
                leftEdgeDp = 40f,
                rightEdgeDp = 44f,
                topEdgeDp = 38f,
                bottomEdgeDp = 40f
            )
        }
    }

    private fun drawYayaImageBubble(
        canvas: Canvas
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val bitmap =
            yayaBubbleBitmap
                ?: return

        withBubbleBackgroundTransform(
            canvas
        ) {
            drawExactImageBubble(
                canvas = canvas,
                bitmap = bitmap,
                destination =
                    RectF(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat()
                    ),
                sourceLeftFraction = 0.32f,
                sourceRightFraction = 0.70f,
                sourceTopFraction = 0.35f,
                sourceBottomFraction = 0.69f,
                leftEdgeDp = 44f,
                rightEdgeDp = 44f,
                topEdgeDp = 40f,
                bottomEdgeDp = 42f
            )
        }
    }

    private fun drawExactImageBubble(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        sourceLeftFraction: Float,
        sourceRightFraction: Float,
        sourceTopFraction: Float,
        sourceBottomFraction: Float,
        leftEdgeDp: Float,
        rightEdgeDp: Float,
        topEdgeDp: Float,
        bottomEdgeDp: Float
    ) {
        val sourceLeft =
            (bitmap.width *
                sourceLeftFraction)
                .toInt()
                .coerceIn(
                    1,
                    bitmap.width - 2
                )

        val sourceRight =
            (bitmap.width *
                sourceRightFraction)
                .toInt()
                .coerceIn(
                    sourceLeft + 1,
                    bitmap.width - 1
                )

        val sourceTop =
            (bitmap.height *
                sourceTopFraction)
                .toInt()
                .coerceIn(
                    1,
                    bitmap.height - 2
                )

        val sourceBottom =
            (bitmap.height *
                sourceBottomFraction)
                .toInt()
                .coerceIn(
                    sourceTop + 1,
                    bitmap.height - 1
                )

        val leftEdge =
            minOf(
                dp(leftEdgeDp),
                destination.width() *
                    0.30f
            )

        val rightEdge =
            minOf(
                dp(rightEdgeDp),
                destination.width() *
                    0.30f
            )

        val topEdge =
            minOf(
                dp(topEdgeDp),
                destination.height() *
                    0.31f
            )

        val bottomEdge =
            minOf(
                dp(bottomEdgeDp),
                destination.height() *
                    0.32f
            )

        val dx =
            floatArrayOf(
                destination.left,
                destination.left +
                    leftEdge,
                destination.right -
                    rightEdge,
                destination.right
            )

        val dy =
            floatArrayOf(
                destination.top,
                destination.top +
                    topEdge,
                destination.bottom -
                    bottomEdge,
                destination.bottom
            )

        val sx =
            intArrayOf(
                0,
                sourceLeft,
                sourceRight,
                bitmap.width
            )

        val sy =
            intArrayOf(
                0,
                sourceTop,
                sourceBottom,
                bitmap.height
            )

        for (row in 0 until 3) {
            for (column in 0 until 3) {
                canvas.drawBitmap(
                    bitmap,
                    Rect(
                        sx[column],
                        sy[row],
                        sx[column + 1],
                        sy[row + 1]
                    ),
                    RectF(
                        dx[column],
                        dy[row],
                        dx[column + 1],
                        dy[row + 1]
                    ),
                    imageBubblePaint
                )
            }
        }
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

        val bitmap =
            yutuanBubbleBitmap
                ?: return

        withBubbleBackgroundTransform(
            canvas
        ) {
            drawYutuanNineSlice(
                canvas = canvas,
                bitmap = bitmap,
                destination =
                    RectF(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat()
                    )
            )
        }
    }

    private fun drawYutuanNineSlice(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF
    ) {
        // The exact selected artwork is kept on the four edges/corners.
        // Only the clean white center is stretched with text length.
        val sourceLeft =
            (bitmap.width * 0.245f)
                .toInt()
                .coerceIn(
                    1,
                    bitmap.width - 2
                )

        val sourceRight =
            (bitmap.width * 0.775f)
                .toInt()
                .coerceIn(
                    sourceLeft + 1,
                    bitmap.width - 1
                )

        val sourceTop =
            (bitmap.height * 0.285f)
                .toInt()
                .coerceIn(
                    1,
                    bitmap.height - 2
                )

        val sourceBottom =
            (bitmap.height * 0.735f)
                .toInt()
                .coerceIn(
                    sourceTop + 1,
                    bitmap.height - 1
                )

        val leftEdge =
            minOf(
                dp(36f),
                destination.width() *
                    0.28f
            )

        val rightEdge =
            minOf(
                dp(34f),
                destination.width() *
                    0.26f
            )

        val topEdge =
            minOf(
                dp(32f),
                destination.height() *
                    0.30f
            )

        val bottomEdge =
            minOf(
                dp(34f),
                destination.height() *
                    0.31f
            )

        val dx =
            floatArrayOf(
                destination.left,
                destination.left +
                    leftEdge,
                destination.right -
                    rightEdge,
                destination.right
            )

        val dy =
            floatArrayOf(
                destination.top,
                destination.top +
                    topEdge,
                destination.bottom -
                    bottomEdge,
                destination.bottom
            )

        val sx =
            intArrayOf(
                0,
                sourceLeft,
                sourceRight,
                bitmap.width
            )

        val sy =
            intArrayOf(
                0,
                sourceTop,
                sourceBottom,
                bitmap.height
            )

        for (row in 0 until 3) {
            for (column in 0 until 3) {
                canvas.drawBitmap(
                    bitmap,
                    Rect(
                        sx[column],
                        sy[row],
                        sx[column + 1],
                        sy[row + 1]
                    ),
                    RectF(
                        dx[column],
                        dy[row],
                        dx[column + 1],
                        dy[row + 1]
                    ),
                    yutuanBitmapPaint
                )
            }
        }
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
        if (orangeTheme) {
            setPadding(
                dpInt(if (compactMode) 21 else 25),
                dpInt(if (compactMode) 21 else 25),
                dpInt(if (compactMode) 21 else 25),
                dpInt(if (compactMode) 24 else 29)
            )
            return
        }

        if (yayaTheme) {
            setPadding(
                dpInt(if (compactMode) 23 else 28),
                dpInt(if (compactMode) 23 else 28),
                dpInt(if (compactMode) 23 else 28),
                dpInt(if (compactMode) 25 else 30)
            )
            return
        }

        if (yutuanTheme) {
            setPadding(
                dpInt(if (compactMode) 20 else 24),
                dpInt(if (compactMode) 21 else 26),
                dpInt(if (compactMode) 20 else 24),
                dpInt(if (compactMode) 24 else 29)
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
