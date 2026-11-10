package com.maico.refurbished;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

/** Minimal browser: download APK/APKS/XAPK into app-owned storage and return it. */
public final class WebDownloadActivity extends Activity {
    private WebView browser;
    private EditText address;
    private TextView message;
    private ProgressBar progress;
    private long downloadId=-1;
    private String downloadedName;
    private File downloadFile;
    private final BroadcastReceiver completion=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()))return;
            if(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1)!=downloadId)return;
            checkDownload();
        }
    };
    private boolean receiverRegistered;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(UiSettings.wrap(base));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setTheme(UiSettings.theme(this));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        address=new EditText(this);
        address.setSingleLine(true);
        address.setHint(R.string.browser_address);
        address.setText("");
        layout.addView(address);
        LinearLayout bar=new LinearLayout(this);
        Button go=new Button(this);go.setText(R.string.browser_go);
        FlatUi.button(this,go,R.drawable.ic_flat_web);
        go.setOnClickListener(v -> browse(address.getText().toString()));
        bar.addView(go,new LinearLayout.LayoutParams(0,-2,1));
        Button back=new Button(this);back.setText(R.string.browser_back);
        FlatUi.button(this,back,R.drawable.ic_flat_source);
        back.setOnClickListener(v -> {if(browser.canGoBack())browser.goBack();});
        bar.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button close=new Button(this);close.setText(R.string.browser_close);
        close.setOnClickListener(v -> finish());
        bar.addView(close,new LinearLayout.LayoutParams(0,-2,1));
        layout.addView(bar);
        LinearLayout sites=new LinearLayout(this);
        Button mirror=new Button(this);
        mirror.setText("APKMirror");
        mirror.setOnClickListener(v -> browse("https://www.apkmirror.com/"));
        sites.addView(mirror,new LinearLayout.LayoutParams(0,-2,1));
        Button pure=new Button(this);
        pure.setText("APKPure");
        pure.setOnClickListener(v -> browse("https://apkpure.com/"));
        sites.addView(pure,new LinearLayout.LayoutParams(0,-2,1));
        layout.addView(sites);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        layout.addView(progress);
        message=new TextView(this);
        message.setText(R.string.browser_hint);
        message.setPadding(12,8,12,8);
        layout.addView(message);
        browser=new WebView(this);
        layout.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(layout);

        WebSettings settings=browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if(Build.VERSION.SDK_INT>=26)settings.setSafeBrowsingEnabled(true);
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){
                progress.setProgress(value);
            }
        });
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                Uri uri=req.getUrl();
                String scheme=uri.getScheme();
                if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return true;
                return false;
            }
            @Override public void onPageFinished(WebView view,String url){address.setText(url);}
        });
        browser.setDownloadListener((url,userAgent,disposition,mime,length) ->
                startDownload(url,userAgent,disposition,mime));

        IntentFilter filter=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(completion,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(completion,filter);
        receiverRegistered=true;
    }

    private void browse(String raw){
        String url=raw.trim();
        if(url.isEmpty()){message.setText("Enter a website address.");return;}
        if(!url.contains("://"))url="https://"+url;
        Uri parsed=Uri.parse(url);
        if(!"https".equalsIgnoreCase(parsed.getScheme()) &&
                !"http".equalsIgnoreCase(parsed.getScheme())){
            message.setText("Only website addresses are supported.");return;
        }
        browser.loadUrl(url);
    }

    private void startDownload(String url,String agent,String disposition,String mime){
        Uri uri=Uri.parse(url);
        if(!"https".equalsIgnoreCase(uri.getScheme())){
            message.setText("Downloads require HTTPS for safer transport.");
            return;
        }
        if(downloadId!=-1){
            message.setText("A download is already in progress.");
            return;
        }
        String name=android.webkit.URLUtil.guessFileName(url,disposition,mime);
        String lower=name.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".apk")&&!lower.endsWith(".apks")&&!lower.endsWith(".xapk")&&!lower.endsWith(".apkm")){
            message.setText("Only APK, APKS, XAPK and APKM downloads are accepted. Found: "+name);
            return;
        }
        name=name.replaceAll("[^a-zA-Z0-9._-]","_");
        name=System.currentTimeMillis()+"_"+name;
        File folder=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(folder==null){
            message.setText("Download storage is unavailable.");
            return;
        }
        downloadFile=new File(folder,name);
        downloadedName=name;
        DownloadManager.Request req=new DownloadManager.Request(uri);
        req.setTitle(name);
        req.setDescription("Refurbished package download");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,name);
        if(agent!=null && !agent.isEmpty())req.addRequestHeader("User-Agent",agent);
        String cookies=CookieManager.getInstance().getCookie(url);
        if(cookies!=null && !cookies.isEmpty())req.addRequestHeader("Cookie",cookies);
        try{
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            downloadId=dm.enqueue(req);
            message.setText("Downloading "+name+"... Leave this screen open to import automatically.");
        }catch(Exception exception){
            downloadId=-1;
            message.setText("Download error: "+exception.getMessage());
        }
    }

    private void checkDownload(){
        DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        try(Cursor result=dm.query(new DownloadManager.Query().setFilterById(downloadId))){
            if(result==null||!result.moveToFirst())throw new IllegalStateException("Download not found");
            int status=result.getInt(result.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(status!=DownloadManager.STATUS_SUCCESSFUL){
                message.setText("Download failed (status "+status+").");
                downloadId=-1;return;
            }
            if(downloadFile==null || !downloadFile.isFile())
                throw new IllegalStateException("Downloaded file unavailable");
            Intent output=new Intent();
            output.setData(Uri.fromFile(downloadFile));
            output.putExtra("download_path",downloadFile.getAbsolutePath());
            output.putExtra("download_name",downloadedName);
            setResult(RESULT_OK,output);
            finish();
        }catch(Exception exception){
            downloadId=-1;
            message.setText("Cannot import download: "+exception.getMessage());
        }
    }

    @Override public void onBackPressed(){
        if(browser!=null&&browser.canGoBack())browser.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(receiverRegistered)unregisterReceiver(completion);
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
}
