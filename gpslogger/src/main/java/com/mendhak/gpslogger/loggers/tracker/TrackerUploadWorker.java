/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.loggers.tracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.Strings;
import com.mendhak.gpslogger.common.network.Networks;
import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads every queued .tb1 batch file to the paired Tracker server. Files
 * are deleted only on a 2xx (or 401, which is unrecoverable and would
 * otherwise wedge the queue). Any other outcome returns retry() so
 * WorkManager re-runs with backoff once the network is available.
 */
public class TrackerUploadWorker extends Worker {

    private static final Logger LOG = Logs.of(TrackerUploadWorker.class);
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    public TrackerUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String batchUrl = PreferenceHelper.getInstance().getTrackerBatchUrl();
        if (Strings.isNullOrEmpty(batchUrl)) {
            LOG.warn("Tracker not paired; discarding queued batches");
            return Result.success();
        }

        File[] batches = TrackerBatchQueue.queueDir().listFiles((d, name) -> name.endsWith(".tb1"));
        if (batches == null || batches.length == 0) {
            return Result.success();
        }
        // Oldest first, so points arrive roughly in order.
        Arrays.sort(batches);

        OkHttpClient client;
        try {
            client = new OkHttpClient.Builder()
                    .sslSocketFactory(Networks.getSocketFactory(AppSettings.getInstance()),
                            (X509TrustManager) Networks.getTrustManager(AppSettings.getInstance()))
                    .build();
        } catch (Exception e) {
            LOG.error("Could not build HTTP client for Tracker upload", e);
            return Result.retry();
        }

        boolean anyRetryable = false;
        for (File batch : batches) {
            try {
                byte[] body = Files.readAllBytes(batch.toPath());
                Request request = new Request.Builder()
                        .url(batchUrl)
                        .post(RequestBody.create(OCTET_STREAM, body))
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() || response.code() == 401) {
                        if (response.code() == 401) {
                            LOG.error("Tracker rejected batch (401 - re-pair the device); discarding " + batch.getName());
                        } else {
                            LOG.debug("Uploaded " + batch.getName());
                        }
                        batch.delete();
                    } else {
                        LOG.warn("Tracker batch upload got HTTP " + response.code() + ", will retry");
                        anyRetryable = true;
                    }
                }
            } catch (Exception e) {
                LOG.warn("Tracker batch upload failed, will retry: " + e.getMessage());
                anyRetryable = true;
            }
        }
        return anyRetryable ? Result.retry() : Result.success();
    }
}
