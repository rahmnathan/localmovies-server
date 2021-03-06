package com.github.rahmnathan.localmovies.event.control;

import com.github.rahmnathan.directory.monitor.DirectoryMonitorObserver;
import com.github.rahmnathan.localmovie.domain.MediaFile;
import com.github.rahmnathan.localmovie.domain.MediaFileEvent;
import com.github.rahmnathan.localmovie.domain.MovieEvent;
import com.github.rahmnathan.localmovies.event.repository.MediaEventRepository;
import com.github.rahmnathan.localmovie.service.boundary.MediaMetadataService;
import com.github.rahmnathan.video.cast.handbrake.control.VideoController;
import com.github.rahmnathan.video.cast.handbrake.data.SimpleConversionJob;
import io.micrometer.core.instrument.Metrics;
import net.bramp.ffmpeg.FFprobe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class MediaFileEventManager implements DirectoryMonitorObserver {
    private final AtomicInteger activeConversionGauge = Metrics.gauge("localmovies.conversions.active", new AtomicInteger(0));
    private final Logger logger = LoggerFactory.getLogger(MediaFileEventManager.class);
    private volatile Set<String> activeConversions = ConcurrentHashMap.newKeySet();
    private final List<MediaFileEvent> mediaFileEvents = new ArrayList<>();
    private final PushNotificationHandler notificationHandler;
    private final MediaMetadataService metadataService;
    private final MediaEventRepository eventRepository;
    private final ExecutorService executorService;
    private FFprobe ffprobe;

    public MediaFileEventManager(@Value("${ffprobe.location:/usr/bin/ffprobe}") String ffprobeLocation, @Value("${concurrent.conversion.limit:1}") Integer concurrentConversions,
                                 MediaMetadataService mediaMetadataService, MediaEventRepository eventRepository,
                                 PushNotificationHandler notificationHandler) {
        logger.info("Number of concurrent video conversions allowed: {}", concurrentConversions);
        this.executorService = Executors.newFixedThreadPool(concurrentConversions);
        this.notificationHandler = notificationHandler;
        this.metadataService = mediaMetadataService;
        this.eventRepository = eventRepository;

        eventRepository.findAll().forEach(mediaFileEvents::add);

        try {
            this.ffprobe = new FFprobe(ffprobeLocation);
        } catch (IOException e){
            logger.error("Failed to instantiate MediaFileEventManager", e);
        }
    }

    @Override
    public void directoryModified(WatchEvent event, Path absolutePath) {
        String relativePath = absolutePath.toString().split("/LocalMedia/")[1];
        MDC.put("Path", relativePath);
        logger.info("Detected movie event.");

        String resultFilePath = absolutePath.toString();
        if(!activeConversions.contains(resultFilePath)) {
            MediaFile mediaFile = null;

            if(event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                if (Files.isRegularFile(absolutePath) && ffprobe != null) {
                    try {
                        activeConversionGauge.getAndIncrement();
                        resultFilePath = launchVideoConverter(resultFilePath).get();
                    } catch (InterruptedException | ExecutionException e){
                        logger.error("Failuring launching video converter", e);
                    } finally {
                        activeConversionGauge.getAndDecrement();
                    }
                }

                metadataService.addMediaMetadata(relativePath);
                mediaFile = getMediaFile(event, relativePath);
                notificationHandler.sendPushNotifications(mediaFile.getMovie().getTitle(), mediaFile.getPath());
            } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE){
                metadataService.deleteMediaMetadata(relativePath);
            }

            addEvent(event, mediaFile, resultFilePath);
        }
    }

    private MediaFile getMediaFile(WatchEvent watchEvent, String relativePath){
        MediaFile mediaFile = metadataService.getMediaMetadata(relativePath);
        if(watchEvent.kind() == StandardWatchEventKinds.ENTRY_DELETE){
            return MediaFile.Builder.copyWithNoImage(mediaFile);
        }

        return mediaFile;
    }

    private void addEvent(WatchEvent watchEvent, MediaFile mediaFile, String resultFilePath){
        logger.info("Adding event to repository.");
        MediaFileEvent event = new MediaFileEvent(MovieEvent.valueOf(watchEvent.kind().name()).getMovieEventString(), mediaFile, resultFilePath.split("/LocalMedia/")[1]);
        mediaFileEvents.add(event);
        eventRepository.save(event);
    }

    private CompletableFuture<String> launchVideoConverter(String inputFilePath){
        // I need to find a way to wait until a file is fully written before converting it
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e){
            logger.error("Failed sleep", e);
        }

        String resultFilePath = inputFilePath.substring(0, inputFilePath.lastIndexOf('.')) + ".mp4";
        SimpleConversionJob conversionJob = new SimpleConversionJob(ffprobe, new File(resultFilePath), new File(inputFilePath));

        logger.info("Launching video converter.");
        return CompletableFuture.supplyAsync(new VideoController(conversionJob, activeConversions), executorService);
    }

    public List<MediaFileEvent> getMediaFileEvents(LocalDateTime lastQueryTime){
        return mediaFileEvents.stream()
                .sorted(Comparator.comparing(MediaFileEvent::getTimestamp))
                .filter(mediaFileEvent -> mediaFileEvent.getTimestamp().isAfter(lastQueryTime))
                .collect(Collectors.toList());
    }
}
