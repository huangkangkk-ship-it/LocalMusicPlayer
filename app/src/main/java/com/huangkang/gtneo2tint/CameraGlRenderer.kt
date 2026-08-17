package com.huangkang.gtneo2tint

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGlRenderer(private val onCameraReady: (Surface) -> Unit) : android.opengl.GLSurfaceView.Renderer {
    private val vertices=floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)
    private val coords=floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f)
    private lateinit var vb:FloatBuffer; private lateinit var cb:FloatBuffer
    private var program=0; private var textureId=0; private var cameraTexture:SurfaceTexture?=null; private var cameraSurface:Surface?=null
    @Volatile private var frameAvailable=false; private var timestamp=0L; @Volatile private var encoderInput:Surface?=null; private var encoderEgl=EGL14.EGL_NO_SURFACE
    private var width=1; private var height=1; var requestRender:(()->Unit)?=null
    fun cameraSurface():Surface?=cameraSurface
    fun setEncoderSurface(surface:Surface?){encoderInput=surface;if(surface==null)encoderEgl=EGL14.EGL_NO_SURFACE}
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){
        vb=direct(vertices);cb=direct(coords);val id=IntArray(1);GLES20.glGenTextures(1,id,0);textureId=id[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        cameraTexture=SurfaceTexture(textureId).also{st->st.setDefaultBufferSize(1920,1080);st.setOnFrameAvailableListener{frameAvailable=true;requestRender?.invoke()}}
        cameraSurface=Surface(cameraTexture);program=program(VS,FS);onCameraReady(cameraSurface!!)
    }
    override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){width=w;height=h;GLES20.glViewport(0,0,w,h)}
    override fun onDrawFrame(gl:GL10?){
        if(frameAvailable){cameraTexture?.updateTexImage();timestamp=cameraTexture?.timestamp?:timestamp;frameAvailable=false};draw()
        val enc=encoderInput;if(enc!=null){val display=EGL14.eglGetCurrentDisplay();val window=EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);val ctx=EGL14.eglGetCurrentContext();if(encoderEgl==EGL14.EGL_NO_SURFACE)encoderEgl=createEncoderSurface(display,enc);if(encoderEgl!=EGL14.EGL_NO_SURFACE){EGL14.eglMakeCurrent(display,encoderEgl,encoderEgl,ctx);draw();if(timestamp>0)EGLExt.eglPresentationTimeANDROID(display,encoderEgl,timestamp);EGL14.eglSwapBuffers(display,encoderEgl);EGL14.eglMakeCurrent(display,window,window,ctx)}}
    }
    private fun draw(){GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0);GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTint"),TintController.normalized());val p=GLES20.glGetAttribLocation(program,"aPosition");val t=GLES20.glGetAttribLocation(program,"aTexCoord");GLES20.glEnableVertexAttribArray(p);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vb);GLES20.glEnableVertexAttribArray(t);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,0,cb);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(t)}
    private fun createEncoderSurface(display:android.opengl.EGLDisplay,surface:Surface):android.opengl.EGLSurface{val attrs=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE);val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val n=IntArray(1);if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)||configs[0]==null)return EGL14.EGL_NO_SURFACE;return EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0)}
    private fun direct(a:FloatArray)=ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(a);position(0)}
    private fun shader(type:Int,src:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw IllegalStateException(GLES20.glGetShaderInfoLog(s));return s}
    private fun program(vs:String,fs:String):Int{val p=GLES20.glCreateProgram();val v=shader(GLES20.GL_VERTEX_SHADER,vs);val f=shader(GLES20.GL_FRAGMENT_SHADER,fs);GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);val ok=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw IllegalStateException(GLES20.glGetProgramInfoLog(p));return p}
    companion object{private const val VS="attribute vec2 aPosition;attribute vec2 aTexCoord;varying vec2 vTexCoord;void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}";private const val FS="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES uTexture;uniform float uTint;varying vec2 vTexCoord;void main(){vec4 c=texture2D(uTexture,vTexCoord);float a=abs(uTint)*0.35;vec3 outc=uTint>=0.0?vec3(c.r*(1.0+a),c.g*(1.0-a),c.b*(1.0+a)):vec3(c.r*(1.0-a),c.g*(1.0+a),c.b*(1.0-a));gl_FragColor=vec4(clamp(outc,0.0,1.0),c.a);}"}
}
