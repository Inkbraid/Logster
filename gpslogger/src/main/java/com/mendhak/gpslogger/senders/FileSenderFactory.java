/*
 * Copyright (C) 2016 mendhak
 *
 * This file is part of GPSLogger for Android.
 *
 * GPSLogger for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * GPSLogger for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GPSLogger for Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mendhak.gpslogger.senders;

import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.slf4j.Logs;
import com.mendhak.gpslogger.loggers.Files;
import com.mendhak.gpslogger.senders.customurl.CustomUrlManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Logster: stripped to the custom-URL sender only; the Tracker batch
// uploader will slot in here later.
public class FileSenderFactory {

    private static final Logger LOG = Logs.of(FileSenderFactory.class);

    public static FileSender getCustomUrlSender(){
        return new CustomUrlManager(PreferenceHelper.getInstance());
    }

    public static void autoSendFiles(final String fileToSend) {

        PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
        LOG.info("Auto-sending file " + fileToSend);

        File gpxFolder = new File(preferenceHelper.getGpsLoggerFolder());

        if (Files.fromFolder(gpxFolder).length < 1) {
            LOG.warn("No files found to send.");
            return;
        }

        List<File> files = new ArrayList<>(Arrays.asList(Files.fromFolder(gpxFolder, new FilenameFilter() {
            @Override
            public boolean accept(File folderPath, String fileName) {
                return Files.getBaseName(fileName).equalsIgnoreCase(fileToSend) &&
                        !fileName.endsWith("zip");
            }
        })));

        if (files.size() == 0) {
            LOG.warn("No files found to send after filtering.");
            return;
        }

        for (FileSender sender : getAvailableFileAutoSenders()) {
            LOG.debug("Sender: " + sender.getClass().getName());
            sender.uploadFile(files);
        }
    }

    public static FileSender getSenderByName(String senderName) {
        switch (senderName){
            case FileSender.SenderNames.CUSTOMURL:
                return getCustomUrlSender();
            default:
                return null;
        }
    }

    private static List<FileSender> getAvailableFileAutoSenders() {
        List<FileSender> senders = new ArrayList<>();
        if(getCustomUrlSender().isAutoSendAvailable()){
            senders.add(getCustomUrlSender());
        }
        return senders;
    }
}
