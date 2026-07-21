/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.ui;

import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.mendhak.gpslogger.R;
import com.mendhak.gpslogger.common.Session;
import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

import java.util.Locale;

/**
 * Full-screen QR of the current position and time. Photograph it with any
 * camera and the photo's own EXIF timestamp can later be compared against the
 * true time encoded here to recover the camera's clock drift.
 *
 * Payload (pipe-delimited, easy to parse from an OCR'd/QR-decoded photo):
 *   LOGSTER1|<epochSeconds>|<lat>|<lon>|<accuracyMeters>
 */
public class LocationQrActivity extends AppCompatActivity {

    private static final Logger LOG = Logs.of(LocationQrActivity.class);
    private static final long REFRESH_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BarcodeEncoder encoder = new BarcodeEncoder();
    private ImageView imageView;
    private TextView detailsView;
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
        // Keep the screen on and bright so the QR is easy to photograph.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_location_qr);
        imageView = findViewById(R.id.locationqr_image);
        detailsView = findViewById(R.id.locationqr_details);
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
        Location loc = Session.getInstance().getCurrentLocationInfo();
        if (loc == null) {
            detailsView.setText(R.string.locationqr_waiting);
            return;
        }
        long epochSeconds = loc.getTime() / 1000L;
        String content = String.format(Locale.US, "LOGSTER1|%d|%.6f|%.6f|%.0f",
                epochSeconds, loc.getLatitude(), loc.getLongitude(),
                loc.hasAccuracy() ? loc.getAccuracy() : 0f);

        // Only re-encode when the content actually changed.
        if (!content.equals(lastContent)) {
            try {
                Bitmap bmp = encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600);
                imageView.setImageBitmap(bmp);
                lastContent = content;
            } catch (Exception e) {
                LOG.error("Could not render location QR", e);
            }
        }
        detailsView.setText(String.format(Locale.US, "%.6f, %.6f\n%s",
                loc.getLatitude(), loc.getLongitude(),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
                        .format(new java.util.Date(loc.getTime()))));
    }
}
