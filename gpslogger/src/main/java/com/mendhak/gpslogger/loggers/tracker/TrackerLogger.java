/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.loggers.tracker;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.mendhak.gpslogger.common.BatteryInfo;
import com.mendhak.gpslogger.common.BundleConstants;
import com.mendhak.gpslogger.common.SerializableLocation;
import com.mendhak.gpslogger.common.Systems;
import com.mendhak.gpslogger.loggers.FileLogger;

/**
 * Feeds each logged point into the {@link TrackerBatchQueue}, which uploads
 * them to the paired Tracker server in compact binary batches.
 */
public class TrackerLogger implements FileLogger {

    private final String name = "Tracker";
    private final int batteryLevel;

    public TrackerLogger(Context context) {
        BatteryInfo batteryInfo = Systems.getBatteryInfo(context);
        this.batteryLevel = batteryInfo.BatteryLevel;
    }

    @Override
    public void write(Location loc) throws Exception {
        // Carry the battery level through so it survives into the batch.
        if (loc.getExtras() == null) {
            loc.setExtras(new Bundle());
        }
        if (!loc.getExtras().containsKey(BundleConstants.BATTERY_LEVEL)) {
            loc.getExtras().putInt(BundleConstants.BATTERY_LEVEL, batteryLevel);
        }
        TrackerBatchQueue.getInstance().add(new SerializableLocation(loc));
    }

    @Override
    public void annotate(String description, Location loc) throws Exception {
        write(loc);
    }

    @Override
    public String getName() {
        return name;
    }
}
