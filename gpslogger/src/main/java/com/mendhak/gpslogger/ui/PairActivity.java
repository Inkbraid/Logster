/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.mendhak.gpslogger.R;
import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.Strings;
import com.mendhak.gpslogger.common.slf4j.Logs;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.slf4j.Logger;

/**
 * Scans the QR code shown in the Tracker web UI to pair this device. The QR
 * encodes the device ingest URL (https://host/api/ingest/{token}); on a valid
 * scan we store it, enable Tracker upload and GPX logging, and finish.
 */
public class PairActivity extends AppCompatActivity {

    private static final Logger LOG = Logs.of(PairActivity.class);

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    finish(); // user cancelled
                    return;
                }
                handleScanned(result.getContents().trim());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.tracker_pair_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void handleScanned(String contents) {
        if (!isValidIngestUrl(contents)) {
            Toast.makeText(this, R.string.tracker_pair_invalid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        PreferenceHelper prefs = PreferenceHelper.getInstance();
        prefs.setTrackerIngestUrl(contents);
        prefs.setTrackerEnabled(true);
        // Ensure a local durable record exists for deferred upload.
        prefs.setShouldLogToGpx(true);

        LOG.info("Paired with Tracker server");
        Toast.makeText(this, R.string.tracker_pair_success, Toast.LENGTH_LONG).show();
        finish();
    }

    /** Accepts an http(s) URL whose path contains /ingest/<token>. */
    private boolean isValidIngestUrl(String contents) {
        if (Strings.isNullOrEmpty(contents)) {
            return false;
        }
        try {
            Uri uri = Uri.parse(contents);
            String scheme = uri.getScheme();
            String path = uri.getPath();
            return scheme != null && (scheme.equals("http") || scheme.equals("https"))
                    && uri.getHost() != null
                    && path != null && path.contains("/ingest/");
        } catch (Exception e) {
            return false;
        }
    }
}
