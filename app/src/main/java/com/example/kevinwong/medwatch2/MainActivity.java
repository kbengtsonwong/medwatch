package com.example.kevinwong.medwatch2;

import android.os.Environment;
import android.util.Log;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;
import android.nfc.NfcAdapter;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.media.MediaRecorder;
import android.Manifest;
import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;


import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

// Main Activity
public class MainActivity extends WearableActivity implements SensorEventListener{
    
    // regulates
    static boolean recording_status = false;

    // various sensors
    private SensorManager sensor_manager;
    private Sensor accelerometer;
    private Sensor lin_accelerometer;
    private Sensor gyroscope;
    private Sensor rotation;

    private String file_name;
    private char file_input_concat = ',';
    private TextView mTextView;

    // String used to name and identify records for saving and upload
    private String time_stamp = "";

    private WavRecorder wavRecorder;
    private FileWriter file_writer;
    private TransferUtility transfer_utility;

    // unused variables
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private boolean permissionToRecordAccepted = false;
    private MediaRecorder soundRecorder;
    private NfcAdapter mNfcAdapter;
    private IntentFilter[] readTagFilters;
    private PendingIntent pendingIntent;
    private String outputFile;


    // method to check if user has granted app appropriate permissions
    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        // clearing permissions
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_med_watch);
        final Button image_button = (Button) findViewById(R.id.imageButton);

        mTextView = (TextView) findViewById(R.id.medName);
        mTextView.setText(Constants.MEDICATION_NAME);
        
        // Enables Always-on
        setAmbientEnabled();
        
        // initialize sensors
        getDefaultActivitySensors();
        final SensorEventListener listener = this;
        image_button.setOnClickListener(new View.OnClickListener() {

            @Override
            // callback function that executes when button is clicked
            public void onClick(View v) {


                // if the button is clicked and recording status is set to false
                if (!recording_status) {
                    image_button.setBackgroundResource(R.drawable.stop_button);
                    recording_status = true;

                    // stores a time stamp of the current (start) time of the recording
                    time_stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
                    Log.d("LOG", "time_stamp value: " + time_stamp);

                    // initializes the FileWriter that will write the spatial data readings to a specified file
                    try {
                        file_name = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MedWatch/" + time_stamp + "_spatial.csv";
                        file_writer = new FileWriter(file_name, true);
                    } catch (IOException ie) {
                        ie.printStackTrace();
                    }

                    // initializes a wavRecorder class with a specified path to store audio files
                    // the time stamp is used in the naming scheme to organize the spatial and audio files

                    wavRecorder = new WavRecorder(Environment.getExternalStorageDirectory().getAbsolutePath() + "/MedWatch/" + time_stamp + "_audio.wav");
                    wavRecorder.startRecording();

                    registerActivitySensorListeners();
                    //handleIntent(getIntent());


                    // if the button is clicked and recording status is set to true
                } else {

                    // unregister the listener and stop the audio recording
                    sensor_manager.unregisterListener(listener);
                    wavRecorder.stopRecording();

                    // reset
                    image_button.setBackgroundResource(R.drawable.start_button);
                    recording_status = false;

                    Log.d("LOG", "Turned off listener and stopped audio recording.");
                    Log.d("LOG", "Reset button.");

                    Log.d("LOG", "Beginning spatial data file upload to S3");
                    Log.d("LOG", "time_stamp value: " + time_stamp);
                    upload(Environment.getExternalStorageDirectory().getAbsolutePath() + "/MedWatch/" + time_stamp + "_spatial.csv");
                    Log.d("LOG", "Spatial data file upload complete.");


                }
            }
        });
    }


    // Initializes desired sensors after displaying in log available sensors for the device
    private void getDefaultActivitySensors()
    {
        sensor_manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = sensor_manager.getSensorList(Sensor.TYPE_ALL);
        for(Sensor i: deviceSensors) {
            Log.d("LOG", String.valueOf(i));
        }

        accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(accelerometer!=null) {
            Log.d("LOG", "Registered accelerometer with min delay:" + accelerometer.getMinDelay());
        }
        else {
            Log.d("LOG", "Accelerometer failed to register.");
        }

        lin_accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(lin_accelerometer!=null) {
            Log.d("LOG", "Registered linear accelerometer with min delay:" + lin_accelerometer.getMinDelay());
        }
        else {
            Log.d("LOG", "Linear Accelerometer failed to register.");
        }

        gyroscope = sensor_manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(gyroscope!=null) {
            Log.d("LOG", "Registered gyroscope with min delay:" + gyroscope.getMinDelay());
        }
        else {
            Log.d("LOG", "Gyroscope failed to register.");
        }

        // GAME_ROTATION vs ROTATION - https://developer.android.com/guide/topics/sensors/sensors_position#sensors-pos-gamerot
        rotation = sensor_manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if(rotation!=null) {
            Log.d("LOG", "Registered Game Rotation Vector with min delay:" + rotation.getMinDelay());
        }
        else {
            Log.d("LOG", "Game Rotation Vector failed to register.");
        }
        
    }

    private void registerActivitySensorListeners( )
    {
        sensor_manager.registerListener(this, accelerometer, Constants.SENSOR_DELAY);
        sensor_manager.registerListener(this, lin_accelerometer, Constants.SENSOR_DELAY);
        sensor_manager.registerListener(this, gyroscope, Constants.SENSOR_DELAY);
        sensor_manager.registerListener(this, rotation, Constants.SENSOR_DELAY);
    }

    private void recordActivitySensorData(SensorEvent event)
    {
        float event_time = event.timestamp;
        long current_time = System.currentTimeMillis();

        //?? SCAFFOLDIGN DMW -- TODO: Add NFC data.

        String file_input_string;
        file_input_string = String.valueOf(current_time) + file_input_concat +
                String.valueOf(event.sensor.getType()) + file_input_concat +
                String.valueOf(event.sensor.getName()) + file_input_concat +
                String.valueOf(event.values[0]) + file_input_concat +
                String.valueOf(event.values[1]) + file_input_concat +
                String.valueOf(event.values[2]) +
                System.lineSeparator();

        try {
            file_writer.write(file_input_string);
        } catch(IOException ie) {
            Log.d("LOG","Error using FileWriter recording activity sensor data.");
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    // callback function that handles new sensor events
    public final void onSensorChanged(SensorEvent event) {

        switch(event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                Log.d("sensor type", String.valueOf(Sensor.TYPE_ACCELEROMETER));
                Log.d("values",String.valueOf(event.values[0]) + "," +String.valueOf(event.values[1])+ "," + String.valueOf(event.values[2]));
                break;

            case Sensor.TYPE_LINEAR_ACCELERATION:
                Log.d("sensor type", String.valueOf(Sensor.TYPE_LINEAR_ACCELERATION));
                Log.d("values",String.valueOf(event.values[0]) + "," +String.valueOf(event.values[1])+ "," + String.valueOf(event.values[2]));
                break;

            case Sensor.TYPE_GYROSCOPE:
                Log.d("sensor type", String.valueOf(Sensor.TYPE_GYROSCOPE));
                Log.d("values",String.valueOf(event.values[0]) + "," +String.valueOf(event.values[1])+ "," + String.valueOf(event.values[2]));
                break;

            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                Log.d("sensor type", String.valueOf(Sensor.TYPE_GAME_ROTATION_VECTOR));
                Log.d("values",String.valueOf(event.values[0]) + "," +String.valueOf(event.values[1])+ "," + String.valueOf(event.values[2]));
                break;
        }

        // call helper method to write sensor event to file
        recordActivitySensorData(event);
    }

    @Override
    //Called as part of the activity lifecycle when an activity is going into the background, but has not (yet) been killed.
    protected void onPause() {
        super.onPause();
        if(file_writer != null) {
            try {
                file_writer.close();
            }
            catch(IOException ie)
            {

            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //setupForegroundDispatch(this, mNfcAdapter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        wavRecorder.stopRecording();
        sensor_manager.unregisterListener(this);

    }


    // Upload a test file to an Amazon S3 bucket
    public void upload(String path){

        // Specifying Android Watch filepath of file to upload
        File fileToUpload = new File(path);

        // Validating file
        if(fileToUpload.exists()) {
            Log.d("FILE", "File exists..");
        } else {
            Log.d("FILE", "File does not exist..");
        }
        Log.d("FILE", fileToUpload.toString());
        Log.d("FILE SIZE", String.valueOf(fileToUpload.length()));



        AmazonS3 s3;
        TransferUtility transferUtility;

        // Initialize the Amazon Cognito credentials provider
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:db7b3ef5-ebef-4c55-a31b-edc804c151d9", // Identity Pool ID
                Regions.US_EAST_1 // Region
        );

        // Authenticate an AWS s3 client with the Cognito Credentials
        s3 = new AmazonS3Client(credentialsProvider);
        s3.setRegion(Region.getRegion(Regions.US_EAST_1));
        Log.d("s3", s3.toString());


        // Instantiate a new TransferUtility cl≤ass that serves as the mechanism for upload to s3
        transferUtility = new TransferUtility(s3, this);

        Log.e("AWS.", " Attempting to upload...");

        // Attempt file upload using a try-catch block
        try {

            // Create an observer instance to monitor the upload
            TransferObserver observer = transferUtility.upload(
                    "android-s3-test-bucket",     /* The bucket to upload to */
                    fileToUpload.getName(),    /* The key for the uploaded object */
                    fileToUpload  );   /* The file where the data to upload exists */


            Log.d("AWS. ",
                    "Transfer ID: " + observer.getId() +
                            ". Bytes transferred: " + observer.getBytesTransferred());

            // Attach a listener to the observer to get state update and progress notifications
            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        // Handle a completed upload.
                        Log.d("YourMainActivity","AWS s3 Upload.  TransferState = COMPLETED");
                    }
                }
                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int)percentDonef;

                    Log.d("AWS s3 Upload", "id:" + id + " bytesCurrent: " + bytesCurrent
                            + " bytesTotal: " + bytesTotal + " " + percentDone + "%");
                }
                @Override
                public void onError(int id, Exception ex) {
                    // Handle errors
                    Log.d("AWS s3 Upload","AWS. Error uploading to S3.");
                }

            });


        } catch(Exception e) {
            Log.e("AWS.  Error in Try block.",e.getMessage());
        }
    }
}
