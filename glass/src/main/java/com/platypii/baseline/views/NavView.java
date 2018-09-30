package com.platypii.baseline.views;

import com.platypii.baseline.Services;
import com.platypii.baseline.location.Geo;
import com.platypii.baseline.location.MyLocationListener;
import com.platypii.baseline.places.Place;
import com.platypii.baseline.measurements.MLocation;
import com.platypii.baseline.util.Convert;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

public class NavView extends View implements MyLocationListener {

    private final float density = getResources().getDisplayMetrics().density;
    private final Paint paint = new Paint();
    private final Path arrow = new Path();

    public NavView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTextAlign(Paint.Align.CENTER);
        // Software layer required for path
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        // Initialize arrow polygon
        final float w2 = 0.056f; // The width of the arrow
        arrow.setLastPoint(-w2, -0.8f);
        arrow.lineTo(0, -1);
        arrow.lineTo(w2, -0.8f);
    }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();

        final float center_x = width * 0.5f;
        final float center_y = height * 0.5f;

        final MLocation currentLocation = Services.location.lastLoc;
        if (currentLocation != null) { // TODO: Check for freshness?
            // Nearest place
            final Place place = Services.places.nearestPlace.cached(currentLocation);
            if (place != null) {
                // Bearing from here to place
                final double absoluteBearing = Geo.bearing(currentLocation.latitude, currentLocation.longitude, place.lat, place.lng);
                // Bearing relative to flight path
                final double bearing = absoluteBearing - Services.location.bearing();

                // Distance to place
                final double distance = Geo.distance(currentLocation.latitude, currentLocation.longitude, place.lat, place.lng);

                // Draw location label
                paint.setColor(0xffaaaaaa);
                paint.setTextSize(18 * density);
                canvas.drawText(place.name, center_x, 18 * density, paint);
                // Draw distance label
                paint.setColor(0xffeeeeee);
                paint.setTextSize(28 * density);
                canvas.drawText(Convert.distanceShort(distance), center_x, center_y + 14 * density, paint);

                // Draw bearing circle
                paint.setStrokeWidth(2);
                paint.setStyle(Paint.Style.STROKE);
                final float radius = 140;
                canvas.drawCircle(center_x, center_y, radius, paint);
                if (!Double.isNaN(bearing)) {
                    canvas.save();
                    canvas.translate(center_x, center_y);
                    canvas.rotate((float) bearing, 0, 0);
                    canvas.scale(radius, radius);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawPath(arrow, paint);
                    canvas.restore();
                }
            } else {
                paint.setColor(0xffeeeeee);
                paint.setTextSize(24 * density);
                final List<Place> places = Services.places.getPlaces();
                if (places == null || places.isEmpty()) {
                    canvas.drawText("no places", center_x, center_y, paint);
                } else {
                    canvas.drawText("no target", center_x, center_y, paint);
                }
            }
        } else {
            paint.setColor(0xffeeeeee);
            paint.setTextSize(24 * density);
            canvas.drawText("no signal", center_x, center_y, paint);
        }
    }

    public void start() {
        // Start listening for location updates
        Services.location.addListener(this);
    }
    public void stop() {
        // Stop listening for location updates
        Services.location.removeListener(this);
    }

    @Override
    public void onLocationChanged(@NonNull MLocation loc) {
        postInvalidate();
    }


}
