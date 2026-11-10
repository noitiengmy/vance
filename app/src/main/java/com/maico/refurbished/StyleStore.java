package com.maico.refurbished;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * Remembers only configuration for a package, not built APKs or a history list.
 * Pictures remain at user-chosen document URIs; no private copies are made.
 */
final class StyleStore {
    static final int DEFAULT_COUNT = 3;
    static final class Saved {
        final String[] names;
        final Uri[] icons;
        final boolean[] missing;
        final int[] backgrounds; // 0 white, 1 transparent
        Saved(int count) {
            names=new String[count];
            icons=new Uri[count];
            missing=new boolean[count];
            backgrounds=new int[count];
        }
        int size(){return names.length;}
    }

    private final SharedPreferences prefs;
    private final ContentResolver resolver;

    StyleStore(Context context) {
        prefs = context.getSharedPreferences("asw_styles", Context.MODE_PRIVATE);
        resolver = context.getContentResolver();
    }

    void save(String packageName,String[] names,Uri[] icons,int[] backgrounds) {
        if (packageName==null || packageName.isEmpty()) return;
        if(names==null || icons==null || backgrounds==null || names.length!=icons.length || names.length!=backgrounds.length) return;
        try {
            JSONObject object=new JSONObject();
            JSONArray styleNames=new JSONArray();
            JSONArray styleIcons=new JSONArray();
            JSONArray styleBackgrounds=new JSONArray();
            for(int i=0;i<names.length;i++) {
                styleNames.put(names[i]==null?"":names[i]);
                styleIcons.put(icons[i]==null?"":icons[i].toString());
                styleBackgrounds.put(backgrounds[i]==1?1:0);
            }
            object.put("names",styleNames);
            object.put("icons",styleIcons);
            object.put("backgrounds",styleBackgrounds);
            object.put("key","default");
            prefs.edit().putString(packageName,object.toString()).apply();
        } catch(JSONException ignored) { }
    }

    Saved load(String packageName) {
        String raw=prefs.getString(packageName,null);
        if(raw==null)return new Saved(DEFAULT_COUNT);
        try {
            JSONObject object=new JSONObject(raw);
            JSONArray names=object.optJSONArray("names");
            JSONArray icons=object.optJSONArray("icons");
            JSONArray backgrounds=object.optJSONArray("backgrounds");
            int count=Math.max(DEFAULT_COUNT,Math.max(names==null?0:names.length(),
                    Math.max(icons==null?0:icons.length(),
                            backgrounds==null?0:backgrounds.length())));
            Saved result=new Saved(count);
            for(int i=0;i<result.size();i++) {
                result.names[i]=names==null?"":names.optString(i,"");
                result.backgrounds[i]=backgrounds==null?0:(backgrounds.optInt(i,0)==1?1:0);
                String value=icons==null?"":icons.optString(i,"");
                if(!value.isEmpty()) {
                    try {
                        Uri uri=Uri.parse(value);
                        if(!"content".equals(uri.getScheme())) {
                            result.missing[i]=true;
                        } else {
                            try(InputStream test=resolver.openInputStream(uri)) {
                                if(test==null)throw new IllegalStateException("File unavailable");
                                result.icons[i]=uri;
                            }
                        }
                    } catch(Exception missingFile) {
                        result.missing[i]=true;
                    }
                }
            }
            return result;
        } catch(JSONException ignored) {return new Saved(DEFAULT_COUNT);}
    }

    void forget(String packageName) {
        if(packageName!=null) prefs.edit().remove(packageName).apply();
    }

    boolean exists(String packageName) {
        return packageName!=null && prefs.contains(packageName);
    }
}
