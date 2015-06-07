package com.platypii.baseline.data;

import com.platypii.baseline.data.measurements.Measurement;

/** Used by Managers to notify of updated sensors */
public interface MySensorListener {

    void onSensorChanged(Measurement measurement);

}
