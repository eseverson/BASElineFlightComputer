package com.platypii.baseline.views;

import com.platypii.baseline.R;
import com.platypii.baseline.Services;
import com.platypii.baseline.audible.AudibleMinMaxPreference;
import com.platypii.baseline.audible.AudibleMode;
import com.platypii.baseline.audible.AudibleSettings;
import com.platypii.baseline.util.Analytics;
import com.platypii.baseline.util.Numbers;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import java.util.Locale;

/**
 * This fragment shows the audible preferences
 */
public class AudibleSettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private AudibleSettings settings;
    private ListPreference modePreference;
    private AudibleMinMaxPreference minPreference;
    private AudibleMinMaxPreference maxPreference;
    private EditTextPreference precisionPreference;
    private EditTextPreference intervalPreference;
    private EditTextPreference ratePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_audible);

        settings = Services.audible.settings;

        final SwitchPreferenceCompat enabledPreference = findPreference("audible_enabled");
        final SwitchPreferenceCompat quietPreference = findPreference("audible_quiet");
        modePreference = findPreference("audible_mode");
        minPreference = findPreference("audible_min");
        maxPreference = findPreference("audible_max");
        precisionPreference = findPreference("audible_precision");
        intervalPreference = findPreference("audible_interval");
        ratePreference = findPreference("audible_rate");

        enabledPreference.setOnPreferenceChangeListener(this);
        quietPreference.setOnPreferenceChangeListener(this);
        modePreference.setOnPreferenceChangeListener(this);
        minPreference.setOnPreferenceChangeListener(this);
        maxPreference.setOnPreferenceChangeListener(this);
        precisionPreference.setOnPreferenceChangeListener(this);
        intervalPreference.setOnPreferenceChangeListener(this);
        ratePreference.setOnPreferenceChangeListener(this);

        updateViews();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(Color.BLACK);
    }

    /**
     * Set summaries and adjust defaults
     */
    private void updateViews() {
        final AudibleMode mode = settings.mode;
        final int precision = settings.precision;
        modePreference.setSummary(mode.name);
        minPreference.setTitle(mode.minimumTitle());
        maxPreference.setTitle(mode.maximumTitle());
        minPreference.setSummary(mode.renderDisplay(settings.min, precision));
        maxPreference.setSummary(mode.renderDisplay(settings.max, precision));
        if (precision <= 0) {
            precisionPreference.setSummary(String.format(Locale.getDefault(), "%d decimal places", precision));
        } else {
            precisionPreference.setSummary(String.format(Locale.getDefault(), "%." + precision + "f decimal places", (float) precision));
        }
        intervalPreference.setSummary("Every " + settings.speechInterval + " sec");
        ratePreference.setSummary(String.format(Locale.getDefault(), "%.2fx", settings.speechRate));
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object value) {
        final String key = preference.getKey();
        switch (key) {
            case "audible_enabled":
                final boolean audibleEnabled = (Boolean) value;
                if (audibleEnabled) {
                    Analytics.logEvent(getActivity(), "pref_start_audible", null);
                    Services.audible.enableAudible();
                } else {
                    Analytics.logEvent(getActivity(), "pref_stop_audible", null);
                    Services.audible.disableAudible();
                }
                break;
            case "audible_quiet":
                Services.audible.settings.airplaneMode = (Boolean) value;
                break;
            case "audible_mode":
                final String audibleMode = (String) value;
                final String previousAudibleMode = modePreference.getValue();
                if (!audibleMode.equals(previousAudibleMode)) {
                    settings.setAudibleMode(audibleMode);
                    minPreference.setValue(settings.mode.defaultMin);
                    maxPreference.setValue(settings.mode.defaultMax);
                    precisionPreference.setText(null);
                    precisionPreference.setDefaultValue(settings.mode.defaultPrecision);
                    updateViews();
                    if (Services.audible.settings.isEnabled) {
                        Services.audible.speakNow(settings.mode.name);
                    }
                }
                break;
            case "audible_min":
                final float min = (Float) value;
                if (!Numbers.isReal(min)) return false;
                settings.min = min;
                updateViews();
                break;
            case "audible_max":
                final float max = (Float) value;
                if (!Numbers.isReal(max)) return false;
                settings.max = max;
                updateViews();
                break;
            case "audible_precision":
                final int precision = Numbers.parseInt((String) value, 1);
                if (precision < 0) {
                    Toast.makeText(getActivity(), "Precision cannot be negative", Toast.LENGTH_SHORT).show();
                    return false;
                } else if (8 < precision) {
                    Toast.makeText(getActivity(), "Precision cannot be greater than 8", Toast.LENGTH_SHORT).show();
                    return false;
                }
                settings.precision = precision;
                updateViews();
                break;
            case "audible_interval":
                final float speechInterval = Numbers.parseFloat((String) value);
                if (!Numbers.isReal(speechInterval)) return false;
                settings.speechInterval = speechInterval;
                updateViews();
                break;
            case "audible_rate":
                final float speechRate = Numbers.parseFloat((String) value);
                if (!Numbers.isReal(speechRate)) return false;
                settings.speechRate = speechRate;
                updateViews();
                break;
        }
        return true;
    }
}
