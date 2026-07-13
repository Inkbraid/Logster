package com.mendhak.gpslogger.senders.customurl;

import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.SerializableLocation;
import com.mendhak.gpslogger.common.Strings;
import com.mendhak.gpslogger.common.Systems;
import com.mendhak.gpslogger.common.slf4j.Logs;
import com.mendhak.gpslogger.loggers.customurl.CustomUrlRequest;
import com.mendhak.gpslogger.loggers.customurl.CustomUrlWorker;
import com.mendhak.gpslogger.senders.FileSender;
import org.slf4j.Logger;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Logster: the CSV-replay auto-send path was removed along with the CSV
// logger; a Tracker binary-batch uploader (reading the GPX files) replaces
// it. Live per-point posting via CustomUrlLogger is unaffected.
public class CustomUrlManager extends FileSender {

    private final PreferenceHelper preferenceHelper;
    private static final Logger LOG = Logs.of(CustomUrlManager.class);

    public CustomUrlManager(PreferenceHelper preferenceHelper){
        this.preferenceHelper = preferenceHelper;
    }

    @Override
    public void uploadFile(List<File> files) {
        LOG.warn("Custom URL file auto-send is not available in Logster.");
    }

    public void sendByHttp(String url, String method, String body, String headers, String username, String password){

        CustomUrlRequest request = new CustomUrlRequest(url, method, body, headers, username, password);
        String serializedRequest = Strings.serializeTojson(request);
        String tag = String.valueOf(Objects.hashCode(serializedRequest));

        HashMap<String, Object> dataMap = new HashMap<String, Object>() {{
            put("urlRequests", new String[]{serializedRequest});
            put("callbackType", "customurl");
        }};

        Systems.startWorkManagerRequest(CustomUrlWorker.class, dataMap, tag);

    }

    public String getFormattedTextblock(String customLoggingUrl,
                                        SerializableLocation sLoc,
                                        String description,
                                        String androidId,
                                        float batteryLevel,
                                        boolean isCharging,
                                        String buildSerial,
                                        long sessionStartTimeStamp,
                                        String fileName,
                                        String profileName,
                                        double distance)
            throws Exception {

        String logUrl = customLoggingUrl;

        Map<String, String> replacements = new LinkedHashMap<String, String>();
        replacements.put("lat", String.valueOf(sLoc.getLatitude()));
        replacements.put("lon", String.valueOf(sLoc.getLongitude()));
        replacements.put("sat", String.valueOf(sLoc.getSatelliteCount()));
        replacements.put("desc", String.valueOf(Strings.getUrlEncodedString(Strings.htmlDecode(description))));
        replacements.put("alt", String.valueOf(sLoc.getAltitude()));
        replacements.put("acc", String.valueOf(sLoc.getAccuracy()));
        replacements.put("dir", String.valueOf(sLoc.getBearing()));
        replacements.put("prov", String.valueOf(sLoc.getProvider()));
        replacements.put("spd_kph", String.valueOf(sLoc.getSpeed()*3.6));
        replacements.put("spd", String.valueOf(sLoc.getSpeed()));
        replacements.put("timestamp", String.valueOf(sLoc.getTime()/1000));

        if(!Strings.isNullOrEmpty(sLoc.getTimeWithOffset())){
            replacements.put("timeoffset", Strings.getUrlEncodedString(sLoc.getTimeWithOffset()));
        }
        else {
            replacements.put("timeoffset", Strings.getUrlEncodedString(Strings.getIsoDateTimeWithOffset(new Date(sLoc.getTime()))));
        }

        replacements.put("time", String.valueOf(Strings.getUrlEncodedString(Strings.getIsoDateTime(new Date(sLoc.getTime())))));
        replacements.put("starttimestamp", String.valueOf(sessionStartTimeStamp/1000));
        replacements.put("date", String.valueOf(Strings.getIsoCalendarDate(new Date(sLoc.getTime()))));
        replacements.put("batt", String.valueOf(batteryLevel));
        replacements.put("ischarging", String.valueOf(isCharging));
        replacements.put("aid", String.valueOf(androidId));
        replacements.put("ser", String.valueOf(buildSerial));
        replacements.put("act", ""); //Activity detection was removed, but keeping this here for backward compatibility.
        replacements.put("filename", fileName);
        replacements.put("profile", Strings.getUrlEncodedString(profileName));
        replacements.put("hdop", sLoc.getHDOP());
        replacements.put("vdop", sLoc.getVDOP());
        replacements.put("pdop", sLoc.getPDOP());
        replacements.put("dist", String.valueOf((int)distance));

        if(customLoggingUrl.toUpperCase().contains("%ALL")){
            StringBuilder sbAll = new StringBuilder();
            for (String q : replacements.keySet()){
                sbAll.append(q + "=%" + q + "&");
            }
            logUrl = logUrl.replaceAll("(?i)%ALL", sbAll.toString());
        }

        for (String m : replacements.keySet()) {
            logUrl = logUrl.replaceAll("(?i)%"+m, replacements.get(m));
        }

        return logUrl;
    }

    @Override
    public boolean isAvailable() {
        return !Strings.isNullOrEmpty(preferenceHelper.getCustomLoggingUrl()) &&
                !Strings.isNullOrEmpty(preferenceHelper.getCustomLoggingHTTPMethod());
    }

    @Override
    public boolean hasUserAllowedAutoSending() {
        // File auto-send via custom URL was CSV-based; removed in Logster.
        return false;
    }

    @Override
    public String getName() {
        return SenderNames.CUSTOMURL;
    }

    @Override
    public boolean accept(File dir, String name) {
        return false;
    }
}
