package com.shiguangbox.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PetHome3DView(
    context: Context
) : TextureView(
    context
),
    TextureView.SurfaceTextureListener {

    private val homeRenderer =
        PetHome3DRenderer()

    @Volatile
    private var renderThread:
        Thread? =
        null

    @Volatile
    private var rendering =
        false

    @Volatile
    private var paused =
        false

    @Volatile
    private var pendingWidth =
        1

    @Volatile
    private var pendingHeight =
        1

    private var lastTouchX =
        0f

    private var lastTouchY =
        0f

    private var lastPinchDistance =
        0f

    init {
        surfaceTextureListener =
            this

        isClickable =
            true

        isFocusable =
            true

        isFocusableInTouchMode =
            true
    }

    fun onResume() {
        paused =
            false

        if (
            isAvailable
        ) {
            surfaceTexture
                ?.let {
                    texture ->
                    startRendererIfNeeded(
                        texture,
                        width
                            .coerceAtLeast(
                                1
                            ),
                        height
                            .coerceAtLeast(
                                1
                            )
                    )
                }
        }
    }

    fun onPause() {
        paused =
            true
    }

    fun resetCamera() {
        homeRenderer
            .resetCamera()
    }

    fun orbitCamera(
        dx: Float,
        dy: Float
    ) {
        homeRenderer
            .rotateCamera(
                dx,
                dy
            )
    }

    fun zoomCamera(
        scaleFactor: Float
    ) {
        homeRenderer
            .zoomBy(
                scaleFactor
            )
    }

    fun goToBed() {
        homeRenderer
            .goToAnchor(
                Home3DAnchor.BED
            )
    }

    fun goToWindow() {
        homeRenderer
            .goToAnchor(
                Home3DAnchor.WINDOW
            )
    }

    fun goToRug() {
        homeRenderer
            .goToAnchor(
                Home3DAnchor.RUG
            )
    }

    fun goToToy() {
        homeRenderer
            .goToAnchor(
                Home3DAnchor.TOY
            )
    }

    fun setAutoTour(
        enabled: Boolean
    ) {
        homeRenderer
            .setAutoTour(
                enabled
            )
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        when (
            event.actionMasked
        ) {
            MotionEvent.ACTION_DOWN -> {
                parent
                    ?.requestDisallowInterceptTouchEvent(
                        true
                    )

                lastTouchX =
                    event.x

                lastTouchY =
                    event.y

                lastPinchDistance =
                    0f

                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                parent
                    ?.requestDisallowInterceptTouchEvent(
                        true
                    )

                if (
                    event.pointerCount >=
                    2
                ) {
                    lastPinchDistance =
                        pointerDistance(
                            event
                        )
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (
                    event.pointerCount >=
                    2
                ) {
                    val distance =
                        pointerDistance(
                            event
                        )

                    if (
                        lastPinchDistance >
                        1f &&
                        distance >
                        1f
                    ) {
                        zoomCamera(
                            (
                                distance /
                                    lastPinchDistance
                                )
                                .coerceIn(
                                    0.88f,
                                    1.14f
                                )
                        )
                    }

                    lastPinchDistance =
                        distance
                } else {
                    val dx =
                        event.x -
                            lastTouchX

                    val dy =
                        event.y -
                            lastTouchY

                    if (
                        kotlin.math.abs(
                            dx
                        ) >
                        0.1f ||
                        kotlin.math.abs(
                            dy
                        ) >
                        0.1f
                    ) {
                        orbitCamera(
                            dx,
                            dy
                        )
                    }

                    lastTouchX =
                        event.x

                    lastTouchY =
                        event.y
                }

                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                lastPinchDistance =
                    0f

                if (
                    event.pointerCount >
                    1
                ) {
                    val remaining =
                        if (
                            event.actionIndex ==
                            0
                        ) {
                            1
                        } else {
                            0
                        }

                    lastTouchX =
                        event.getX(
                            remaining
                        )

                    lastTouchY =
                        event.getY(
                            remaining
                        )
                }

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent
                    ?.requestDisallowInterceptTouchEvent(
                        false
                    )

                lastPinchDistance =
                    0f

                performClick()
                return true
            }
        }

        return true
    }

    override fun performClick():
        Boolean {
        super.performClick()
        return true
    }

    override fun onSurfaceTextureAvailable(
        surface:
            SurfaceTexture,
        width: Int,
        height: Int
    ) {
        pendingWidth =
            width
                .coerceAtLeast(
                    1
                )

        pendingHeight =
            height
                .coerceAtLeast(
                    1
                )

        startRendererIfNeeded(
            surface,
            pendingWidth,
            pendingHeight
        )
    }

    override fun onSurfaceTextureSizeChanged(
        surface:
            SurfaceTexture,
        width: Int,
        height: Int
    ) {
        pendingWidth =
            width
                .coerceAtLeast(
                    1
                )

        pendingHeight =
            height
                .coerceAtLeast(
                    1
                )
    }

    override fun onSurfaceTextureDestroyed(
        surface:
            SurfaceTexture
    ): Boolean {
        stopRenderer()
        return true
    }

    override fun onSurfaceTextureUpdated(
        surface:
            SurfaceTexture
    ) =
        Unit

    override fun onDetachedFromWindow() {
        stopRenderer()
        super.onDetachedFromWindow()
    }

    private fun pointerDistance(
        event: MotionEvent
    ): Float {
        if (
            event.pointerCount <
            2
        ) {
            return 0f
        }

        val dx =
            event.getX(
                0
            ) -
                event.getX(
                    1
                )

        val dy =
            event.getY(
                0
            ) -
                event.getY(
                    1
                )

        return kotlin.math.sqrt(
            dx *
                dx +
                dy *
                dy
        )
    }

    @Synchronized
    private fun startRendererIfNeeded(
        texture:
            SurfaceTexture,
        width: Int,
        height: Int
    ) {
        if (
            rendering
        ) {
            pendingWidth =
                width
            pendingHeight =
                height
            return
        }

        rendering =
            true

        pendingWidth =
            width

        pendingHeight =
            height

        val thread =
            Thread(
                {
                    runRenderLoop(
                        texture
                    )
                },
                "PetHome3D-EGL"
            )

        renderThread =
            thread

        thread.start()
    }

    @Synchronized
    private fun stopRenderer() {
        rendering =
            false

        val thread =
            renderThread

        thread
            ?.interrupt()

        if (
            thread !=
            null &&
            thread !==
            Thread.currentThread()
        ) {
            runCatching {
                thread.join(
                    500L
                )
            }
        }

        renderThread =
            null
    }

    private fun runRenderLoop(
        texture:
            SurfaceTexture
    ) {
        var display:
            EGLDisplay =
            EGL14.EGL_NO_DISPLAY

        var context:
            EGLContext =
            EGL14.EGL_NO_CONTEXT

        var eglSurface:
            EGLSurface =
            EGL14.EGL_NO_SURFACE

        var nativeSurface:
            Surface? =
            null

        try {
            display =
                EGL14.eglGetDisplay(
                    EGL14.EGL_DEFAULT_DISPLAY
                )

            if (
                display ==
                EGL14.EGL_NO_DISPLAY
            ) {
                return
            }

            val version =
                IntArray(
                    2
                )

            if (
                !EGL14.eglInitialize(
                    display,
                    version,
                    0,
                    version,
                    1
                )
            ) {
                return
            }

            val configAttributes =
                intArrayOf(
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_ALPHA_SIZE,
                    8,
                    EGL14.EGL_DEPTH_SIZE,
                    24,
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )

            val configs =
                arrayOfNulls<
                    EGLConfig
                    >(
                    1
                )

            val configCount =
                IntArray(
                    1
                )

            if (
                !EGL14.eglChooseConfig(
                    display,
                    configAttributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    configCount,
                    0
                ) ||
                configCount[0] <=
                0
            ) {
                return
            }

            val config =
                configs[0]
                    ?: return

            val contextAttributes =
                intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION,
                    2,
                    EGL14.EGL_NONE
                )

            context =
                EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    contextAttributes,
                    0
                )

            if (
                context ==
                EGL14.EGL_NO_CONTEXT
            ) {
                return
            }

            nativeSurface =
                Surface(
                    texture
                )

            eglSurface =
                EGL14.eglCreateWindowSurface(
                    display,
                    config,
                    nativeSurface,
                    intArrayOf(
                        EGL14.EGL_NONE
                    ),
                    0
                )

            if (
                eglSurface ==
                EGL14.EGL_NO_SURFACE
            ) {
                return
            }

            if (
                !EGL14.eglMakeCurrent(
                    display,
                    eglSurface,
                    eglSurface,
                    context
                )
            ) {
                return
            }

            homeRenderer
                .onSurfaceCreated()

            var appliedWidth =
                -1

            var appliedHeight =
                -1

            while (
                rendering &&
                !Thread
                    .currentThread()
                    .isInterrupted
            ) {
                if (
                    paused
                ) {
                    try {
                        Thread.sleep(
                            30L
                        )
                    } catch (
                        _: InterruptedException
                    ) {
                        break
                    }

                    continue
                }

                val targetWidth =
                    pendingWidth
                        .coerceAtLeast(
                            1
                        )

                val targetHeight =
                    pendingHeight
                        .coerceAtLeast(
                            1
                        )

                if (
                    targetWidth !=
                    appliedWidth ||
                    targetHeight !=
                    appliedHeight
                ) {
                    homeRenderer
                        .onSurfaceChanged(
                            targetWidth,
                            targetHeight
                        )

                    appliedWidth =
                        targetWidth

                    appliedHeight =
                        targetHeight
                }

                homeRenderer
                    .onDrawFrame()

                if (
                    !EGL14.eglSwapBuffers(
                        display,
                        eglSurface
                    )
                ) {
                    break
                }

                try {
                    Thread.sleep(
                        16L
                    )
                } catch (
                    _: InterruptedException
                ) {
                    break
                }
            }
        } finally {
            if (
                display !=
                EGL14.EGL_NO_DISPLAY
            ) {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )

                if (
                    eglSurface !=
                    EGL14.EGL_NO_SURFACE
                ) {
                    EGL14.eglDestroySurface(
                        display,
                        eglSurface
                    )
                }

                if (
                    context !=
                    EGL14.EGL_NO_CONTEXT
                ) {
                    EGL14.eglDestroyContext(
                        display,
                        context
                    )
                }

                EGL14.eglTerminate(
                    display
                )
            }

            nativeSurface
                ?.release()

            rendering =
                false
        }
    }
}

enum class Home3DAnchor(
    val x: Float,
    val z: Float,
    val label: String
) {
    BED(
        x = -2.10f,
        z = -1.35f,
        label = "床边"
    ),
    WINDOW(
        x = 1.95f,
        z = -1.52f,
        label = "窗边"
    ),
    RUG(
        x = 0.15f,
        z = 0.95f,
        label = "地毯"
    ),
    TOY(
        x = 1.55f,
        z = 1.38f,
        label = "玩具区"
    ),
    QUIET_CORNER(
        x = -1.75f,
        z = 1.48f,
        label = "安静角落"
    )
}

private class PetHome3DRenderer {

    private val projection =
        FloatArray(
            16
        )

    private val view =
        FloatArray(
            16
        )

    private val model =
        FloatArray(
            16
        )

    private val mvp =
        FloatArray(
            16
        )

    private val temp =
        FloatArray(
            16
        )

    private val normalMatrix =
        FloatArray(
            16
        )

    private val inverseModel =
        FloatArray(
            16
        )

    private var program =
        0

    private var positionHandle =
        0

    private var normalHandle =
        0

    private var mvpHandle =
        0

    private var modelHandle =
        0

    private var normalMatrixHandle =
        0

    private var colorHandle =
        0

    private lateinit var cube:
        Mesh3D

    private lateinit var sphere:
        Mesh3D

    @Volatile
    private var yaw =
        34f

    @Volatile
    private var pitch =
        25f

    @Volatile
    private var distance =
        8.8f

    @Volatile
    private var autoTour =
        true

    private var cameraEyeX =
        0f

    private var cameraEyeZ =
        0f

    private var petX =
        Home3DAnchor
            .RUG
            .x

    private var petZ =
        Home3DAnchor
            .RUG
            .z

    private var petFacingDegrees =
        180f

    @Volatile
    private var target =
        Home3DAnchor
            .RUG

    private var lastFrameNanos =
        0L

    @Volatile
    private var arrivedAtNanos =
        0L

    private var tourIndex =
        0

    private val tour =
        arrayOf(
            Home3DAnchor.RUG,
            Home3DAnchor.TOY,
            Home3DAnchor.WINDOW,
            Home3DAnchor.QUIET_CORNER,
            Home3DAnchor.BED,
            Home3DAnchor.RUG
        )

    @Volatile
    private var moving =
        false

    private var elapsedSeconds =
        0f

    fun onSurfaceCreated() {
        GLES20.glClearColor(
            0.93f,
            0.90f,
            0.84f,
            1f
        )

        GLES20.glEnable(
            GLES20.GL_DEPTH_TEST
        )

        GLES20.glEnable(
            GLES20.GL_CULL_FACE
        )

        GLES20.glCullFace(
            GLES20.GL_BACK
        )

        program =
            createProgram(
                VERTEX_SHADER,
                FRAGMENT_SHADER
            )

        positionHandle =
            GLES20.glGetAttribLocation(
                program,
                "aPosition"
            )

        normalHandle =
            GLES20.glGetAttribLocation(
                program,
                "aNormal"
            )

        mvpHandle =
            GLES20.glGetUniformLocation(
                program,
                "uMvp"
            )

        modelHandle =
            GLES20.glGetUniformLocation(
                program,
                "uModel"
            )

        normalMatrixHandle =
            GLES20.glGetUniformLocation(
                program,
                "uNormalMatrix"
            )

        colorHandle =
            GLES20.glGetUniformLocation(
                program,
                "uColor"
            )

        cube =
            Mesh3D.cube()

        sphere =
            Mesh3D.sphere(
                latitudeBands =
                    12,
                longitudeBands =
                    16
            )

        lastFrameNanos =
            System.nanoTime()

        arrivedAtNanos =
            lastFrameNanos
    }

    fun onSurfaceChanged(
        width: Int,
        height: Int
    ) {
        GLES20.glViewport(
            0,
            0,
            width,
            height
        )

        val ratio =
            width.toFloat() /
                height
                    .coerceAtLeast(
                        1
                    )
                    .toFloat()

        Matrix.perspectiveM(
            projection,
            0,
            42f,
            ratio,
            0.1f,
            50f
        )
    }

    fun onDrawFrame() {
        val now =
            System.nanoTime()

        val dt =
            (
                now -
                    lastFrameNanos
                )
                .coerceAtLeast(
                    0L
                )
                .coerceAtMost(
                    80_000_000L
                )
                .toFloat() /
                1_000_000_000f

        lastFrameNanos =
            now

        elapsedSeconds +=
            dt

        updatePet(
            now,
            dt
        )

        GLES20.glClear(
            GLES20.GL_COLOR_BUFFER_BIT or
                GLES20.GL_DEPTH_BUFFER_BIT
        )

        GLES20.glUseProgram(
            program
        )

        updateCamera()

        drawRoom()
        drawFurniture()
        drawPet()
    }

    fun rotateCamera(
        dx: Float,
        dy: Float
    ) {
        yaw =
            (
                yaw +
                    dx *
                    0.34f
                )
                .let {
                    angle ->
                    (
                        (
                            angle %
                                360f
                            ) +
                            360f
                        ) %
                        360f
                }

        pitch =
            (
                pitch -
                    dy *
                    0.22f
                )
                .coerceIn(
                    10f,
                    58f
                )
    }

    fun zoomBy(
        scaleFactor: Float
    ) {
        if (
            scaleFactor <=
            0f
        ) {
            return
        }

        distance =
            (
                distance /
                    scaleFactor
                )
                .coerceIn(
                    5.6f,
                    13.8f
                )
    }

    fun resetCamera() {
        yaw =
            34f
        pitch =
            25f
        distance =
            9.2f
    }

    fun setAutoTour(
        enabled: Boolean
    ) {
        autoTour =
            enabled
    }

    fun goToAnchor(
        anchor:
            Home3DAnchor
    ) {
        target =
            anchor
        moving =
            true
        arrivedAtNanos =
            0L
    }

    private fun updatePet(
        now: Long,
        dt: Float
    ) {
        if (
            !moving &&
            autoTour &&
            now -
                arrivedAtNanos >
            3_600_000_000L
        ) {
            tourIndex =
                (
                    tourIndex +
                        1
                    ) %
                    tour.size

            target =
                tour[
                    tourIndex
                ]

            moving =
                true
        }

        if (
            !moving
        ) {
            return
        }

        val dx =
            target.x -
                petX

        val dz =
            target.z -
                petZ

        val length =
            sqrt(
                dx *
                    dx +
                    dz *
                    dz
            )

        if (
            length <
            0.04f
        ) {
            petX =
                target.x
            petZ =
                target.z
            moving =
                false
            arrivedAtNanos =
                now
            return
        }

        val nx =
            dx /
                length

        val nz =
            dz /
                length

        val speed =
            0.72f

        val step =
            (
                speed *
                    dt
                )
                .coerceAtMost(
                    length
                )

        petX +=
            nx *
                step

        petZ +=
            nz *
                step

        petFacingDegrees =
            (
                atan2(
                    nx.toDouble(),
                    nz.toDouble()
                ) *
                    180.0 /
                    PI
                )
                .toFloat()
    }

    private fun updateCamera() {
        val yawRad =
            yaw /
                180f *
                PI.toFloat()

        val pitchRad =
            pitch /
                180f *
                PI.toFloat()

        val horizontal =
            distance *
                cos(
                    pitchRad
                )

        val eyeX =
            horizontal *
                sin(
                    yawRad
                )

        val eyeZ =
            horizontal *
                cos(
                    yawRad
                ) +
                0.7f

        val eyeY =
            distance *
                sin(
                    pitchRad
                ) +
                1.7f

        cameraEyeX =
            eyeX
        cameraEyeZ =
            eyeZ

        Matrix.setLookAtM(
            view,
            0,
            eyeX,
            eyeY,
            eyeZ,
            0f,
            0.8f,
            0f,
            0f,
            1f,
            0f
        )
    }

    private fun drawRoom() {
        val floor =
            floatArrayOf(
                0.62f,
                0.46f,
                0.32f,
                1f
            )

        val floorSeam =
            floatArrayOf(
                0.42f,
                0.31f,
                0.23f,
                1f
            )

        val wallBack =
            floatArrayOf(
                0.95f,
                0.89f,
                0.80f,
                1f
            )

        val wallLeft =
            floatArrayOf(
                0.90f,
                0.84f,
                0.76f,
                1f
            )

        val trim =
            floatArrayOf(
                0.73f,
                0.57f,
                0.40f,
                1f
            )

        val sky =
            floatArrayOf(
                0.50f,
                0.71f,
                0.82f,
                1f
            )

        val curtain =
            floatArrayOf(
                0.70f,
                0.45f,
                0.40f,
                1f
            )

        // Main wooden floor.
        drawBox(
            x = 0f,
            y = -0.12f,
            z = 0f,
            sx = 6.4f,
            sy = 0.16f,
            sz = 5.4f,
            color =
                floor
        )

        // Floor plank seams create real depth instead of one flat slab.
        var plankZ =
            -2.35f

        while (
            plankZ <=
            2.35f
        ) {
            drawBox(
                x = 0f,
                y = -0.025f,
                z = plankZ,
                sx = 6.15f,
                sy = 0.012f,
                sz = 0.018f,
                color =
                    floorSeam
            )

            plankZ +=
                0.43f
        }

        var jointX =
            -2.75f

        var row =
            0

        while (
            jointX <=
            2.75f
        ) {
            val offset =
                if (
                    row %
                        2 ==
                    0
                ) {
                    0.20f
                } else {
                    -0.20f
                }

            drawBox(
                x =
                    jointX,
                y =
                    -0.023f,
                z =
                    -1.40f +
                        offset,
                sx =
                    0.018f,
                sy =
                    0.012f,
                sz =
                    1.90f,
                color =
                    floorSeam
            )

            drawBox(
                x =
                    jointX +
                        0.55f,
                y =
                    -0.023f,
                z =
                    1.35f -
                        offset,
                sx =
                    0.018f,
                sy =
                    0.012f,
                sz =
                    1.85f,
                color =
                    floorSeam
            )

            jointX +=
                1.10f
            row +=
                1
        }

        // Doll-house style open corner walls. A wall disappears when the
        // orbit camera moves behind it so the room remains readable.
        val showBackWall =
            cameraEyeZ >
                -2.35f

        val showLeftWall =
            cameraEyeX >
                -2.90f

        if (
            showBackWall
        ) {
            drawBox(
                x = 0f,
                y = 2.0f,
                z = -2.60f,
                sx = 6.35f,
                sy = 4.15f,
                sz = 0.12f,
                color =
                    wallBack
            )

            drawBox(
                0f,
                0.12f,
                -2.49f,
                6.18f,
                0.20f,
                0.10f,
                trim
            )

            drawBox(
                0f,
                3.76f,
                -2.43f,
                6.20f,
                0.16f,
                0.18f,
                trim
            )
        }

        if (
            showLeftWall
        ) {
            drawBox(
                x = -3.12f,
                y = 2.0f,
                z = 0f,
                sx = 0.12f,
                sy = 4.15f,
                sz = 5.35f,
                color =
                    wallLeft
            )

            drawBox(
                -3.01f,
                0.12f,
                0f,
                0.10f,
                0.20f,
                5.16f,
                trim
            )

            drawBox(
                -2.95f,
                3.76f,
                0f,
                0.18f,
                0.16f,
                5.0f,
                trim
            )
        }

        if (
            showBackWall
        ) {
        // Window outside sky panel.
        drawBox(
            x = 1.87f,
            y = 2.16f,
            z = -2.50f,
            sx = 1.86f,
            sy = 1.70f,
            sz = 0.055f,
            color =
                sky
        )

        val frame =
            floatArrayOf(
                0.73f,
                0.56f,
                0.39f,
                1f
            )

        // Window frame and crossbars.
        drawBox(
            1.87f,
            3.06f,
            -2.37f,
            2.08f,
            0.13f,
            0.11f,
            frame
        )

        drawBox(
            1.87f,
            1.27f,
            -2.37f,
            2.08f,
            0.13f,
            0.11f,
            frame
        )

        drawBox(
            0.89f,
            2.16f,
            -2.37f,
            0.13f,
            1.92f,
            0.11f,
            frame
        )

        drawBox(
            2.85f,
            2.16f,
            -2.37f,
            0.13f,
            1.92f,
            0.11f,
            frame
        )

        drawBox(
            1.87f,
            2.16f,
            -2.35f,
            0.08f,
            1.70f,
            0.08f,
            frame
        )

        drawBox(
            1.87f,
            2.16f,
            -2.35f,
            1.86f,
            0.08f,
            0.08f,
            frame
        )

        // Window sill.
        drawBox(
            1.87f,
            1.16f,
            -2.18f,
            2.25f,
            0.13f,
            0.42f,
            frame
        )

        // Curtain rod.
        drawBox(
            1.87f,
            3.26f,
            -2.14f,
            2.70f,
            0.07f,
            0.07f,
            frame
        )

        // Curtains, split so the view outside remains visible.
        drawBox(
            0.56f,
            2.12f,
            -2.16f,
            0.42f,
            2.18f,
            0.10f,
            curtain
        )

        drawBox(
            3.18f,
            2.12f,
            -2.16f,
            0.42f,
            2.18f,
            0.10f,
            curtain
        )

        }

        // Wall art on left/back walls.
        val artFrame =
            floatArrayOf(
                0.42f,
                0.31f,
                0.22f,
                1f
            )

        if (
            showBackWall
        ) {
            drawBox(
                -1.05f,
                2.46f,
                -2.42f,
                0.92f,
                0.72f,
                0.08f,
                artFrame
            )

            drawBox(
                -1.05f,
                2.46f,
                -2.34f,
                0.72f,
                0.52f,
                0.045f,
                floatArrayOf(
                    0.63f,
                    0.75f,
                    0.59f,
                    1f
                )
            )
        }

        if (
            showLeftWall
        ) {
            drawBox(
                -2.98f,
                2.34f,
                0.62f,
                0.08f,
                0.88f,
                0.78f,
                artFrame
            )

            drawBox(
                -2.90f,
                2.34f,
                0.62f,
                0.045f,
                0.68f,
                0.58f,
                floatArrayOf(
                    0.80f,
                    0.62f,
                    0.45f,
                    1f
                )
            )
        }
    }


    private fun drawFurniture() {
        val wood =
            floatArrayOf(
                0.53f,
                0.36f,
                0.23f,
                1f
            )

        val woodLight =
            floatArrayOf(
                0.69f,
                0.49f,
                0.31f,
                1f
            )

        val cream =
            floatArrayOf(
                0.95f,
                0.88f,
                0.77f,
                1f
            )

        val terracotta =
            floatArrayOf(
                0.72f,
                0.40f,
                0.30f,
                1f
            )

        val moss =
            floatArrayOf(
                0.34f,
                0.50f,
                0.30f,
                1f
            )

        // Layered oval-ish rug built from two low boxes.
        drawBox(
            x = 0.12f,
            y = 0.015f,
            z = 0.95f,
            sx = 2.72f,
            sy = 0.055f,
            sz = 1.82f,
            color =
                floatArrayOf(
                    0.55f,
                    0.33f,
                    0.28f,
                    1f
                )
        )

        drawBox(
            x = 0.12f,
            y = 0.052f,
            z = 0.95f,
            sx = 2.48f,
            sy = 0.035f,
            sz = 1.58f,
            color =
                floatArrayOf(
                    0.80f,
                    0.54f,
                    0.43f,
                    1f
                )
        )

        // Bed frame.
        drawBox(
            -2.12f,
            0.18f,
            -1.34f,
            1.72f,
            0.28f,
            1.48f,
            wood
        )

        // Headboard and feet.
        drawBox(
            -2.12f,
            0.82f,
            -1.98f,
            1.72f,
            1.05f,
            0.12f,
            woodLight
        )

        for (
            x in
            listOf(
                -2.84f,
                -1.40f
            )
        ) {
            drawBox(
                x,
                0.10f,
                -0.78f,
                0.12f,
                0.34f,
                0.12f,
                wood
            )
        }

        // Mattress.
        drawBox(
            -2.12f,
            0.42f,
            -1.34f,
            1.55f,
            0.24f,
            1.30f,
            cream
        )

        // Blanket.
        drawBox(
            -2.12f,
            0.56f,
            -1.09f,
            1.48f,
            0.12f,
            0.68f,
            floatArrayOf(
                0.68f,
                0.48f,
                0.40f,
                1f
            )
        )

        // Pillow.
        drawBox(
            -2.12f,
            0.61f,
            -1.68f,
            0.70f,
            0.14f,
            0.34f,
            floatArrayOf(
                0.98f,
                0.94f,
                0.87f,
                1f
            )
        )

        // Bedside table.
        drawBox(
            -1.03f,
            0.52f,
            -1.64f,
            0.58f,
            0.10f,
            0.52f,
            woodLight
        )

        for (
            x in
            listOf(
                -1.22f,
                -0.84f
            )
        ) {
            for (
                z in
                listOf(
                    -1.82f,
                    -1.46f
                )
            ) {
                drawBox(
                    x,
                    0.26f,
                    z,
                    0.08f,
                    0.50f,
                    0.08f,
                    wood
                )
            }
        }

        // Small warm bedside lamp.
        drawBox(
            -1.03f,
            0.78f,
            -1.64f,
            0.07f,
            0.42f,
            0.07f,
            floatArrayOf(
                0.51f,
                0.39f,
                0.31f,
                1f
            )
        )

        drawSphere(
            -1.03f,
            1.04f,
            -1.64f,
            0.26f,
            0.18f,
            0.26f,
            floatArrayOf(
                0.96f,
                0.74f,
                0.37f,
                1f
            )
        )

        // Window-side reading table.
        drawBox(
            2.05f,
            0.74f,
            0.32f,
            1.25f,
            0.14f,
            0.76f,
            woodLight
        )

        for (
            x in
            listOf(
                1.55f,
                2.55f
            )
        ) {
            for (
                z in
                listOf(
                    0.08f,
                    0.56f
                )
            ) {
                drawBox(
                    x,
                    0.36f,
                    z,
                    0.11f,
                    0.68f,
                    0.11f,
                    wood
                )
            }
        }

        // Cup / tiny object on table.
        drawSphere(
            1.76f,
            0.92f,
            0.28f,
            0.11f,
            0.15f,
            0.11f,
            floatArrayOf(
                0.80f,
                0.85f,
                0.79f,
                1f
            )
        )

        // Collection shelf: open shelving instead of one block.
        drawBox(
            -2.54f,
            1.12f,
            1.58f,
            0.14f,
            2.12f,
            0.44f,
            wood
        )

        drawBox(
            -1.72f,
            1.12f,
            1.58f,
            0.14f,
            2.12f,
            0.44f,
            wood
        )

        for (
            y in
            listOf(
                0.16f,
                0.78f,
                1.40f,
                2.02f
            )
        ) {
            drawBox(
                -2.13f,
                y,
                1.58f,
                0.96f,
                0.10f,
                0.54f,
                woodLight
            )
        }

        // Books / boxes on shelves.
        drawBox(
            -2.34f,
            0.48f,
            1.55f,
            0.18f,
            0.46f,
            0.34f,
            floatArrayOf(
                0.63f,
                0.42f,
                0.36f,
                1f
            )
        )

        drawBox(
            -2.08f,
            0.45f,
            1.55f,
            0.16f,
            0.40f,
            0.32f,
            floatArrayOf(
                0.35f,
                0.54f,
                0.53f,
                1f
            )
        )

        drawBox(
            -1.84f,
            0.43f,
            1.55f,
            0.16f,
            0.36f,
            0.30f,
            floatArrayOf(
                0.70f,
                0.58f,
                0.35f,
                1f
            )
        )

        // Discovery objects on upper shelves.
        drawSphere(
            -2.32f,
            1.05f,
            1.50f,
            0.13f,
            0.13f,
            0.13f,
            floatArrayOf(
                0.97f,
                0.77f,
                0.23f,
                1f
            )
        )

        drawSphere(
            -1.92f,
            1.05f,
            1.50f,
            0.11f,
            0.14f,
            0.11f,
            floatArrayOf(
                0.47f,
                0.72f,
                0.55f,
                1f
            )
        )

        drawSphere(
            -2.15f,
            1.68f,
            1.50f,
            0.14f,
            0.10f,
            0.14f,
            floatArrayOf(
                0.53f,
                0.71f,
                0.86f,
                1f
            )
        )

        // Plant in the quiet corner.
        drawBox(
            -1.18f,
            0.23f,
            1.72f,
            0.38f,
            0.42f,
            0.38f,
            terracotta
        )

        drawBox(
            -1.18f,
            0.70f,
            1.72f,
            0.06f,
            0.72f,
            0.06f,
            floatArrayOf(
                0.30f,
                0.42f,
                0.25f,
                1f
            )
        )

        drawSphere(
            -1.42f,
            0.88f,
            1.72f,
            0.32f,
            0.13f,
            0.18f,
            moss
        )

        drawSphere(
            -0.94f,
            1.02f,
            1.70f,
            0.32f,
            0.14f,
            0.18f,
            moss
        )

        drawSphere(
            -1.22f,
            1.16f,
            1.88f,
            0.20f,
            0.14f,
            0.34f,
            moss
        )

        // Floor lamp.
        drawBox(
            2.68f,
            0.10f,
            1.62f,
            0.40f,
            0.10f,
            0.40f,
            floatArrayOf(
                0.43f,
                0.33f,
                0.27f,
                1f
            )
        )

        drawBox(
            2.68f,
            1.14f,
            1.62f,
            0.07f,
            2.08f,
            0.07f,
            floatArrayOf(
                0.49f,
                0.39f,
                0.31f,
                1f
            )
        )

        drawSphere(
            2.68f,
            2.24f,
            1.62f,
            0.42f,
            0.30f,
            0.42f,
            floatArrayOf(
                0.97f,
                0.76f,
                0.42f,
                1f
            )
        )

        // Toy basket / toy area.
        drawBox(
            1.50f,
            0.20f,
            1.70f,
            0.68f,
            0.34f,
            0.52f,
            floatArrayOf(
                0.64f,
                0.47f,
                0.31f,
                1f
            )
        )

        drawBox(
            1.50f,
            0.41f,
            1.70f,
            0.78f,
            0.08f,
            0.62f,
            woodLight
        )

        drawSphere(
            1.30f,
            0.49f,
            1.62f,
            0.22f,
            0.22f,
            0.22f,
            floatArrayOf(
                0.38f,
                0.60f,
                0.74f,
                1f
            )
        )

        drawSphere(
            1.70f,
            0.48f,
            1.76f,
            0.18f,
            0.18f,
            0.18f,
            floatArrayOf(
                0.78f,
                0.48f,
                0.40f,
                1f
            )
        )

        // Food and water bowls close to the wall.
        drawSphere(
            2.42f,
            0.12f,
            -0.72f,
            0.28f,
            0.09f,
            0.28f,
            floatArrayOf(
                0.64f,
                0.70f,
                0.73f,
                1f
            )
        )

        drawSphere(
            1.84f,
            0.12f,
            -0.72f,
            0.28f,
            0.09f,
            0.28f,
            floatArrayOf(
                0.72f,
                0.53f,
                0.43f,
                1f
            )
        )

        // Soft floor cushion in quiet corner.
        drawSphere(
            -1.72f,
            0.20f,
            1.28f,
            0.58f,
            0.16f,
            0.46f,
            floatArrayOf(
                0.74f,
                0.68f,
                0.57f,
                1f
            )
        )
    }


    private fun drawPet() {
        val walkBob =
            if (
                moving
            ) {
                abs(
                    sin(
                        elapsedSeconds *
                            9.5f
                    )
                ) *
                    0.07f
            } else {
                sin(
                    elapsedSeconds *
                        2.2f
                ) *
                    0.015f
            }

        val root =
            FloatArray(
                16
            )

        Matrix.setIdentityM(
            root,
            0
        )

        Matrix.translateM(
            root,
            0,
            petX,
            0.42f +
                walkBob,
            petZ
        )

        Matrix.rotateM(
            root,
            0,
            petFacingDegrees,
            0f,
            1f,
            0f
        )

        val orange =
            floatArrayOf(
                0.92f,
                0.51f,
                0.19f,
                1f
            )

        val cream =
            floatArrayOf(
                0.98f,
                0.82f,
                0.62f,
                1f
            )

        val dark =
            floatArrayOf(
                0.10f,
                0.08f,
                0.07f,
                1f
            )

        // Soft contact shadow under the pet.
        drawSphere(
            x =
                petX,
            y =
                0.012f,
            z =
                petZ,
            sx =
                0.78f,
            sy =
                0.035f,
            sz =
                0.56f,
            color =
                floatArrayOf(
                    0.28f,
                    0.22f,
                    0.18f,
                    1f
                )
        )

        // Body.
        drawChildSphere(
            root,
            0f,
            0.45f,
            0f,
            0.48f,
            0.58f,
            0.52f,
            orange
        )

        // Head.
        drawChildSphere(
            root,
            0f,
            1.03f,
            0.06f,
            0.43f,
            0.42f,
            0.41f,
            orange
        )

        // Muzzle.
        drawChildSphere(
            root,
            0f,
            0.94f,
            0.39f,
            0.23f,
            0.17f,
            0.12f,
            cream
        )

        // Eyes.
        drawChildSphere(
            root,
            -0.15f,
            1.10f,
            0.39f,
            0.055f,
            0.065f,
            0.045f,
            dark
        )

        drawChildSphere(
            root,
            0.15f,
            1.10f,
            0.39f,
            0.055f,
            0.065f,
            0.045f,
            dark
        )

        // Ears.
        drawChildBox(
            root,
            -0.24f,
            1.38f,
            0.02f,
            0.18f,
            0.32f,
            0.16f,
            -14f,
            orange
        )

        drawChildBox(
            root,
            0.24f,
            1.38f,
            0.02f,
            0.18f,
            0.32f,
            0.16f,
            14f,
            orange
        )

        // Legs with alternating walk lift.
        val step =
            if (
                moving
            ) {
                sin(
                    elapsedSeconds *
                        10.0f
                ) *
                    0.07f
            } else {
                0f
            }

        drawChildBox(
            root,
            -0.22f,
            0.05f +
                step,
            0.22f,
            0.16f,
            0.34f,
            0.18f,
            0f,
            orange
        )

        drawChildBox(
            root,
            0.22f,
            0.05f -
                step,
            0.22f,
            0.16f,
            0.34f,
            0.18f,
            0f,
            orange
        )

        drawChildBox(
            root,
            -0.22f,
            0.04f -
                step,
            -0.19f,
            0.18f,
            0.32f,
            0.20f,
            0f,
            orange
        )

        drawChildBox(
            root,
            0.22f,
            0.04f +
                step,
            -0.19f,
            0.18f,
            0.32f,
            0.20f,
            0f,
            orange
        )

        // Tail: stretched sphere. Small sway while walking.
        val tailRoot =
            root.copyOf()

        Matrix.translateM(
            tailRoot,
            0,
            -0.43f,
            0.52f,
            -0.19f
        )

        Matrix.rotateM(
            tailRoot,
            0,
            -52f +
                sin(
                    elapsedSeconds *
                        if (
                            moving
                        ) {
                            7.5f
                        } else {
                            2.4f
                        }
                ) *
                16f,
            0f,
            1f,
            0f
        )

        Matrix.rotateM(
            tailRoot,
            0,
            24f,
            1f,
            0f,
            0f
        )

        Matrix.scaleM(
            tailRoot,
            0,
            0.13f,
            0.13f,
            0.52f
        )

        drawMesh(
            sphere,
            tailRoot,
            orange
        )
    }

    private fun drawChildSphere(
        parent: FloatArray,
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        color: FloatArray
    ) {
        val m =
            parent.copyOf()

        Matrix.translateM(
            m,
            0,
            x,
            y,
            z
        )

        Matrix.scaleM(
            m,
            0,
            sx,
            sy,
            sz
        )

        drawMesh(
            sphere,
            m,
            color
        )
    }

    private fun drawChildBox(
        parent: FloatArray,
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        rotateZ: Float,
        color: FloatArray
    ) {
        val m =
            parent.copyOf()

        Matrix.translateM(
            m,
            0,
            x,
            y,
            z
        )

        if (
            rotateZ !=
            0f
        ) {
            Matrix.rotateM(
                m,
                0,
                rotateZ,
                0f,
                0f,
                1f
            )
        }

        Matrix.scaleM(
            m,
            0,
            sx,
            sy,
            sz
        )

        drawMesh(
            cube,
            m,
            color
        )
    }

    private fun drawBox(
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        color: FloatArray
    ) {
        Matrix.setIdentityM(
            model,
            0
        )

        Matrix.translateM(
            model,
            0,
            x,
            y,
            z
        )

        Matrix.scaleM(
            model,
            0,
            sx,
            sy,
            sz
        )

        drawMesh(
            cube,
            model,
            color
        )
    }

    private fun drawSphere(
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        color: FloatArray
    ) {
        Matrix.setIdentityM(
            model,
            0
        )

        Matrix.translateM(
            model,
            0,
            x,
            y,
            z
        )

        Matrix.scaleM(
            model,
            0,
            sx,
            sy,
            sz
        )

        drawMesh(
            sphere,
            model,
            color
        )
    }

    private fun drawMesh(
        mesh: Mesh3D,
        modelMatrix:
            FloatArray,
        color:
            FloatArray
    ) {
        Matrix.multiplyMM(
            temp,
            0,
            view,
            0,
            modelMatrix,
            0
        )

        Matrix.multiplyMM(
            mvp,
            0,
            projection,
            0,
            temp,
            0
        )

        val invertOk =
            Matrix.invertM(
                inverseModel,
                0,
                modelMatrix,
                0
            )

        if (
            invertOk
        ) {
            Matrix.transposeM(
                normalMatrix,
                0,
                inverseModel,
                0
            )
        } else {
            Matrix.setIdentityM(
                normalMatrix,
                0
            )
        }

        GLES20.glUniformMatrix4fv(
            mvpHandle,
            1,
            false,
            mvp,
            0
        )

        GLES20.glUniformMatrix4fv(
            modelHandle,
            1,
            false,
            modelMatrix,
            0
        )

        GLES20.glUniformMatrix4fv(
            normalMatrixHandle,
            1,
            false,
            normalMatrix,
            0
        )

        GLES20.glUniform4fv(
            colorHandle,
            1,
            color,
            0
        )

        mesh.draw(
            positionHandle,
            normalHandle
        )
    }

    private fun createProgram(
        vertexShader:
            String,
        fragmentShader:
            String
    ): Int {
        val vertex =
            compileShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShader
            )

        val fragment =
            compileShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShader
            )

        val result =
            GLES20.glCreateProgram()

        GLES20.glAttachShader(
            result,
            vertex
        )

        GLES20.glAttachShader(
            result,
            fragment
        )

        GLES20.glLinkProgram(
            result
        )

        val linkStatus =
            IntArray(
                1
            )

        GLES20.glGetProgramiv(
            result,
            GLES20.GL_LINK_STATUS,
            linkStatus,
            0
        )

        if (
            linkStatus[0] ==
            0
        ) {
            val info =
                GLES20.glGetProgramInfoLog(
                    result
                )

            GLES20.glDeleteProgram(
                result
            )

            throw IllegalStateException(
                "3D program link failed: " +
                    info
            )
        }

        GLES20.glDeleteShader(
            vertex
        )

        GLES20.glDeleteShader(
            fragment
        )

        return result
    }

    private fun compileShader(
        type: Int,
        source: String
    ): Int {
        val shader =
            GLES20.glCreateShader(
                type
            )

        GLES20.glShaderSource(
            shader,
            source
        )

        GLES20.glCompileShader(
            shader
        )

        val status =
            IntArray(
                1
            )

        GLES20.glGetShaderiv(
            shader,
            GLES20.GL_COMPILE_STATUS,
            status,
            0
        )

        if (
            status[0] ==
            0
        ) {
            val info =
                GLES20.glGetShaderInfoLog(
                    shader
                )

            GLES20.glDeleteShader(
                shader
            )

            throw IllegalStateException(
                "3D shader compile failed: " +
                    info
            )
        }

        return shader
    }

    companion object {
        private const val VERTEX_SHADER =
            """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat4 uNormalMatrix;

            attribute vec3 aPosition;
            attribute vec3 aNormal;

            varying vec3 vNormal;
            varying vec3 vWorldPosition;

            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vWorldPosition = world.xyz;
                vNormal = normalize((uNormalMatrix * vec4(aNormal, 0.0)).xyz);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
            """

        private const val FRAGMENT_SHADER =
            """
            precision mediump float;

            uniform vec4 uColor;

            varying vec3 vNormal;
            varying vec3 vWorldPosition;

            void main() {
                vec3 n = normalize(vNormal);

                vec3 keyDir = normalize(vec3(-0.45, 0.92, 0.58));
                vec3 fillDir = normalize(vec3(0.70, 0.38, -0.45));

                float key = max(dot(n, keyDir), 0.0);
                float fill = max(dot(n, fillDir), 0.0);
                float hemi = 0.5 + 0.5 * max(n.y, 0.0);

                float lampDistance =
                    distance(
                        vWorldPosition,
                        vec3(2.68, 2.24, 1.62)
                    );

                float lamp =
                    max(
                        0.0,
                        1.0 - lampDistance / 3.8
                    );

                vec3 warm =
                    vec3(
                        1.0,
                        0.82,
                        0.62
                    ) *
                    lamp *
                    0.12;

                float shade =
                    0.33 +
                    0.38 * key +
                    0.16 * fill +
                    0.13 * hemi;

                vec3 color =
                    uColor.rgb *
                    shade +
                    warm;

                gl_FragColor =
                    vec4(
                        color,
                        uColor.a
                    );
            }
            """
    }
}

private class Mesh3D(
    private val vertices:
        FloatBuffer,
    private val normals:
        FloatBuffer,
    private val vertexCount:
        Int
) {
    fun draw(
        positionHandle:
            Int,
        normalHandle:
            Int
    ) {
        vertices.position(
            0
        )

        normals.position(
            0
        )

        GLES20.glEnableVertexAttribArray(
            positionHandle
        )

        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            vertices
        )

        GLES20.glEnableVertexAttribArray(
            normalHandle
        )

        GLES20.glVertexAttribPointer(
            normalHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            normals
        )

        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLES,
            0,
            vertexCount
        )

        GLES20.glDisableVertexAttribArray(
            positionHandle
        )

        GLES20.glDisableVertexAttribArray(
            normalHandle
        )
    }

    companion object {
        fun cube():
            Mesh3D {
            val positions =
                mutableListOf<Float>()

            val normals =
                mutableListOf<Float>()

            fun face(
                normal:
                    FloatArray,
                a:
                    FloatArray,
                b:
                    FloatArray,
                c:
                    FloatArray,
                d:
                    FloatArray
            ) {
                val order =
                    arrayOf(
                        a,
                        b,
                        c,
                        a,
                        c,
                        d
                    )

                order.forEach {
                    p ->
                    positions.add(
                        p[0]
                    )
                    positions.add(
                        p[1]
                    )
                    positions.add(
                        p[2]
                    )

                    normals.add(
                        normal[0]
                    )
                    normals.add(
                        normal[1]
                    )
                    normals.add(
                        normal[2]
                    )
                }
            }

            val n =
                0.5f

            face(
                floatArrayOf(
                    0f,
                    0f,
                    1f
                ),
                floatArrayOf(-n, -n, n),
                floatArrayOf(n, -n, n),
                floatArrayOf(n, n, n),
                floatArrayOf(-n, n, n)
            )

            face(
                floatArrayOf(
                    0f,
                    0f,
                    -1f
                ),
                floatArrayOf(n, -n, -n),
                floatArrayOf(-n, -n, -n),
                floatArrayOf(-n, n, -n),
                floatArrayOf(n, n, -n)
            )

            face(
                floatArrayOf(
                    1f,
                    0f,
                    0f
                ),
                floatArrayOf(n, -n, n),
                floatArrayOf(n, -n, -n),
                floatArrayOf(n, n, -n),
                floatArrayOf(n, n, n)
            )

            face(
                floatArrayOf(
                    -1f,
                    0f,
                    0f
                ),
                floatArrayOf(-n, -n, -n),
                floatArrayOf(-n, -n, n),
                floatArrayOf(-n, n, n),
                floatArrayOf(-n, n, -n)
            )

            face(
                floatArrayOf(
                    0f,
                    1f,
                    0f
                ),
                floatArrayOf(-n, n, n),
                floatArrayOf(n, n, n),
                floatArrayOf(n, n, -n),
                floatArrayOf(-n, n, -n)
            )

            face(
                floatArrayOf(
                    0f,
                    -1f,
                    0f
                ),
                floatArrayOf(-n, -n, -n),
                floatArrayOf(n, -n, -n),
                floatArrayOf(n, -n, n),
                floatArrayOf(-n, -n, n)
            )

            return Mesh3D(
                floatBuffer(
                    positions
                ),
                floatBuffer(
                    normals
                ),
                positions.size /
                    3
            )
        }

        fun sphere(
            latitudeBands:
                Int,
            longitudeBands:
                Int
        ): Mesh3D {
            val positions =
                mutableListOf<Float>()

            val normals =
                mutableListOf<Float>()

            fun point(
                lat:
                    Int,
                lon:
                    Int
            ): FloatArray {
                val theta =
                    PI *
                        lat.toDouble() /
                        latitudeBands
                            .toDouble()

                val phi =
                    2.0 *
                        PI *
                        lon.toDouble() /
                        longitudeBands
                            .toDouble()

                val sinTheta =
                    sin(
                        theta
                    )

                return floatArrayOf(
                    (
                        sinTheta *
                            cos(
                                phi
                            )
                        )
                        .toFloat(),
                    cos(
                        theta
                    )
                        .toFloat(),
                    (
                        sinTheta *
                            sin(
                                phi
                            )
                        )
                        .toFloat()
                )
            }

            fun add(
                p:
                    FloatArray
            ) {
                positions.add(
                    p[0] *
                        0.5f
                )
                positions.add(
                    p[1] *
                        0.5f
                )
                positions.add(
                    p[2] *
                        0.5f
                )

                normals.add(
                    p[0]
                )
                normals.add(
                    p[1]
                )
                normals.add(
                    p[2]
                )
            }

            for (
                lat in
                0 until
                latitudeBands
            ) {
                for (
                    lon in
                    0 until
                    longitudeBands
                ) {
                    val p00 =
                        point(
                            lat,
                            lon
                        )

                    val p10 =
                        point(
                            lat +
                                1,
                            lon
                        )

                    val p11 =
                        point(
                            lat +
                                1,
                            lon +
                                1
                        )

                    val p01 =
                        point(
                            lat,
                            lon +
                                1
                        )

                    // Counter-clockwise winding when viewed from outside.
                    add(
                        p00
                    )
                    add(
                        p11
                    )
                    add(
                        p10
                    )

                    add(
                        p00
                    )
                    add(
                        p01
                    )
                    add(
                        p11
                    )
                }
            }

            return Mesh3D(
                floatBuffer(
                    positions
                ),
                floatBuffer(
                    normals
                ),
                positions.size /
                    3
            )
        }

        private fun floatBuffer(
            values:
                List<Float>
        ): FloatBuffer {
            val buffer =
                ByteBuffer
                    .allocateDirect(
                        values.size *
                            4
                    )
                    .order(
                        ByteOrder
                            .nativeOrder()
                    )
                    .asFloatBuffer()

            values.forEach(
                buffer::put
            )

            buffer.position(
                0
            )

            return buffer
        }
    }
}
