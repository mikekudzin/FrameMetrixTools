package com.mikekudzin.framemetrixdumper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by KudzinM on 21.07.2016.
 */
public class FrameMetricsDumper {
    public static final String DUMPS_SUBDIR = "framemetrics";
    private HashMap<Activity, ScreenFrameMetrixWriter> metricsListeners = new HashMap<>();
    Context context;

    private static final String METRICS_EXT = ".mtx";
    private static final String FILE_NAME_PATTERN = "%1$s_%2$s_%3$s" + METRICS_EXT; //app label, screen name, variant, extension

    private final String variant;
    private String metricsDirPath;
    private String appLabel;
    private LooperThread looperThread;

    private static volatile FrameMetricsDumper INSTANCE;

    private FrameMetricsDumper(String variantName) {
        variant = variantName;
    }

    private FrameMetricsDumper(Context context, String variant) {
        this(variant);
        //get path for performance stats dumps
        File filesDir = Environment.getExternalStorageDirectory();
        File frameMetricsDir = new File(filesDir.toString(), DUMPS_SUBDIR);
        if (!frameMetricsDir.exists()) {
            frameMetricsDir.mkdirs();
        }

        metricsDirPath = frameMetricsDir.getPath();

        looperThread = new LooperThread();
        looperThread.start();

        appLabel = getAppLabel(context);
    }

    public String getAppLabel(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(context.getApplicationInfo().packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
        }
        return (String) (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo) : "Unknown");
    }

    public static synchronized FrameMetricsDumper getInstance(@NonNull Context context, @NonNull String variant) {
        if (INSTANCE == null) {
            INSTANCE = new FrameMetricsDumper(context.getApplicationContext(), variant);
        }
        return INSTANCE;
    }

    public static synchronized FrameMetricsDumper getInstance(){
        if (INSTANCE == null) {
            throw new RuntimeException("FrameMetricsDumper should be instantiated first with context and variant");
        }
        return INSTANCE;
    }

    public void trackMetricsFor(@NonNull Activity activity) {
        //not supported below N
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        Window.OnFrameMetricsAvailableListener metricsListener = metricsListeners.get(activity);
        if (metricsListener == null) {
            // TODO: 22.07.2016 handle file properly
            //check if we have a file
            String fileName = String.format(Locale.US, FILE_NAME_PATTERN, appLabel, activity.getClass().getSimpleName(), variant);
            File file = new File(metricsDirPath, fileName);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            metricsListener = new ScreenFrameMetrixWriter(file);
        }

        activity.getWindow().addOnFrameMetricsAvailableListener(metricsListener, looperThread.getHandler());
    }

    public void endMetricsListeningFor(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        ScreenFrameMetrixWriter metricsListener = metricsListeners.get(activity);
        if (metricsListener == null) {
            return;
        }

        activity.getWindow().removeOnFrameMetricsAvailableListener(metricsListener);
        metricsListener.finish();
        metricsListeners.remove(activity);
    }

    //This thread will be used to write events to files
    private static class LooperThread extends Thread {
        private volatile Handler handler;

        @Override
        public void run() {
            Looper.prepare();

            handler = new Handler();

            Looper.loop();
        }

        public void stopThread() {
            if (handler != null) {
                handler.getLooper().quit();
            }
        }

        public
        @Nullable
        Handler getHandler() {
            return handler;
        }
    }


    @TargetApi(Build.VERSION_CODES.N)
    private class ScreenFrameMetrixWriter implements Window.OnFrameMetricsAvailableListener {
        FileWriter fileWriter;
        //contract for data format
        private static final String METRIC_DATA_FORMAT = "FM:\n%1$d %2$d %3$d %4$d %5$d %6$d %7$d %8$d %8$d %10$d";

        public ScreenFrameMetrixWriter(File metricsDumpFile) {
            try {
                fileWriter = new FileWriter(metricsDumpFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics, int i) {
//            StringBuilder stringBuilder = new StringBuilder();
//            stringBuilder.append(" ANIM_DUR: ").append(frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION))
//                    .append(" COM_ISSUE_DUR: ").append(frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION))
//                    .append(" DRAW_DUR: ").append(frameMetrics.getMetric(FrameMetrics.DRAW_DURATION));
////                    .append("\n");
//            stringBuilder.append(" FI_DRA_FR ").append(frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
//                    .append(" IN_HAN_DUR: ").append(frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))
//                    .append(" LAY_MEAS_DUR: ").append(frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION));
////                    .append("\n");
//            stringBuilder.append(" SWA_BUFF_DUR: ").append(frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
//                    .append(" SYNC_SUR: ").append(frameMetrics.getMetric(FrameMetrics.SYNC_DURATION))
//                    .append(" TOT_SUR: ").append(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION))
//                    .append(" UNKN_DEL_DURA: ").append(frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION));
////                    .append("\n");
//            Log.d("FrameMetrics", stringBuilder.toString());
//
//            writeOutEvent(stringBuilder.toString());
            writeOutEvent(String.format(Locale.US, METRIC_DATA_FORMAT,
                    frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION),
                    frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                    frameMetrics.getMetric(FrameMetrics.DRAW_DURATION),
                    frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME),
                    frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
                    frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
                    frameMetrics.getMetric(FrameMetrics.SYNC_DURATION),
                    frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
                    frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)));
        }

        public void writeOutEvent(String eventString) {
            if (fileWriter == null) {
                return;
            }

            try {
                fileWriter.append(eventString).append("\n").flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void finish() {
            if (fileWriter == null) {
                return;
            }
            try {
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
