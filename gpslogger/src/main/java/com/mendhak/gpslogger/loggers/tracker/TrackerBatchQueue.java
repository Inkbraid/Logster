/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.loggers.tracker;

import com.google.gson.Gson;
import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.SerializableLocation;
import com.mendhak.gpslogger.common.Systems;
import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Buffers logged points and flushes them to the Tracker server in compact
 * binary batches. GPX files remain the durable on-device record; this queue
 * is purely the network-upload path.
 *
 * Flushing writes each batch to an immutable .tb1 file in a queue directory
 * and enqueues {@link TrackerUploadWorker}. That makes uploads crash-safe and
 * naturally deferred: when offline the files simply accumulate until a send
 * succeeds. A singleton because {@code FileLoggerFactory} builds a fresh
 * logger per point, so buffer state must live outside the logger instance.
 */
public class TrackerBatchQueue {

    private static final Logger LOG = Logs.of(TrackerBatchQueue.class);
    private static final String QUEUE_DIR = "tracker_queue";
    private static final String BUFFER_FILE = "tracker_buffer.json";
    private static final Gson GSON = new Gson();

    private static TrackerBatchQueue instance;

    private final List<SerializableLocation> buffer = new ArrayList<>();
    private boolean loaded = false;

    public static synchronized TrackerBatchQueue getInstance() {
        if (instance == null) {
            instance = new TrackerBatchQueue();
        }
        return instance;
    }

    public static File queueDir() {
        File dir = new File(AppSettings.getInstance().getFilesDir(), QUEUE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File bufferFile() {
        return new File(AppSettings.getInstance().getFilesDir(), BUFFER_FILE);
    }

    /** Adds a point and flushes a batch once the configured size is reached. */
    public synchronized void add(SerializableLocation location) {
        loadIfNeeded();
        buffer.add(location);
        persistBuffer();

        int batchSize = PreferenceHelper.getInstance().getTrackerBatchSize();
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    /** Encodes the current buffer to a queued .tb1 file and triggers upload. */
    public synchronized void flush() {
        loadIfNeeded();
        if (buffer.isEmpty()) {
            return;
        }
        try {
            byte[] encoded = TB1Encoder.encode(buffer);
            // Millisecond-stamped name keeps batches ordered and unique. new
            // Date() is fine here (this runs on-device, not in a replayed job).
            File out = new File(queueDir(), System.currentTimeMillis() + ".tb1");
            Files.write(out.toPath(), encoded);
            LOG.debug("Queued Tracker batch " + out.getName() + " (" + buffer.size() + " points, " + encoded.length + " bytes)");
            buffer.clear();
            persistBuffer();
        } catch (Exception e) {
            LOG.error("Failed to queue Tracker batch", e);
            return;
        }
        enqueueUpload();
    }

    public static void enqueueUpload() {
        Systems.startWorkManagerRequest(TrackerUploadWorker.class, new HashMap<>(), "tracker-upload");
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = bufferFile();
        if (!f.exists()) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(f.toPath()));
            SerializableLocation[] saved = GSON.fromJson(json, SerializableLocation[].class);
            if (saved != null) {
                for (SerializableLocation s : saved) {
                    buffer.add(s);
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not restore Tracker buffer, starting fresh", e);
        }
    }

    private void persistBuffer() {
        try (FileWriter w = new FileWriter(bufferFile())) {
            GSON.toJson(buffer, w);
        } catch (Exception e) {
            LOG.warn("Could not persist Tracker buffer", e);
        }
    }
}
