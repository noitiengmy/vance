package com.maico.refurbished.patcher;

import com.android.apksig.ApkVerifier;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Verify the signed result rather than assuming a successful ZIP write is enough. */
final class PatchedApkVerifier {
    private static final String NS="http://schemas.android.com/apk/res/android";
    private PatchedApkVerifier(){}

    static void verify(File file,String expectedPackage,String injectedDex,int styleCount) throws Exception {
        if(styleCount<1)throw new IOException("At least one display style is required");
        if(!file.isFile()||file.length()<1024)
            throw new IOException("Signed APK is missing or unusually small");
        ApkVerifier.Result check=new ApkVerifier.Builder(file).build().verify();
        if(!check.isVerified()) {
            StringBuilder detail=new StringBuilder();
            detail.append("APK signature verification failed: errors=")
                    .append(check.getErrors())
                    .append(", warnings=").append(check.getWarnings())
                    .append(", v1Signers=").append(check.getV1SchemeSigners().size())
                    .append(", v2Signers=").append(check.getV2SchemeSigners().size())
                    .append(", v3Signers=").append(check.getV3SchemeSigners().size())
                    .append(", v4Signers=").append(check.getV4SchemeSigners().size())
                    .append(", verifiedV1=").append(check.isVerifiedUsingV1Scheme())
                    .append(", verifiedV2=").append(check.isVerifiedUsingV2Scheme())
                    .append(", verifiedV3=").append(check.isVerifiedUsingV3Scheme())
                    .append(", verifiedV4=").append(check.isVerifiedUsingV4Scheme())
                    .append(", fileLength=").append(file.length());
            for (int i=0;i<check.getV1SchemeSigners().size();i++) {
                ApkVerifier.Result.V1SchemeSignerInfo signer=check.getV1SchemeSigners().get(i);
                detail.append(", v1Signer[").append(i).append("]Errors=")
                        .append(signer.getErrors())
                        .append(",Warnings=").append(signer.getWarnings());
            }
            for (int i=0;i<check.getV2SchemeSigners().size();i++) {
                ApkVerifier.Result.V2SchemeSignerInfo signer=check.getV2SchemeSigners().get(i);
                detail.append(", v2Signer[").append(i).append("]Errors=")
                        .append(signer.getErrors())
                        .append(",Warnings=").append(signer.getWarnings());
            }
            for (int i=0;i<check.getV3SchemeSigners().size();i++) {
                ApkVerifier.Result.V3SchemeSignerInfo signer=check.getV3SchemeSigners().get(i);
                detail.append(", v3Signer[").append(i).append("]Errors=")
                        .append(signer.getErrors())
                        .append(",Warnings=").append(signer.getWarnings());
            }
            throw new IOException(detail.toString());
        }

        try(ApkModule apk=ApkModule.loadApkFile(file)){
            AndroidManifestBlock manifest=apk.getAndroidManifest();
            if(manifest==null || !expectedPackage.equals(manifest.getPackageName()))
                throw new IOException("Patched package identity changed");
            if(apk.getInputSource(injectedDex)==null)
                throw new IOException("Injected DEX missing: "+injectedDex);
            ResXmlElement app=manifest.getApplicationElement();
            if(app==null)throw new IOException("Patched manifest has no application");
            Set<String> aliases=new HashSet<>();
            int shortcutId=0;
            int enabledByDefault=0;
            for(ResXmlElement alias:app.listElements("activity-alias")){
                String name=value(alias,"name");
                if(name==null||!name.startsWith(ApkPatcher.ALIAS))continue;
                aliases.add(name);
                if(!booleanValue(alias,"exported"))
                    throw new IOException("Launcher alias is not exported: "+name);
                if(booleanValue(alias,"enabled"))enabledByDefault++;
                boolean hasLauncher=false;
                for(ResXmlElement filter:alias.listElements("intent-filter")){
                    boolean main=false,launcher=false;
                    for(ResXmlElement action:filter.listElements("action"))
                        main|="android.intent.action.MAIN".equals(value(action,"name"));
                    for(ResXmlElement category:filter.listElements("category"))
                        launcher|="android.intent.category.LAUNCHER".equals(value(category,"name"));
                    if(main&&launcher)hasLauncher=true;
                }
                if(!hasLauncher)throw new IOException("Missing launcher filter: "+name);
                boolean hasMetadata=false;
                for(ResXmlElement meta:alias.listElements("meta-data")){
                    if(!"android.app.shortcuts".equals(value(meta,"name")))continue;
                    ResXmlAttribute ref=meta.searchAttribute(NS,"resource");
                    if(ref==null||ref.getValueType()!=ValueType.REFERENCE||ref.getData()==0)
                        throw new IOException("Shortcut metadata resource invalid: "+name);
                    if(shortcutId!=0&&shortcutId!=ref.getData())
                        throw new IOException("Launcher aliases have different shortcut XML references");
                    shortcutId=ref.getData();
                    hasMetadata=true;
                }
                if(!hasMetadata)
                    throw new IOException("Change Display shortcut metadata missing on alias: "+name);
            }
            for(int i=0;i<=styleCount;i++)
                if(!aliases.contains(ApkPatcher.ALIAS+i))
                    throw new IOException("Required launcher alias missing: "+i);
            if(aliases.size()!=styleCount+1)
                throw new IOException("Incorrect number of launcher aliases: "+aliases.size());
            if(enabledByDefault!=1)
                throw new IOException("Expected one default enabled alias, found: "+enabledByDefault);
            int dialogCount=0;
            for(ResXmlElement activity:app.listElements("activity"))
                if(ApkPatcher.DIALOG.equals(value(activity,"name")))dialogCount++;
            if(dialogCount!=1)throw new IOException(
                    "Expected one Change Display activity, found: "+dialogCount);
            int vanceCount=0;
            for(ResXmlElement activity:app.listElements("activity"))
                if(ApkPatcher.VANCE.equals(value(activity,"name")))vanceCount++;
            if(vanceCount!=1)throw new IOException("Expected one Vance dispatcher activity, found: "+vanceCount);
            for(ResXmlElement activity:app.listElements("activity")){
                if(ApkPatcher.VANCE.equals(value(activity,"name")) ||
                        ApkPatcher.DIALOG.equals(value(activity,"name"))){
                    if(!":vance".equals(value(activity,"process")))
                        throw new IOException("Vance components must run in :vance process");
                }
            }
            int refreshReceiverCount=0;
            for(ResXmlElement receiver:app.listElements("receiver"))
                if("ipa.maico.furbish.AliasRefreshReceiver".equals(
                        value(receiver,"name")))refreshReceiverCount++;
            if(refreshReceiverCount!=1)throw new IOException(
                    "Expected one alias update receiver, found: "+refreshReceiverCount);
            boolean foundShortcut=false;
            for(InputSource source:apk.listInputSources()){
                String name=source.getAlias();
                if(name!=null && name.startsWith("res/xml/asw")
                        && name.endsWith("_shortcuts.xml")){
                    try(InputStream in=source.openStream()){
                        ResXmlDocument xml=new ResXmlDocument();
                        xml.readBytes(in);
                        ResXmlElement root=xml.getDocumentElement();
                        if(root==null||!"shortcuts".equals(root.getName()))
                            throw new IOException("Static shortcut XML root is invalid");
                        boolean containsVance=false;
                        for(ResXmlElement shortcut:root.listElements("shortcut")){
                            String id=value(shortcut,"shortcutId");
                            if(id==null||(!id.endsWith("_change_display")&&!id.endsWith("_vance")))continue;
                            for(ResXmlElement intent:shortcut.listElements("intent")){
                                if(ApkPatcher.VANCE.equals(value(intent,"targetClass"))
                                        && expectedPackage.equals(value(intent,"targetPackage"))){
                                    // An omitted static-shortcut rank has Android's
                                    // default value 0. Vance is inserted at index 0
                                    // in the XML, so accept that equivalent encoding.
                                    ResXmlAttribute rank=shortcut.searchAttribute(NS,"rank");
                                    if(rank!=null && rank.getData()!=0)
                                        throw new IOException("Vance must have the highest static shortcut rank");
                                    ResXmlAttribute icon=shortcut.searchAttribute(NS,"icon");
                                    if(icon==null||icon.getValueType()!=ValueType.REFERENCE
                                            ||icon.getData()==0)
                                        throw new IOException("Vance has no dedicated shortcut icon");
                                    String iconPath=resourceFile(apk,icon.getData());
                                    if(iconPath==null||
                                            !(iconPath.startsWith("res/drawable-nodpi/asw")
                                            &&iconPath.endsWith("_shortcut_icon.png")))
                                        throw new IOException("Vance uses unexpected icon: "+iconPath);
                                    InputSource iconSource=apk.getInputSource(iconPath);
                                    if(iconSource==null)
                                        throw new IOException("Change Display icon resource is missing");
                                    try(InputStream iconStream=iconSource.openStream()){
                                        byte[] png=new byte[8];
                                        if(iconStream.read(png)!=8 || (png[0]&255)!=137
                                                ||png[1]!=80||png[2]!=78||png[3]!=71)
                                            throw new IOException("Change Display icon PNG is invalid");
                                    }
                                    containsVance=true;
                                }
                            }
                        }
                        if(!containsVance)
                            throw new IOException("Vance static shortcut or intent is absent");
                    }
                    foundShortcut=true;
                }
            }
            if(!foundShortcut)throw new IOException("Static shortcut resource missing");
        }
    }
    private static String resourceFile(ApkModule apk,int resourceId){
        TableBlock table=apk.getTableBlock(false);
        if(table==null)return null;
        for(PackageBlock block:table.listPackages()){
            for(java.util.Iterator<Entry> it=block.getEntries(resourceId);it.hasNext();){
                Entry entry=it.next();
                if(entry!=null && entry.isDefined() && entry.getValueAsString()!=null)
                    return entry.getValueAsString();
            }
        }
        return null;
    }
    private static boolean booleanValue(ResXmlElement e,String name) {
        ResXmlAttribute a=e.searchAttribute(NS,name);
        return a!=null && a.getValueAsBoolean();
    }
    private static String value(ResXmlElement e,String name){
        ResXmlAttribute a=e.searchAttribute(NS,name);
        return a==null?null:a.getValueString();
    }
}
