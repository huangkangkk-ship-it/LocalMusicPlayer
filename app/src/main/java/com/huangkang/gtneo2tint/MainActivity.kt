package com.huangkang.gtneo2tint

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import android.widget.*
import android.opengl.GLSurfaceView

class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: CameraGlRenderer
    private lateinit var status: TextView
    private lateinit var recordButton: Button
    private lateinit var tintLabel: TextView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null
    private var outputUri: android.net.Uri? = null
    private var recording = false
    private var openingCamera = false
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildUi(); startCameraThread()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
    }
    private fun buildUi() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(android.graphics.Color.BLACK);setPadding(12,12,12,12)}
        glView=GLSurfaceView(this); glView.setEGLContextClientVersion(2)
        renderer=CameraGlRenderer { surface -> runOnUiThread { openCameraWhenReady(surface) } }
        renderer.requestRender={glView.requestRender()}; glView.setRenderer(renderer); glView.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
        root.addView(glView,LinearLayout.LayoutParams(-1,0,1f))
        tintLabel=TextView(this).apply{setTextColor(android.graphics.Color.WHITE);textSize=18f;text="Tint  0"};root.addView(tintLabel)
        root.addView(SeekBar(this).apply{max=200;progress=100;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean){TintController.value=p-100;tintLabel.text="Tint  ${TintController.value}";glView.requestRender();if(recording)status.text="录像中 · Tint ${TintController.value}"}
            override fun onStartTrackingTouch(s:SeekBar?){ } override fun onStopTrackingTouch(s:SeekBar?){ }
        })})
        recordButton=Button(this).apply{text="开始录像";setOnClickListener{if(recording)stopRecording()else startRecording()}};root.addView(recordButton)
        status=TextView(this).apply{setTextColor(android.graphics.Color.WHITE);text="Camera2 + OpenGL ES：初始化中…"};root.addView(status);setContentView(root)
    }
    private fun startCameraThread(){cameraThread=HandlerThread("Camera2").also{it.start();cameraHandler=Handler(it.looper)}}
    private fun openCameraWhenReady(surface:Surface){openCamera(surface)}
    private fun openCamera(surface:Surface?=renderer.cameraSurface()){
        if(surface==null||camera!=null||openingCamera||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return
        openingCamera=true
        try{val id=cameraManager.cameraIdList.firstOrNull{cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}?:run{openingCamera=false;return}
            cameraManager.openCamera(id,object:CameraDevice.StateCallback(){
                override fun onOpened(c:CameraDevice){openingCamera=false;camera=c;createSession(surface)}
                override fun onDisconnected(c:CameraDevice){openingCamera=false;c.close();camera=null}
                override fun onError(c:CameraDevice,e:Int){openingCamera=false;c.close();camera=null;runOnUiThread{status.text="Camera2 错误：$e"}}
            },cameraHandler)
        }catch(e:Exception){openingCamera=false;status.text="打开相机失败：${e.message}"}
    }
    private fun createSession(surface:Surface){camera?.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
        override fun onConfigured(s:CameraCaptureSession){cameraSession=s;val req=camera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply{addTarget(surface);set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO)}.build();s.setRepeatingRequest(req,null,cameraHandler);runOnUiThread{status.text="Camera2 + OpenGL ES 已就绪 · Tint 0"}}
        override fun onConfigureFailed(s:CameraCaptureSession){runOnUiThread{status.text="Camera2 会话配置失败"}}
    },cameraHandler)}
    private fun prepareRecorder():Boolean{val resolver=contentResolver;val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"GTNeo2_Tint_${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/GTNeo2Tint")};val uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:return false;outputUri=uri;val pfd=resolver.openFileDescriptor(uri,"w")?:return false;val r=MediaRecorder();r.setVideoSource(MediaRecorder.VideoSource.SURFACE);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setVideoEncoder(MediaRecorder.VideoEncoder.H264);r.setVideoEncodingBitRate(12_000_000);r.setVideoFrameRate(30);r.setVideoSize(1920,1080);r.setOutputFile(pfd.fileDescriptor);return try{r.prepare();outputPfd=pfd;recorder=r;true}catch(e:Exception){pfd.close();resolver.delete(uri,null,null);r.release();status.text="MediaRecorder 初始化失败：${e.message}";false}}
    private fun startRecording(){if(recording||!prepareRecorder())return;val r=recorder?:return;renderer.setEncoderSurface(r.surface);glView.requestRender();try{r.start();recording=true;recordButton.text="停止录像";status.text="录像中 · Tint ${TintController.value}"}catch(e:Exception){renderer.setEncoderSurface(null);r.release();recorder=null;outputPfd?.close();outputPfd=null;status.text="开始录像失败：${e.message}"}}
    private fun stopRecording(){try{recorder?.stop()}catch(_:Exception){};recorder?.reset();recorder?.release();recorder=null;renderer.setEncoderSurface(null);outputPfd?.close();outputPfd=null;recording=false;recordButton.text="开始录像";status.text="录像已保存 · Tint ${TintController.value}";outputUri=null;glView.requestRender()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==10&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)openCamera()}
    override fun onResume(){super.onResume();glView.onResume()}
    override fun onPause(){if(recording)stopRecording();glView.onPause();super.onPause()}
    override fun onDestroy(){if(recording)stopRecording();cameraSession?.close();camera?.close();cameraThread?.quitSafely();super.onDestroy()}
}
