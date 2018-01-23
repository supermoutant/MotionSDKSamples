package com.sensetime.motionsdksamples.Common;

import android.app.Fragment;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.sensetime.motionsdksamples.Photography.Photo;
import com.sensetime.motionsdksamples.R;
import com.sensetime.motionsdksamples.Utils.Accelerometer;
import com.sensetime.motionsdksamples.Utils.UniqueId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public abstract class BaseCameraFragment extends Fragment {
    //parameter
    protected static final int DEAULT_CAMERA_FACING = Camera.CameraInfo.CAMERA_FACING_FRONT;
    protected static final int CAMERA_PREVIEW_WIDTH = 1280;
    protected static final int CAMERA_PREVIEW_HEIGHT = 720;
    protected static final int CAMERA_PICTURE_HEIGHT = 2448;
    protected static final int CAMERA_PICTURE_WIDTH = 3264;

    private static final String TAG = BaseCameraFragment.class.getSimpleName();

    protected Camera mCamera;
    protected Camera.CameraInfo mCameraInfo;
    protected SurfaceView mPreviewSurfaceView;
    protected SurfaceView mOverlapSurfaceView;
    protected SurfaceHolder mSurfaceHolder;
    //protected TextView mtextViewInfo;
    //protected TextView mTextViewRes;
    private Accelerometer mAccelerometer;
    protected Matrix mMatrix = new Matrix();
    protected String mModelPath = null;

    private boolean mSurfaceCreated = false;

    View mView;
    private Photo mPhoto;
    private int mShotMode = 2;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    //protected void onCreate(Bundle savedInstanceState) {
        //super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        //setContentView(R.layout.activity_base_camera);
        mView = inflater.inflate(R.layout.activity_base_camera, null);

        mOverlapSurfaceView = (SurfaceView) mView.findViewById(R.id.surfaceViewOverlap);
        mOverlapSurfaceView.setZOrderOnTop(true);
        mOverlapSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mPreviewSurfaceView = (SurfaceView) mView.findViewById(R.id.surfaceViewPreview);
        // Makes preview and overlay surface view to apply an aspect fit layout
        mPreviewSurfaceView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Haha! The typo 'GlobalOn' had been fixed in API level 16
                // API level 14 'GlobalOn' -> API level 16 'OnGlobal'
                mPreviewSurfaceView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                makePreviewAspectFit(mPreviewSurfaceView);
                makePreviewAspectFit(mOverlapSurfaceView);
            }
        });
        mSurfaceHolder = mPreviewSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceCreated = true;
                if (mCamera == null) {
                    openCamera(DEAULT_CAMERA_FACING);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    mMatrix.setScale(width / (float) CAMERA_PREVIEW_HEIGHT, height / (float) CAMERA_PREVIEW_WIDTH);
                } else
                {
                    mMatrix.setScale(width / (float) CAMERA_PREVIEW_WIDTH, height / (float) CAMERA_PREVIEW_HEIGHT);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurfaceCreated = false;
            }
        });

        //mtextViewInfo = (TextView) mView.findViewById(R.id.textViewInfo);
        //mTextViewRes = (TextView) mView.findViewById(R.id.textViewRes);
        mAccelerometer = new Accelerometer(getActivity());

        initPhoto();

        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAccelerometer != null)
            mAccelerometer.start();
        if(mCamera == null && mSurfaceCreated){
            openCamera(DEAULT_CAMERA_FACING);
        }
    }

    @Override
    public void onPause(){
        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        super.onPause();
        if (mAccelerometer != null)
            mAccelerometer.stop();
    }

    abstract protected void onPreviewFrame(byte[] data);

    /*
    protected void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .show();
    }
    */

    protected void copyModelInAssets(String modelName, String targetPath) throws IOException {
        File targetFile = new File(targetPath);
        InputStream in = null;
        OutputStream out = null;
        try {
            if (targetFile.exists())
                targetFile.delete();
            targetFile.createNewFile();
            in = getActivity().getAssets().open(modelName);
            out = new FileOutputStream(targetFile);
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            targetFile.delete();
            throw e;
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }

    private void openCamera(int facing) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        RuntimeException runtimeException = null;
        int cameraNum = Camera.getNumberOfCameras();
        for (int i = 0; i < cameraNum; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing || cameraNum == 1) {
                try {
                    mCamera = Camera.open(i);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    runtimeException = e;
                    mCamera = null;
                    continue;
                }
                mCameraInfo = info;
                break;
            }
        }
        if (mCamera == null) {
            if (runtimeException != null) {
                //showDialog("Open Camera Error", runtimeException.getMessage());
            } else {
                //showDialog("open Camera Error", "runtimeexception is null,the number of Cameras is " + Camera.getNumberOfCameras());
            }
        } else {
            try {
                initCamera();
            } catch (IOException e) {
                e.printStackTrace();
                //showDialog("Init Camera Error", e.getMessage());
            }
        }
    }

    private void initCamera() throws IOException {
        if (null != mCamera) {
            List<Camera.Size> pictureSizes = mCamera.getParameters().getSupportedPictureSizes();
            List<Camera.Size> previewSizes = mCamera.getParameters().getSupportedPreviewSizes();

            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            parameters.setPreviewFormat(ImageFormat.NV21);
            parameters.setPreviewSize(CAMERA_PREVIEW_WIDTH, CAMERA_PREVIEW_HEIGHT);

            //picture
            parameters.setPictureSize(CAMERA_PICTURE_WIDTH, CAMERA_PICTURE_HEIGHT);
            parameters.setPictureFormat(ImageFormat.JPEG);

            mCamera.setParameters(parameters);
            for (int i = 0; i < 2; i++) {
                // Make sure there is always a buffer waiting for data,
                // while another one may be blocked in the callback
                mCamera.addCallbackBuffer(new byte[CAMERA_PREVIEW_WIDTH * CAMERA_PREVIEW_HEIGHT * 3 / 2]);
            }
            mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    BaseCameraFragment.this.onPreviewFrame(data);

                    // Add the buffer back to receive new data
                    mCamera.addCallbackBuffer(data);
                }
            });
            setCameraDisplayOrientation();
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        }
    }

    private void setCameraDisplayOrientation() {
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (mCameraInfo.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
    }

    private void setCameraDisplayOrientation2() {
        int orientation = getMotionOrientation();
        mCamera.setDisplayOrientation(orientation);
    }

    private void makePreviewAspectFit(View view) {
        float previewWidth = CAMERA_PREVIEW_WIDTH;
        float previewHeight = CAMERA_PREVIEW_HEIGHT;

        float viewWidth = view.getWidth();
        float viewHeight = view.getHeight();

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            float previewRatio = previewWidth / previewHeight;
            float viewRatio = viewHeight / viewWidth;
            if(previewRatio > viewRatio) {
                layoutParams.width = (int) (viewHeight / previewRatio);
                layoutParams.height = (int) viewHeight;
            }else{
                layoutParams.width = (int) viewWidth;
                layoutParams.height = (int) (viewWidth * previewRatio);
            }
        } else{
            if (previewWidth * viewHeight > viewWidth * previewHeight) {
                layoutParams.width = (int) viewWidth;
                layoutParams.height = (int) (viewWidth * previewHeight / previewWidth);
            } else {
                layoutParams.width = (int) (viewHeight * previewWidth / previewHeight);
                layoutParams.height = (int) viewHeight;
            }
        }
        float previewRatio = previewWidth / previewHeight;

        layoutParams.width = 534;
        layoutParams.height = 300;

        view.setLayoutParams(layoutParams);
    }

    private boolean isFrontCamera(){
        if(mCameraInfo == null) {
            return false;
        }
        return mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    protected int getMotionOrientation() {
        if(mCamera == null || mCameraInfo == null)
            return -1;
        /**
         * 如果使用前置摄像头，请注意显示的图像与帧图像左右对称，需处理坐标
         */
        boolean frontCamera = isFrontCamera();
        /**
         * 获取重力传感器返回的方向 即手机方向 Portrait or landscape,only choose
         * portrait
         */
        int dir = Accelerometer.getDirection();
        int orientation = mCameraInfo.orientation;//MI3 front:270; back:90

        /**
         * 请注意前置摄像头与后置摄像头旋转定义不同
         * 请注意不同手机摄像头旋转定义不同
         */
        int ori = orientation / 90;
        if (frontCamera && ((orientation == 270) || (orientation == 90))) {
            if ((dir & 1)==0) {
                dir = dir ^ 2;
            }
        }

        dir = (((4 + dir) - 1) % 4 + ori) % 4;
        dir =  1<< dir;
        return dir;
    }

    public int getRotate() {
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        result = (mCameraInfo.orientation + degrees + 360) % 360;
        return result;
    }

    public Matrix getMatrix() {
        return mMatrix;
    }

    /*
    private void takePicture() {
        if (mCameraDevice == null)
            return;
        // 创建拍照需要的CaptureRequest.Builder
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //拍照
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, null, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    */

    /**
     * 自动对焦 对焦成功后 就进行拍照
     */
    Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {//对焦成功

                camera.takePicture(new Camera.ShutterCallback() {//按下快门
                    @Override
                    public void onShutter() {
                        //按下快门瞬间的操作
                    }
                }, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {//是否保存原始图片的信息

                    }
                }, pictureCallback);
            }
        }
    };

    /**
     * 获取图片
     */
    Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            final Bitmap resource = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (resource == null) {
                //Toast.makeText(CameraActivity.this, "拍照失败", Toast.LENGTH_SHORT).show();
            }
            final Matrix matrix = new Matrix();
            //matrix.setRotate(90);
            matrix.setRotate(0);
            final Bitmap bitmap = Bitmap.createBitmap(resource, 0, 0, resource.getWidth(), resource.getHeight(), matrix, true);

            //mPhoto.setBitmap(bitmap);
            //mPhoto.process();

            if (2 == mShotMode) {
                mPhoto.setBitmap2(bitmap);
            } else {
                mPhoto.setBitmap3(bitmap);
            }
            mCamera.startPreview();

            /*
            if (bitmap != null && iv_show != null && iv_show.getVisibility() == View.GONE) {
                mCamera.stopPreview();
                iv_show.setVisibility(View.VISIBLE);
                mSurfaceView.setVisibility(View.GONE);
                Toast.makeText(CameraActivity.this, "拍照", Toast.LENGTH_SHORT).show();
                iv_show.setImageBitmap(bitmap);
            }
            */
        }
    };

    public synchronized void takePicture(int mode) {
        if (mCamera == null) return;
        //自动对焦后拍照
        mShotMode = mode;
        mCamera.autoFocus(autoFocusCallback);
    }

    public void initPhoto() {
        mPhoto = Photo.getInstance();
    }
}
