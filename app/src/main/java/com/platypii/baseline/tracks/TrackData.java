package com.platypii.baseline.tracks;

import com.platypii.baseline.measurements.MLocation;

import androidx.annotation.NonNull;
import java.io.File;
import java.util.List;
import java9.util.concurrent.CompletableFuture;

/**
 * Parse location data from track file
 */
public class TrackData {

    @NonNull
    public final List<MLocation> data;

    @NonNull
    public final TrackStats stats;

    public TrackData(@NonNull File trackFile) {
        final List<MLocation> all = new TrackFileReader(trackFile).read();
        // Trim plane and ground
        data = TrackDataTrimmer.autoTrim(all);
        // Compute stats
        stats = new TrackStats(data);
    }

    @NonNull
    public static CompletableFuture<TrackData> fromTrackFileAsync(@NonNull File trackFile) {
        final CompletableFuture<TrackData> future = new CompletableFuture<>();
        new Thread(() -> future.complete(new TrackData(trackFile))).start();
        return future;
    }

}
