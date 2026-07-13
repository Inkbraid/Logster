package com.mendhak.gpslogger.loggers.tracker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.mendhak.gpslogger.common.SerializableLocation;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the TB1 wire format against the exact bytes the Tracker Go decoder
 * (server/internal/binfmt) expects. The expected arrays here are mirrored by
 * a Go test (TestJavaContract) so both sides are pinned to the same contract.
 */
public class TB1EncoderTest {

    @Test
    public void encodesFullPoint() {
        // ts=1s, lat=1.0 (1e7=0x00989680), lon=-2.0 (-2e7=0xFECED300)
        SerializableLocation p = Mockito.mock(SerializableLocation.class);
        when(p.getTime()).thenReturn(1000L);      // 1 second epoch
        when(p.getLatitude()).thenReturn(1.0);
        when(p.getLongitude()).thenReturn(-2.0);
        when(p.hasAltitude()).thenReturn(true);
        when(p.getAltitude()).thenReturn(42.0);
        when(p.hasSpeed()).thenReturn(true);
        when(p.getSpeed()).thenReturn(1.25f);      // 125 cm/s
        when(p.hasBearing()).thenReturn(true);
        when(p.getBearing()).thenReturn(270.5f);   // 2705 deg*10
        when(p.getSatelliteCount()).thenReturn(9);
        when(p.hasAccuracy()).thenReturn(true);
        when(p.getAccuracy()).thenReturn(4.5);     // 45 dm
        when(p.getBatteryLevel()).thenReturn(87);

        List<SerializableLocation> points = new ArrayList<>();
        points.add(p);

        byte[] got = TB1Encoder.encode(points);

        byte[] want = new byte[]{
                'T', 'B', 1, 1, 0,                       // header, count=1
                0x01, 0x00, 0x00, 0x00,                  // ts = 1
                (byte) 0x80, (byte) 0x96, (byte) 0x98, 0x00, // lat = 10000000
                0x00, (byte) 0xD3, (byte) 0xCE, (byte) 0xFE, // lon = -20000000
                0x3F,                                    // flags: ele|spd|dir|sat|acc|bat
                42, 0,                                   // ele = 42
                125, 0,                                  // spd = 125
                (byte) 0x91, 0x0A,                       // dir = 2705
                9,                                       // sat = 9
                45, 0,                                   // acc = 45
                87,                                      // bat = 87
        };
        assertArrayEquals(want, got);
    }

    @Test
    public void encodesBarePoint() {
        SerializableLocation p = Mockito.mock(SerializableLocation.class);
        when(p.getTime()).thenReturn(2000L);
        when(p.getLatitude()).thenReturn(0.0);
        when(p.getLongitude()).thenReturn(0.0);
        // all has* default to false, satellite/battery 0

        List<SerializableLocation> points = new ArrayList<>();
        points.add(p);

        byte[] got = TB1Encoder.encode(points);
        byte[] want = new byte[]{
                'T', 'B', 1, 1, 0,
                0x02, 0x00, 0x00, 0x00,  // ts = 2
                0x00, 0x00, 0x00, 0x00,  // lat = 0
                0x00, 0x00, 0x00, 0x00,  // lon = 0
                0x00,                    // flags: none
        };
        assertArrayEquals(want, got);
        assertEquals(5 + 13, got.length);
    }
}
