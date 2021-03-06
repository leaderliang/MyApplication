package com.android.zxing.myapplication;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/*
 * Copyright 2014 David Lázaro Esparcia.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * QRCodeReaderView - Class which uses ZXING lib and let you easily integrate a QR decoder view.
 * Take some classes and made some modifications in the original ZXING - Barcode Scanner project.  
 *
 */
public class QRCodeReaderView extends SurfaceView implements SurfaceHolder.Callback,Camera.PreviewCallback {

	public interface OnQRCodeReadListener {
		
		public void onQRCodeRead(String text);
		public void cameraNotFound();
		public void QRCodeNotFoundOnCamImage();
		public void openCameraError();
	}
	
	private OnQRCodeReadListener mOnQRCodeReadListener;
	
	private static final String TAG = QRCodeReaderView.class.getName();
	private Context mContext;
	private QRCodeReader mQRCodeReader;
    private int mPreviewWidth; 
    private int mPreviewHeight; 
    private SurfaceHolder mHolder;
    private CameraManager mCameraManager;
    private volatile boolean mDecoding;

    ExecutorService threadPool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    });
    
	public QRCodeReaderView(Context context) {
		super(context);
		this.mContext = context;
		init();
	}
	
	public QRCodeReaderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.mContext = context;
		init();
	}
	
	public void setOnQRCodeReadListener(OnQRCodeReadListener onQRCodeReadListener) {
		mOnQRCodeReadListener = onQRCodeReadListener;
	}
	
	public CameraManager getCameraManager() {
		return mCameraManager;
	}

	@SuppressWarnings("deprecation")
	private void init() {
        mDecoding = false;
		if (checkCameraHardware(getContext())){
			mCameraManager = new CameraManager(getContext());

			mHolder = this.getHolder();
			mHolder.addCallback(this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);  // Need to set this flag despite it's deprecated
		} else {
			Log.e(TAG, "Error: Camera not found");
			if (mOnQRCodeReadListener != null) {
				mOnQRCodeReadListener.cameraNotFound();
			}
		}
	}
	

	
	/****************************************************
	 * SurfaceHolder.Callback,Camera.PreviewCallback
	 ****************************************************/
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			// Indicate camera, our View dimensions
			mCameraManager.openDriver(holder,this.getWidth(),this.getHeight());
		}catch (Exception e) {
			Log.w(TAG, "Can not openDriver: "+e.getMessage());
			mCameraManager.closeDriver();
		}


		try {
			mQRCodeReader = new QRCodeReader();
			mCameraManager.startPreview();
		} catch (Exception e) {
			Log.e(TAG, "Exception: " + e.getMessage());
			mCameraManager.closeDriver();
		}
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(TAG, "surfaceDestroyed");
		if (mCameraManager!=null&&mCameraManager.getCamera()!=null){
			mCameraManager.getCamera().setPreviewCallback(null);
			mCameraManager.getCamera().stopPreview();
			mCameraManager.getCamera().release();
			mCameraManager.closeDriver();
		}
	}
	
	// Called when camera take a frame 
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
        if(!mDecoding){
            mDecoding = true;
            threadPool.execute(new DecodeTask(data));
        }
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d(TAG, "surfaceChanged");

		if (mHolder.getSurface() == null){
			Log.e(TAG, "Error: preview surface does not exist");
			return;
		}

		//preview_width = width;
		//preview_height = height;
		if (mCameraManager!=null&&mCameraManager.getCamera()!=null){
			mPreviewWidth = mCameraManager.getPreviewSize().x;
			mPreviewHeight = mCameraManager.getPreviewSize().y;

			mCameraManager.stopPreview();
			mCameraManager.getCamera().setPreviewCallback(this);
			mCameraManager.getCamera().setDisplayOrientation(90); // Portrait mode

			mCameraManager.startPreview();
		}else {
			if(mOnQRCodeReadListener!=null){
				mOnQRCodeReadListener.openCameraError();
			}
		}

	}
	
	/**
	 * Transform result to surfaceView coordinates
	 * 
	 * This method is needed because coordinates are given in landscape camera coordinates.
	 * Now is working but transform operations aren't very explained
	 * 
	 * TODO re-write this method explaining each single value    
	 * 
	 * @return a new PointF array with transformed points
	 */
	private PointF[] transformToViewCoordinates(ResultPoint[] resultPoints) {
		
		PointF[] transformedPoints = new PointF[resultPoints.length];
		int index = 0;
		if (resultPoints != null){
			float previewX = mCameraManager.getPreviewSize().x;
			float previewY = mCameraManager.getPreviewSize().y;
			float scaleX = this.getWidth()/previewY;
			float scaleY = this.getHeight()/previewX;
			
			for (ResultPoint point :resultPoints){
				PointF tmppoint = new PointF((previewY- point.getY())*scaleX, point.getX()*scaleY);
				transformedPoints[index] = tmppoint;
				index++;
			}
		}
		return transformedPoints;
		
	}
	
	
	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
			// this device has a camera
			return true;
		} 
		else if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)){
			// this device has a front camera
			return true;
		}
		else if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){
			// this device has any camera
			return true;
		}
		else {
			// no camera on this device
			return false;
		}
	}

    private class DecodeTask implements Runnable {
        private byte[] data;

        public DecodeTask(byte[] data) {
            this.data = data;
        }

        public void run() {
            Log.d(TAG, "begin task");
            PlanarYUVLuminanceSource source = mCameraManager.buildLuminanceSource(data, mPreviewWidth, mPreviewHeight);

            HybridBinarizer hybBin = new HybridBinarizer(source);
            BinaryBitmap bitmap = new BinaryBitmap(hybBin);

            try {
                Result result = mQRCodeReader.decode(bitmap);

                //we found a QRCode
                mOnQRCodeReadListener.onQRCodeRead(result.getText());
            } catch (ChecksumException e) {
                Log.d(TAG, "ChecksumException");
            } catch (NotFoundException e) {

            } catch (Exception e) {
                Log.d(TAG, "FormatException");
            } finally {
                mQRCodeReader.reset();
            }

            Log.d(TAG, "end task: %d");
            mDecoding = false;
        }
    }
	
}
