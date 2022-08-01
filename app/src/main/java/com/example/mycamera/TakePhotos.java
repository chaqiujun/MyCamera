package com.example.mycamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class TakePhotos extends Fragment {
    private static final String TAG = "TakePictureFragment";

    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        //手机ROTATION逆时针旋转
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    private final MainActivity preActivity;

    // 预览框控件
    private TextureView mTextureView;
    private String mCameraId;         // 摄像头Id
    private Size mPreviewSize;      //获取分辨率
    private ImageReader mImageReader;  //图片阅读器
    private CameraDevice mCameraDevice;   //摄像头设备
    private CameraCaptureSession mCaptureSession;   //获取会话
    private CaptureRequest.Builder mPreviewRequestBuilder;   //获取到预览请求的Builder通过它创建预览请求
    private Surface mPreviewSurface;  //预览显示图

    // Fragment是否创建成功
    protected boolean isCreated = false;
    // Fragment是否可见
    private boolean isVisible;
    private float mOldDistance;
    private float newDistance;
    private CameraCharacteristics mCameraCharacteristics;
    // 放大的最大值，用于计算每次放大/缩小操作改变的大小
    private final int MAX_ZOOM = 100;
    // 缩放
    private int mZoom = 0;
    // 每次改变的宽度大小
    private float mStepWidth;
    // 每次改变的高度大小
    private float mStepHeight;

    public TakePhotos(MainActivity mainActivity) {
        preActivity = mainActivity;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreateView: success");
        View view = inflater.inflate(R.layout.fragment_take_photos, container, false);
        // 注册监听控件
        initView(view);
        // surfaceView回调里面配置相机打开相机
        mTextureView.setSurfaceTextureListener(mSurfaceTextListener);
        // 设置触摸事件
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 当触碰点有2个时，才去放大缩小
                if (event.getPointerCount() == 2) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_POINTER_DOWN:
                            // 点下时，得到两个点间的距离为mOldDistance
                            mOldDistance = getFingerSpacing(event);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            // 移动时，根据距离是变大还是变小，去放大还是缩小预览画面
                            newDistance = getFingerSpacing(event);
                            if (newDistance > mOldDistance) {
                                handleZoom(true);
                            } else if (newDistance < mOldDistance) {
                                handleZoom(false);
                            }
                            // 更新mOldDistance
                            mOldDistance = newDistance;
                            break;
                    }
                }
                return true;
            }
        });
        //Fragment View 创建成功
        isCreated = true;
        //显示当前View
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 如果fragment 可见
        if (isVisible) {
            // textureView 可用
            if (mTextureView.isAvailable()) {
                // 打开相机
                openCamera();
            } else {
                // 设置相机参数
                mTextureView.setSurfaceTextureListener(mSurfaceTextListener);
            }
        }
    }

    // 监听Fragment是否显示
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        // 判断是否是第一次创建
        if (!isCreated) {
            return;
        }
        // 如果可见
        if (isVisibleToUser) {
            isVisible = true;
            // 并且textureView 可用
            if (mTextureView.isAvailable()) {
                // 打开相机
                openCamera();
            } else {
                // 先配置相机再打开相机
                mTextureView.setSurfaceTextureListener(mSurfaceTextListener);
            }
        } else {
            // 当切换成录像时,将当前fragment置为不可见
            Log.d(TAG, TAG + "releaseCamera");
            isVisible = false;
            closeCamera();
        }
    }

    // 第一步 注册视图上监听控件ID
    private void initView(View view) {
        Log.d(TAG, "initView: success");
        //预览框控件
        mTextureView = view.findViewById(R.id.photos_texture_view);
    }

    // 第二步 回调函数
    // 1.SurfaceView状态回调。定义一个表面纹理变更监听器，TextureView准备就绪后，立即开启相机
    private TextureView.SurfaceTextureListener mSurfaceTextListener = new TextureView.SurfaceTextureListener() {
        // textureView可用时触发
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: success");
            // 配置相机
            setupCamera();
            // 打开相机
            openCamera();
        }

        // 在SurfaceView尺寸发生改变时触发
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        }

        // 在SurfaceView销毁时触发
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            // 关闭相机
            closeCamera();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        }
    };

    // 2.摄像头状态回调，相机准备就绪后，开启捕捉影像的会话
    private CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {
        // 打开成功获取到camera设备
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "onOpened: success");
            mCameraDevice = cameraDevice;
            //开启预览
            startPreview();
        }

        //打开失败
        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Toast.makeText(getContext(), "摄像头设备连接失败", Toast.LENGTH_SHORT).show();
            cameraDevice.close();
            mCameraDevice = null;
        }

        //打开错误
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Toast.makeText(getContext(), "摄像头设备连接出错", Toast.LENGTH_SHORT).show();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    // 3.监听ImageReader的事件，当有图像流数据可用时会回调onImageAvailable
    private ImageReader.OnImageAvailableListener mOnImageAvaiableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Toast.makeText(getContext(), "图片正在保存...", Toast.LENGTH_SHORT).show();
            //获得image
            Image image = imageReader.acquireNextImage();
            // 将照片转字节
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            //开启线程一部保存图片
            ImageSaver imageSaver = new ImageSaver(preActivity, mCameraId, data);
            image.close();
            new Thread(imageSaver).start();
        }
    };

    // 4.影像配置就绪后，将预览画面呈现到手机屏幕上
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            mCaptureSession = cameraCaptureSession;
            reapeatPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
        }
    };

    // 5.实现PreviewCallback  拍照时调用
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        // 一旦捕获完成
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };


    // 5.开始拍照，重启预览，因为mCaptureBuilder设置ImageReader作为target，
    // 所以会自动回调ImageReader的onImageAvailable()方法保存图片
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            reapeatPreview();
        }
    };

    // 第三步  设置(配置)相机
    // 设置摄像机id参数
    private void setupCamera() {
        Log.d(TAG, "setupCamera: success");
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            // 遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                // 获取摄像机的特征
                mCameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                // 默认打开后置  - 忽略前置 LENS（镜头）
                if (mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                // 初始化缩放参数
                initZoomParameter();
                // 获取StreamConfigurationMap,他是管理摄像头支持的所有输出格式
                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                // 获取最佳的预览大小
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), mTextureView.getWidth(), mTextureView.getHeight());
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 选择sizeMap中大于并且接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        Log.d(TAG, "getOptimalSize: success");
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            // 当 width > height
            if (width > height) {
                // 选取宽度大于surface的宽度并且选取的高度大于surface的高度
                if (option.getWidth() > width && option.getHeight() > height) {
                    // 符合的添加到sizeList
                    sizeList.add(option);
                }
            } else {
                // 否则选择宽度大于surface的高度并且选择的高度大于surface的宽度
                if (option.getWidth() > height && option.getHeight() > width) {
                    // 符合的添加到sizeList
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size t1) {
                    return Long.signum(size.getWidth() * size.getHeight() - t1.getWidth() * t1.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    //第四步 打开相机
    private void openCamera() {
        Log.d(TAG, "openCamera: success");
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        //检查权限
        try {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            } else {
                // 通过manager.openCamera(id,cameraStateCallback,处理的线程)
                manager.openCamera(mCameraId, mDeviceStateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //  关闭相机
    public void closeCamera() {
        Log.d(TAG, "closeCamera: success");

        if (mCaptureSession != null) {
            Log.d(TAG, "closeCaptureSession: success");
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            Log.d(TAG, "closeCameraDevice: success");
            mCameraDevice.close();
            mCameraDevice = null;
        }
        // 关闭图像读取器
        if (mImageReader != null) {
            Log.d(TAG, "closeImageReader: success");
            mImageReader.close();
            mImageReader = null;
        }
    }

    //第五步 开启相机预览
    private void startPreview() {
        Log.d(TAG, "startPreview: success");
        // 设置图片阅读器
        // 前三个参数分别是需要的尺寸和格式,最后一个参数代表每次最多获取几帧数据
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(mOnImageAvaiableListener, null);
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        // 设置TextureView的缓冲区大小
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // 获取Surface显示预览数据
        mPreviewSurface = new Surface(mSurfaceTexture);
        try {
            //创建预览请求的Builder
            try {
                //通过cameraDevice获取到预览请求的Builder
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //设置预览的显示图
                mPreviewRequestBuilder.addTarget(mPreviewSurface);
                // 设置自动对焦模式
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 设置自动曝光模式
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 开始对焦
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // 通过CameraDevice创建相机捕捉会话
            // 第一个参数是捕获数据的输出Surface列表,第二个参数是CameraCaptureSession的状态回调接口,
            // 当他创建好后会回调onCconfigured方法
            // 第三个参数用来确定Callback在那个线程执行,null表示在当前线程执行
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), mSessionStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 重复预览
    private void reapeatPreview() {
        mPreviewRequestBuilder.setTag(TAG);
        try {
            //设置反复捕获会话的请求,这样预览界面就会一直有数据显示
            //第一个参数就是预览请求,第二个参数是PreviewCallback,第三个是处理的线程
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreviewCaptureCallback, null);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    // 第六步 拍照
    public void takePhoto() {
        Log.d(TAG, "takePhoto: success");
        try {
            // 首先创建拍照的请求 CaptureRequest
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 获取屏幕方向
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            //获取到当前预览窗口的图
            Rect zoomRect = mPreviewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            if (zoomRect != null) {
                mCaptureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
            }
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            //设置拍照方向
            if (mCameraId.equals("1")) {
                rotation = 2;
            }
            // 设置图片的方向，因为默认的是横屏，我们使用手机一般是竖屏所以需要处理
            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            // 停止预览
            mCaptureSession.stopRepeating();
            // 拍照
            mCaptureSession.capture(mCaptureBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 保存图片子线程
    public class ImageSaver implements Runnable {

        //图片
        private byte[] mImageData;
        private MainActivity preActivity;
        private final String mCameraId;

        public ImageSaver(MainActivity mainActivity, String cameraId, byte[] data) {
            Log.d(TAG, "ImageSaver: success");
            mImageData = data;
            preActivity = mainActivity;
            mCameraId = cameraId;
        }

        @Override
        public void run() {
            String path = Environment.getExternalStorageDirectory() + "/DCIM/MyCameraPhotos/";
            String name = System.currentTimeMillis() + ".jpg";
            File fileDir = new File(path);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            File imageFile = new File(path + name);
            FileOutputStream fos = null;
            try {
                if (mCameraId.equals("1")) {
                    //jpeg byte 数组转换为bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(mImageData, 0, mImageData.length);
                    Matrix m = new Matrix();
                    // 利用matrix 对矩阵进行转换，y轴镜像
                    m.postScale(-1f, 1f);
                    // m转换传入bitmap
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                    // 以下再次把bitmap转换为byte 数组，
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    mImageData = baos.toByteArray();
                }
                fos = new FileOutputStream(imageFile);
                fos.write(mImageData, 0, mImageData.length);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                Message message = new Message();
                message.what = 0;
                Bundle mBundle = new Bundle();
                mBundle.putString("imgPath", path + name);
                message.setData(mBundle);
                mHandler.sendMessage(message);
            }
        }

        // 异步消息处理
        private Handler mHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull Message message) {
                super.handleMessage(message);
                switch (message.what) {
                    case 0:
                        Bundle bundle = message.getData();
                        //通过指定的键值对获取到刚刚发送过来的地址
                        String imgPath = bundle.getString("imgPath");
                        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(imgPath);
                        preActivity.imgPreview.setImageBitmap(bitmap);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + message.what);
                }

            }
        };
    }

    // 改变前后摄像头
    public void changeCamera() {
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

    // 手指触摸缩放
    private static float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    // 每次切换摄像头计算一次就行，结果缓存到成员变量中
    private void initZoomParameter() {
        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Log.d(TAG, "sensor_info_active_array_size: " + rect);
        // max_digital_zoom 表示 active_rect 除以 crop_rect 的最大值
        float max_digital_zoom = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        Log.d(TAG, "max_digital_zoom: " + max_digital_zoom);
        // crop_rect的最小宽高
        float minWidth = rect.width() / max_digital_zoom;
        float minHeight = rect.height() / max_digital_zoom;
        // 因为缩放时两边都要变化，所以要除以2
        mStepWidth = (rect.width() - minWidth) / MAX_ZOOM / 2;
        mStepHeight = (rect.height() - minHeight) / MAX_ZOOM / 2;
    }

    public void handleZoom(boolean isZoomIn) {
        if (mCameraDevice == null || mCameraCharacteristics == null || mPreviewRequestBuilder == null) {
            return;
        }
        if (isZoomIn && mZoom < MAX_ZOOM) { // 放大
            mZoom++;
        } else if (mZoom > 0) { // 缩小
            mZoom--;
        }
        Log.v(TAG, "handleZoom: mZoom: " + mZoom);
        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int cropW = (int) (mStepWidth * mZoom);
        int cropH = (int) (mStepHeight * mZoom);
        Rect zoomRect = new Rect(rect.left + cropW, rect.top + cropH, rect.right - cropW, rect.bottom - cropH);
        Log.d(TAG, "zoomRect: " + zoomRect);
        mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        reapeatPreview();
    }
}
