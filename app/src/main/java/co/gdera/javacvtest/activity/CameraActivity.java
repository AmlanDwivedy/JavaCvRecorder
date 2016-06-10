package co.gdera.javacvtest.activity;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import co.gdera.javacvtest.R;
import co.gdera.javacvtest.utils.FrameData;
import co.gdera.javacvtest.utils.PreviewUtils;
import co.gdera.javacvtest.utils.SavedFrames;
import co.gdera.javacvtest.utils.Utils;
import co.gdera.javacvtest.view.CameraPreview;

import static co.gdera.javacvtest.utils.PreviewUtils.PREVIEW_HEIGHT;
import static co.gdera.javacvtest.utils.PreviewUtils.PREVIEW_WIDTH;
import static org.bytedeco.javacpp.opencv_core.*;


/**
 * Created by amlan on 8/6/16.
 */
public class CameraActivity extends Activity implements CameraPreview.PreviewReadyCallback {

    private SensorManager mSensorManager;
    private Sensor mOrientationSensor;
    private LocationManager mLocationManager;
    private String mLocationProvider;
    private float mDirection;
    private float mTargetDirection;
    private boolean mStopDrawing;
    private Handler mHandler = new Handler();
    private final float MAX_ROATE_DEGREE = 1.0f;
    private TextView angleTextView;
    private RelativeLayout frameLayout;
    private CameraPreview mPreview;
    private Button button;
    private String ffmpeg_link;
    private FFmpegFrameRecorder recorder;
    private boolean isRecordingStarted = false;
    private SavedFrames lastSavedframe;
    private TreeMap<Integer, IplImage> dataTreeMap = new TreeMap<>();
    private int direction2;
    private float direction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);

        frameLayout = (RelativeLayout) findViewById(R.id.frame);
        angleTextView = (TextView) findViewById(R.id.angleTextView);
        button = (Button) findViewById(R.id.start);
        initResources();
        initServices();

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (button.getText().toString().equalsIgnoreCase("start")) {
                    initRecorder();
                    isRecordingStarted = true;
                    button.setText("Stop");
                } else {
                    isRecordingStarted = false;
                    stopRecorder();
                    button.setText("Start");
                    recordImages();
                }
            }
        });

    }

    private void recordImages() {
        String sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
        String folder = sdCard + "/image360";
        File fileFolder = new File(folder);
        if (!fileFolder.exists())
            fileFolder.mkdirs();
        File file = new File(fileFolder.getAbsolutePath() + "/" + "images" + new Random().nextLong() + ".mp4");
        if (!file.exists())
            try {
                file.createNewFile();
                ffmpeg_link = file.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        FrameRecorder recorder = new FFmpegFrameRecorder(ffmpeg_link, PreviewUtils.PREVIEW_WIDTH, PreviewUtils.PREVIEW_HEIGHT, 1);
        recorder.setFormat("mp4");
        recorder.setFrameRate(10);
        try {
            recorder.start();
            Iterator it = dataTreeMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer,IplImage> pair = (Map.Entry)it.next();
                System.out.println(pair.getKey() + " = " + pair.getValue());
                it.remove(); // avoids a ConcurrentModificationException
                recorder.record(pair.getValue());
            }
            recorder.stop();
            recorder.release();
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecorder() {
        try {
            recorder.stop();
            recorder.release();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
        recorder = null;
    }

    private void initRecorder() {
        if (ffmpeg_link != null) {
            recorder = new FFmpegFrameRecorder(ffmpeg_link, PreviewUtils.PREVIEW_WIDTH, PreviewUtils.PREVIEW_HEIGHT, 1);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
//            recorder.setFrameRate(30);
            try {
                recorder.start();
            } catch (FrameRecorder.Exception e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(CameraActivity.this, "Can't create file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mLocationProvider != null) {
            updateLocation(mLocationManager.getLastKnownLocation(mLocationProvider));
            mLocationManager.requestLocationUpdates(mLocationProvider, 2000, 10, mLocationListener);
        } else {
//            mLocationTextView.setText(R.string.cannot_get_location);
        }
        if (mOrientationSensor != null) {
            mSensorManager.registerListener(mOrientationSensorEventListener, mOrientationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        mStopDrawing = false;
        mHandler.postDelayed(mCompassViewUpdater, 20);

        mPreview = new CameraPreview(this, 0, CameraPreview.LayoutMode.FitToParent);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.addView(mPreview, params);
        mPreview.setOnPreviewReady(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStopDrawing = true;
        if (mOrientationSensor != null) {
            mSensorManager.unregisterListener(mOrientationSensorEventListener);
        }
        if (mLocationProvider != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }

        mPreview.setPreviewCallback(null);
        mPreview.stop();
        frameLayout.removeAllViews();
        mPreview = null;
    }

    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[0] * -1.0f;
            mTargetDirection = normalizeDegree(direction);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }

    LocationListener mLocationListener = new LocationListener() {

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (status != LocationProvider.OUT_OF_SERVICE) {
                updateLocation(mLocationManager.getLastKnownLocation(mLocationProvider));
            } else {
//                mLocationTextView.setText(R.string.cannot_get_location);
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onLocationChanged(Location location) {
            updateLocation(location);
        }

    };

    private void updateLocation(Location lastKnownLocation) {

    }

    private void initServices() {
        // sensor manager
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        // location manager
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        mLocationProvider = mLocationManager.getBestProvider(criteria, true);

    }

    private void initResources() {
        String sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
        String folder = sdCard + "/image360";
        File fileFolder = new File(folder);
        if (!fileFolder.exists())
            fileFolder.mkdirs();
        File file = new File(fileFolder.getAbsolutePath() + "/" + new Date().getTime() + ".mp4");
        if (!file.exists())
            try {
                file.createNewFile();
                ffmpeg_link = file.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        mDirection = 0.0f;
        mTargetDirection = 0.0f;
    }

    protected Runnable mCompassViewUpdater = new Runnable() {
        @Override
        public void run() {
//            if (mPointer != null && !mStopDrawing) {
            if (mDirection != mTargetDirection) {

                // calculate the short routine
                float to = mTargetDirection;
                if (to - mDirection > 180) {
                    to -= 360;
                } else if (to - mDirection < -180) {
                    to += 360;
                }

                // limit the max speed to MAX_ROTATE_DEGREE
                float distance = to - mDirection;
                if (Math.abs(distance) > MAX_ROATE_DEGREE) {
                    distance = distance > 0 ? MAX_ROATE_DEGREE : (-1.0f * MAX_ROATE_DEGREE);
                }

                // need to slow down if the distance is short
//                    mDirection = normalizeDegree(mDirection
//                            + ((to - mDirection) * mInterpolator.getInterpolation(Math
//                            .abs(distance) > MAX_ROATE_DEGREE ? 0.4f : 0.3f)));
//                    mPointer.updateDirection(mDirection);
            }

            updateDirection();

            mHandler.postDelayed(mCompassViewUpdater, 20);
//            }
        }
    };

    private void updateDirection() {

        direction = normalizeDegree(mTargetDirection * -1.0f);
        direction2 = (int) direction;
//        Log.e("amlan", "----------" + direction2);

        angleTextView.setText("Angle" + direction2);
    }


    @Override
    public void onPreviewReady(Camera camera) {
        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] data, Camera camera) {

                if (isRecordingStarted) {
                    /*IplImage iplImage = processImage(data, PreviewUtils.PREVIEW_WIDTH, PreviewUtils.PREVIEW_HEIGHT);
                    try {
                        iplImage.roi();
                        recorder.record(iplImage);
                    } catch (FrameRecorder.Exception e) {
                        e.printStackTrace();
                    }*/

                    long frameTimeStamp = new Date().getTime();

                    if (lastSavedframe == null) {
                        lastSavedframe = new SavedFrames(data, frameTimeStamp);
                        return;
                    }
                    try {
                        IplImage yuvIplImage = IplImage.create(PREVIEW_HEIGHT, PREVIEW_WIDTH, opencv_core.IPL_DEPTH_8U, 2);
                        yuvIplImage.getByteBuffer().put(lastSavedframe.getFrameBytesData());

                        IplImage bgrImage = IplImage.create(PREVIEW_WIDTH, PREVIEW_HEIGHT, opencv_core.IPL_DEPTH_8U, 4);// In my case, PREVIEW_WIDTH = 1280 andPREVIEW_HEIGHT = 720
                        IplImage transposed = IplImage.create(PREVIEW_HEIGHT, PREVIEW_WIDTH, yuvIplImage.depth(), 4);
                        final IplImage squared = IplImage.create(PREVIEW_HEIGHT, PREVIEW_HEIGHT, yuvIplImage.depth(), 4);

                        int[] _temp = new int[PREVIEW_WIDTH * PREVIEW_HEIGHT];

                        Utils.YUV_NV21_TO_BGR(_temp, data, PREVIEW_WIDTH, PREVIEW_HEIGHT);

                        bgrImage.getIntBuffer().put(_temp);

                        opencv_core.cvTranspose(bgrImage, transposed);
                        opencv_core.cvFlip(transposed, transposed, 1);

                        opencv_core.cvSetImageROI(transposed, opencv_core.cvRect(0, 0, PREVIEW_HEIGHT, PREVIEW_HEIGHT));
                        opencv_core.cvCopy(transposed, squared, null);
                        opencv_core.cvResetImageROI(transposed);

//                        recorder.setTimestamp(lastSavedframe.getTimeStamp());
                        try {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    dataTreeMap.put(direction2, squared);
                                }
                            }).start();
//                            recorder.record(squared);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    lastSavedframe = new SavedFrames(data, frameTimeStamp);
                }

                camera.addCallbackBuffer(data);
            }
        });
    }

    protected opencv_core.IplImage processImage(byte[] data, int width, int height) {
        int f = 1;// SUBSAMPLING_FACTOR;

        // First, downsample our image and convert it into a grayscale IplImage
        opencv_core.IplImage grayImage = opencv_core.IplImage.create(width / f, height / f, IPL_DEPTH_8U, 1);

        int imageWidth = grayImage.width();
        int imageHeight = grayImage.height();
        int dataStride = f * width;
        int imageStride = grayImage.widthStep();
        ByteBuffer imageBuffer = grayImage.getByteBuffer();
        for (int y = 0; y < imageHeight; y++) {
            int dataLine = y * dataStride;
            int imageLine = y * imageStride;
            for (int x = 0; x < imageWidth; x++) {
                imageBuffer.put(imageLine + x, data[dataLine + f * x]);
            }
        }

        return grayImage;
    }

}
