package com.platypii.baseline;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.crash.FirebaseCrash;
import com.platypii.baseline.altimeter.MyAltimeter;
import com.platypii.baseline.audible.MyAudible;
import com.platypii.baseline.bluetooth.BluetoothService;
import com.platypii.baseline.data.KVStore;
import com.platypii.baseline.data.MyDatabase;
import com.platypii.baseline.data.MyFlightManager;
import com.platypii.baseline.data.MySensorManager;
import com.platypii.baseline.location.LocationService;
import com.platypii.baseline.util.Convert;
import com.platypii.baseline.util.Util;

/**
 * Start and stop essential services
 */
public class Services {
    private static final String TAG = "Services";

    // Count the number of times an activity has started.
    // This allows us to only stop services once the app is really done.
    private static int startCount = 0;
    private static boolean initialized = false;

    // How long to wait after the last activity shutdown to terminate services
    private final static Handler handler = new Handler();
    private static final int shutdownDelay = 10000;

    // Services
    public static LocationService location;
    public static MySensorManager sensors;
    public static BluetoothService bluetooth;
    public static MyAudible audible;

    public static void start(@NonNull Activity activity) {
        startCount++;
        if(!initialized) {
            initialized = true;
            Log.i(TAG, "Starting services");
            final Context appContext = activity.getApplicationContext();
            handler.removeCallbacks(stopRunnable);

            // Start the various services

            Log.i(TAG, "Loading app settings");
            loadPreferences(appContext);

            Log.i(TAG, "Starting key value store");
            KVStore.start(appContext);

            // Create audible class, but wait for text-to-speech to start
            audible = new MyAudible();

            Log.i(TAG, "Starting bluetooth service");
            if(bluetooth == null) {
                bluetooth = new BluetoothService();
            }
            if(BluetoothService.preferenceEnabled) {
                bluetooth.startAsync(activity);
            }

            Log.i(TAG, "Starting location service");
            location = new LocationService();
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // Enable location services
                try {
                    location.start(appContext);
                } catch (SecurityException e) {
                    Log.e(TAG, "Failed to start location service", e);
                }
            } else {
                // Request the missing permissions
                final String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
                ActivityCompat.requestPermissions(activity, permissions, BaseActivity.MY_PERMISSIONS_REQUEST_LOCATION);
            }

            Log.i(TAG, "Starting sensors");
            sensors = new MySensorManager(appContext);

            Log.i(TAG, "Starting altimeter");
            MyAltimeter.startAsync(appContext);

            Log.i(TAG, "Checking for text-to-speech data");
            checkTextToSpeech(activity);

            Log.i(TAG, "Services started");
        } else {
            Log.v(TAG, "Services already started");
        }

        // If you wanted to automatically upload any unsynced files, this is how:
        // TheCloud.uploadAll();
    }

    /**
     * Call this function once text-to-speech data is ready
     */
    static void onTtsLoaded(Context appContext) {
        if(audible != null) {
            // TTS loaded, start the audible
            audible.start(appContext);
        }
    }

    public static void stop() {
        startCount--;
        if(startCount == 0) {
            handler.postDelayed(stopRunnable, shutdownDelay);
        }
    }

    /**
     * A thread that shuts down services after activity has stopped
     */
    private static final Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            if(initialized && startCount == 0) {
                if(!MyDatabase.isLogging() && !audible.isEnabled()) {
                    Log.i(TAG, "All activities have stopped. Stopping services.");
                    // Stop services
                    audible.stop();
                    audible = null;
                    MyAltimeter.stop();
                    sensors.stop();
                    sensors = null;
                    location.stop();
                    location = null;
                    bluetooth.stop();
                    bluetooth = null;
                    KVStore.stop();
                    initialized = false;
                } else {
                    if(MyDatabase.isLogging()) {
                        Log.w(TAG, "All activities have stopped, but still recording track. Leaving services running.");
                    }
                    if(audible.isEnabled()) {
                        Log.w(TAG, "All activities have stopped, but audible still active. Leaving services running.");
                    }
                }
            }
        }
    };

    private static void loadPreferences(Context appContext) {
        final SharedPreferences prefs =  PreferenceManager.getDefaultSharedPreferences(appContext);

        // Metric
        Convert.metric = prefs.getBoolean("metric_enabled", false);

        // Bluetooth
        BluetoothService.preferenceEnabled = prefs.getBoolean("bluetooth_enabled", false);
        BluetoothService.preferenceDeviceId = prefs.getString("bluetooth_device_id", null);
        BluetoothService.preferenceDeviceName = prefs.getString("bluetooth_device_name", null);

        // Home location
        final double home_latitude = Util.parseDouble(prefs.getString("home_latitude", null));
        final double home_longitude = Util.parseDouble(prefs.getString("home_longitude", null));
        if(Util.isReal(home_latitude) && Util.isReal(home_longitude)) {
            // Set home location
            MyFlightManager.homeLoc = new LatLng(home_latitude, home_longitude);
        }
    }

    private static void checkTextToSpeech(final Activity activity) {
        new AsyncTask<Void,Void,Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                Log.i("TextToSpeech", "Checking for text-to-speech");
                final Intent checkIntent = new Intent();
                checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);

                // Check if intent is supported
                final PackageManager pm = activity.getPackageManager();
                final ResolveInfo resolveInfo = pm.resolveActivity(checkIntent, PackageManager.MATCH_DEFAULT_ONLY);
                if(resolveInfo != null) {
                    try {
                        activity.startActivityForResult(checkIntent, BaseActivity.MY_TTS_DATA_CHECK_CODE);
                        return true;
                    } catch(ActivityNotFoundException e) {
                        Log.e("TextToSpeech", "Failed to check for TTS package", e);
                        FirebaseCrash.report(e);
                        return false;
                    }
                } else {
                    Log.e("TextToSpeech", "TTS package not supported");
                    return false;
                }
            }
            @Override
            protected void onPostExecute(Boolean success) {
                if(!success) {
                    // Let the user know the audible won't be working
                    Toast.makeText(activity, R.string.error_audible_not_available, Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

}
