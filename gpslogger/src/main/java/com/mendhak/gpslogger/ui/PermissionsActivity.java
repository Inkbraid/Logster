/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Plain-language explanation of why Logster asks for each permission, with a
 * shortcut to the system app-settings page to grant them. Kept deliberately
 * simple — Logster's whole settings surface is minimal.
 */
public class PermissionsActivity extends AppCompatActivity {

    private static final String[][] ITEMS = {
            {"Location (precise)",
                    "The core permission. Logster records where you are; without it there is nothing to log."},
            {"Location — allow all the time (background)",
                    "So tracking continues when the screen is off or another app is in front. Without background access, Android suspends logging the moment you leave the app."},
            {"Notifications",
                    "Logster runs as a foreground service and shows an ongoing notification while logging — Android requires notification permission to display it."},
            {"Battery — unrestricted / ignore optimizations",
                    "Aggressive battery savers pause background apps and silently stop logging. Marking Logster unrestricted keeps it running on long trips."},
            {"Camera",
                    "Only used momentarily when you scan the pairing QR code from the Tracker web app. Logster never records video or photos."},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(com.mendhak.gpslogger.R.string.permissions_title));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        for (String[] item : ITEMS) {
            TextView title = new TextView(this);
            title.setText(item[0]);
            title.setTextSize(16);
            title.setPadding(0, pad, 0, 0);
            root.addView(title);

            TextView body = new TextView(this);
            body.setText(item[1]);
            body.setTextSize(14);
            root.addView(body);
        }

        Button open = new Button(this);
        open.setText("Open app permission settings");
        open.setPadding(0, pad, 0, 0);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            }
        });
        root.addView(open);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
