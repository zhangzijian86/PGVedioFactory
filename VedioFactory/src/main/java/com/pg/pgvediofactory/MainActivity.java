package com.pg.pgvediofactory;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.hardware.Camera;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import com.pg.pgvediofactory.camera.AvcEncoder;
import java.nio.ByteBuffer;



public class MainActivity extends Activity implements
        SurfaceHolder.Callback,Camera.PreviewCallback {
    private Button doButton;
    private SurfaceView surfaceview;// 显示视频的控件
    private SurfaceHolder surfaceHolder;
    private Camera camera = null;
    private Camera.Parameters params;
    private boolean running = false;
    private int width = 352;
    private int height = 288;
    private int framerate = 20;
    private int bitrate = 2500000;
    private byte[] h264 = new byte[width*height*3/2];
    private AvcEncoder avcCodec;
    private int flag;
    private int pushFlag;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        setContentView(R.layout.activity_main);
        //按钮初始化
        doButton = (Button) this.findViewById(R.id.doButton);
        doButton.setOnClickListener(new TestVideoListener());

        //surfaceview初始化
        surfaceview = (SurfaceView) this.findViewById(R.id.surfaceview);
        surfaceHolder = surfaceview.getHolder();// 取得holder
        surfaceHolder.addCallback(this); // holder加入回调接口
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //MediaCodec初始化
        avcCodec = new AvcEncoder(width,height,framerate,bitrate);

        flag = 0;
        pushFlag = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);//Camera.CameraInfo.CAMERA_FACING_BACK
        camera.setDisplayOrientation(90);
    }

    class TestVideoListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.doButton) {
                if(pushFlag==0){
                    doButton.setText("Stop");
                    pushFlag = 1;
                }else{
                    doButton.setText("Start");
                    pushFlag = 0;
                }
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format,
                               int width, int height) {
        if (running) {
            camera.stopPreview();
        }
        if (camera != null) {
            params = camera.getParameters();
            params.setPreviewSize(352, 288);
            params.setPreviewFormat(ImageFormat.YV12);
            camera.setParameters(params);
            camera.setPreviewCallback(this);
        }
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();//开启预览
        } catch (IOException e) {
            e.printStackTrace();
        }
        running = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 将holder，这个holder为开始在oncreat里面取得的holder，将它赋给surfaceHolder
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // surfaceDestroyed的时候同时对象设置为null
        surfaceview = null;
        surfaceHolder = null;
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        running = false;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if(pushFlag==1) {
            int ret = avcCodec.offerEncoder(data, h264);
            byte[] tmpData = new byte[ret];
            for(int i = 0;i < tmpData.length;i++){
                tmpData[i] = h264[i];
            }
            if(flag>=1){
                String storagePath = "";
                File parentPath = Environment.getExternalStorageDirectory();
                if(storagePath.equals("")){
                    storagePath = parentPath.getAbsolutePath()+"/" + "20160330";
                    File f = new File(storagePath);
                    if(!f.exists()){
                        f.mkdir();
                    }
                }
                String mp4Name = storagePath + "/file.h264";
                try {
                    FileOutputStream fout = new FileOutputStream(mp4Name,true);
                    BufferedOutputStream bos = new BufferedOutputStream(fout);
                    if(flag==1){
                        bos.write(avcCodec.getM_info());
                    }
                    bos.write(tmpData);
                    bos.flush();
                    fout.close();
                    bos.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            flag++;
        }
    }
}
