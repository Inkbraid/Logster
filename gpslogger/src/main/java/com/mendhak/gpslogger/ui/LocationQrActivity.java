/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.mendhak.gpslogger.GpsLoggingService;
import com.mendhak.gpslogger.R;
import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.Session;
import com.mendhak.gpslogger.common.Strings;
import com.mendhak.gpslogger.common.events.CommandEvents;
import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

import java.util.Locale;

import de.greenrobot.event.EventBus;

/**
 * The simple tracking view and the notification's tap target: current
 * coordinates on top, a QR of the current fix in the middle, telemetry
 * (elevation / speed / satellites / heading) below, and a stop button.
 *
 * QR payload (pipe-delimited, easy to recover from a photographed code):
 *   LOGSTER1|<deviceId>|<epochSeconds>|<lat>|<lon>|<accuracyMeters>
 *
 * deviceId is the paired Tracker device token, so a photo of the QR can later
 * be matched to the device and its track (and used to correct camera clock
 * drift against the encoded GPS time).
 */
public class LocationQrActivity extends AppCompatActivity {

    private static final Logger LOG = Logs.of(LocationQrActivity.class);
    private static final long REFRESH_MS = 1000;
    private static final String[] COMPASS = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BarcodeEncoder encoder = new BarcodeEncoder();
    private ImageView qrView;
    private TextView coordsView, timeView, elevationView, speedView, satellitesView, directionView;
    private Button stopStartButton;
    private String lastContent = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_location_qr);
        qrView = findViewById(R.id.simple_qr);
        coordsView = findViewById(R.id.simple_coords);
        timeView = findViewById(R.id.simple_time);
        elevationView = findViewById(R.id.simple_elevation);
        speedView = findViewById(R.id.simple_speed);
        satellitesView = findViewById(R.id.simple_satellites);
        directionView = findViewById(R.id.simple_direction);

        stopStartButton = findViewById(R.id.simple_stop);
        stopStartButton.setOnClickListener(v -> toggleLogging());
        updateButton();
    }

    /** Start or stop logging without leaving this screen. */
    private void toggleLogging() {
        if (Session.getInstance().isStarted()) {
            EventBus.getDefault().post(new CommandEvents.RequestStartStop(false));
        } else {
            // The service stops itself when logging ends, so bring it back up
            // before asking it to start again.
            ContextCompat.startForegroundService(this, new Intent(this, GpsLoggingService.class));
            EventBus.getDefault().post(new CommandEvents.RequestStartStop(true));
        }
        // Reflect the request immediately; refresh() keeps it in sync after.
        stopStartButton.postDelayed(this::updateButton, 300);
    }

    private void updateButton() {
        stopStartButton.setText(Session.getInstance().isStarted()
                ? R.string.btn_stop_logging : R.string.btn_start_logging);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void refresh() {
        updateButton();
        Location loc = Session.getInstance().getCurrentLocationInfo();
        if (loc == null) {
            coordsView.setText(R.string.locationqr_waiting);
            return;
        }

        coordsView.setText(String.format(Locale.US, "%.6f, %.6f",
                loc.getLatitude(), loc.getLongitude()));
        timeView.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
                .format(new java.util.Date(loc.getTime())));

        elevationView.setText(loc.hasAltitude()
                ? String.format(Locale.US, "%.0f m", loc.getAltitude()) : "—");
        speedView.setText(loc.hasSpeed()
                ? String.format(Locale.US, "%.1f m/s", loc.getSpeed()) : "—");
        int sats = Session.getInstance().getVisibleSatelliteCount();
        satellitesView.setText(sats > 0 ? String.valueOf(sats) : "—");
        directionView.setText(loc.hasBearing() ? compass(loc.getBearing()) : "—");

        long epochSeconds = loc.getTime() / 1000L;
        String deviceId = PreferenceHelper.getInstance().getTrackerDeviceId();
        if (Strings.isNullOrEmpty(deviceId)) {
            deviceId = "-"; // not paired; still encode time+position
        }
        String content = String.format(Locale.US, "LOGSTER1|%s|%d|%.6f|%.6f|%.0f",
                deviceId, epochSeconds, loc.getLatitude(), loc.getLongitude(),
                loc.hasAccuracy() ? loc.getAccuracy() : 0f);

        if (!content.equals(lastContent)) {
            try {
                Bitmap bmp = encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600);
                qrView.setImageBitmap(bmp);
                lastContent = content;
            } catch (Exception e) {
                LOG.error("Could not render location QR", e);
            }
        }
    }

    private static String compass(float bearingDegrees) {
        float b = bearingDegrees % 360f;
        if (b < 0) b += 360f;
        int idx = Math.round(b / 45f) % 8;
        return String.format(Locale.US, "%s %.0f°", COMPASS[idx], b);
    }
}
