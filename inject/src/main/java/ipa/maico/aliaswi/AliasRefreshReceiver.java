package ipa.maico.furbish;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.TreeMap;

/**
 * After updating a previously refurbished APK, an enabled alias may have
 * disappeared because the user chose fewer styles. Preserve an available
 * enabled alias, or restore Alias0 so the launcher entry cannot be stranded.
 */
public final class AliasRefreshReceiver extends BroadcastReceiver {
    private static final String PREFIX="ipa.maico.furbish.Alias";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null ||
                !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
        PackageManager pm=context.getPackageManager();
        String pkg=context.getPackageName();
        try{
            PackageInfo packageInfo=pm.getPackageInfo(pkg,
                    PackageManager.GET_ACTIVITIES|PackageManager.MATCH_DISABLED_COMPONENTS);
            TreeMap<Integer,ActivityInfo> aliases=new TreeMap<>();
            if(packageInfo.activities==null)return;
            for(ActivityInfo info:packageInfo.activities){
                String name=info.name;
                if(name==null||!name.startsWith(PREFIX))continue;
                String suffix=name.substring(PREFIX.length());
                try{
                    int index=Integer.parseInt(suffix);
                    if(index>=0 && (PREFIX+index).equals(name))aliases.put(index,info);
                }catch(NumberFormatException ignored){}
            }
            if(!aliases.containsKey(0))return;
            int selected=-1;
            for(java.util.Map.Entry<Integer,ActivityInfo> item:aliases.entrySet()){
                ComponentName component=new ComponentName(pkg,item.getValue().name);
                int state=pm.getComponentEnabledSetting(component);
                if(state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||
                        (state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                                item.getValue().enabled)){
                    selected=item.getKey();break;
                }
            }
            if(selected==-1)selected=0;
            ComponentName keep=new ComponentName(pkg,PREFIX+selected);
            pm.setComponentEnabledSetting(keep,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            for(Integer index:aliases.keySet()){
                if(index==selected)continue;
                pm.setComponentEnabledSetting(new ComponentName(pkg,PREFIX+index),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }catch(Exception ignored){
            // Never interrupt the host app's update lifecycle.
        }
    }
}
