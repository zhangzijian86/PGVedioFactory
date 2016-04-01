package com.pg.pgvediofactory;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaFormat;
import android.media.MediaRecorder;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    // 音频获取源
    private int audioSource = MediaRecorder.AudioSource.MIC;
    // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    private static int sampleRateInHz = 44100;
    // 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private static int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    // 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
    private static int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区字节大小
    private int bufferSizeInBytes = 0;
    private Button Start;
    private Button Stop;
    private AudioRecord audioRecord;
    private boolean isRecord = false;// 设置正在录制的状态
    //AudioName裸音频数据文件
    private static  String AudioName = "/sdcard/love.raw";
    //NewAudioName可播放的音频文件
    private static  String NewAudioName = "/sdcard/new.wav";

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

        //=====音频==========
        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz,
                channelConfig, audioFormat);
        // 创建AudioRecord对象
        audioRecord = new AudioRecord(audioSource, sampleRateInHz,
                channelConfig, audioFormat, bufferSizeInBytes);
        //=====音频==========


        String storagePath = "";
        File parentPath = Environment.getExternalStorageDirectory();
        if(storagePath.equals("")){
            storagePath = parentPath.getAbsolutePath()+"/" + "20160330";
            File f = new File(storagePath);
            if(!f.exists()){
                f.mkdir();
            }
        }

        AudioName = storagePath + "/love.raw";
        NewAudioName = storagePath +  "/new.wav";

        flag = 0;
        pushFlag = 0;
    }

    class AudioRecordThread implements Runnable {
        @Override
        public void run() {
            writeDateTOFile();//往文件中写入裸数据
            copyWaveFile(AudioName, NewAudioName);//给裸数据加上头文件
        }
    }

    /**
     * 这里将数据写入文件，但是并不能播放，因为AudioRecord获得的音频是原始的裸音频，
     * 如果需要播放就必须加入一些格式或者编码的头信息。但是这样的好处就是你可以对音频的 裸数据进行处理，比如你要做一个爱说话的TOM
     * 猫在这里就进行音频的处理，然后重新封装 所以说这样得到的音频比较容易做一些音频的处理。
     */
    private void writeDateTOFile() {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[bufferSizeInBytes];
        FileOutputStream fos = null;
        int readsize = 0;
        try {
            File file = new File(AudioName);
            if (file.exists()) {
                file.delete();
            }
            fos = new FileOutputStream(file);// 建立一个可存取字节的文件
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (isRecord == true) {
            readsize = audioRecord.read(audiodata, 0, bufferSizeInBytes);
            if (AudioRecord.ERROR_INVALID_OPERATION != readsize) {
                try {
                    fos.write(audiodata);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            fos.close();// 关闭写入流
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 这里得到可播放的音频文件
    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = sampleRateInHz;
        int channels = 2;
        long byteRate = 16 * sampleRateInHz * channels / 8;
        byte[] data = new byte[bufferSizeInBytes];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 这里提供一个头信息。插入这些信息就可以得到可以播放的文件。
     * 为我为啥插入这44个字节，这个还真没深入研究，不过你随便打开一个wav
     * 音频的文件，可以发现前面的头文件可以说基本一样哦。每种格式的文件都有
     * 自己特有的头文件。
     */
    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
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
                    audioRecord.startRecording();
                    // 让录制状态为true
                    isRecord = true;
                    // 开启音频文件写入线程
                    new Thread(new AudioRecordThread()).start();
                }else{
                    doButton.setText("Start");
                    pushFlag = 0;
                    if (audioRecord != null) {
                        isRecord = false;//停止文件写入
                        audioRecord.stop();
                        audioRecord.release();//释放资源
                        audioRecord = null;
                    }
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