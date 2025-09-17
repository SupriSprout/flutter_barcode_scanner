package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;


/**
 * FlutterBarcodeScannerPlugin
 */
public class FlutterBarcodeScannerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, EventChannel.StreamHandler {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";

    private MethodChannel channel;
    private EventChannel eventChannel;
    private static EventChannel.EventSink barcodeStream;

    private ActivityPluginBinding activityBinding;
    private Activity activity;
    private Application applicationContext;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;

    private static final int RC_BARCODE_CAPTURE = 9001;

    // Configurations for scanning
    public static String lineColor = "#DC143C";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;

    private Map<String, Object> arguments;

    private static final String TAG = "FlutterBarcodeScannerPlugin";

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        BinaryMessenger messenger = binding.getBinaryMessenger();
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        channel = null;
        eventChannel = null;
        barcodeStream = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        activity = binding.getActivity();
        applicationContext = (Application) activity.getApplicationContext();

        binding.addActivityResultListener(this::onActivityResult);

        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        observer = new LifeCycleObserver(activity);
        lifecycle.addObserver(observer);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this::onActivityResult);
            activityBinding = null;
        }
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
        }
        activity = null;
        applicationContext = null;
        lifecycle = null;
        observer = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            if (call.method.equals("scanBarcode")) {
                if (!(call.arguments instanceof Map)) {
                    result.error("INVALID_ARGUMENT", "Arguments must be a map", null);
                    return;
                }
                pendingResult = result;
                arguments = (Map<String, Object>) call.arguments;

                lineColor = (String) arguments.get("lineColor");
                isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
                isContinuousScan = (boolean) arguments.get("isContinuousScan");

                if (lineColor == null || lineColor.isEmpty()) {
                    lineColor = "#DC143C";
                }

                int scanMode = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                if (arguments.get("scanMode") != null) {
                    int mode = (int) arguments.get("scanMode");
                    if (mode == BarcodeCaptureActivity.SCAN_MODE_ENUM.DEFAULT.ordinal()) {
                        scanMode = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                    } else {
                        scanMode = mode;
                    }
                }
                BarcodeCaptureActivity.SCAN_MODE = scanMode;

                startBarcodeScannerActivityView((String) arguments.get("cancelButtonText"), isContinuousScan);
            } else {
                result.notImplemented();
            }
        } catch (Exception e) {
            Log.e(TAG, "onMethodCall: " + e.getLocalizedMessage());
            result.error("ERROR", e.getLocalizedMessage(), null);
        }
    }

    private Result pendingResult;

    private void startBarcodeScannerActivityView(String cancelButtonText, boolean isContinuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class);
            intent.putExtra("cancelButtonText", cancelButtonText);
            if (isContinuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startBarcodeScannerActivityView: " + e.getLocalizedMessage());
            if (pendingResult != null) {
                pendingResult.error("ACTIVITY_START_FAILED", e.getLocalizedMessage(), null);
                pendingResult = null;
            }
        }
    }

    private boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (pendingResult == null) return false;

            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    try {
                        Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                        if (barcode != null && barcode.rawValue != null) {
                            pendingResult.success(barcode.rawValue);
                        } else {
                            pendingResult.success("-1");
                        }
                    } catch (Exception e) {
                        pendingResult.success("-1");
                    }
                } else {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }
            pendingResult = null;
            arguments = null;
            return true;
        }
        return false;
    }

    // EventChannel.StreamHandler
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        barcodeStream = events;
    }

    @Override
    public void onCancel(Object arguments) {
        barcodeStream = null;
    }

    // Called by BarcodeCaptureActivity when a barcode is scanned continuously
    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode == null || barcode.rawValue == null || barcode.rawValue.isEmpty()) return;
        if (barcodeStream != null && activity != null) {
            activity.runOnUiThread(() -> barcodeStream.success(barcode.rawValue));
        }
    }

    // Lifecycle observer to clean up
    private class LifeCycleObserver implements DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

        private final Activity observedActivity;

        LifeCycleObserver(Activity activity) {
            this.observedActivity = activity;
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            if (activity == observedActivity && applicationContext != null) {
                applicationContext.unregisterActivityLifecycleCallbacks(this);
            }
        }

        @Override public void onCreate(@NonNull LifecycleOwner owner) { }
        @Override public void onStart(@NonNull LifecycleOwner owner) { }
        @Override public void onResume(@NonNull LifecycleOwner owner) { }
        @Override public void onPause(@NonNull LifecycleOwner owner) { }
        @Override public void onStop(@NonNull LifecycleOwner owner) { }
        @Override public void onDestroy(@NonNull LifecycleOwner owner) { }

        @Override public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) { }
        @Override public void onActivityStarted(@NonNull Activity activity) { }
        @Override public void onActivityResumed(@NonNull Activity activity) { }
        @Override public void onActivityPaused(@NonNull Activity activity) { }
        @Override public void onActivityStopped(@NonNull Activity activity) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    }
}