package com.platypii.baseline.util;

import androidx.annotation.NonNull;
import android.util.Log;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Parse location data from track file
 */
public class CSVParse {
    private static final String TAG = "CSVParse";

    public static double getColumnDouble(@NonNull String[] row, @NonNull CSVHeader columns, @NonNull String columnName) {
        final Integer index = columns.get(columnName);
        if (index != null && index < row.length) {
            try {
                final String col = row[index];
                if (!col.isEmpty()) {
                    return Double.parseDouble(col);
                }
            } catch (Exception e) {
                Log.w(TAG, "CSV column double parsing exception", e);
            }
        }
        return Double.NaN;
    }

    public static long getColumnLong(@NonNull String[] row, @NonNull CSVHeader columns, @NonNull String columnName) {
        final Integer index = columns.get(columnName);
        if (index != null && index < row.length) {
            try {
                final String col = row[index];
                if (!col.isEmpty()) {
                    return Long.parseLong(col);
                }
            } catch (Exception e) {
                Log.w(TAG, "CSV column long parsing exception", e);
            }
        }
        return -1L;
    }

    public static long getColumnDate(@NonNull String[] row, @NonNull CSVHeader columns, @NonNull String columnName) {
        final Integer index = columns.get(columnName);
        if (index != null && index < row.length) {
            try {
                final String col = row[index];
                if (!col.isEmpty()) {
                    return parseFlySightDate(col);
                }
            } catch (Exception e) {
                Log.w(TAG, "CSV column date parsing exception", e);
            }
        }
        return -1L;
    }

    @NonNull
    public static String getColumnString(@NonNull String[] row, @NonNull CSVHeader columns, @NonNull String columnName) {
        final Integer index = columns.get(columnName);
        if (index != null && index < row.length) {
            return row[index];
        } else {
            return "";
        }
    }

    /**
     * Return true if the column is "Y". Case sensiTive.
     */
    public static boolean getColumnYes(@NonNull String[] row, @NonNull CSVHeader columns, @NonNull String columnName) {
        final Integer index = columns.get(columnName);
        if (index != null && index < row.length) {
            return "Y".equals(row[index]);
        } else {
            return false;
        }
    }

    private static SimpleDateFormat df;
    static long parseFlySightDate(@NonNull String dateString) throws ParseException {
        // Lazy init
        if (df == null) {
            // 2018-01-25T11:48:09.80Z
            df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        // Handle milliseconds separately
        long millis = 0;
        final int len = dateString.length();
        if (dateString.charAt(len - 4) == '.') {
            millis = 10 * Long.parseLong(dateString.substring(len - 3, len - 1));
        }
        return df.parse(dateString).getTime() + millis;
    }
}
