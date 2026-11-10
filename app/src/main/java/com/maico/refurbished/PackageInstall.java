package com.maico.refurbished;

import com.maico.refurbished.patcher.PackageSets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Properties;

/**
 * Installs an APK set. When an existing package is signed by another
 * certificate, first requests a user-confirmed uninstall, then installs
 * the staged APK set after Android confirms removal.
 */
final class PackageInstall {
    static final String EXTRA_PHASE="install_phase";
    static final String EXTRA_TOKEN="install_token";
    static final String PHASE_UNINSTALL="uninstall";
    static final String PHASE_INSTALL="install";
    private static final String ACTION="com.maico.vance.INSTALL_COMPLETE";
    private static final String PENDING_DIR="pending-installs";
    private PackageInstall() { }

    static void install(Context context,PackageSets.Stage source) throws Exception {
        if(source.containsObb)
            throw new IOException("This XAPK contains OBB expansion files. Android's APK session cannot install OBB; use an OBB-aware installer or import APKs alone.");
        File base=findBase(source);
        PackageInfo installed=getInstalledInfo(context,source.packageName);
        if(installed!=null && !sameSigner(context,base,installed)){
            String token=UUID.randomUUID().toString();
            File pending=savePending(context,source,token);
            showToast(context,
                    "The installed app has a different signing certificate. Android will ask you to uninstall it before Vance installs this build. Uninstalling removes that app's data.");
            try{
                requestUninstall(context,source.packageName,token);
            }catch(Exception error){
                deleteTree(pending);
                throw error;
            }
            return;
        }
        installFiles(context,source.packageName,source.apks,source.isBase,null);
    }

    private static File findBase(PackageSets.Stage source) throws IOException {
        for(int i=0;i<source.apks.size();i++)
            if(source.isBase.get(i))return source.apks.get(i);
        throw new IOException("Base APK missing from selected package");
    }

    private static PackageInfo getInstalledInfo(Context context,String packageName)
            throws Exception {
        try {
            if(Build.VERSION.SDK_INT>=33)
                return context.getPackageManager().getPackageInfo(packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            return context.getPackageManager().getPackageInfo(packageName,
                    Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:
                            PackageManager.GET_SIGNATURES);
        } catch(PackageManager.NameNotFoundException absent) {
            return null;
        }
    }

    private static Signature[] archiveSigners(Context context,File base) throws IOException {
        PackageManager pm=context.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:
                PackageManager.GET_SIGNATURES;
        PackageInfo archive=pm.getPackageArchiveInfo(base.getAbsolutePath(),flags);
        if(archive==null)throw new IOException("Cannot read package identity from base APK");
        Signature[] signers;
        if(Build.VERSION.SDK_INT>=28 && archive.signingInfo!=null)
            signers=archive.signingInfo.getApkContentsSigners();
        else signers=archive.signatures;
        if(signers==null||signers.length==0)
            throw new IOException("The selected APK has no readable signing certificate");
        return signers;
    }

    private static Signature[] installedSigners(PackageInfo info) throws IOException {
        Signature[] signers;
        if(Build.VERSION.SDK_INT>=28 && info.signingInfo!=null)
            signers=info.signingInfo.getApkContentsSigners();
        else signers=info.signatures;
        if(signers==null||signers.length==0)
            throw new IOException("Cannot read installed app signing certificate");
        return signers;
    }

    private static boolean sameSigner(Context context,File base,PackageInfo installed)
            throws Exception {
        List<String> incoming=signatureSet(archiveSigners(context,base));
        List<String> current=signatureSet(installedSigners(installed));
        return incoming.equals(current);
    }

    private static List<String> signatureSet(Signature[] signatures) {
        List<String> result=new ArrayList<>();
        for(Signature signature:signatures){
            byte[] bytes=signature.toByteArray();
            StringBuilder hex=new StringBuilder(bytes.length*2);
            for(byte value:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            result.add(hex.toString());
        }
        Collections.sort(result);
        return result;
    }

    private static File savePending(Context context,PackageSets.Stage source,String token)
            throws Exception {
        File root=new File(context.getFilesDir(),PENDING_DIR);
        if(!root.exists()&&!root.mkdirs())throw new IOException("Cannot create pending install folder");
        File dir=new File(root,token);
        if(!dir.mkdirs())throw new IOException("Cannot create pending install record");
        Properties meta=new Properties();
        meta.setProperty("package",source.packageName);
        meta.setProperty("count",Integer.toString(source.apks.size()));
        byte[] buffer=new byte[65536];
        try{
            for(int i=0;i<source.apks.size();i++){
                String name=source.isBase.get(i)?"base.apk":"split_"+i+".apk";
                File destination=new File(dir,name);
                try(InputStream in=new FileInputStream(source.apks.get(i));
                    OutputStream out=new FileOutputStream(destination)){
                    int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                meta.setProperty("file."+i,name);
                meta.setProperty("base."+i,Boolean.toString(source.isBase.get(i)));
            }
            try(OutputStream out=new FileOutputStream(new File(dir,"install.properties"))){
                meta.store(out,"Vance pending install");
            }
            return dir;
        }catch(Exception error){
            deleteTree(dir);
            throw error;
        }
    }

    private static void requestUninstall(Context context,String packageName,String token)
            throws Exception {
        PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        Intent callback=new Intent(context,InstallReceiver.class);
        callback.setAction(ACTION);
        callback.putExtra(EXTRA_PHASE,PHASE_UNINSTALL);
        callback.putExtra(EXTRA_TOKEN,token);
        PendingIntent pending=pending(context,token.hashCode(),callback);
        installer.uninstall(packageName,pending.getIntentSender());
    }

    static void handleUninstallResult(Context context,Intent intent,int status,String details) {
        String token=intent.getStringExtra(EXTRA_TOKEN);
        if(token==null)return;
        File dir=new File(new File(context.getFilesDir(),PENDING_DIR),token);
        if(status!=PackageInstaller.STATUS_SUCCESS){
            deleteTree(dir);
            notifyResult(context,"Uninstall was not completed. The refurbished app was not installed"+
                    (details==null?"":": "+details));
            return;
        }
        try{
            Properties meta=new Properties();
            try(InputStream in=new FileInputStream(new File(dir,"install.properties"))){
                meta.load(in);
            }
            String packageName=meta.getProperty("package");
            int count=Integer.parseInt(meta.getProperty("count","0"));
            if(packageName==null||count<1)throw new IOException("Pending install data is incomplete");
            ArrayList<File> files=new ArrayList<>();
            ArrayList<Boolean> base=new ArrayList<>();
            for(int i=0;i<count;i++){
                String name=meta.getProperty("file."+i);
                if(name==null)throw new IOException("Pending APK entry missing");
                File apk=new File(dir,name);
                if(!apk.isFile())throw new IOException("Pending APK file missing: "+name);
                files.add(apk);
                base.add(Boolean.parseBoolean(meta.getProperty("base."+i,"false")));
            }
            installFiles(context,packageName,files,base,token);
        }catch(Exception error){
            deleteTree(dir);
            notifyResult(context,"Old app was removed, but the new app could not be installed: "+
                    error.getMessage());
        }
    }

    private static void installFiles(Context context,String packageName,
            List<File> files,List<Boolean> isBase,String pendingToken) throws Exception {
        PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams options=new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        options.setAppPackageName(packageName);
        int id=installer.createSession(options);
        PackageInstaller.Session session=null;
        boolean committed=false;
        try{
            session=installer.openSession(id);
            byte[] buffer=new byte[65536];
            for(int i=0;i<files.size();i++){
                File apk=files.get(i);
                String entry=isBase.get(i)?"base.apk":"split_"+i+".apk";
                try(InputStream input=new FileInputStream(apk);
                    OutputStream output=session.openWrite(entry,0,apk.length())){
                    int size;while((size=input.read(buffer))!=-1)output.write(buffer,0,size);
                    session.fsync(output);
                }
            }
            Intent callback=new Intent(context,InstallReceiver.class);
            callback.setAction(ACTION);
            callback.putExtra(EXTRA_PHASE,PHASE_INSTALL);
            if(pendingToken!=null)callback.putExtra(EXTRA_TOKEN,pendingToken);
            PendingIntent pending=pending(context,id,callback);
            session.commit(pending.getIntentSender());
            committed=true;
        }finally{
            if(session!=null){
                if(!committed)session.abandon();
                session.close();
            }else if(!committed)installer.abandonSession(id);
        }
    }

    static void handleInstallResult(Context context,Intent intent,int status,String details) {
        String token=intent.getStringExtra(EXTRA_TOKEN);
        if(token!=null)deleteTree(new File(new File(context.getFilesDir(),PENDING_DIR),token));
        notifyResult(context,status==PackageInstaller.STATUS_SUCCESS
                ?"Package installed successfully"
                :"Package install failed ("+status+"): "+details);
    }

    private static PendingIntent pending(Context context,int id,Intent callback){
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(context,id,callback,flags);
    }

    private static void showToast(Context context,String message){
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(()->android.widget.Toast.makeText(context,message,
                android.widget.Toast.LENGTH_LONG).show());
    }

    private static void notifyResult(Context context,String message){
        android.widget.Toast.makeText(context,message,android.widget.Toast.LENGTH_LONG).show();
        Intent feedback=new Intent("com.maico.vance.INSTALL_RESULT");
        feedback.setPackage(context.getPackageName());
        feedback.putExtra("result",message);
        context.sendBroadcast(feedback);
    }

    private static void deleteTree(File file){
        if(file==null||!file.exists())return;
        File[] children=file.listFiles();
        if(children!=null)for(File child:children)deleteTree(child);
        file.delete();
    }
}
