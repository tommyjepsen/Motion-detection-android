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
import android.widget.TextView;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Calendar;

import dk.nodes.utils.NLog;
import io.socket.client.IO;
import io.socket.client.Socket;

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
    private int iterateCameraTrigger = 0; //Miliseconds
    private int diffCountMaxExceed = 60; //Magic number
    private Socket mSocket;
    private TextView text;
    private String ipAddress = "http://192.168.1.209:3000";
    private Bitmap fullSize;
    private Bitmap newImageBitmap;
    private double oldTotal = 0;
    private double totalTrigger = 0;
    private boolean takingImage = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        iv_image = (ImageView) findViewById(R.id.imageView);
        sv = (SurfaceView) findViewById(R.id.surfaceView);
        text = (TextView) findViewById(R.id.text);

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

                parameters = mCamera.getParameters();
                for (Camera.Size s : parameters.getSupportedPictureSizes()) {
                    parameters.setPictureSize(s.width, s.height);
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();

                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        compareBytes(data);
                    }
                });

                mCall = new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        setImage(data);

                    }
                };

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

    private void setImage(byte[] data) {
        fullSize = BitmapFactory.decodeByteArray(data, 0, data.length);
        iv_image.setImageBitmap(fullSize);
        takingImage = false;
        attemptSend();
    }

    private void compareBytes(byte[] newByte) {

        double total = 0;
        for (int i = 0; i < newByte.length; i++) {
            total += newByte[i];
        }

        double abs = (Math.abs(oldTotal - total) / newByte.length);
        NLog.d(MainActivity.class.getSimpleName(), "" + Calendar.getInstance().getTime() + "ABS: " + abs);

        if (abs > 5 && !takingImage) {

            text.setText("DIFFERENT IMAGE, BRAH");

            if (!takingImage) {
                try {
                    mCamera.takePicture(null, null, mCall);
                } catch (Exception e) {
                    NLog.e(e);
                }
            }

        } else {
            text.setText("SAME IMAGE BRO");
        }

        oldTotal = total;
    }

    private void iterateCameraTrigger() {
//        h.postDelayed(
//                new Runnable() {
//                    private long time = 0;
//
//                    @Override
//                    public void run() {
//        mCamera.startPreview();
//        mCamera.takePicture(null, null, mCall);
//                    }
//                }, iterateCameraTrigger);
    }

    private void compareImages(byte[] newImageData) {
        //Make it a bitmap
        fullSize = BitmapFactory.decodeByteArray(newImageData, 0, newImageData.length);
        newImageBitmap = Bitmap.createScaledBitmap(fullSize, 1, 1, false);

        iv_image.setImageBitmap(newImageBitmap);

        if (oldImageBitmap != null) {
            if (equals(newImageBitmap, oldImageBitmap)) {
                NLog.d(MainActivity.class.getSimpleName(), "SAME IMAGE");
                text.setText("SAME IMAGE BRO");
            } else {
                NLog.d(MainActivity.class.getSimpleName(), "DIFFERENT IMAGE BRO");
                text.setText("DIFFERENT IMAGE, BRAH");
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
        int p1_0 = bitmap1.getPixel(0, 0);
        int p2_0 = bitmap2.getPixel(0, 0);
        int calcDiff1 = Math.abs(Color.red(p1_0) - Color.red(p2_0)) + Math.abs(Color.green(p1_0) - Color.green(p2_0)) + Math.abs(Color.blue(p1_0) - Color.blue(p2_0));

        if (calcDiff1 < diffCountMaxExceed) {
            return true;
        } else {
            return false;
        }

    }

}
