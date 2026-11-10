package com.maico.refurbished.patcher;

import com.maico.refurbished.patcher.ApkInspector;
import com.maico.refurbished.patcher.ApkPatcher;
import com.maico.refurbished.patcher.PatchRequest;
import com.maico.refurbished.patcher.SigningKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * One pipeline for single APKs, installed split packages, APKS and XAPK archives.
 * Never edits input APKs. Output is one signed APK or a zip of consistently signed APKs.
 */
public final class PackageSets {
    private static final long MAX_ENTRY_BYTES=1024L*1024L*1024L; // 1 GiB per APK
    private static final long MAX_TOTAL_BYTES=2L*1024L*1024L*1024L;
    private static final int MAX_APKS=100;

    public static final class Stage implements AutoCloseable {
        public final File dir;
        public final ArrayList<File> apks=new ArrayList<>();
        final ArrayList<String> entryNames=new ArrayList<>();
        public final ArrayList<Boolean> isBase=new ArrayList<>();
        File base, universal;
        String universalPath;
        public String packageName;
        String versionName;
        long versionCode;
        int launcherEntries;
        public String[] launcherComponents=new String[0];
        boolean archived;
        public boolean containsObb;
        boolean xapk;
        boolean apkm;
        String importedExtension;
        File originalArchive;

        Stage(File dir){this.dir=dir;}
        public boolean multi(){return apks.size()>1;}
        public boolean canBuildSingle(){return base!=null || universal!=null;}
        public String format(){return xapk?"XAPK":apkm?"APKM":multi()?"APKS":"APK";}
        public String extension(){return xapk?".xapk":apkm?".apkm":multi()||".apks".equals(importedExtension)?".apks":".apk";}
        public String describe(){
            return "Package: "+packageName+"\nVersion: "+versionName+" ("+versionCode+")"+
                    "\nAPK modules: "+apks.size()+" ("+(multi()?"split package":"standalone")+")"+
                    (containsObb?"\nOBB detected (preserved in XAPK output)":"");
        }
    private void addFile(File source,String entryName) throws IOException {
        if(apks.size()>=MAX_APKS)throw new IOException("Too many APK modules (limit 100)");
        File target=new File(dir,"input_"+apks.size()+".apk");
        try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)){
            copyLimited(in,out,MAX_ENTRY_BYTES);
        }
        apks.add(target);
        entryNames.add(entryName);
    }

    private void readArchive(File archive) throws IOException {
        originalArchive=archive;
        try(ZipFile zip=new ZipFile(archive)){
            String archiveName=archive.getName().toLowerCase(Locale.ROOT);
            if(archiveName.endsWith(".apks")){
                xapk=false;apkm=false;
            }else if(archiveName.endsWith(".xapk")){
                xapk=true;apkm=false;
            }else if(archiveName.endsWith(".apkm")){
                xapk=false;apkm=true;
            }else{
                xapk=zip.getEntry("manifest.json")!=null;
                apkm=!xapk && zip.getEntry("info.json")!=null;
            }
            boolean hasSplitDirectory=false;
            java.util.Enumeration<? extends ZipEntry> probe=zip.entries();
            while(probe.hasMoreElements()){
                String member=probe.nextElement().getName().toLowerCase(Locale.ROOT);
                if(member.startsWith("splits/") && member.endsWith(".apk")){
                    hasSplitDirectory=true;break;
                }
            }
            java.util.Enumeration<? extends ZipEntry> items=zip.entries();
            long total=0;
            Set<String> used=new HashSet<>();
            while(items.hasMoreElements()){
                ZipEntry entry=items.nextElement();
                if(entry.isDirectory())continue;
                String path=entry.getName().replace('\\','/');
                String lower=path.toLowerCase(Locale.ROOT);
                if(lower.endsWith(".obb")){containsObb=true;xapk=true;apkm=false;}
                if(!lower.endsWith(".apk"))continue;
                // Bundletool .apks may carry both split APKs and standalones.
                // They are alternatives, never one PackageInstaller session.
                if(hasSplitDirectory && (lower.startsWith("standalones/") ||
                        lower.startsWith("instant/")))continue;
                boolean universalEntry=lower.equals("universal.apk") ||
                        lower.endsWith("/universal.apk");
                if(path.startsWith("/")||path.contains("../")||path.contains("/..")
                        ||path.contains(":"))
                    throw new IOException("Unsafe archive path: "+path);
                String leaf=path.substring(path.lastIndexOf('/')+1);
                if(leaf.isEmpty())continue;
                if(!used.add(leaf.toLowerCase(Locale.ROOT)))
                    leaf="split_"+apks.size()+".apk";
                if(!universalEntry && apks.size()>=MAX_APKS)throw new IOException("Too many APKs in archive");
                File target=new File(dir,universalEntry?"universal.apk":"input_"+apks.size()+".apk");
                try(InputStream in=zip.getInputStream(entry);
                    OutputStream out=new FileOutputStream(target)){
                    long written=copyLimited(in,out,MAX_ENTRY_BYTES);
                    total+=written;
                    if(total>MAX_TOTAL_BYTES)
                        throw new IOException("Archive APK payload exceeds 2 GiB");
                }
                if(universalEntry){
                    if(universal!=null)throw new IOException("Multiple universal APKs in archive");
                    universal=target;
                    universalPath=path;
                }else{
                    apks.add(target);
                    entryNames.add(path);
                }
            }
            // A bundletool archive may include one prebuilt standalone APK
            // alongside the selected split set. It is a legitimate single-APK
            // alternative, not a merge of individual split resources.
            if(hasSplitDirectory && universal==null) {
                ZipEntry onlyStandalone=null;
                java.util.Enumeration<? extends ZipEntry> candidates=zip.entries();
                while(candidates.hasMoreElements()){
                    ZipEntry candidate=candidates.nextElement();
                    String name=candidate.getName().toLowerCase(Locale.ROOT);
                    if(candidate.isDirectory() || !name.startsWith("standalones/")
                            || !name.endsWith(".apk"))continue;
                    if(onlyStandalone!=null){onlyStandalone=null;break;}
                    onlyStandalone=candidate;
                }
                if(onlyStandalone!=null){
                    File dest=new File(dir,"standalone_candidate.apk");
                    try(InputStream in=zip.getInputStream(onlyStandalone);
                        OutputStream out=new FileOutputStream(dest)){
                        copyLimited(in,out,MAX_ENTRY_BYTES);
                    }
                    universal=dest;
                    universalPath=onlyStandalone.getName();
                }
            }
        }
        if(apks.isEmpty()){
            if(universal==null)throw new IOException("Archive contains no APK files");
            addFile(universal,universalPath);
        }
    }

    private void validate() throws Exception {
        ApkInspector inspector=new ApkInspector();
        int foundBase=0;
        for(int i=0;i<apks.size();i++){
            ApkInspector.Info info=inspector.inspect(apks.get(i));
            if(packageName==null){
                packageName=info.packageName;
                versionCode=info.versionCode;
                versionName=info.versionName;
            }else if(!packageName.equals(info.packageName)){
                throw new IOException("Archive contains different packages: "+
                        packageName+" and "+info.packageName);
            }else if(info.versionCode!=versionCode){
                throw new IOException("Split modules have different version codes");
            }
            if(!info.split){
                foundBase++;
                base=apks.get(i);
                launcherEntries=info.launcherEntries;
                launcherComponents=info.launcherComponents;
                isBase.add(Boolean.TRUE);
            }else{
                isBase.add(Boolean.FALSE);
            }
        }
        if(foundBase!=1)throw new IOException(
                "Expected exactly one base APK; found "+foundBase);
        if(universal!=null){
            ApkInspector.Info alt=inspector.inspect(universal);
            if(alt.split || !packageName.equals(alt.packageName)
                    || versionCode!=alt.versionCode || alt.launcherEntries!=1){
                universal=null; // Invalid alternative must never enable single export.
            }
        }
        if(apks.size()>1 && isBase.size()!=apks.size())
            throw new IOException("Incomplete split list");
    }

    public ApkPatcher.Result patchSingle(File moduleDex,String[] names,byte[][] images,
                        int[] backgrounds,SigningKey key,File outputFile,
                        String launcherComponent,byte[] shortcutIcon) throws Exception {
        File single=multi()?universal:base;
        if(single==null && multi()){
            // Real split -> standalone merge, using ARSCLib's resource/Dex/native
            // and manifest merge rather than exporting an incomplete base.apk.
            single=new File(dir,"merged_standalone_unsigned.apk");
            RealSplitMerger.merge(this,single);
        }
        if(single==null)
            throw new IOException("No viable standalone APK source.");
        ApkInspector.Info info=new ApkInspector().inspect(single);
        if(info.split || !packageName.equals(info.packageName)
                || info.versionCode!=versionCode || info.launcherEntries<1)
            throw new IOException("Standalone merge verification failed.");
        File unsigned=new File(dir,"single_unsigned.apk");
        File signedSingle=new File(dir,"single_signed.apk");
        ApkPatcher.Result result=new ApkPatcher().patch(new PatchRequest(single,moduleDex,
                unsigned,signedSingle,names,images,backgrounds,key,launcherComponent,shortcutIcon));
        copyFile(signedSingle,outputFile);
        return result;
    }

    public Output patch(File moduleDex,File keyFile,String[] names,byte[][] images,
                 int[] backgrounds,SigningKey key,File resultFile,
                 String launcherComponent,byte[] shortcutIcon) throws Exception {
        if(launcherEntries<1)
            throw new IOException("No launcher activity in base APK");
        File unsigned=new File(dir,"unsigned.apk");
        File signedBase=new File(dir,"signed_base.apk");
        ApkPatcher.Result result=new ApkPatcher().patch(new PatchRequest(
                base,moduleDex,unsigned,signedBase,names,images,backgrounds,key,launcherComponent,shortcutIcon));
        if(!multi() && ".apk".equals(extension())){
            copyFile(signedBase,resultFile);
            return new Output(result,".apk",1);
        }
        // All split files MUST be re-signed with the same certificate.
        ArrayList<File> signed=new ArrayList<>();
        for(int i=0;i<apks.size();i++){
            if(isBase.get(i)){signed.add(signedBase);continue;}
            File other=new File(dir,"signed_split_"+i+".apk");
            key.sign(apks.get(i),other);
            signed.add(other);
        }
        if(xapk || apkm){
            File signedUniversal=null;
            if(universal!=null && !entryNames.contains(universalPath)) {
                signedUniversal=new File(dir,"signed_universal.apk");
                patchSingle(moduleDex,names,images,backgrounds,key,signedUniversal,launcherComponent,shortcutIcon);
            }
            writeXapk(resultFile,signed,signedUniversal);
            return new Output(result,extension(),signed.size());
        }
        try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(resultFile))){
            Set<String> namesUsed=new HashSet<>();
            for(int i=0;i<signed.size();i++){
                String name=isBase.get(i)?"base.apk":"split_"+i+".apk";
                if(!namesUsed.add(name))throw new IOException("Duplicate APK entry: "+name);
                output.putNextEntry(new ZipEntry(name));
                try(InputStream in=new FileInputStream(signed.get(i))){
                    copyLimited(in,output,MAX_ENTRY_BYTES);
                }
                output.closeEntry();
            }
        }
        return new Output(result,".apks",signed.size());
    }

    private void writeXapk(File resultFile,List<File> signed,File signedUniversal) throws IOException {
        if(originalArchive==null)
            throw new IOException("Original XAPK is unavailable");
        try(ZipFile source=new ZipFile(originalArchive);
            ZipOutputStream output=new ZipOutputStream(new FileOutputStream(resultFile))){
            java.util.Enumeration<? extends ZipEntry> entries=source.entries();
            Set<String> seen=new HashSet<>();
            int count=0;
            while(entries.hasMoreElements()){
                ZipEntry original=entries.nextElement();
                String path=original.getName();
                if(!seen.add(path))throw new IOException("Duplicate archive entry: "+path);
                // APKMirror's archive-level JAR signature becomes invalid when
                // APK payload changes. Never copy stale signature metadata.
                if(apkm && path.toUpperCase(Locale.ROOT).matches(
                        "META-INF/[^/]+\\.(SF|RSA|DSA|EC|MF)"))continue;
                output.putNextEntry(new ZipEntry(path));
                if(!original.isDirectory()){
                    int index=entryNames.indexOf(path);
                    if(signedUniversal!=null && path.equals(universalPath)){
                        try(InputStream input=new FileInputStream(signedUniversal)){
                            copyLimited(input,output,MAX_ENTRY_BYTES);
                        }
                    }else if(index>=0){
                        try(InputStream input=new FileInputStream(signed.get(index))){
                            copyLimited(input,output,MAX_ENTRY_BYTES);
                        }
                        count++;
                    }else{
                        try(InputStream input=source.getInputStream(original)){
                            copyLimited(input,output,MAX_TOTAL_BYTES);
                        }
                    }
                }
                output.closeEntry();
            }
            if(count!=signed.size())
                throw new IOException("XAPK missing signed APK entries");
        }
    }

    public void exportOriginal(File resultFile) throws IOException {
        if(xapk || apkm){copyFile(originalArchive,resultFile);return;}
        if(!multi()){copyFile(base,resultFile);return;}
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(resultFile))){
            for(int i=0;i<apks.size();i++){
                out.putNextEntry(new ZipEntry(isBase.get(i)?"base.apk":"split_"+i+".apk"));
                try(InputStream in=new FileInputStream(apks.get(i))){
                    copyLimited(in,out,MAX_ENTRY_BYTES);
                }
                out.closeEntry();
            }
        }
    }


        @Override public void close(){deleteRecursively(dir);}
    }

    public static final class Output {
        public final ApkPatcher.Result result;
        public final String extension;
        public final int count;
        public Output(ApkPatcher.Result r,String extension,int n){
            result=r;this.extension=extension;count=n;
        }
    }

    public static Stage prepare(File input, File[] installedApks, File cacheDir)
            throws Exception {
        File temp=new File(cacheDir,"asw_set_"+System.nanoTime());
        if(!temp.mkdirs())throw new IOException("Cannot create temporary folder");
        Stage stage=new Stage(temp);
        try {
            if(installedApks!=null && installedApks.length>0){
                for(int i=0;i<installedApks.length;i++){
                    File src=installedApks[i];
                    if(src==null || !src.isFile())
                        throw new IOException("Installed APK module unavailable: "+src);
                    stage.addFile(src,i==0?"base.apk":"split_"+i+".apk");
                }
            } else if(input!=null && input.isFile()){
                String lowerName=input.getName().toLowerCase(Locale.ROOT);
                for(String suffix:new String[]{".apkm",".xapk",".apks",".apk"}){
                    if(lowerName.endsWith(suffix)){stage.importedExtension=suffix;break;}
                }
                if(isStandaloneApk(input)){
                    stage.addFile(input,"base.apk");
                }else{
                    stage.archived=true;
                    stage.readArchive(input);
                }
            }else throw new IOException("APK source is missing");
            stage.validate();
            return stage;
        }catch(Exception e){stage.close();throw e;}
    }

    private static boolean isStandaloneApk(File file) throws IOException {
        try(ZipFile zip=new ZipFile(file)){
            return zip.getEntry("AndroidManifest.xml")!=null;
        }
    }

    private static void copyFile(File source,File output) throws IOException {
        try(InputStream in=new FileInputStream(source);
            OutputStream out=new FileOutputStream(output)){
            copyLimited(in,out,MAX_TOTAL_BYTES);
        }
    }
    private static long copyLimited(InputStream in,OutputStream out,long max)
            throws IOException {
        byte[] buffer=new byte[65536];
        long total=0;
        int count;
        while((count=in.read(buffer))!=-1){
            total+=count;
            if(total>max)throw new IOException("APK exceeds supported size limit");
            out.write(buffer,0,count);
        }
        return total;
    }
    private static void deleteRecursively(File file){
        if(file==null||!file.exists())return;
        if(file.isDirectory()){
            File[] children=file.listFiles();
            if(children!=null)for(File child:children)deleteRecursively(child);
        }
        file.delete();
    }
}
