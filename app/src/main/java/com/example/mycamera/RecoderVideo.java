package com.example.mycamera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RecoderVideo extends Fragment {

    private final String TAG = "RecorderVideoFragment";
    private final MainActivity preActivity;
    private Chronometer mTimer;
    private TextureView mTextureView; //预览框控件
    private CaptureRequest.Builder mPreviewCaptureRequest; //获取请求创建者
    private CameraDevice mCameraDevice; //camera设备
    private MediaRecorder mMediaRecorder; //音视频录制
    //摄像头ID 默认置为后置BACK FRONT值为0 == BACK
    private String mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_FRONT);
    private CameraCaptureSession mCameraCaptureSession;   //获取会话
    private Handler mChildHandler;   //子线程
    private CameraManager mCameraManager; //摄像头管理者
    private boolean isVisible = false;  //fragment是否可见
    public boolean isRecording = false;   //是否在录制视频
    private HandlerThread mHandlerThread;  //线程处理者
    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    private CameraCharacteristics mCameraCharacteristics;

    public RecoderVideo(MainActivity mainActivity) {
        preActivity = mainActivity;
    }

    //Fragment 中 onCreateView返回的就是fragment要显示的view.
    @Nullable
    @Override
    /**
     * 第一个参数LayoutInflater inflater第二个参数ViewGroup container第三个参数 Bundle savedInstanceState
     * LayoutInflater inflater：作用类似于findViewById()用来寻找xml布局下的具体的控件Button、TextView等，
     * LayoutInflater inflater()用来找res/layout/下的xml布局文件
     * ViewGroup container：表示容器，View放在里面
     * Bundle savedInstanceState：保存当前的状态，在活动的生命周期中，只要离开了可见阶段，活动很可能就会被进程终止，
     * 这种机制能保存当时的状态
     */
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: success");
        View view = inflater.inflate(R.layout.fragment_recoder_video, container, false);
        initView(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isVisible) {
           /* // 初始化子线程
            initChildHandler();
            if (mTextureView.isAvailable()) {
                openCamera();
            } else {
                initTextureView();
            }*/
        }
    }

   /* @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // 如果Fragment可见 把isVisible置为true
            isVisible = true;
            Log.d(TAG, "setUserVisibleHint: true");
            //初始化子线程
            initChildHandler();
            //如果textureView可用
            if (mTextureView.isAvailable()) {
                openCamera();
            } else {
                initTextureView();
            }
        } else {
            isVisible = false;
            closeCamera();
        }
    }*/

    // 第一步 初始化
    // 初始化监听控件
    private void initView(View view) {
        mTextureView = view.findViewById(R.id.video_texture_view);
        mTimer = view.findViewById(R.id.video_time);
    }

    // 初始化子线程Handler，操作Camera2需要一个子线程的Handler
    private void initChildHandler() {
        mHandlerThread = new HandlerThread("MyCamera");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper());
    }

    // 关闭线程
    public void stopBackgroundThread() {
        if (mHandlerThread != null) {
            //quitSafely 安全退出
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
                mHandlerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 初始化Camera2的相机管理，CameraManager用于获取摄像头分辨率，摄像头方向，摄像头id与打开摄像头的工作
    private void initCameraManager() {
        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
    }

    // 初始化TextureView的纹理生成监听，只有纹理生成准备好了。才能去进行摄像头的初始化工作让TextureView接收摄像头预览画面
    public void initTextureView() {
        mTextureView.setSurfaceTextureListener(mSurfaceTextListener);
    }

    // 第二步 回调函数
    // 1.SurfaceView状态回调。定义一个表面纹理变更监听器，TextureView准备就绪后，立即开启相机
    private TextureView.SurfaceTextureListener mSurfaceTextListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //可以使用纹理
            selectCamera();
            openCamera();
        }

        //纹理尺寸变化
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        //纹理被销毁
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        //纹理更新
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    // 2.摄像头状态回调
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        //摄像头被打开
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        //摄像头断开
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Toast.makeText(getContext(), "摄像头设备连接失败", Toast.LENGTH_SHORT).show();
            camera.close();
            mCameraDevice = null;
        }

        //异常
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Toast.makeText(getContext(), "摄像头设备连接出错", Toast.LENGTH_SHORT).show();
            camera.close();
            mCameraDevice = null;
        }
    };

    // 3.预览时会话状态回调
    private CameraCaptureSession.StateCallback mPreviewSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            mCameraCaptureSession = session;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Toast.makeText(getActivity().getApplicationContext(), "Faileedsa ", Toast.LENGTH_SHORT).show();
        }
    };


    // 3.录像时消息捕获回调
    private CameraCaptureSession.CaptureCallback mRecoderSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    // 4.录像时会话状态回调
    private CameraCaptureSession.StateCallback mRecoderSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraCaptureSession = session;
            updatePreview();
            try {
                //执行重复获取数据请求，等于一直获取数据呈现预览画面，mRecoderSessionCaptureCallback会返回此次操作的信息回调
                mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequest.build(), mRecoderSessionCaptureCallback, mChildHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    // 第三步 配置摄像机
    // 选择一颗我们需要使用的摄像头，主要是选择使用前摄还是后摄或者是外接摄像头
    private void selectCamera() {
        try {
            if (mCameraManager == null) {
                initCameraManager();
            }
            // 获取当前设备的全部摄像头id集合
            String[] cameraIdList = mCameraManager.getCameraIdList();
            if (cameraIdList.length == 0) {
                Log.e(TAG, "selectCamera: cameraIdList length is 0");
            }

            // 遍历所有摄像头
            for (String cameraId : cameraIdList) {
                //遍历所有摄像头
                //屏幕方向
                int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                mPreviewCaptureRequest.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
                if (rotation == CameraCharacteristics.LENS_FACING_BACK) {
                    //这里选择了后摄像头
                    mCameraId = cameraId;
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 第四步 录制视频相关配置
    // 配置录制视频相关数据
    private void configMediaRecorder() {
        String path = Environment.getExternalStorageDirectory() + "/DCIM/MyCameraPhotos/";
        File fileDir = new File(path);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }

        String name = System.currentTimeMillis() + ".mp4";
        File file = new File(path + name);
        if (file.exists()) {
            file.delete();
        }

        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        //设置音频来源
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //设置视频来源
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //设置输出格式
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //设置音频编码格式，请注意这里使用默认，实际app项目需要考虑兼容问题，应该选择AAC
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //设置视频编码格式，请注意这里使用默认，实际app项目需要考虑兼容问题，应该选择H264
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //设置比特率 一般是 1*分辨率 到 10*分辨率 之间波动。比特率越大视频越清晰但是视频文件也越大。
        mMediaRecorder.setVideoEncodingBitRate(8 * 1024 * 1920);
        //设置帧数 选择 30即可， 过大帧数也会让视频文件更大当然也会更流畅，但是没有多少实际提升。人眼极限也就30帧了。
        mMediaRecorder.setVideoFrameRate(30);
        Size size = getMatchingSize();
        mMediaRecorder.setVideoSize(size.getWidth(), size.getHeight());
        mMediaRecorder.setOrientationHint(90);
        //如果是前置
        if (mCameraId.equals("1")) {
            mMediaRecorder.setOrientationHint(270);
        }

        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 配置录制视频时的CameraCaptureSession
    void configSession() {
        try {
            if (mCameraCaptureSession != null) {
                // 停止预览，准备切换到录制视频
                mCameraCaptureSession.stopRepeating();
                // 关闭预览的会话，需要重新创建录制视频的会话
                mCameraCaptureSession.close();
                mCameraCaptureSession = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        configMediaRecorder();
        Size cameraSize = getMatchingSize();
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        // 从获取录制视频需要的Surface
        Surface recorderSurface = mMediaRecorder.getSurface();
        try {
            mPreviewCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewCaptureRequest.addTarget(previewSurface);
            mPreviewCaptureRequest.addTarget(recorderSurface);
            // 注意这里设置了Arrays.asList(previewSurface,recorderSurface) 2个Surface，很好理解录制视频也需要有画面预览，
            // 第一个是预览的Surface，第二个是录制视频使用的Surface
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), mRecoderSessionStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 第四步 打开/关闭相机
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            if (mCameraManager == null) {
                initCameraManager();
            }
            mCameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        // 关闭预览就是关闭捕获会话
        stopPreview();
        // 关闭当前相机
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mMediaRecorder) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        if (mHandlerThread != null) {
            stopBackgroundThread();
        }
    }

    // 第五步 开启/更新/关闭相机预览
    // 计算需要的使用的摄像头分辨率
    private Size getMatchingSize() {
        Size selectSize = null;
        try {
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap streamConfigurationMap = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            //这里是将预览铺满屏幕,所以直接获取屏幕分辨率
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            //屏幕分辨率宽
            int deviceWidth = displayMetrics.widthPixels;
            //屏幕分辨率高
            int deviceHeight = displayMetrics.heightPixels;
            /**
             * 循环40次，让宽度范围从最小逐步增加,找到最符合屏幕宽度的分辨率,
             * 你要是不放心那就增加循环，肯定会找到一个分辨率，不会出现此方法返回一个null的Size的情况
             * ,但是循环越大后获取的分辨率就越不匹配
             */
            //遍历所有Size
            for (int j = 1; j < 41; j++) {
                for (int i = 0; i < sizes.length; i++) {
                    Size itemSize = sizes[i];
                    // 判断 当前Size高度小于屏幕宽度+j*5  &&  判断当前Size高度大于屏幕宽度-j*5  &&  判断当前Size宽度小于当前屏幕高度
                    if (itemSize.getHeight() < (deviceWidth + j * 5) && itemSize.getHeight() > (deviceWidth - j * 5)) {
                        //如果之前已经找到一个匹配的宽度
                        if (selectSize != null) {
                            // 求绝对值算出最接近设备高度的尺寸
                            if (Math.abs(deviceHeight - itemSize.getWidth()) < Math.abs(deviceHeight - selectSize.getWidth())) {
                                selectSize = itemSize;
                                continue;
                            }
                        } else {
                            selectSize = itemSize;
                        }
                    }
                }
                if (selectSize != null) { //如果不等于null 说明已经找到了 跳出循环
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "getMatchingSize: 选择的分辨率宽度=" + selectSize.getWidth());
        Log.e(TAG, "getMatchingSize: 选择的分辨率高度=" + selectSize.getHeight());
        return selectSize;
    }

    //设置模式  闪光灯用
    private void setupCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    // 开启预览，使用TextureView显示相机预览数据，预览和拍照数据都是使用CameraCaptureSession会话来请求
    private void startPreview() {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        Size cameraSize = getMatchingSize();
        //设置TextureView的缓冲区大小
        mSurfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        //获取Surface显示预览数据
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            // 创建CaptureRequestBuilder,TEMPLATE_PREVIEW比表示预览请求
            mPreviewCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 设置Surface作为预览数据的显示界面
            mPreviewCaptureRequest.addTarget(previewSurface);
            // 创建相机捕获会话,第一个参数是捕获数据Surface列表，第二个参数是CameraCaptureSession的状态回调接口，当他创建好后会回调onConfigured方法,第三个参数用来确定Callback在哪个线程执行
            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface), mPreviewSessionStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 更新预览
    private void updatePreview() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            setupCaptureRequestBuilder(mPreviewCaptureRequest);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequest.build(), null, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 关闭预览
    private void stopPreview() {
        //关闭预览就是关闭捕获会话
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    // 第六步 录像
    // 开始录制视频
    void startRecorder() {
        mMediaRecorder.start();
        // 开始计时
        //计时器清零
        mTimer.setBase(SystemClock.elapsedRealtime());
        mTimer.start();
    }

    // 停止录制视频（暂停后视频文件会自动保存）
    void stopRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            // 停止计时
            mTimer.stop();
            //计时器清零
            mTimer.setBase(SystemClock.elapsedRealtime());
        }
        startPreview();
    }

    // 改变前后摄像头
    private void changeCamera() {
        Log.d(TAG, "changeCamera: success");
        if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
            Toast.makeText(getContext(), "前置转后置", Toast.LENGTH_SHORT).show();
            mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_FRONT);
        } else {
            Toast.makeText(getContext(), "后置转前置", Toast.LENGTH_SHORT).show();
            mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_BACK);
        }
        mCameraDevice.close();
        openCamera();
    }

}

