package com.tommyjepsen.beaniemotion;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import dk.nodes.utils.NLog;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends Activity {

    private ImageView iv_image;
    private SurfaceView sv;
    private Bitmap oldImageBitmap;
    private SurfaceHolder sHolder;
    private Camera mCamera;
    private Camera.Parameters parameters;
    private Camera.PictureCallback mCall;
    private byte[] oldImageData;
    private Handler h = new Handler();
    private int iterateCameraTrigger = 500; //Miliseconds
    private int diffCountMaxExceed = 20; //Magic number
    private Socket mSocket;
    private String ipAddress = "http://192.168.1.209:3000";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        iv_image = (ImageView) findViewById(R.id.imageView);
        sv = (SurfaceView) findViewById(R.id.surfaceView);

        try {
            mSocket = IO.socket(ipAddress);
            mSocket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        sHolder = sv.getHolder();
        sHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(MainActivity.class.getSimpleName(), "Camera setting up");

                mCamera = Camera.open();
                try {
                    mCamera.setPreviewDisplay(holder);

                } catch (IOException exception) {
                    mCamera.release();
                    mCamera = null;
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(MainActivity.class.getSimpleName(), "Camera taking first picture");

                parameters = mCamera.getParameters();

                mCamera.setParameters(parameters);
                mCamera.startPreview();

                mCall = new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        NLog.d(MainActivity.class.getSimpleName(), "New picture taken");
                        compareImages(data);
                    }
                };

                mCamera.takePicture(null, null, mCall);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        });
        sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    }

    private void attemptSend() {
        try {
            String message = "Different image!";

            mSocket.emit("visitor", message);
        } catch (Exception e) {
            NLog.e(e);
        }
    }

    private void iterateCameraTrigger() {
        h.postDelayed(
                new Runnable() {
                    private long time = 0;

                    @Override
                    public void run() {
                        mCamera.startPreview();
                        mCamera.takePicture(null, null, mCall);
                    }
                }, iterateCameraTrigger);
    }

    private void compareImages(byte[] newImageData) {
        //Make it a bitmap
        Bitmap fullSize = BitmapFactory.decodeByteArray(newImageData, 0, newImageData.length);
        Bitmap newImageBitmap = Bitmap.createScaledBitmap(fullSize, 1, 1, false);

        iv_image.setImageBitmap(newImageBitmap);

        if (oldImageBitmap != null) {
            if (equals(newImageBitmap, oldImageBitmap)) {
                NLog.d(MainActivity.class.getSimpleName(), "SAME IMAGE");
            } else {
                NLog.d(MainActivity.class.getSimpleName(), "DIFFERENT IMAGE BRO");
                attemptSend();
            }
        }

        NLog.d(MainActivity.class.getSimpleName(), "_____");
        //Set old image to the new one
        oldImageData = newImageData;
        oldImageBitmap = newImageBitmap;

        //Take new picture
        iterateCameraTrigger();
    }

    public boolean equals(Bitmap bitmap1, Bitmap bitmap2) {
        int p1 = bitmap1.getPixel(0, 0);
        int p2 = bitmap2.getPixel(0, 0);
        int calcDiff = Math.abs(Color.red(p1) - Color.red(p2)) + Math.abs(Color.green(p1) - Color.green(p2)) + Math.abs(Color.blue(p1) - Color.blue(p2));

        NLog.d(MainActivity.class.getSimpleName(), "Calculated difference: " + calcDiff);
        if (calcDiff < diffCountMaxExceed) {
            return true;
        } else {
            return false;
        }

    }

}
