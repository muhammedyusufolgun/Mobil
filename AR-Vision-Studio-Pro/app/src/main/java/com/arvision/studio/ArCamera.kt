package com.arvision.studio

import android.app.Activity
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.graphics.BitmapFactory
import android.opengl.Matrix
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** ARCore camera, tracked reference overlays and scene-pose 3D primitives. */
class ArCameraView(private val activity: Activity, private val onError: (String)->Unit) : GLSurfaceView(activity), GLSurfaceView.Renderer {
    private var session: Session? = null
    private val sessionLock = Any()
    private val disposed = AtomicBoolean(false)
    private var glReady = false
    private var consecutiveFrameErrors = 0
    private var lastLogAt = 0L
    private val effects = Simple3dRenderer()
    private var texture = 0
    private var program = 0
    private var widthPx = 1
    private var heightPx = 1
    private var appliedScenes: List<ArScene> = emptyList()
    private val pendingScenes = AtomicReference<List<ArScene>>(emptyList())
    val currentText = AtomicReference("")
    val currentTitle = AtomicReference("")
    val screenCenter = AtomicReference(Pair(0.5f, 0.5f))
    private var lastSeenAt = 0L
    private val position = buf(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f))
    private val texCoords = buf(FloatArray(8))
    private fun buf(f: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(f.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(f); position(0) }

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8,8,8,8,16,0)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    fun scenes(list: List<ArScene>) { pendingScenes.set(list.toList()) }
    fun start() {
        if (disposed.get()) return
        try {
            synchronized(sessionLock) {
                if (session == null) session = Session(activity)
                session?.resume()
            }
            super.onResume()
        } catch (e: Exception) {
            reportFatal("AR kamera başlatılamadı: ${e.message}", e)
        }
    }
    fun stop() {
        // Official ARCore lifecycle order: pause GL rendering BEFORE pausing Session.
        runCatching { super.onPause() }
        synchronized(sessionLock) { runCatching { session?.pause() } }
        currentText.set(""); currentTitle.set("")
    }
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        stop()
        synchronized(sessionLock) { runCatching { session?.close() }; session = null }
    }
    private fun reportFatal(message: String, error: Throwable) {
        Log.e("ARVision/Camera", message, error)
        activity.runOnUiThread { onError(message) }
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val vertex = "attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){gl_Position=vec4(aPos,0.,1.);vUv=aUv;}"
        val fragment = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vUv; uniform samplerExternalOES uTexture; void main(){gl_FragColor=texture2D(uTexture,vUv);}"
        program = GLES20.glCreateProgram().also { p ->
            for ((kind,source) in listOf(GLES20.GL_VERTEX_SHADER to vertex,GLES20.GL_FRAGMENT_SHADER to fragment)) {
                val sh = GLES20.glCreateShader(kind)
                GLES20.glShaderSource(sh,source); GLES20.glCompileShader(sh)
                GLES20.glAttachShader(p,sh); GLES20.glDeleteShader(sh)
            }
            GLES20.glLinkProgram(p)
        }
        effects.initialize()
        glReady = true
        } catch (e: Throwable) {
            glReady = false
            reportFatal("AR grafik motoru başlatılamadı: ${e.message}", e)
        }
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { widthPx=w; heightPx=h; GLES20.glViewport(0,0,w,h) }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0.035f,0.08f,0.14f,1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!glReady || disposed.get()) return
        synchronized(sessionLock) {
        val s = session ?: return
        try {
            val next = pendingScenes.get()
            if (next != appliedScenes) {
                val db = AugmentedImageDatabase(s)
                var accepted = 0
                next.take(60).forEach { item ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(item.imagePath, bounds)
                    if (bounds.outWidth < 64 || bounds.outHeight < 64) return@forEach
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                        var sample = 1
                        while (bounds.outWidth / sample > 1100 || bounds.outHeight / sample > 1100) sample *= 2
                        inSampleSize = sample
                    }
                    val bitmap = BitmapFactory.decodeFile(item.imagePath, opts) ?: return@forEach
                    try {
                        if (item.widthMeters > 0f) db.addImage(item.id, bitmap, item.widthMeters)
                        else db.addImage(item.id,bitmap)
                        accepted++
                    } catch(e: Exception) {
                        Log.w("ARVision/Camera", "Reference rejected: ${item.title}", e)
                    } finally { bitmap.recycle() }
                }
                if (next.isNotEmpty() && accepted == 0)
                    activity.runOnUiThread { onError("Referans görseller AR için kabul edilmedi; net, ayrıntılı bir görsel yükleyin") }
                val cfg = Config(s).apply { augmentedImageDatabase = db }
                s.configure(cfg)
                appliedScenes = next
                currentText.set("")
            }
            s.setCameraTextureName(texture)
            @Suppress("DEPRECATION")
            val rotation = activity.windowManager.defaultDisplay.rotation
            s.setDisplayGeometry(rotation, widthPx, heightPx)
            val frame = s.update()
            consecutiveFrameErrors = 0
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, position, Coordinates2d.TEXTURE_NORMALIZED, texCoords)
            position.position(0); texCoords.position(0)
            GLES20.glUseProgram(program)
            val aPos = GLES20.glGetAttribLocation(program,"aPos")
            val aUv = GLES20.glGetAttribLocation(program,"aUv")
            GLES20.glEnableVertexAttribArray(aPos); GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glVertexAttribPointer(aPos,2,GLES20.GL_FLOAT,false,0,position)
            GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,0,texCoords)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            GLES20.glDisableVertexAttribArray(aPos); GLES20.glDisableVertexAttribArray(aUv)
            // Project each currently tracked image's center into the current camera screen.
            // Screen overlay follows the physical reference; this is not a rendered GLB model.
            val projection = FloatArray(16)
            val view = FloatArray(16)
            val pv = FloatArray(16)
            val clip = FloatArray(4)
            frame.camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
            frame.camera.getViewMatrix(view, 0)
            Matrix.multiplyMM(pv, 0, projection, 0, view, 0)
            var visible = false
            for (image in s.getAllTrackables(AugmentedImage::class.java)) {
                if (image.trackingState == TrackingState.TRACKING && image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING) {
                    val scene = appliedScenes.find { it.id == image.name } ?: continue
                    val pose = image.centerPose
                    Matrix.multiplyMV(clip, 0, pv, 0, floatArrayOf(pose.tx(),pose.ty(),pose.tz(),1f), 0)
                    if (clip[3] <= 0f) continue
                    val x = 0.5f + (clip[0] / clip[3]) * 0.5f
                    val y = 0.5f - (clip[1] / clip[3]) * 0.5f
                    if (x !in 0f..1f || y !in 0f..1f) continue
                    effects.draw(scene, pose, projection, view, System.currentTimeMillis())
                    currentTitle.set(scene.title)
                    currentText.set(scene.displayText)
                    screenCenter.set(Pair(x, y))
                    lastSeenAt = System.currentTimeMillis()
                    visible = true
                    break
                }
            }
            if (System.currentTimeMillis()-lastSeenAt > 2000) { currentText.set(""); currentTitle.set("") }
        } catch (e: Exception) {
            if (e !is com.google.ar.core.exceptions.SessionPausedException) {
                consecutiveFrameErrors++
                val now = System.currentTimeMillis()
                if (now - lastLogAt > 2000L) {
                    Log.w("ARVision/Camera", "AR frame failed", e)
                    lastLogAt = now
                }
                if (consecutiveFrameErrors > 120) {
                    consecutiveFrameErrors = 0
                    reportFatal("AR kamera sürekli hata verdi; standart kameraya geçiliyor: ${e.message}", e)
                }
            }
        } catch (e: Throwable) {
            reportFatal("AR motoru durduruldu: ${e.message}", e)
        }
        }
    }
}
