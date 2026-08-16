package com.huangkang.gtneo2tint

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.widget.*

class MainActivity : Activity() {
    private lateinit var manager: CameraManager
    private lateinit var preview: TextureView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var chars: CameraCharacteristics? = null
    private var iso = 400
    private var tint = 0
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setContentView(buildUi())
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 7) else openCamera()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(16,16,16,16) }
        preview = TextureView(this); root.addView(preview, LinearLayout.LayoutParams(-1,0,1f))
        fun text(s:String) = TextView(this).apply { this.text=s; setTextColor(Color.WHITE); textSize=16f }
        root.addView(text("快门  1/60"))
        val isoLabel=text("ISO  $iso"); root.addView(isoLabel)
        root.addView(SeekBar(this).apply { max=3100; progress=iso-100; setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){iso=p+100;isoLabel.text="ISO  $iso";applyRequest()}; override fun onStartTrackingTouch(s:SeekBar?){ }; override fun onStopTrackingTouch(s:SeekBar?){ }
        })})
        root.addView(text("色温  4000K"))
        val tintLabel=text("Tint  $tint"); root.addView(tintLabel)
        root.addView(SeekBar(this).apply { max=200; progress=100; setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){tint=p-100;tintLabel.text="Tint  $tint";applyRequest()}; override fun onStartTrackingTouch(s:SeekBar?){ }; override fun onStopTrackingTouch(s:SeekBar?){ }
        })})
        status=text("Camera2 能力检测：检测中…"); root.addView(status)
        root.addView(Button(this).apply { text="后置主摄 / 刷新能力"; setOnClickListener{checkCapabilities()} })
        return root
    }

    private fun openCamera() {
        try {
            val id=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK} ?: return
            chars=manager.getCameraCharacteristics(id)
            manager.openCamera(id,object:CameraDevice.StateCallback(){override fun onOpened(c:CameraDevice){camera=c;startPreview()};override fun onDisconnected(c:CameraDevice){c.close()};override fun onError(c:CameraDevice,e:Int){c.close()}},null)
        } catch (_:Exception) { }
    }
    private fun startPreview(){val st=preview.surfaceTexture?:return;st.setDefaultBufferSize(1920,1080);val sf=Surface(st);camera?.createCaptureSession(listOf(sf),object:CameraCaptureSession.StateCallback(){override fun onConfigured(s:CameraCaptureSession){session=s;applyRequest()};override fun onConfigureFailed(s:CameraCaptureSession){}},null)}
    private fun applyRequest(){val c=camera?:return;val s=session?:return;val sf=Surface(preview.surfaceTexture?:return);val r=c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);r.addTarget(sf);r.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_OFF);r.set(CaptureRequest.SENSOR_EXPOSURE_TIME,16_666_667L);r.set(CaptureRequest.SENSOR_SENSITIVITY,iso);r.set(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);r.set(CaptureRequest.COLOR_CORRECTION_GAINS,RggbChannelVector(1f+0.35f*tint/100f,1f,1f,1f-0.35f*tint/100f));try{s.setRepeatingRequest(r.build(),null,null)}catch(_:Exception){}}
    private fun checkCapabilities(){val c=chars?:return;val caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?:intArrayOf();val manual=caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING);val range=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);status.text="Camera2：MANUAL_POST_PROCESSING=${if(manual)"支持" else "不支持"}\nRGB Gains=${if(manual)"可尝试" else "受限"}\nISO范围=${range?.lower}–${range?.upper}"}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)openCamera()}
    override fun onDestroy(){session?.close();camera?.close();super.onDestroy()}
}
