package com.platypii.baseline.events;

import com.platypii.baseline.measurements.MLocation;

import androidx.annotation.Nullable;

/**
 * Indicates that audible has either started or stopped
 */
public class ChartFocusEvent {

    @Nullable
    public final MLocation location;

    public ChartFocusEvent(@Nullable MLocation location) {
        this.location = location;
    }

}
