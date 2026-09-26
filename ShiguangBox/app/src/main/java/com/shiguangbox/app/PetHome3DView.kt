package com.shiguangbox.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
) : GLSurfaceView(context) {

    private val homeRenderer =
        PetHome3DRenderer()

    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object :
                ScaleGestureDetector
                    .SimpleOnScaleGestureListener() {
                override fun onScale(
                    detector:
                        ScaleGestureDetector
                ): Boolean {
                    homeRenderer.zoomBy(
                        detector.scaleFactor
                    )
                    return true
                }
            }
        )

    init {
        setEGLContextClientVersion(
            2
        )
        setRenderer(
            homeRenderer
        )
        renderMode =
            RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause =
            true
    }

    fun resetCamera() {
        homeRenderer
            .resetCamera()
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
        scaleDetector
            .onTouchEvent(
                event
            )

        when (
            event.actionMasked
        ) {
            MotionEvent.ACTION_DOWN -> {
                lastX =
                    event.x
                lastY =
                    event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (
                    !scaleDetector
                        .isInProgress
                ) {
                    val dx =
                        event.x -
                            lastX
                    val dy =
                        event.y -
                            lastY

                    homeRenderer
                        .rotateCamera(
                            dx,
                            dy
                        )

                    lastX =
                        event.x
                    lastY =
                        event.y
                }
            }
        }

        return true
    }
}

enum class Home3DAnchor(
    val x: Float,
    val z: Float,
    val label: String
) {
    BED(
        x = -2.05f,
        z = -1.32f,
        label = "床边"
    ),
    WINDOW(
        x = 1.95f,
        z = -1.55f,
        label = "窗边"
    ),
    RUG(
        x = 0.25f,
        z = 1.12f,
        label = "地毯"
    )
}

private class PetHome3DRenderer :
    GLSurfaceView.Renderer {

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

    private var target =
        Home3DAnchor
            .RUG

    private var lastFrameNanos =
        0L

    private var arrivedAtNanos =
        0L

    private var tourIndex =
        0

    private val tour =
        arrayOf(
            Home3DAnchor.RUG,
            Home3DAnchor.WINDOW,
            Home3DAnchor.BED,
            Home3DAnchor.RUG
        )

    private var moving =
        false

    private var elapsedSeconds =
        0f

    override fun onSurfaceCreated(
        gl: javax.microedition.khronos.opengles.GL10?,
        config:
            javax.microedition.khronos.egl.EGLConfig?
    ) {
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

    override fun onSurfaceChanged(
        gl: javax.microedition.khronos.opengles.GL10?,
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

    override fun onDrawFrame(
        gl: javax.microedition.khronos.opengles.GL10?
    ) {
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
                    0.22f
                )
                .coerceIn(
                    -65f,
                    80f
                )

        pitch =
            (
                pitch -
                    dy *
                    0.15f
                )
                .coerceIn(
                    13f,
                    48f
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
                    6.4f,
                    12.5f
                )
    }

    fun resetCamera() {
        yaw =
            34f
        pitch =
            25f
        distance =
            8.8f
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
            2_700_000_000L
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
        drawBox(
            x = 0f,
            y = -0.10f,
            z = 0f,
            sx = 6.2f,
            sy = 0.12f,
            sz = 5.2f,
            color =
                floatArrayOf(
                    0.72f,
                    0.61f,
                    0.48f,
                    1f
                )
        )

        drawBox(
            x = 0f,
            y = 2.0f,
            z = -2.56f,
            sx = 6.2f,
            sy = 4.1f,
            sz = 0.10f,
            color =
                floatArrayOf(
                    0.95f,
                    0.89f,
                    0.80f,
                    1f
                )
        )

        drawBox(
            x = -3.08f,
            y = 2.0f,
            z = 0f,
            sx = 0.10f,
            sy = 4.1f,
            sz = 5.2f,
            color =
                floatArrayOf(
                    0.88f,
                    0.82f,
                    0.73f,
                    1f
                )
        )

        // Window recess + blue glass.
        drawBox(
            x = 1.95f,
            y = 2.15f,
            z = -2.48f,
            sx = 1.70f,
            sy = 1.55f,
            sz = 0.07f,
            color =
                floatArrayOf(
                    0.58f,
                    0.76f,
                    0.82f,
                    1f
                )
        )

        val frameColor =
            floatArrayOf(
                0.72f,
                0.58f,
                0.42f,
                1f
            )

        drawBox(
            1.95f,
            2.95f,
            -2.39f,
            1.92f,
            0.10f,
            0.08f,
            frameColor
        )

        drawBox(
            1.95f,
            1.35f,
            -2.39f,
            1.92f,
            0.10f,
            0.08f,
            frameColor
        )

        drawBox(
            1.05f,
            2.15f,
            -2.39f,
            0.10f,
            1.70f,
            0.08f,
            frameColor
        )

        drawBox(
            2.85f,
            2.15f,
            -2.39f,
            0.10f,
            1.70f,
            0.08f,
            frameColor
        )

        drawBox(
            1.95f,
            2.15f,
            -2.37f,
            0.07f,
            1.55f,
            0.08f,
            frameColor
        )
    }

    private fun drawFurniture() {
        // Rug.
        drawBox(
            x = 0.20f,
            y = 0.02f,
            z = 1.12f,
            sx = 2.35f,
            sy = 0.06f,
            sz = 1.60f,
            color =
                floatArrayOf(
                    0.78f,
                    0.50f,
                    0.40f,
                    1f
                )
        )

        // Bed.
        drawBox(
            -2.15f,
            0.20f,
            -1.35f,
            1.55f,
            0.35f,
            1.30f,
            floatArrayOf(
                0.55f,
                0.38f,
                0.27f,
                1f
            )
        )

        drawBox(
            -2.15f,
            0.43f,
            -1.35f,
            1.42f,
            0.20f,
            1.18f,
            floatArrayOf(
                0.90f,
                0.75f,
                0.62f,
                1f
            )
        )

        drawBox(
            -2.45f,
            0.58f,
            -1.64f,
            0.58f,
            0.15f,
            0.34f,
            floatArrayOf(
                0.97f,
                0.91f,
                0.82f,
                1f
            )
        )

        // Small table.
        val wood =
            floatArrayOf(
                0.56f,
                0.39f,
                0.26f,
                1f
            )

        drawBox(
            1.92f,
            0.78f,
            0.62f,
            1.25f,
            0.14f,
            0.82f,
            wood
        )

        for (
            x in
            listOf(
                1.42f,
                2.42f
            )
        ) {
            for (
                z in
                listOf(
                    0.34f,
                    0.90f
                )
            ) {
                drawBox(
                    x,
                    0.39f,
                    z,
                    0.12f,
                    0.72f,
                    0.12f,
                    wood
                )
            }
        }

        // Collection shelf.
        drawBox(
            -2.62f,
            1.18f,
            1.45f,
            0.78f,
            2.20f,
            0.38f,
            wood
        )

        drawBox(
            -2.18f,
            1.18f,
            1.45f,
            0.08f,
            2.20f,
            0.52f,
            floatArrayOf(
                0.72f,
                0.54f,
                0.36f,
                1f
            )
        )

        // Toy ball.
        drawSphere(
            x = 1.25f,
            y = 0.22f,
            z = 1.72f,
            sx = 0.23f,
            sy = 0.23f,
            sz = 0.23f,
            color =
                floatArrayOf(
                    0.38f,
                    0.60f,
                    0.74f,
                    1f
                )
        )

        // Tiny discovery star placeholder.
        drawSphere(
            x = -2.18f,
            y = 1.45f,
            z = 1.18f,
            sx = 0.13f,
            sy = 0.13f,
            sz = 0.13f,
            color =
                floatArrayOf(
                    0.97f,
                    0.77f,
                    0.23f,
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
                vec3 lightDir = normalize(vec3(-0.45, 0.92, 0.58));
                float diffuse = max(dot(normalize(vNormal), lightDir), 0.0);
                float hemisphere = 0.5 + 0.5 * max(normalize(vNormal).y, 0.0);
                float shade = 0.42 + 0.42 * diffuse + 0.16 * hemisphere;
                vec3 color = uColor.rgb * shade;
                gl_FragColor = vec4(color, uColor.a);
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

                    add(
                        p00
                    )
                    add(
                        p10
                    )
                    add(
                        p11
                    )

                    add(
                        p00
                    )
                    add(
                        p11
                    )
                    add(
                        p01
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
