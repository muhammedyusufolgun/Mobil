package com.arvision.studio

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sin

/** ARCore scene-pose geometry; simple 3D shapes, not GLB models. */
internal class Simple3dRenderer {
    private var program = 0
    private fun buf(arr: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(arr.size*4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(arr); position(0) }

    private val cube = buf(run {
        val pts = arrayOf(
            floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(.5f,-.5f,-.5f),
            floatArrayOf(.5f,.5f,-.5f),floatArrayOf(-.5f,.5f,-.5f),
            floatArrayOf(-.5f,-.5f,.5f),floatArrayOf(.5f,-.5f,.5f),
            floatArrayOf(.5f,.5f,.5f),floatArrayOf(-.5f,.5f,.5f))
        val faces = arrayOf(intArrayOf(0,1,2,3),intArrayOf(5,4,7,6),
            intArrayOf(4,0,3,7),intArrayOf(1,5,6,2),intArrayOf(3,2,6,7),intArrayOf(4,5,1,0))
        val values = ArrayList<Float>(108)
        for(face in faces) for(index in intArrayOf(0,1,2,0,2,3))
            values.addAll(pts[face[index]].toList())
        values.toFloatArray()
    })
    private val arrow = buf(floatArrayOf(
        -.50f,0f,-.12f, .10f,0f,-.12f, -.50f,0f,.12f,
        -.50f,0f,.12f, .10f,0f,-.12f, .10f,0f,.12f,
         .08f,0f,-.28f, .50f,0f,0f, .08f,0f,.28f
    ))
    private fun shader(type: Int, code: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh,code)
        GLES20.glCompileShader(sh)
        val state=IntArray(1)
        GLES20.glGetShaderiv(sh,GLES20.GL_COMPILE_STATUS,state,0)
        check(state[0]!=0) { "Shader error: ${GLES20.glGetShaderInfoLog(sh)}" }
        return sh
    }
    fun initialize() {
        val v = shader(GLES20.GL_VERTEX_SHADER,
            "uniform mat4 uMvp; attribute vec3 aPosition; void main(){gl_Position=uMvp*vec4(aPosition,1.0);}")
        val f = shader(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}")
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program,v); GLES20.glAttachShader(program,f)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        val state=IntArray(1)
        GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,state,0)
        check(state[0]!=0) { "3D program link failed" }
    }
    fun draw(scene: ArScene, pose: Pose, projection: FloatArray, view: FloatArray, nowMs: Long) {
        if(scene.effect !in setOf("CUBE","ARROW") || program==0) return
        val model=FloatArray(16)
        pose.toMatrix(model,0)
        val size=scene.sizeMeters.coerceIn(.02f,2f)
        val bob=(sin(nowMs/370.0)*.015).toFloat()
        Matrix.translateM(model,0,0f,scene.heightMeters+bob+
            (if(scene.effect=="CUBE")size/2f else 0f),0f)
        if(scene.effect=="CUBE")
            Matrix.rotateM(model,0,(nowMs%7000L)*360f/7000f,0f,1f,0f)
        Matrix.scaleM(model,0,size,size,size)
        val vm=FloatArray(16); val mvp=FloatArray(16)
        Matrix.multiplyMM(vm,0,view,0,model,0)
        Matrix.multiplyMM(mvp,0,projection,0,vm,0)
        val rgb=runCatching { android.graphics.Color.parseColor(scene.color) }
            .getOrDefault(android.graphics.Color.CYAN)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"uColor"),
            android.graphics.Color.red(rgb)/255f,android.graphics.Color.green(rgb)/255f,
            android.graphics.Color.blue(rgb)/255f,1f)
        val attr=GLES20.glGetAttribLocation(program,"aPosition")
        GLES20.glEnableVertexAttribArray(attr)
        val vertices=if(scene.effect=="CUBE")cube else arrow
        vertices.position(0)
        GLES20.glVertexAttribPointer(attr,3,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,if(scene.effect=="CUBE")36 else 9)
        GLES20.glDisableVertexAttribArray(attr)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }
}
