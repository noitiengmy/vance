package com.maico.refurbished;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** In-app installed package browser; nothing is extracted or modified here. */
final class InstalledAppPicker {
    interface Selection { void onSelect(AppItem item); }

    static final class AppItem {
        final String name, pkg, version, source;
        final String[] splits;
        final long versionCode;
        final boolean split, system;
        final Drawable icon;

        AppItem(String name,String pkg,String version,long code,
                String source,String[] splits,boolean split,boolean system,Drawable icon) {
            this.name=name;this.pkg=pkg;this.version=version;this.versionCode=code;
            this.source=source;this.splits=splits==null?new String[0]:splits.clone();
            this.split=split;this.system=system;this.icon=icon;
        }
    }

    private InstalledAppPicker() { }

    static void show(Activity activity,Selection callback) {
        final PackageManager pm=activity.getPackageManager();
        final int pad=dp(activity,12);
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad,pad,pad,0);

        TextView hint=new TextView(activity);
        hint.setText(R.string.installed_title);
        body.addView(hint);

        EditText search=new EditText(activity);
        search.setSingleLine(true);
        search.setHint(R.string.installed_search);
        body.addView(search);

        CheckBox includeSystem=new CheckBox(activity);
        includeSystem.setText(R.string.installed_system);
        includeSystem.setChecked(false);
        body.addView(includeSystem);

        ProgressBar loading=new ProgressBar(activity);
        body.addView(loading,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(activity,40)));

        ListView list=new ListView(activity);
        body.addView(list,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle(R.string.installed_title)
                .setView(body)
                .setNegativeButton(R.string.cancel,null).create();
        dialog.setOnShowListener(ignored -> {
            if(dialog.getWindow()!=null)
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int)(activity.getResources().getDisplayMetrics().heightPixels*0.82f));
        });
        dialog.show();

        new Thread(() -> {
            final ArrayList<AppItem> all=new ArrayList<>();
            String failure=null;
            try {
                List<PackageInfo> packages=pm.getInstalledPackages(0);
                for(PackageInfo info:packages) {
                    ApplicationInfo ai=info.applicationInfo;
                    if(ai==null || ai.sourceDir==null)continue;
                    boolean system=(ai.flags&(ApplicationInfo.FLAG_SYSTEM|
                            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
                    boolean split=ai.splitSourceDirs!=null && ai.splitSourceDirs.length>0;
                    CharSequence label=ai.loadLabel(pm);
                    String name=label==null?info.packageName:label.toString();
                    long versionCode=Build.VERSION.SDK_INT>=28?
                            info.getLongVersionCode():info.versionCode;
                    Drawable icon;
                    try {icon=ai.loadIcon(pm);}catch(Exception e){icon=null;}
                    all.add(new AppItem(name,info.packageName,info.versionName,
                            versionCode,ai.sourceDir,ai.splitSourceDirs,split,system,icon));
                }
                Collections.sort(all,Comparator.comparing(
                        x -> x.name.toLowerCase(Locale.ROOT)));
            } catch(Exception e) { failure=e.getMessage(); }
            final String error=failure;
            new Handler(Looper.getMainLooper()).post(() -> {
                if(!dialog.isShowing())return;
                body.removeView(loading);
                if(error!=null){
                    hint.setText("Cannot list installed apps: "+error);
                    return;
                }
                final ArrayList<AppItem> visible=new ArrayList<>();
                final ArrayAdapter<AppItem> adapter=new ArrayAdapter<AppItem>(
                        activity,android.R.layout.simple_list_item_1,visible) {
                    @Override public View getView(int position,View reused,ViewGroup parent) {
                        AppItem app=getItem(position);
                        LinearLayout line=new LinearLayout(activity);
                        line.setGravity(Gravity.CENTER_VERTICAL);
                        line.setPadding(dp(activity,4),dp(activity,6),
                                dp(activity,4),dp(activity,6));
                        ImageView icon=new ImageView(activity);
                        icon.setImageDrawable(app.icon);
                        line.addView(icon,new LinearLayout.LayoutParams(
                                dp(activity,44),dp(activity,44)));
                        LinearLayout details=new LinearLayout(activity);
                        details.setOrientation(LinearLayout.VERTICAL);
                        details.setPadding(dp(activity,12),0,0,0);
                        TextView title=new TextView(activity);
                        title.setText(app.name+(app.split?"  [SPLIT]":""));
                        title.setTextSize(15);
                        details.addView(title);
                        TextView detail=new TextView(activity);
                        detail.setText(app.pkg+" · "+(app.version==null?"":app.version));
                        detail.setTextSize(11);
                        details.addView(detail);
                        line.addView(details);
                        return line;
                    }
                };
                list.setAdapter(adapter);
                final Runnable refresh=() -> {
                    String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
                    visible.clear();
                    for(AppItem item:all) {
                        if(!includeSystem.isChecked() && item.system)continue;
                        if(!q.isEmpty() && !item.name.toLowerCase(Locale.ROOT).contains(q)
                                && !item.pkg.toLowerCase(Locale.ROOT).contains(q))continue;
                        visible.add(item);
                    }
                    adapter.notifyDataSetChanged();
                    hint.setText(visible.size()+" of "+all.size()+" installed apps. "+
                            "Split apps are supported as full sets.");
                };
                search.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                    @Override public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}
                    @Override public void afterTextChanged(Editable e){}
                });
                includeSystem.setOnCheckedChangeListener((view,checked)->refresh.run());
                list.setOnItemClickListener((parent,view,position,id)->{
                    AppItem app=visible.get(position);
                    File source=new File(app.source);
                    boolean readable=source.isFile()&&source.canRead();
                    for(String splitPath:app.splits){
                        File splitFile=new File(splitPath);
                        readable &= splitFile.isFile()&&splitFile.canRead();
                    }
                    if(!readable) {
                        new AlertDialog.Builder(activity)
                            .setTitle("APK is not accessible")
                            .setMessage("Android has restricted access to this APK. "+
                                    "You can export its APK separately and choose it from Files.")
                            .setPositiveButton("OK",null).show();
                        return;
                    }
                    dialog.dismiss();
                    callback.onSelect(app);
                });
                refresh.run();
            });
        }).start();
    }

    private static int dp(Activity activity,int size) {
        return (int)(size*activity.getResources().getDisplayMetrics().density+0.5f);
    }
}
