package com.maico.refurbished.patcher;

import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/** Lightweight, read-only inspection before users configure a clean APK. */
public final class ApkInspector {
    private static final String NS = "http://schemas.android.com/apk/res/android";
    private static final String MAIN = "android.intent.action.MAIN";
    private static final String LAUNCHER = "android.intent.category.LAUNCHER";

    public static final class Info {
        public final String packageName, versionName;
        public final long versionCode;
        public final int launcherEntries;
        public final String[] launcherComponents;
        public final boolean split;

        Info(String pkg, String name, long code, int entries, boolean isSplit,
             String[] launchers) {
            packageName=pkg; versionName=name; versionCode=code;
            launcherEntries=entries; split=isSplit;launcherComponents=launchers.clone();
        }
    }

    public Info inspect(File file) throws IOException {
        try (ApkModule apk=ApkModule.loadApkFile(file)) {
            AndroidManifestBlock manifest=apk.getAndroidManifest();
            if (manifest==null) throw new IOException("AndroidManifest.xml not found");
            String pkg=manifest.getPackageName();
            if (pkg==null || pkg.trim().isEmpty())
                throw new IOException("APK has no package name");
            String version=manifest.getVersionName();
            Integer code=manifest.getVersionCode();
            int count=0;
            List<String> launchers=new ArrayList<>();
            ResXmlElement app=manifest.getApplicationElement();
            boolean refurbished=false;
            if(app!=null){
                for(ResXmlElement activity:app.listElements("activity")){
                    ResXmlAttribute name=activity.searchAttribute(NS,"name");
                    if(name!=null){
                        String value=name.getValueString();
                        if(ApkPatcher.DIALOG.equals(value) ||
                                "ipa.maico.aliaswi.ChangeDisplayActivity".equals(value)){
                            refurbished=true;break;
                        }
                    }
                }
            }
            if (app != null) {
                for (Iterator<ResXmlElement> components=app.getElements();components.hasNext();) {
                    ResXmlElement component=components.next();
                    if (!component.equalsName("activity") && !component.equalsName("activity-alias"))
                        continue;
                    for (ResXmlElement filter:component.listElements("intent-filter")) {
                        if (has(filter,"action",MAIN) && has(filter,"category",LAUNCHER)) {
                            count++;
                            ResXmlAttribute componentName=component.searchAttribute(NS,"name");
                            String raw=componentName==null?null:componentName.getValueString();
                            if(raw!=null){
                                String componentNameFull=raw.startsWith(".")?pkg+raw:
                                        raw.contains(".")?raw:pkg+"."+raw;
                                // Show a previously refurbished alias group as one
                                // logical launcher, not dozens of style variants.
                                if(refurbished &&
                                        (componentNameFull.startsWith(ApkPatcher.ALIAS) ||
                                         componentNameFull.startsWith("ipa.maico.aliaswi.Alias")) &&
                                        !componentNameFull.equals(ApkPatcher.ALIAS+"0") &&
                                        !componentNameFull.equals("ipa.maico.aliaswi.Alias0")){
                                    count--;
                                }else launchers.add(componentNameFull);
                            }
                            break;
                        }
                    }
                }
            }
            return new Info(pkg,version==null?"(unspecified)":version,
                    code==null?0L:code.longValue(),count,manifest.isSplit(),
                    launchers.toArray(new String[0]));
        } catch (RuntimeException failure) {
            throw new IOException("Cannot parse APK manifest: "+failure.getMessage(),failure);
        }
    }

    private static boolean has(ResXmlElement filter,String type,String value) {
        for (ResXmlElement child:filter.listElements(type)) {
            ResXmlAttribute attribute=child.searchAttribute(NS,"name");
            if (attribute!=null && value.equals(attribute.getValueString())) return true;
        }
        return false;
    }
}
