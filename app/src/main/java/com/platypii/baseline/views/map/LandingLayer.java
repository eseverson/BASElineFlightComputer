package com.platypii.baseline.views.map;

import com.platypii.baseline.R;
import com.platypii.baseline.Services;
import com.platypii.baseline.location.LandingZone;
import android.support.annotation.NonNull;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import java.util.ArrayList;
import java.util.List;

class LandingLayer implements MapLayer {

    @NonNull
    private final Marker landingMarker;
    @NonNull
    private final Polyline landingPath;
    private final List<LatLng> landingPoints = new ArrayList<>();

    LandingLayer(GoogleMap map) {
        // Add projected landing zone
        landingMarker = map.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .visible(false)
                .title("landing")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_target))
                .anchor(0.5f, 0.5f)
        );
        // Add line to projected landing zone
        landingPath = map.addPolyline(new PolylineOptions()
                .visible(false)
                .width(10)
                .color(0x66ff0000)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
        );
    }

    @Override
    public void update() {
        final LatLng landingLocation = LandingZone.getLandingLocation();
        if (landingLocation != null) {
            final LatLng currentLoc = Services.location.lastLoc.latLng();
            landingMarker.setPosition(landingLocation);
            landingMarker.setVisible(true);
            landingPoints.clear();
            landingPoints.add(currentLoc);
            landingPoints.add(landingLocation);
            landingPath.setPoints(landingPoints);
            landingPath.setVisible(true);
        } else {
            landingMarker.setVisible(false);
            landingPath.setVisible(false);
        }
    }
}
