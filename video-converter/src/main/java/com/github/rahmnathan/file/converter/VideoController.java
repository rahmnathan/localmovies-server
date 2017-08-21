package com.github.rahmnathan.file.converter;

import com.github.rahmnathan.directorymonitor.DirectoryMonitorObserver;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@Component
public class VideoController implements DirectoryMonitorObserver {
    @Value("${ffmpeg.location}")
    private String ffmpegLocation;
    @Value("${ffprobe.location}")
    private String ffprobeLocation;
    private final Logger logger = Logger.getLogger(VideoController.class.getName());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private volatile Set<String> convertedFiles = new HashSet<>();

    @Override
    public void directoryModified(WatchEvent event, Path absolutePath) {
        if(convertedFiles.contains(absolutePath.toString())){
            convertedFiles.remove(absolutePath.toString());
            return;
        }

        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            convertToCastableFormat(absolutePath.toFile());
        }
    }

    private void convertToCastableFormat(File videoFile) {
        if (!isCorrectFormat(videoFile)) {
            executor.execute(new VideoConverter(videoFile, convertedFiles, ffmpegLocation, ffprobeLocation));
        }
    }

    private boolean isCorrectFormat(File videoFile) {
        if(ffprobeLocation == null)
            return true;

        boolean isH264 = false;
        boolean isAAC = false;

        try {
            FFprobe probe = new FFprobe(ffprobeLocation);
            FFmpegProbeResult probeResult = probe.probe(videoFile.getAbsolutePath());
            for (FFmpegStream stream : probeResult.getStreams()) {
                String codecName = stream.codec_name;
                logger.info(videoFile.getAbsolutePath() + " " + codecName);
                if (codecName.equalsIgnoreCase("aac"))
                    isAAC = true;
                else if (codecName.equalsIgnoreCase("h264"))
                    isH264 = true;
            }
        } catch (IOException e){
            logger.severe(e.toString());
        }

        return isH264 && isAAC;
    }
}