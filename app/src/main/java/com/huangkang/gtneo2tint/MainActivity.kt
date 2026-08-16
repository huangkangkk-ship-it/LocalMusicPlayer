package com.huangkang.gtneo2tint

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.widget.*
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var manager: CameraManager
    private lateinit var preview: TextureView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var chars: CameraCharacteristics? = null
    private var recorder: MediaRecorder? = null
    private var recorderSurface: Surface? = null
    private var recordingUri: Uri? = null
    private var recording = false
    private var iso = 400
    private var tint = 0
    private lateinit var status: TextView
    private lateinit var recordButton: Button

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(buildUi())
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 7) else openCamera()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(16,16,16,16) }
        preview = TextureView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        fun text(s: String) = TextView(this).apply { this.text=s; setTextColor(Color.WHITE); textSize=16f }
        root.addView(text("快门  1/60"))
        val isoLabel=text("ISO  $iso"); root.addView(isoLabel)
        root.addView(SeekBar(this).apply { max=3100; progress=iso-100; setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){iso=p+100;isoLabel.text="ISO  $iso";applyRequest()}; override fun onStartTrackingTouch(s:SeekBar?){ }; override fun onStopTrackingTouch(s:SeekBar?){ }
        }) })
        val tintLabel=text("Tint  $tint"); root.addView(tintLabel)
        root.addView(SeekBar(this).apply { max=200; progress=100; setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){tint=p-100;tintLabel.text="Tint  $tint";applyRequest()}; override fun onStartTrackingTouch(s:SeekBar?){ }; override fun onStopTrackingTouch(s:SeekBar?){ }
        }) })
        recordButton=Button(this).apply{text="开始录像";setOnClickListener{if(recording)stopRecording()else startRecording()}};root.addView(recordButton)
        status=text("Camera2：检测中…");root.addView(status)
        root.addView(Button(this).apply{text="后置主摄 / 刷新能力";setOnClickListener{checkCapabilities()}})
        return root
    }

    private fun openCamera(){
        try{val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}?:return
            chars=manager.getCameraCharacteristics(id);manager.openCamera(id,object:CameraDevice.StateCallback(){override fun onOpened(c:CameraDevice){camera=c;startPreview()};override fun onDisconnected(c:CameraDevice){c.close()};override fun onError(c:CameraDevice,e:Int){c.close();status.text="相机打开失败：$e"}},null)
        }catch(e:Exception){status.text="相机错误：${e.message}"}
    }

    private fun startPreview(){val st=preview.surfaceTexture?:return;st.setDefaultBufferSize(1920,1080);val sf=Surface(st);camera?.createCaptureSession(listOf(sf),object:CameraCaptureSession.StateCallback(){override fun onConfigured(s:CameraCaptureSession){session=s;applyRequest()};override fun onConfigureFailed(s:CameraCaptureSession){status.text="预览配置失败"}},null)}

    private fun tintGains():RggbChannelVector{val t=tint/100f;val rb=1f+0.35f*t;val g=1f-0.35f*t;return RggbChannelVector(max(0.5f,min(1.5f,rb)),max(0.5f,min(1.5f,g)),max(0.5f,min(1.5f,g)),max(0.5f,min(1.5f,rb)))}

    private fun buildRequest(targets:List<Surface>):CaptureRequest{val c=camera?:throw IllegalStateException("camera unavailable");val r=c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);targets.forEach{r.addTarget(it)};r.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);r.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_OFF);r.set(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_FAST);r.set(CaptureRequest.COLOR_CORRECTION_GAINS,tintGains());r.set(CaptureRequest.SENSOR_EXPOSURE_TIME,16_666_667L);r.set(CaptureRequest.SENSOR_SENSITIVITY,iso);return r.build()}

    private fun applyRequest(){val s=session?:return;val ps=Surface(preview.surfaceTexture?:return);val targets=if(recording)listOf(ps,recorderSurface?:return)else listOf(ps);try{s.setRepeatingRequest(buildRequest(targets),null,null)}catch(e:Exception){status.text="参数应用失败：${e.message}"}}

    private fun prepareRecorder():Boolean{val resolver=contentResolver;val values=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"GTNeo2_Tint_${System.currentTimeMillis()}.mp4");put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/GTNeo2Tint")};val uri=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)?:return false;recordingUri=uri;val r=MediaRecorder();r.setVideoSource(MediaRecorder.VideoSource.SURFACE);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setVideoEncodingBitRate(12_000_000);r.setVideoFrameRate(30);r.setVideoSize(1920,1080);r.setVideoEncoder(MediaRecorder.VideoEncoder.H264);val pfd=resolver.openFileDescriptor(uri,"w")?:return false;r.setOutputFile(pfd.fileDescriptor);try{r.prepare()}catch(e:Exception){pfd.close();resolver.delete(uri,null,null);r.release();status.text="录像编码器初始化失败：${e.message}";return false};pfd.close();recorder=r;recorderSurface=r.surface;return true}

    private fun startRecording(){if(camera==null||preview.surfaceTexture==null||!prepareRecorder())return;val ps=Surface(preview.surfaceTexture!!);val vs=recorderSurface?:return;camera?.createCaptureSession(listOf(ps,vs),object:CameraCaptureSession.StateCallback(){override fun onConfigured(s:CameraCaptureSession){session=s;recording=true;try{s.setRepeatingRequest(buildRequest(listOf(ps,vs)),null,null);recorder?.start();recordButton.text="停止录像";status.text="录像中 · Tint $tint（录像输出同步应用）"}catch(e:Exception){recording=false;status.text="开始录像失败：${e.message}";recorder?.release();recorder=null;recorderSurface=null}};override fun onConfigureFailed(s:CameraCaptureSession){status.text="录像会话配置失败";recorder?.release();recorder=null;recorderSurface=null}},null)}

    private fun stopRecording(){try{recorder?.stop()}catch(_:Exception){};recorder?.reset();recorder?.release();recorder=null;recorderSurface=null;recording=false;recordButton.text="开始录像";status.text="录像已保存 · Tint $tint";recordingUri?.let{status.append("\n${it.lastPathSegment?:"视频"}")};recordingUri=null;session?.close();session=null;startPreview()}

    private fun checkCapabilities(){val c=chars?:return;val caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?:intArrayOf();val manual=caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING);val range=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);status.text="Camera2：MANUAL_POST_PROCESSING=${if(manual)"支持"else"不支持"}\nTint=$tint：${if(manual)"使用 Camera2 RGB Gains"else"设备可能限制色彩矩阵"}\nISO范围=${range?.lower}–${range?.upper}"}

    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)openCamera()}
    override fun onDestroy(){if(recording)stopRecording();session?.close();camera?.close();super.onDestroy()}
}
