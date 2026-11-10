package com.maico.refurbished.patcher;

import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import java.io.File;
import java.io.IOException;

/**
 * True binary split merge backed by ARSCLib 1.4.0:
 * merges resource configurations, DEX/native payloads, and manifest components.
 * An APK must still pass structural validation before being patched and signed.
 */
final class RealSplitMerger {
    private RealSplitMerger(){}

    static void merge(PackageSets.Stage set,File output) throws Exception {
        if(!set.multi())throw new IOException("No split modules to merge");
        ApkBundle bundle=new ApkBundle();
        ApkModule merged=null;
        try{
            for(int i=0;i<set.apks.size();i++){
                String name=set.isBase.get(i)?"base":"split_"+i;
                ApkModule part=ApkModule.loadApkFile(set.apks.get(i),name);
                bundle.addModule(part);
            }
            merged=bundle.mergeModules(false);
            if(merged==null || merged.getAndroidManifest()==null)
                throw new IOException("ARSCLib did not produce a valid merged manifest.");
            if(merged.getAndroidManifest().isSplit())
                throw new IOException("Merged output still declares itself as split.");
            merged.setApkSignatureBlock(null);
            merged.refreshTable();
            merged.refreshManifest();
            merged.writeApk(output);
            ApkInspector.Info inspected=new ApkInspector().inspect(output);
            if(inspected.split || !set.packageName.equals(inspected.packageName)
                    || set.versionCode!=inspected.versionCode
                    || inspected.launcherEntries<1){
                throw new IOException("Merged APK did not pass package/version/launcher validation.");
            }
        }catch(RuntimeException runtime){
            throw new IOException("Unable to merge this split layout: "+
                    runtime.getMessage(),runtime);
        }finally{
            if(merged!=null)merged.close();
            bundle.close();
        }
    }
}
