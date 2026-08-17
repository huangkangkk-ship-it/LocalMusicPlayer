package com.huangkang.gtneo2tint

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGlRenderer(private val onCameraReady: (Surface) -> Unit) : android.opengl.GLSurfaceView.Renderer {
    private val vertices = floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)
    private val coords = floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f)
    private lateinit var vb: FloatBuffer
    private lateinit var cb: FloatBuffer
    private var program = 0
    private var textureId = 0
    private var cameraTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    private var frameAvailable = false
    private var timestamp = 0L
    @Volatile private var encoderInput: Surface? = null
    private var encoderEgl = EGL14.EGL_NO_SURFACE
    var requestRender: (() -> Unit)? = null

    fun cameraSurface(): Surface? = cameraSurface
    fun setEncoderSurface(surface: Surface?) { encoderInput = surface; if (surface == null) encoderEgl = EGL14.EGL_NO_SURFACE }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        vb = direct(vertices); cb = direct(coords)
        val id = IntArray(1)
        GLES30.glGenTextures(1,id,0); textureId=id[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE)
        cameraTexture=SurfaceTexture(textureId).also { st ->
            st.setDefaultBufferSize(1920,1080)
            st.setOnFrameAvailableListener { frameAvailable=true; requestRender?.invoke() }
        }
        cameraSurface=Surface(cameraTexture)
        program=program(VS,FS)
        onCameraReady(cameraSurface!!)
    }

    override fun onDrawFrame(gl: GL10?) {
        if(frameAvailable){ cameraTexture?.updateTexImage(); timestamp=cameraTexture?.timestamp ?: timestamp; frameAvailable=false }
        val display=EGL14.eglGetCurrentDisplay()
        val window=EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        draw()
        val enc=encoderInput
        if(enc!=null){
            if(encoderEgl==EGL14.EGL_NO_SURFACE) encoderEgl=createEncoderSurface(display,enc)
            if(encoderEgl!=EGL14.EGL_NO_SURFACE){
                EGL14.eglMakeCurrent(display,encoderEgl,encoderEgl,EGL14.eglGetCurrentContext())
                draw()
                if(timestamp>0) EGLExt.eglPresentationTimeANDROID(display,encoderEgl,timestamp)
                EGL14.eglSwapBuffers(display,encoderEgl)
                EGL14.eglMakeCurrent(display,window,window,EGL14.eglGetCurrentContext())
            }
        }
    }

    private fun draw(){
        GLES30.glViewport(0,0,1920,1080); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"uTexture"),0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"uTint"),TintController.normalized())
        val p=GLES30.glGetAttribLocation(program,"aPosition"); val t=GLES30.glGetAttribLocation(program,"aTexCoord")
        GLES30.glEnableVertexAttribArray(p); GLES30.glVertexAttribPointer(p,2,GLES30.GL_FLOAT,false,0,vb)
        GLES30.glEnableVertexAttribArray(t); GLES30.glVertexAttribPointer(t,2,GLES30.GL_FLOAT,false,0,cb)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4)
        GLES30.glDisableVertexAttribArray(p); GLES30.glDisableVertexAttribArray(t)
    }

    private fun createEncoderSurface(display: android.opengl.EGLDisplay, surface: Surface): android.opengl.EGLSurface {
        val attrs=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES3_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE)
        val configs=arrayOfNulls<EGLConfig>(1); val n=IntArray(1)
        if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)||configs[0]==null) return EGL14.EGL_NO_SURFACE
        return EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0)
    }

    private fun direct(a:FloatArray)=ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(a);position(0)}
    private fun shader(type:Int,src:String):Int{val s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,src);GLES30.glCompileShader(s);val ok=IntArray(1);GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw IllegalStateException(GLES30.glGetShaderInfoLog(s));return s}
    private fun program(vs:String,fs:String):Int{val p=GLES30.glCreateProgram();val v=shader(GLES30.GL_VERTEX_SHADER,vs);val f=shader(GLES30.GL_FRAGMENT_SHADER,fs);GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);GLES30.glLinkProgram(p);val ok=IntArray(1);GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw IllegalStateException(GLES30.glGetProgramInfoLog(p));GLES30.glDeleteShader(v);GLES30.glDeleteShader(f);return p}

    companion object {
        private const val VS="#version 300 es\nin vec2 aPosition;in vec2 aTexCoord;out vec2 vTexCoord;void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}"
        private const val FS="#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\nprecision mediump float;uniform samplerExternalOES uTexture;uniform float uTint;in vec2 vTexCoord;out vec4 fragColor;void main(){vec4 c=texture(uTexture,vTexCoord);float a=abs(uTint)*0.35;vec3 m=vec3(c.r*(1.0+a),c.g*(1.0-a),c.b*(1.0+a));vec3 g=vec3(c.r*(1.0-a),c.g*(1.0+a),c.b*(1.0-a));fragColor=vec4(clamp(uTint>=0.0?m:g,0.0,1.0),c.a);}"
    }
}