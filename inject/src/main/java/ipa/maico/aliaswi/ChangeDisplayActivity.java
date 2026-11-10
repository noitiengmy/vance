package ipa.maico.furbish;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reusable module for any package and any number of generated launcher aliases.
 * No package name, style count, ARSC resource name, or target activity is baked in.
 */
public final class ChangeDisplayActivity extends Activity {
    private static final String PREFIX="ipa.maico.furbish.Alias";
    private static final int FLAGS=PackageManager.DONT_KILL_APP;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final PackageManager pm=getPackageManager();
        final String pkg=getPackageName();
        final TreeMap<Integer,ActivityInfo> aliases=new TreeMap<>();
        try {
            PackageInfo info=pm.getPackageInfo(pkg,
                    PackageManager.GET_ACTIVITIES | PackageManager.MATCH_DISABLED_COMPONENTS);
            if(info.activities!=null) {
                for(ActivityInfo item:info.activities){
                    String name=item.name;
                    if(name==null||!name.startsWith(PREFIX))continue;
                    String number=name.substring(PREFIX.length());
                    if(number.isEmpty())continue;
                    boolean numeric=true;
                    for(int j=0;j<number.length();j++)
                        if(number.charAt(j)<'0'||number.charAt(j)>'9'){numeric=false;break;}
                    if(!numeric)continue;
                    try {
                        int index=Integer.parseInt(number);
                        if(index>=0 && name.equals(PREFIX+index))aliases.put(index,item);
                    } catch(NumberFormatException ignored){}
                }
            }
        } catch(PackageManager.NameNotFoundException error) {
            new AlertDialog.Builder(this).setMessage(error.toString())
                    .setPositiveButton(android.R.string.ok,(d,w)->finish()).show();
            return;
        }
        if(aliases.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("No display aliases found.")
                    .setPositiveButton(android.R.string.ok,(d,w)->finish()).show();
            return;
        }

        RadioGroup group=new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.setPadding(dp(14),dp(8),dp(14),dp(8));
        int selected=0;
        int checkedId=-1;
        final java.util.HashMap<Integer,Integer> choices=new java.util.HashMap<>();
        for(Map.Entry<Integer,ActivityInfo> entry:aliases.entrySet()) {
            int index=entry.getKey();
            ActivityInfo info=entry.getValue();
            RadioButton radio=new RadioButton(this);
            int viewId=View.generateViewId();
            choices.put(viewId,index);
            radio.setId(viewId);
            CharSequence label=info.loadLabel(pm);
            radio.setText(label==null||label.length()==0?
                    (index==0?"Original":"Style "+index):label);
            try {
                Drawable icon=info.loadIcon(pm);
                if(icon!=null) {
                    icon.setBounds(0,0,dp(36),dp(36));
                    radio.setCompoundDrawables(icon,null,null,null);
                    radio.setCompoundDrawablePadding(dp(10));
                }
            } catch(Exception ignored){}
            group.addView(radio);
            int stateValue=pm.getComponentEnabledSetting(
                    new ComponentName(pkg,info.name));
            if(stateValue==PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (stateValue==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && info.enabled)){
                selected++;
                checkedId=viewId;
            }
        }
        if(selected==0)checkedId=group.getChildAt(0).getId();
        group.check(checkedId);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(group);
        new AlertDialog.Builder(this)
                .setTitle("Change Display")
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel,(d,w)->finish())
                .setPositiveButton("Save",(d,w)->{
                    Integer chosen=choices.get(group.getCheckedRadioButtonId());
                    if(chosen==null)return;
                    try{
                        // Enable the target first, so there is always one launcher.
                        ComponentName target=new ComponentName(pkg,PREFIX+chosen);
                        pm.setComponentEnabledSetting(target,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,FLAGS);
                        for(Integer index:aliases.keySet()){
                            if(index.equals(chosen))continue;
                            pm.setComponentEnabledSetting(
                                    new ComponentName(pkg,PREFIX+index),
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,FLAGS);
                        }
                        finish();
                    }catch(Exception error){
                        new AlertDialog.Builder(this).setMessage(error.toString())
                                .setPositiveButton(android.R.string.ok,(d2,w2)->finish()).show();
                    }
                })
                .setOnCancelListener(d->finish())
                .show();
    }
    private int dp(int value){
        return (int)(getResources().getDisplayMetrics().density*value+0.5f);
    }
}
