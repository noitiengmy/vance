package com.maico.refurbished;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Locale;

/** UI-only preferences, without touching package patching configuration. */
final class UiSettings {
    private static final String PREFS="refurbished_interface";
    private UiSettings() {}
    private static SharedPreferences pref(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static Context wrap(Context base) {
        String language=pref(base).getString("language","auto");
        if("auto".equals(language))return base;
        Configuration config=new Configuration(base.getResources().getConfiguration());
        Locale locale=new Locale(language);
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
    static boolean dark(Context c) {
        String theme=pref(c).getString("theme","auto");
        if("dark".equals(theme))return true;
        if("light".equals(theme))return false;
        return (c.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    static int theme(Context c) {
        return dark(c)?android.R.style.Theme_Material_NoActionBar:
                android.R.style.Theme_Material_Light_NoActionBar;
    }
    static void show(Activity activity) {
        SharedPreferences pref=pref(activity);
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(16*activity.getResources().getDisplayMetrics().density+0.5f);
        content.setPadding(padding,padding,padding,padding);
        TextView languageLabel=new TextView(activity);
        languageLabel.setText(R.string.language);
        content.addView(languageLabel);
        Spinner language=new Spinner(activity);
        String[] langs=activity.getResources().getStringArray(R.array.interface_languages);
        ArrayAdapter<String> la=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,langs);
        la.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        language.setAdapter(la);
        String storedLang=pref.getString("language","auto");
        language.setSelection("vi".equals(storedLang)?2:"en".equals(storedLang)?1:0);
        content.addView(language);
        TextView appearanceLabel=new TextView(activity);
        appearanceLabel.setText(R.string.theme);
        content.addView(appearanceLabel);
        Spinner appearance=new Spinner(activity);
        ArrayAdapter<String> ta=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                activity.getResources().getStringArray(R.array.interface_themes));
        ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appearance.setAdapter(ta);
        String storedTheme=pref.getString("theme","auto");
        appearance.setSelection("dark".equals(storedTheme)?2:"light".equals(storedTheme)?1:0);
        content.addView(appearance);
        TextView notice=new TextView(activity);
        notice.setText(R.string.apply_next_launch);
        content.addView(notice);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.settings_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.done,(dialog,which)->{
                    String langCode=new String[]{"auto","en","vi"}[
                            language.getSelectedItemPosition()];
                    String themeCode=new String[]{"auto","light","dark"}[
                            appearance.getSelectedItemPosition()];
                    pref.edit().putString("language",langCode)
                            .putString("theme",themeCode).apply();
                }).show();
    }
}
