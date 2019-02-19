package com.webronin_26.picturenoteapp;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity {

    // 退回主 Activity 的按鈕
    LinearLayout backToGalleryLinearLayout = null;
    // 拍照按鈕
    ImageButton pictureImageButton = null;
    // 目前鏡頭的 辨識碼
    private String mCameraId;

    private int mSensorOrientation;
    // 預覽的尺寸
    // Size 物件儲存 Width & Height
    private Size mPreviewSize;
    // 設備是否有閃光燈裝置
    private boolean mFlashSupported;
    // 目前設備的輸出(系統相冊)
    private String externalPublicDcimFilePath;
    // ImageReader 類 用來儲存照片
    private ImageReader mImageReader;

    private boolean autoFocusSupported = false;

    WeakReference<CameraActivity> cameraActivityWeakReference = null;

    // ImageReader 的回調監聽器，onImageAvailable 會在 Session capture 成功調用的時候回調
    // acquireNextImage 將會擷取下一個在螢幕 queue 中出現的畫面
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            String fileName = "PictureNoteApp_" + String.valueOf(System.currentTimeMillis());

            mBackgroundHandler.post(new ImageSaver(
                    reader.acquireNextImage()
                    , new File( externalPublicDcimFilePath , fileName ).getAbsolutePath()
                    , fileName
                    , cameraActivityWeakReference
            ) );
        }
    };

    private void initFilePath() {
        externalPublicDcimFilePath =  Environment.getExternalStorageDirectory() + "/DCIM/Camera";
    }

    private static class ImageSaver implements Runnable{

        Image image = null;
        String filePath = "";
        String fileName = "";
        WeakReference<CameraActivity> cameraActivityWeakReference = null;

        public ImageSaver( Image mImage , String mfilePath  , String mfileName , WeakReference<CameraActivity> mCameraActivityWeakReference ) {
            image = mImage;
            filePath = mfilePath;
            fileName = mfileName;
            cameraActivityWeakReference = mCameraActivityWeakReference;
        }

        @Override
        public void run() {

            CameraActivity cameraActivity = cameraActivityWeakReference.get();

            if( cameraActivity != null ) {

                OutputStream outputStream = null;

                try {
                    ContentValues values = new ContentValues();
                    ContentResolver resolver = cameraActivity.getContentResolver();
                    values.put(MediaStore.Images.ImageColumns.DATA, filePath);
                    values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/jpg");
                    // 將圖片時間設定為現在時間
                    values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, System.currentTimeMillis() + "");
                    Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                    if (uri != null) {
                        outputStream = resolver.openOutputStream(uri);
                        Bitmap bitmap = changeImageIntoBitmap( image );
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    }
                } catch (Exception e) {
                    Log.e( "------" , e.toString() );
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.flush();
                            outputStream.close();
                        } catch (IOException e) {
                            Log.e( "------" , e.toString() );
                        }
                    }
                }
            }
        }

        private Bitmap changeImageIntoBitmap( Image image ) {

            Bitmap bitmap = null;

            try {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
            }catch ( Exception e ) {
                Log.e( "------" , "changeImageIntoBitmap : " + e.toString() );
            }
            return bitmap;
        }
    }

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     *  --------------------------  onCreate  -----------------------------------
     *  設定兩個 Button：
     *  按下 照相 Button 調用 拍照方法 takePicture()
     *  按下 返回 Button 移動到 相冊
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView( R.layout.camera_activity_layout_land );
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.camera_activity_layout_port);
        }
        initView();
    }

    private void initView() {
        textureView = findViewById( R.id.texture_view );
        backToGalleryLinearLayout = findViewById( R.id.back_to_gallery );
        backToGalleryLinearLayout.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity( new Intent( CameraActivity.this , PictureInformationActivity.class ) );
                finish();
            }
        } );
        pictureImageButton = findViewById( R.id.picture_image_button );
        pictureImageButton.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        } );
    }

    /**
     * 開啟相機線程 和 初始化相機
     */
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        initFilePath();

        cameraActivityWeakReference = new WeakReference<>( CameraActivity.this );

        if ( textureView.isAvailable() ) {
            openCamera( textureView.getWidth(), textureView.getHeight() );
        } else {
            // 如果 TextureView 不可使用
            // 設定監聽器，在 onSurfaceTextureAvailable() 當中回調 openCamera()
            textureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    /**
     * 關閉相機 和 停止相機的線程
     */
    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * 設置全螢幕
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if( hasFocus ) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    /**
     * 0. 預覽
     * 1. 等待上鎖
     * 2. 等待前置作業( 對焦/曝光 )
     * 3. 等待非前置作業( 閃光燈 )
     * 4. 已照相
     *
     * 初始化設定為 "預覽" 狀態
     */
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private int mState = STATE_PREVIEW;

    /**
     * 只有一個線程能夠操作 開啟 / 關閉 的鎖
     *      - 開啟時得到鎖：在 openCamera() 裡面 Acquire()，並且在 CameraDevice.StateCallback 裡面 release()
     *      - 關閉時得到鎖：在 closeCamera() 裡面 Acquire() / release()
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     *  --------------------------  Camera Thread  -----------------------------------
     *  定義相機的 Handler 和 HandlerThread
     *      - onResume 的時候 startBackgroundThread()
     *      - onPause 的時候 stopBackgroundThread()
     */
    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     *  --------------------------  TextureView & Listener  -----------------------------------
     */
    private AutoFitTextureView textureView = null;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    /**
     *  --------------------------  CaptureRequest & CallBack  -----------------------------------
     */

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CaptureRequest mPreviewRequest;

    /**
     *  --------------------------  CameraDevice & CallBack  -----------------------------------
     */

    private CameraDevice mCameraDevice;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        /**
         * 只要 Camera Device 被正常啟動了，立即開啟預覽模式
         * 將鏡頭的畫面渲染到目標 TextureView 上
         */
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }
    };

    /**
     *  ----------------------  CameraCaptureSession & CallBack  -------------------------------
     */
    private CameraCaptureSession mCaptureSession;

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

        private void process(CaptureResult result) {

            switch (mState) {

                case STATE_PREVIEW: {
                    break;
                }

                case STATE_WAITING_LOCK: {

                    /**
                     * 取得目前相機抓到的 "結果" 的 "自動對焦" 狀態
                     *      如果是 null 狀態：建構拍照方法
                     *      如果不是 null 狀態，是
                     *          1. 已經對焦 而且 locked focused
                     *          2. 未對焦 而且 locked focused
                     *          3. 正在對焦中
                     *          就取得目前的曝光狀態
                     *              如果曝光度完美：建構拍照方法
                     *              如果曝光度未完成：回調預處理狀態
                     */
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    /**
                     * 0 = CONTROL_AF_STATE_INACTIVE           | 自動對焦是關閉狀態
                     * 1 = CONTROL_AF_STATE_PASSIVE_SCAN       | 正在對焦狀態
                     * 2 = CONTROL_AF_STATE_PASSIVE_FOCUSED    | 對焦完畢，但是可能會重新對焦
                     * 3 = CONTROL_AF_STATE_ACTIVE_SCAN        | 正在對焦(因為有呼叫對焦方法)
                     * 4 = CONTROL_AF_STATE_FOCUSED_LOCKED     | 對焦完畢，而且正鎖定焦距中
                     * 5 = CONTROL_AF_STATE_NOT_FOCUSED_LOCKED | 無法對焦，但正鎖定這個焦距當中
                     * 6 = CONTROL_AF_STATE_PASSIVE_UNFOCUSED  | 對焦動作結束，但無法對焦，但是可能會重新對焦
                     */
                    Log.e( "-----" , "  STATE_WAITING_LOCK    afState  =  " + afState.toString() );

                    if ( null == afState) {

                        captureStillPicture();

                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN == afState ) {

                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {

                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();

                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }

                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }

                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

    };

    /**
     *  ---------------------------------  methods  ----------------------------------------
     */
    private void openCamera(int width, int height) {

        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e( "-------" , e.toString() );
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * 設置相機輸出
     * 判斷 是哪一個輸出相機，選擇 輸出畫質
     * 判斷 目前的翻轉情況，確認輸出畫面的大小
     * 判斷 閃光燈狀況
     */
    private void setUpCameraOutputs(int width, int height) {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // getCameraIdList() 回傳一個鏡頭列表，每一個列表的物件都使用某代號代表某一個鏡頭
            // 然後使用 LENS_FACING 來查巡這個代號代表的鏡頭是哪個鏡頭(EX：前/後/外接鏡頭)
            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                // 不使用前置鏡頭
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // getOutputSizes(ImageFormat.JPEG)得到一個 Map<Size> 裡面儲存了裝置各種螢幕大小的 size 物件
                // Collections.max( Map<Size> , Comparator<Size> ) 選出 Map 裡面解析度最高的 Size 物件
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);

                mImageReader.setOnImageAvailableListener( mOnImageAvailableListener, mBackgroundHandler);

                // 得到目前螢幕旋轉方向，是橫還是豎
                //   橫：ROTATION_90 或是 ROTATION_270
                //   豎：ROTATION_0 或是 ROTATION_180
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                boolean swappedDimensions = false;

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                switch (displayRotation) {

                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;

                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;

                    default:
                        Log.e( "------" , "Display rotation is invalid: " + displayRotation);
                }

                // 將目前螢幕的尺寸放入 Point displaySize 物件裡
                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                // 旋轉前的寬和高
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                // 將當前的尺寸設定為最大的尺寸
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;
                // 如果需要旋轉，高和寬就互調
                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }
                // 不可以高於目前最大的寬和高 1920*1080
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH)
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT)
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;

                // 計算最合適的螢幕尺寸
                // map.getOutputSizes(SurfaceTexture.class)表示 SurfaceTexture 支持的尺寸List
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // 獲得目前螢幕方向
                int orientation = getResources().getConfiguration().orientation;

                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;

                // check auto focus
                int[] afAvailableModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);

                if (afAvailableModes.length == 0 || (afAvailableModes.length == 1
                        && afAvailableModes[0] == CameraMetadata.CONTROL_AF_MODE_OFF)) {
                    autoFocusSupported = false;
                } else {
                    autoFocusSupported = true;
                }

                return;
            }
        } catch (Exception e) {
            Log.e( "------" , e.toString() );
        }
    }

    private void takePicture() {

        if (autoFocusSupported) {
            lockFocus();
        } else {
            captureStillPicture();
        }

    }

    private void lockFocus() {
        try {
            // 啟動自動對焦
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e( "-------" , e.toString() );
        }
    }

    /**
     *  螢幕方向改變的方法
     */
    private void configureTransform(int viewWidth, int viewHeight) {

        if (null == textureView || null == mPreviewSize ) {
            return;
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    /**
     * 建構預覽的方法
     * 建構 Request , Session 並且渲染到目標 TextureView 上
     *
     * 目前的狀態是在 預覽：STATE_PREVIEW
     */
    private void createCameraPreviewSession() {

        SurfaceTexture texture = textureView.getSurfaceTexture();
        // 相機關閉的話，預覽 Session 不成立
        // 如果用來渲染的 SurfaceTexture 物件是 null，不繼續預覽
        if ( null == mCameraDevice || null == texture ) {
            return;
        }

        try {

            // 設定 texture 的 長和寬，建構預覽的 Surface 物件
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            // 建構預覽的 Request，以及設定用來預覽的 Surface 物件
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // 建構預覽的 Session
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                            mCaptureSession = cameraCaptureSession;

                            try {
                                // 設定自動對焦
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // 設定自動閃光
                                if (mFlashSupported) {
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                }
                                // 建構預覽的 Request
                                mPreviewRequest = mPreviewRequestBuilder.build();

                                // 建構主要的 Session 物件
                                // 這時候的狀態是 STATE_PREVIEW，Session CallBack 並不會做任何動作
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);

                            } catch (CameraAccessException e) {
                                Log.e( "-------" , e.toString() );
                            }
                        }

                        @Override
                        public void onConfigureFailed( @NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText( CameraActivity.this , "相機預覽問題" , Toast.LENGTH_SHORT ).show();
                        }
                    }, null
            );

        } catch (CameraAccessException e) {
            Log.e( "-------" , e.toString() );
        }
    }

    /**
     * 建構拍照操作的方法
     * 建構 Request
     */
    private void captureStillPicture() {

        try {

            if (null == mCameraDevice) {
                return;
            }

            // 建構拍照的 Request
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(mImageReader.getSurface());

            // 設定自動對焦和自動閃光
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (mFlashSupported) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            }

            // 得到旋轉方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Toast.makeText( CameraActivity.this , "照片已儲存" , Toast.LENGTH_SHORT ).show();
                    unlockFocus();
                }
            }, null);

        } catch (CameraAccessException e) {
            Log.e( "-------" , e.toString() );
        }
    }

    /**
     *  預處理狀態
     *  設定 Request 為 pre-capture
     */
    private void runPrecaptureSequence() {

        try {

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            mState = STATE_WAITING_PRECAPTURE;

            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);

        } catch (CameraAccessException e) {

            e.printStackTrace();

        }

    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            if (mFlashSupported) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            }
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}
