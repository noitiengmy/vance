package com.maico.refurbished.patcher;

import android.R;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Initial binary patcher for a normal non-split APK. Does not touch old DEX.
 * Return an error rather than silently choosing between several launcher icons.
 */
public final class ApkPatcher {
    public static final String ALIAS="ipa.maico.furbish.Alias";
    public static final String DIALOG="ipa.maico.furbish.ChangeDisplayActivity";
    public static final String VANCE="ipa.maico.vance.VanceActivity";
    private static final String LEGACY_ALIAS="ipa.maico.aliaswi.Alias";
    private static final String LEGACY_DIALOG="ipa.maico.aliaswi.ChangeDisplayActivity";
    private static final String LEGACY_RECEIVER="ipa.maico.aliaswi.AliasRefreshReceiver";
    private static final String MAIN="android.intent.action.MAIN";
    private static final String LAUNCHER="android.intent.category.LAUNCHER";
    private static final String SHORTCUT_META="android.app.shortcuts";
    private static final String NS="http://schemas.android.com/apk/res/android";
    private static final Pattern DEX=Pattern.compile("classes([0-9]*)\\.dex");

    public static final class Result {
        public final String packageName, launcherClass, addedDex;
        public final List<String> warnings;
        Result(String pkg,String launcher,String dex,List<String> notes) {
            packageName=pkg; launcherClass=launcher; addedDex=dex;
            warnings=Collections.unmodifiableList(new ArrayList<>(notes));
        }
    }
    public Result patch(PatchRequest r) throws Exception {
        if (!r.baseApk.isFile() || !r.moduleDex.isFile()) throw new IOException("Missing APK or DEX");
        if (r.unsignedApk.exists() || r.outputApk.exists())
            throw new IOException("Output exists: refusing overwrite");
        List<String> warnings=new ArrayList<>();
        String pkg,launcher,dexName;
        try(ApkModule apk=ApkModule.loadApkFile(r.baseApk)) {
            AndroidManifestBlock manifest=apk.getAndroidManifest();
            if (manifest==null || manifest.isSplit()) throw new IOException("Only standalone base APKs supported in preview");
            pkg=manifest.getPackageName();
            if (pkg==null || pkg.isEmpty()) throw new IOException("Missing package name");
            // APKs with no compiled resources can legitimately omit resources.arsc.
            // The preview's own APK is an example (its UI is built in Java).
            TableBlock table=apk.getTableBlock(false);
            if (table==null) {
                apk.ensureTableBlock();
                table=apk.getTableBlock(false);
                if (table==null) throw new IOException("Failed to create resources.arsc");
                warnings.add("Input has no resources.arsc; created a new resource table.");
            }
            PackageBlock resources=null;
            for(PackageBlock block:table.listPackages()) {
                if(pkg.equals(block.getName())) { resources=block; break; }
            }
            if(resources==null) {
                // Namespace and applicationId can differ; the resource package
                // may not have the same name as the manifest's package.
                int packageId=manifest.guessCurrentPackageId();
                resources=table.pickOne(packageId);
                if(resources==null) resources=table.pickOne(0x7f);
                if(resources==null) resources=table.pickOne();
                if(resources!=null)
                    warnings.add("Resource package name differs from manifest: "+
                        resources.getName()+"; using resource package ID "+
                        Integer.toHexString(resources.getId()));
            }
            if(resources==null) {
                resources=table.newPackage(0x7f,pkg);
                warnings.add("Created missing application resource package.");
            }
            ResXmlElement app=manifest.getApplicationElement();
            if(app==null) throw new IOException("No application element");
            // A previously refurbished APK is a valid input. Alias0 is the
            // preserved original display; its targetActivity is the original
            // launcher, whose MAIN/LAUNCHER filter was removed on first patch.
            ResXmlElement oldDialog=findComponent(app,DIALOG);
            if(oldDialog==null)oldDialog=findComponent(app,LEGACY_DIALOG);
            ResXmlElement preservedOriginal=null;
            if(oldDialog==null && (findComponent(app,ALIAS+"0")!=null ||
                    findComponent(app,LEGACY_ALIAS+"0")!=null))
                throw new IOException("Existing Refurbished Display aliases have no matching Change Display activity");
            if(oldDialog!=null){
                for(ResXmlElement alias:app.listElements("activity-alias")){
                    String aliasName=full(pkg,str(alias,"name"));
                    if((ALIAS+"0").equals(aliasName)||(LEGACY_ALIAS+"0").equals(aliasName)){
                        preservedOriginal=alias;
                        break;
                    }
                }
                if(preservedOriginal==null || !hasLauncherFilter(preservedOriginal)
                        || !hasShortcutMetadata(preservedOriginal))
                    throw new IOException("Existing aliaswi patch is incomplete; refusing unsafe rewrite");
            }

            List<ResXmlElement> launchers=new ArrayList<>();
            for(Iterator<ResXmlElement> it=app.getElements();it.hasNext();) {
                ResXmlElement item=it.next();
                if(("activity".equals(item.getName()) || "activity-alias".equals(item.getName()))
                    && hasLauncherFilter(item)) launchers.add(item);
            }
            if(launchers.isEmpty())
                throw new IOException("No launcher entry in base APK");
            ResXmlElement original=null;
            if(preservedOriginal!=null){
                original=preservedOriginal;
                if(r.launcherComponent!=null && !r.launcherComponent.isEmpty()
                        && !isGeneratedAlias(r.launcherComponent))
                    warnings.add("Existing Refurbished alias group was updated; other original launchers were preserved.");
            }else if(r.launcherComponent!=null && !r.launcherComponent.isEmpty()){
                for(ResXmlElement candidate:launchers)
                    if(r.launcherComponent.equals(full(pkg,str(candidate,"name")))){
                        original=candidate;break;
                    }
                if(original==null)
                    throw new IOException("Selected launcher not found: "+r.launcherComponent);
            }else if(launchers.size()==1){
                original=launchers.get(0);
            }else{
                throw new IOException("Found "+launchers.size()+
                        " launchers; select one to customize in Refurbished.");
            }
            String originalName=full(pkg,str(original,"name"));
            if(originalName==null) throw new IOException("No launcher component class");
            boolean isAlias="activity-alias".equals(original.getName());
            launcher=isAlias ? full(pkg,str(original,"targetActivity")) : originalName;
            if(launcher==null || findActivity(app,launcher,pkg)==null)
                throw new IOException("Cannot resolve target Activity: "+launcher);

            ResXmlAttribute oldIcon=attr(original,"icon");
            if(oldIcon==null) oldIcon=attr(app,"icon");
            ResXmlAttribute oldLabel=attr(original,"label");
            if(oldLabel==null) oldLabel=attr(app,"label");
            final String originalLiteralLabel=oldLabel!=null &&
                    oldLabel.getValueType()!=ValueType.REFERENCE
                    ?oldLabel.getValueString():null;
            // Use a package-specific, collision-free resource namespace.
            // Never overwrite resource entries or files belonging to the APK.
            // Reuse our previous resource namespace when re-refurbishing.
            // This avoids creating a growing pile of unused style assets.
            String priorShortcutsPath=preservedOriginal==null?null:
                    shortcutResourcePath(apk,resources,preservedOriginal);
            String priorPrefix=resourcePrefixFromPath(priorShortcutsPath);
            if(preservedOriginal!=null && priorPrefix==null)
                throw new IOException("Existing aliaswi shortcut namespace cannot be verified");
            String resourcePrefix=priorPrefix!=null?priorPrefix:
                    uniquePrefix(resources,apk,pkg,r.names.length);
            int count=r.names.length;
            int[] labels=new int[count+1],icons=new int[count+1];
            labels[0]=oldLabel!=null && oldLabel.getValueType()==ValueType.REFERENCE
                    ? oldLabel.getData() : putString(resources,resourcePrefix+"_name0",
                    oldLabel!=null ? oldLabel.getValueString() : "Original");
            if(oldIcon!=null && oldIcon.getValueType()==ValueType.REFERENCE)
                icons[0]=oldIcon.getData();
            if(icons[0]==0) warnings.add("Original icon missing/non-reference; alias 0 will inherit app icon");

            // Static resources only for supplied name/icon pairs.
            // Do not overwrite alias 0's original label when it is a literal.
            for(int i=1;i<=count;i++) {
                byte[] normalizedPng = IconNormalizer.normalize(r.pngs[i-1], i);
                labels[i]=putString(resources,resourcePrefix+"_name"+i,r.names[i-1]);
                String foregroundPath="res/drawable-nodpi/"+resourcePrefix+"_foreground"+i+".png";
                int foreground=putFile(resources,"drawable",resourcePrefix+"_foreground"+i,foregroundPath);
                replaceGeneratedFile(apk,foregroundPath,normalizedPng,priorPrefix!=null);
                String wrapperPath="res/mipmap-anydpi-v26/"+resourcePrefix+"_icon"+i+".xml";
                icons[i]=putFile(resources,"mipmap",resourcePrefix+"_icon"+i,wrapperPath);
                replaceGeneratedFile(apk,wrapperPath,
                        adaptiveXml(foreground,r.backgrounds[i-1]),priorPrefix!=null);
            }
            int shortcutLabel=putString(resources,resourcePrefix+"_shortcut_name","Vance");
            // Own immutable visual identity for Change Display, independent
            // of Alias0 and user styles. Reuse the same resource on re-patch.
            String shortcutIconPath="res/drawable-nodpi/"+
                    resourcePrefix+"_shortcut_icon.png";
            int shortcutIcon=putFile(resources,"drawable",
                    resourcePrefix+"_shortcut_icon",shortcutIconPath);
            replaceGeneratedFile(apk,shortcutIconPath,r.shortcutIcon==null?ShortcutIcon.png():r.shortcutIcon,
                    priorPrefix!=null);
            String shortcutsPath="res/xml/"+resourcePrefix+"_shortcuts.xml";
            int shortcutsId=putFile(resources,"xml",resourcePrefix+"_shortcuts",shortcutsPath);
            ResXmlDocument shortcuts=readShortcuts(apk,resources,original,warnings);
            if(preservedOriginal!=null)removePreviousShortcut(shortcuts);
            putShortcut(shortcuts,pkg,shortcutLabel,shortcutIcon,resourcePrefix+"_vance");
            replaceGeneratedFile(apk,shortcutsPath,
                    serializeBinaryXml(shortcuts,"static shortcuts"),priorPrefix!=null);

            if(preservedOriginal!=null){
                // Remove only aliaswi-owned components. Other app aliases and
                // their launcher shortcuts are not ours to alter.
                app.removeElementsIf(component->
                        ("activity-alias".equals(component.getName()) &&
                                isGeneratedAlias(full(pkg,str(component,"name")))) ||
                        ("activity".equals(component.getName()) &&
                                (DIALOG.equals(full(pkg,str(component,"name"))) ||
                                 LEGACY_DIALOG.equals(full(pkg,str(component,"name"))) ||
                                 VANCE.equals(full(pkg,str(component,"name"))))) ||
                        ("receiver".equals(component.getName()) &&
                                ("ipa.maico.furbish.AliasRefreshReceiver".equals(
                                        full(pkg,str(component,"name"))) ||
                                 LEGACY_RECEIVER.equals(full(pkg,str(component,"name"))))));
                warnings.add("Updated existing Refurbished aliases and Change Display module.");
            }else{
                // First patch: move MAIN/LAUNCHER only from selected launcher.
                for(ResXmlElement filter:new ArrayList<>(original.listElements("intent-filter")))
                    if(isLauncherFilter(filter))original.removeElementsIf(el->el==filter);
            }

            for(int i=0;i<=count;i++) {
                ResXmlElement alias=app.newElement("activity-alias");
                setString(alias,"name",ALIAS+i);
                setString(alias,"targetActivity",launcher);
                setBool(alias,"exported",true);
                setBool(alias,"enabled",i==0);
                if(i==0 && originalLiteralLabel!=null)
                    setString(alias,"label",originalLiteralLabel);
                else setRef(alias,"label",labels[i]);
                if(icons[i]!=0) setRef(alias,"icon",icons[i]);
                ResXmlElement intent=alias.newElement("intent-filter");
                setString(intent.newElement("action"),"name",MAIN);
                setString(intent.newElement("category"),"name",LAUNCHER);
                ResXmlElement meta=alias.newElement("meta-data");
                setString(meta,"name",SHORTCUT_META);
                setRef(meta,"resource",shortcutsId);
            }
            ResXmlElement receiver=app.newElement("receiver");
            setString(receiver,"name","ipa.maico.furbish.AliasRefreshReceiver");
            setBool(receiver,"exported",false);
            setBool(receiver,"enabled",true);
            ResXmlElement receiverIntent=receiver.newElement("intent-filter");
            setString(receiverIntent.newElement("action"),"name",
                    "android.intent.action.MY_PACKAGE_REPLACED");
            ResXmlElement vance=app.newElement("activity");
            setString(vance,"name",VANCE);
            setString(vance,"process",":vance");
            setString(vance,"taskAffinity","");
            setBool(vance,"exported",true);
            setRef(vance,"theme",R.style.Theme_Material_Light_NoActionBar);
            ResXmlElement dialog=app.newElement("activity");
            setString(dialog,"name",DIALOG);
            setString(dialog,"process",":vance");
            // Static shortcut intents use CLEAR_TASK. A dedicated affinity keeps
            // opening Change Display from clearing the original app task.
            setString(dialog,"taskAffinity","");
            setBool(dialog,"exported",true);
            setRef(dialog,"theme",R.style.Theme_Material_Light_Dialog_Alert);
            if(preservedOriginal!=null){
                // Do not add a second definition of ChangeDisplayActivity.
                dexName=findInjectedDex(apk);
                apk.removeInputSource(dexName);
                add(apk,dexName,readBytes(r.moduleDex));
            }else{
                dexName=nextDex(apk);
                add(apk,dexName,readBytes(r.moduleDex));
            }
            apk.setApkSignatureBlock(null);
            // Remove v1 entries belonging to the original signer.
            for(InputSource source:new ArrayList<>(apk.listInputSources())) {
                String name=source.getAlias();
                if(name!=null && name.matches("(?i)META-INF/[^/]+\\.(RSA|DSA|EC|SF|MF)"))
                    apk.removeInputSource(name);
            }
            apk.writeApk(r.unsignedApk);
        }
        try {
            r.signingKey.sign(r.unsignedApk,r.outputApk);
            PatchedApkVerifier.verify(r.outputApk,pkg,dexName,r.names.length);
        }
        catch(Exception e) { r.outputApk.delete(); throw e; }
        finally { r.unsignedApk.delete(); }
        return new Result(pkg,launcher,dexName,warnings);
    }

    private static boolean isGeneratedAlias(String name){
        if(name==null)return false;
        String prefix=name.startsWith(ALIAS)?ALIAS:
                name.startsWith(LEGACY_ALIAS)?LEGACY_ALIAS:null;
        if(prefix==null)return false;
        String suffix=name.substring(prefix.length());
        if(suffix.isEmpty())return false;
        for(int i=0;i<suffix.length();i++)
            if(suffix.charAt(i)<'0'||suffix.charAt(i)>'9')return false;
        try{return Integer.parseInt(suffix)>=0;}catch(NumberFormatException e){return false;}
    }
    private static boolean hasShortcutMetadata(ResXmlElement alias){
        for(ResXmlElement meta:alias.listElements("meta-data"))
            if(SHORTCUT_META.equals(str(meta,"name")))return true;
        return false;
    }
    private static String shortcutResourcePath(ApkModule apk,PackageBlock block,
                                               ResXmlElement alias) throws IOException{
        for(ResXmlElement meta:alias.listElements("meta-data")){
            if(!SHORTCUT_META.equals(str(meta,"name")))continue;
            ResXmlAttribute attr=attr(meta,"resource");
            if(attr==null||attr.getValueType()!=ValueType.REFERENCE)
                throw new IOException("Existing shortcut resource reference is invalid");
            for(Iterator<Entry> it=block.getEntries(attr.getData());it.hasNext();){
                Entry entry=it.next();
                if(entry==null||!entry.isDefined())continue;
                String path=entry.getValueAsString();
                if(path!=null && apk.containsFile(path))return path;
            }
        }
        throw new IOException("Existing Change Display shortcuts XML was not found");
    }
    private static String resourcePrefixFromPath(String path){
        if(path==null||!path.startsWith("res/xml/asw")||
                !path.endsWith("_shortcuts.xml"))return null;
        String prefix=path.substring("res/xml/".length(),
                path.length()-"_shortcuts.xml".length());
        for(int i=0;i<prefix.length();i++){
            char c=prefix.charAt(i);
            if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')
                    ||(c>='0'&&c<='9')||c=='_'))return null;
        }
        return prefix;
    }
    private static void removePreviousShortcut(ResXmlDocument xml){
        ResXmlElement root=xml.getDocumentElement();
        if(root==null||!root.equalsName("shortcuts"))
            throw new IllegalArgumentException("Existing shortcuts root is invalid");
        for(ResXmlElement shortcut:new ArrayList<>(root.listElements("shortcut"))){
            boolean ours=false;
            for(ResXmlElement intent:shortcut.listElements("intent"))
                if(DIALOG.equals(str(intent,"targetClass")) ||
                        LEGACY_DIALOG.equals(str(intent,"targetClass")) ||
                        VANCE.equals(str(intent,"targetClass")))ours=true;
            if(ours)root.removeElementsIf(element->element==shortcut);
        }
    }
    private static void replaceGeneratedFile(ApkModule apk,String path,byte[] data,
                                              boolean update) throws IOException{
        if(update && apk.containsFile(path))apk.removeInputSource(path);
        add(apk,path,data);
    }
    private static String findInjectedDex(ApkModule apk) throws IOException{
        final byte[][] descriptors={
                "Lipa/maico/aliaswi/ChangeDisplayActivity;".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "Lipa/maico/furbish/ChangeDisplayActivity;".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "Lipa/maico/vance/VanceActivity;".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        };
        String result=null;
        for(InputSource input:apk.listInputSources()){
            String path=input.getAlias();
            if(path==null||!DEX.matcher(path).matches())continue;
            boolean found=false;
            try(InputStream in=new BufferedInputStream(input.openStream(),32768)){
                byte[] window=new byte[65536];int carry=0,n;
                while((n=in.read(window,carry,window.length-carry))!=-1){
                    int length=carry+n;
                    for(byte[] descriptor:descriptors){
                        outer:for(int i=0;i<=length-descriptor.length;i++){
                            for(int j=0;j<descriptor.length;j++)
                                if(window[i+j]!=descriptor[j])continue outer;
                            found=true;break;
                        }
                        if(found)break;
                    }
                    if(found)break;
                    carry=Math.min(64,length);
                    System.arraycopy(window,length-carry,window,0,carry);
                }
            }
            if(found){
                if(result!=null)throw new IOException(
                        "Multiple aliaswi DEX definitions found; refusing unsafe replacement");
                result=path;
            }
        }
        if(result==null)throw new IOException(
                "Existing Refurbished Display/Vance DEX was not found; refusing duplicate injection");
        return result;
    }

    private static int androidAttr(String name) {
        try { return R.attr.class.getField(name).getInt(null); }
        catch(Exception e) { throw new IllegalStateException("Framework attribute missing: "+name,e); }
    }
    private static ResXmlAttribute attr(ResXmlElement e,String n) {
        return e==null?null:e.searchAttribute(NS,n);
    }
    private static String str(ResXmlElement e,String n) {
        ResXmlAttribute a=attr(e,n);return a==null?null:a.getValueString();
    }
    private static void setString(ResXmlElement e,String n,String v) {
        e.getOrCreateAndroidAttribute(n,androidAttr(n)).setValueAsString(v);
    }
    private static void setRef(ResXmlElement e,String n,int v) {
        ResXmlAttribute attribute=e.getOrCreateAndroidAttribute(n,androidAttr(n));
        attribute.setValueType(ValueType.REFERENCE);
        attribute.setData(v);
    }
    private static void setBool(ResXmlElement e,String n,boolean v) {
        e.getOrCreateAndroidAttribute(n,androidAttr(n)).setValueAsBoolean(v);
    }
    private static String full(String pkg,String name) {
        if(name==null || name.isEmpty()) return null;
        if(name.charAt(0)=='.') return pkg+name;
        return name.contains(".")?name:pkg+"."+name;
    }
    private static ResXmlElement findComponent(ResXmlElement app,String name) {
        for(Iterator<ResXmlElement> it=app.getElements();it.hasNext();) {
            ResXmlElement e=it.next();
            if(name.equals(str(e,"name"))) return e;
        }
        return null;
    }
    private static ResXmlElement findActivity(ResXmlElement app,String name,String pkg) {
        for(ResXmlElement e:app.listElements("activity"))
            if(name.equals(full(pkg,str(e,"name"))))return e;
        return null;
    }
    private static boolean hasLauncherFilter(ResXmlElement component) {
        for(ResXmlElement filter:component.listElements("intent-filter"))
            if(isLauncherFilter(filter))return true;
        return false;
    }
    private static boolean isLauncherFilter(ResXmlElement filter) {
        boolean main=false,launcher=false;
        for(ResXmlElement a:filter.listElements("action")) if(MAIN.equals(str(a,"name")))main=true;
        for(ResXmlElement c:filter.listElements("category")) if(LAUNCHER.equals(str(c,"name")))launcher=true;
        return main && launcher;
    }
    private static String uniquePrefix(PackageBlock block,ApkModule apk,String pkg,int styleCount) {
        String base="asw"+Integer.toHexString(pkg.hashCode());
        for(int i=0;i<1000;i++){
            String p=i==0?base:base+"_"+i;
            boolean collision=block.getEntry("string",p+"_name0")!=null
                    ||block.getEntry("string",p+"_name1")!=null
                    ||block.getEntry("string",p+"_name2")!=null
                    ||block.getEntry("string",p+"_name3")!=null
                    ||block.getEntry("string",p+"_shortcut_name")!=null
                    ||block.getEntry("drawable",p+"_shortcut_icon")!=null
                    ||apk.containsFile("res/drawable-nodpi/"+p+"_shortcut_icon.png")
                    ||block.getEntry("xml",p+"_shortcuts")!=null
                    ||apk.containsFile("res/xml/"+p+"_shortcuts.xml");
            for(int j=1;j<=styleCount;j++){
                collision|=block.getEntry("drawable",p+"_foreground"+j)!=null
                        ||block.getEntry("mipmap",p+"_icon"+j)!=null
                        ||apk.containsFile("res/drawable-nodpi/"+p+"_foreground"+j+".png")
                        ||apk.containsFile("res/mipmap-anydpi-v26/"+p+"_icon"+j+".xml");
            }
            if(!collision)return p;
        }
        throw new IllegalStateException("No collision-free resource namespace available");
    }

    private static int putString(PackageBlock block,String name,String value) {
        Entry e=block.getOrCreate("","string",name);e.setValueAsString(value);return e.getResourceId();
    }
    private static int putFile(PackageBlock block,String type,String name,String path) {
        Entry e=block.getOrCreate("",type,name);e.setValueAsString(path);return e.getResourceId();
    }
    private static byte[] adaptiveXml(int foreground,int background) throws IOException {
        ResXmlDocument doc=new ResXmlDocument();
        ResXmlElement root=doc.newElement("adaptive-icon");
        setRef(root.newElement("background"),"drawable",
                background==1?R.color.transparent:R.color.white);
        setRef(root.newElement("foreground"),"drawable",foreground);
        return serializeBinaryXml(doc,"adaptive icon");
    }
    /**
     * ARSCLib 1.4.0 does not automatically refresh the chunk headers when a
     * ResXmlDocument is constructed programmatically. Calling getBytes()
     * directly writes a nominal XML type 0x0003 but headerSize/chunkSize=0:
     * Android cannot parse it and silently ignores manifest shortcuts/icons.
     */
    private static byte[] serializeBinaryXml(ResXmlDocument doc,String description)
            throws IOException {
        doc.refreshFull();
        byte[] xml=doc.getBytes();
        if(xml.length<8 || (xml[0]&255)!=3 || xml[1]!=0
                || (xml[2]&255)!=8 || xml[3]!=0) {
            throw new IOException("Invalid binary XML header for "+description);
        }
        int declared=(xml[4]&255) | ((xml[5]&255)<<8)
                | ((xml[6]&255)<<16) | ((xml[7]&255)<<24);
        if(declared!=xml.length)
            throw new IOException("Invalid binary XML length for "+description+
                    ": expected "+declared+", actual "+xml.length);
        return xml;
    }
    private static ResXmlDocument readShortcuts(ApkModule apk,PackageBlock block,
        ResXmlElement launcher,List<String> warnings) throws IOException {
        ResXmlDocument doc=new ResXmlDocument();
        for(ResXmlElement meta:launcher.listElements("meta-data")) {
            if(!SHORTCUT_META.equals(str(meta,"name")))continue;
            ResXmlAttribute a=attr(meta,"resource");
            if(a==null || a.getValueType()!=ValueType.REFERENCE)
                throw new IOException("Original shortcuts reference invalid");
            Entry ref=null;
            for(Iterator<Entry> it=block.getEntries(a.getData());it.hasNext();) {
                Entry e=it.next();
                if(e!=null && e.isDefined() && e.getValueAsString()!=null){ref=e;break;}
            }
            if(ref==null) throw new IOException("Original shortcuts resource missing");
            InputSource source=apk.getInputSource(ref.getValueAsString());
            if(source==null) throw new IOException("Original shortcuts XML missing");
            try(InputStream in=source.openStream()){doc.readBytes(in);}
            warnings.add("Static shortcuts copied; launcher limits still apply.");
            return doc;
        }
        doc.newElement("shortcuts");
        return doc;
    }
    private static void putShortcut(ResXmlDocument doc,String pkg,int name,int icon,String shortcutId) {
        ResXmlElement root=doc.getDocumentElement();
        if(root==null || !root.equalsName("shortcuts"))
            throw new IllegalArgumentException("Unexpected shortcuts XML");
        for(ResXmlElement existing:root.listElements("shortcut"))
            if(shortcutId.equals(str(existing,"shortcutId")))
                throw new IllegalArgumentException("Static shortcut ID collision");
        ResXmlElement item=root.newElementAt(0,"shortcut");
        setString(item,"shortcutId",shortcutId);
        setBool(item,"enabled",true);
        // android:rank is optional in the static-shortcut schema. Omitting it
        // gives this shortcut the default rank 0, while avoiding reliance on
        // android.R.attr.rank (not exposed by every compile SDK).
        setRef(item,"shortcutShortLabel",name);
        if(icon!=0)setRef(item,"icon",icon);
        ResXmlElement intent=item.newElement("intent");
        setString(intent,"action","android.intent.action.VIEW");
        setString(intent,"targetPackage",pkg);
        setString(intent,"targetClass",VANCE);
    }
    private static String nextDex(ApkModule apk) throws IOException {
        int largest=0;
        for(InputSource entry:apk.listInputSources()) {
            String path=entry.getAlias();
            if(path==null || path.contains("/"))continue;
            Matcher m=DEX.matcher(path);
            if(m.matches())largest=Math.max(largest,m.group(1).isEmpty()?1:Integer.parseInt(m.group(1)));
        }
        if(largest==0)throw new IOException("Missing classes.dex");
        return "classes"+(largest+1)+".dex";
    }
    private static void add(ApkModule apk,String name,byte[] data) throws IOException {
        if(apk.containsFile(name))throw new IOException("Collision: "+name);
        apk.add(new ByteInputSource(data,name));
    }
    private static byte[] readBytes(File file) throws IOException {
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();
        }
    }
}
