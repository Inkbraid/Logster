/*
 * Part of Logster (a fork of GPSLogger for Android), GPL v2.
 */

package com.mendhak.gpslogger.loggers.tracker;

import com.mendhak.gpslogger.common.SerializableLocation;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Encodes track points into the compact "TB1" binary batch format consumed by
 * the Tracker server (see server/internal/binfmt in the Tracker repo). A point
 * is 13-23 bytes here versus ~200 bytes URL-encoded, to minimise mobile data.
 *
 * Layout (little-endian):
 *   header: 'T' 'B'  version u8 (=1)  count u16
 *   point:  ts u32 (epoch seconds UTC)
 *           lat i32 (degrees * 1e7)
 *           lon i32 (degrees * 1e7)
 *           flags u8: bit0 ele, bit1 spd, bit2 dir, bit3 sat, bit4 acc, bit5 bat
 *           [ele i16 m] [spd u16 cm/s] [dir u16 deg*10] [sat u8] [acc u16 dm] [bat u8]
 */
public final class TB1Encoder {

    public static final int VERSION = 1;

    private static final int FLAG_ELE = 1;
    private static final int FLAG_SPD = 1 << 1;
    private static final int FLAG_DIR = 1 << 2;
    private static final int FLAG_SAT = 1 << 3;
    private static final int FLAG_ACC = 1 << 4;
    private static final int FLAG_BAT = 1 << 5;

    private TB1Encoder() {}

    public static byte[] encode(List<SerializableLocation> points) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5 + points.size() * 23);
        out.write('T');
        out.write('B');
        out.write(VERSION);
        writeU16(out, points.size());

        for (SerializableLocation p : points) {
            writeU32(out, p.getTime() / 1000L);
            writeI32(out, (int) Math.round(p.getLatitude() * 1e7));
            writeI32(out, (int) Math.round(p.getLongitude() * 1e7));

            // A satellite count is present only when > 0; battery level is
            // 0..100 and always meaningful once captured.
            boolean hasSat = p.getSatelliteCount() > 0;
            boolean hasBat = p.getBatteryLevel() > 0;

            int flags = 0;
            if (p.hasAltitude()) flags |= FLAG_ELE;
            if (p.hasSpeed())    flags |= FLAG_SPD;
            if (p.hasBearing())  flags |= FLAG_DIR;
            if (hasSat)          flags |= FLAG_SAT;
            if (p.hasAccuracy()) flags |= FLAG_ACC;
            if (hasBat)          flags |= FLAG_BAT;
            out.write(flags);

            if (p.hasAltitude()) writeI16(out, clampI16(Math.round(p.getAltitude())));
            if (p.hasSpeed())    writeU16(out, clampU16(Math.round(p.getSpeed() * 100)));   // cm/s
            if (p.hasBearing())  writeU16(out, clampU16(Math.round(p.getBearing() * 10)));  // deg*10
            if (hasSat)          out.write(p.getSatelliteCount() & 0xFF);
            if (p.hasAccuracy()) writeU16(out, clampU16(Math.round(p.getAccuracy() * 10))); // decimetres
            if (hasBat)          out.write(p.getBatteryLevel() & 0xFF);
        }
        return out.toByteArray();
    }

    private static int clampU16(long v) {
        if (v < 0) return 0;
        if (v > 0xFFFF) return 0xFFFF;
        return (int) v;
    }

    // Altitude in metres; clamp to the signed 16-bit range so an aircraft
    // altitude or a GPS glitch can't wrap around to a bogus value.
    private static int clampI16(long v) {
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        return (int) v;
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeI16(ByteArrayOutputStream out, int v) {
        writeU16(out, v & 0xFFFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 24) & 0xFF));
    }

    private static void writeI32(ByteArrayOutputStream out, int v) {
        writeU32(out, ((long) v) & 0xFFFFFFFFL);
    }
}
