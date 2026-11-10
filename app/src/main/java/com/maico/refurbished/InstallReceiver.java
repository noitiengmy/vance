package com.maico.refurbished;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Routes the system's uninstall and install confirmations/results. */
public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        int code=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String details=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if(code==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm;
            if(Build.VERSION.SDK_INT>=33)
                confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);
            else confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm!=null){
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try{context.startActivity(confirm);}
                catch(Exception error){
                    android.widget.Toast.makeText(context,
                            "Cannot launch system confirmation: "+error.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                }
            }else{
                android.widget.Toast.makeText(context,"System confirmation unavailable",
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        String phase=intent.getStringExtra(PackageInstall.EXTRA_PHASE);
        if(PackageInstall.PHASE_UNINSTALL.equals(phase)){
            PackageInstall.handleUninstallResult(context,intent,code,details);
        }else{
            PackageInstall.handleInstallResult(context,intent,code,details);
        }
    }
}
