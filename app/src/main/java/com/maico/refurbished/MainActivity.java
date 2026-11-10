package com.maico.refurbished;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.maico.refurbished.patcher.IconNormalizer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.maico.refurbished.patcher.ApkInspector;
import com.maico.refurbished.patcher.PackageSets;
import com.maico.refurbished.patcher.ApkPatcher;
import com.maico.refurbished.patcher.PatchRequest;
import com.maico.refurbished.patcher.SigningKey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Vance module host for the shared APK workspace.
 * User's selections are remembered by package name, not by filename/version.
 */
public final class MainActivity extends Activity {
    private static final int PICK_APK=10, PICK_PNG=20, SAVE_APK=30, SAVE_ORIGINAL=40, PICK_WEB=50, PICK_INSTALL=60, PICK_KEY=70, IMPORT_PORTABLE=71, EXPORT_PORTABLE=72;
    private static final int MAX_PNG_BYTES=24*1024*1024;
    private static final int INITIAL_STYLE_ROWS=3;
    private final java.util.ArrayList<StyleRow> styleRows=new java.util.ArrayList<>();
    private LinearLayout stylesContainer;
    private StyleRow pendingStyle;
    private static final class StyleRow {
        LinearLayout container;
        TextView title,description;
        EditText name;
        ImageView preview;
        Spinner background;
        Button choose;
        Uri icon;
        int previewSerial;
    }
    private boolean restoringStyles;
    private boolean forgotStyles;
    private Uri apkUri;
    private File[] installedApkSources;
    private String generatedExtension=".apk";
    private boolean sourceIsSplit;
    private boolean canBuildSingle;
    private boolean sourceHasObb;
    private String sourceExtension=".apk";
    private String currentPackage;
    private String currentApkName;
    private Spinner launcherPicker;
    private String[] launcherOptions=new String[0];
    private StyleStore styles;
    private SignerManager signerManager;
    private PortableKeyManager portableKeys;
    private SigningPanel signingPanel;
    private TextView signerStatus;
    private File signedOutput;
    private String signedOutputPackage;
    private Uri lastSavedUri;
    private String requestedExportName,requestedOriginalName;
    private TextView status, apkInfo, memoryInfo;
    private Button patchButton, singleButton, saveConfigButton, clearConfigButton, exportButton, exportOriginalButton, installSavedButton;
    private int selectionSerial;
    private boolean inspecting;
    private UiSections uiSections;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(UiSettings.wrap(base));
    }

    @Override public void onCreate(Bundle state) {
        setTheme(UiSettings.theme(this));
        super.onCreate(state);
        styles=new StyleStore(this);
        signerManager=new SignerManager(this);
        portableKeys=new PortableKeyManager(this);
        ScrollView scroll=new ScrollView(this);
        LinearLayout screen=new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18),dp(14),dp(18),dp(25));
        scroll.addView(screen);
        setContentView(scroll);

        // A bordered header card separates the identity/settings area
        // from the four workflow navigation controls.
        boolean dark=UiSettings.dark(this);
        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14),dp(12),dp(10),dp(12));
        GradientDrawable headerBorder=new GradientDrawable();
        headerBorder.setColor(dark?0xff1d1d1d:0xfffafafa);
        headerBorder.setCornerRadius(dp(14));
        headerBorder.setStroke(dp(1),dark?0xff777777:0xffbbbbbb);
        header.setBackground(headerBorder);
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView headingTitle=text(getString(R.string.app_name),22);
        headingTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.addView(headingTitle);
        heading.addView(text(getString(R.string.subtitle),12));
        header.addView(heading,new LinearLayout.LayoutParams(
                0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        Button settings=new Button(this);
        settings.setText(R.string.settings);
        settings.setAllCaps(false);
        settings.setMinHeight(dp(48));
        settings.setMinimumHeight(dp(48));
        settings.setTextSize(12);
                FlatUi.button(this,settings,R.drawable.ic_flat_settings);
settings.setOnClickListener(v->UiSettings.show(this));
        header.addView(settings,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,dp(48)));
        screen.addView(header,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        uiSections=new UiSections(this,scroll,screen);

        addDashboardModule(uiSections.panels[0],getString(R.string.module_refurbish),
                getString(R.string.module_refurbish_desc),
                R.drawable.ic_vance_refurbish,()->uiSections.show(1));
        addDashboardModule(uiSections.panels[0],getString(R.string.module_explorer),
                getString(R.string.module_explorer_desc),
                R.drawable.ic_vance_explorer,()->moduleNotReady("Explorer"));
        addDashboardModule(uiSections.panels[0],getString(R.string.module_monitor),
                getString(R.string.module_monitor_desc),
                R.drawable.ic_vance_monitor,()->moduleNotReady("Monitor"));
        addDashboardModule(uiSections.panels[0],getString(R.string.module_inspect),
                getString(R.string.module_inspect_desc),
                R.drawable.ic_vance_inspect,()->moduleNotReady("Inspect Layout"));
        addDashboardModule(uiSections.panels[0],getString(R.string.module_agent),
                getString(R.string.module_agent_desc),
                R.drawable.ic_vance_agent,()->moduleNotReady("Scan Agent"));
        addDashboardModule(uiSections.panels[0],getString(R.string.module_more),
                getString(R.string.module_more_desc),
                R.drawable.ic_vance_more,()->moreModules());

        uiSections.panels[1].addView(sectionTitle(R.string.source_title));
        uiSections.panels[1].addView(text(getString(R.string.source_hint),13));
        Button chooseApk=new Button(this);
        chooseApk.setText(R.string.import_package);
                FlatUi.button(this,chooseApk,R.drawable.ic_flat_source);
chooseApk.setOnClickListener(v -> openFile(PICK_APK,"*/*"));
        uiSections.panels[1].addView(chooseApk);
        Button installed=new Button(this);
        installed.setText(R.string.installed_package);
                FlatUi.button(this,installed,R.drawable.ic_flat_apps);
installed.setOnClickListener(v -> InstalledAppPicker.show(this,app -> {
            if(currentPackage!=null && !inspecting && !forgotStyles)saveCurrentStyles();
            File[] installedFiles=new File[1+app.splits.length];
            installedFiles[0]=new File(app.source);
            for(int j=0;j<app.splits.length;j++)installedFiles[j+1]=new File(app.splits[j]);
            inspectSource(null,installedFiles,app.name);
        }));
        uiSections.panels[1].addView(installed);
        Button webButton=new Button(this);
        webButton.setText(R.string.web_download);
                FlatUi.button(this,webButton,R.drawable.ic_flat_web);
webButton.setOnClickListener(v ->
                startActivityForResult(new Intent(this,WebDownloadActivity.class),PICK_WEB));
        uiSections.panels[1].addView(webButton);

        exportOriginalButton=new Button(this);
        exportOriginalButton.setText(R.string.export_original);
        exportOriginalButton.setEnabled(false);
                FlatUi.button(this,exportOriginalButton,R.drawable.ic_flat_download);
exportOriginalButton.setOnClickListener(v -> requestOriginalExport());
        uiSections.panels[1].addView(exportOriginalButton);

        apkInfo=text(getString(R.string.no_package),14);
        uiSections.panels[1].addView(apkInfo);
        launcherPicker=new Spinner(this);
        launcherPicker.setVisibility(View.GONE);
        uiSections.panels[1].addView(launcherPicker);
        uiSections.panels[2].addView(sectionTitle(R.string.style_title));
        uiSections.panels[2].addView(text(getString(R.string.style_hint),13));
        memoryInfo=text(getString(R.string.saved_style_hint),12);
        uiSections.panels[2].addView(memoryInfo);

        stylesContainer=new LinearLayout(this);
        stylesContainer.setOrientation(LinearLayout.VERTICAL);
        uiSections.panels[2].addView(stylesContainer);
        for(int i=0;i<INITIAL_STYLE_ROWS;i++)addStyleRow();
        Button addMore=new Button(this);
        addMore.setText(R.string.add_more_style);
        FlatUi.button(this,addMore,R.drawable.ic_flat_image);
        addMore.setOnClickListener(v->{
            addStyleRow();
            if(currentPackage!=null)saveCurrentStyles();
        });
        uiSections.panels[2].addView(addMore);

        saveConfigButton=new Button(this);
        saveConfigButton.setText(R.string.save_style);
        saveConfigButton.setEnabled(false);
                FlatUi.button(this,saveConfigButton,R.drawable.ic_flat_save);
saveConfigButton.setOnClickListener(v -> {
            saveCurrentStyles();
            status.setText(getString(R.string.status_saved_style,currentPackage));
        });
        uiSections.panels[2].addView(saveConfigButton);
        uiSections.panels[3].addView(sectionTitle(R.string.sign_title));
        uiSections.panels[3].addView(text(getString(R.string.sign_hint),13));
        signingPanel=new SigningPanel(this,signerManager,()->openFile(PICK_KEY,"*/*"));
        uiSections.panels[3].addView(signingPanel.view());
        Button generatePortable=new Button(this);
        generatePortable.setText(R.string.create_portable);
                FlatUi.button(this,generatePortable,R.drawable.ic_flat_key);
generatePortable.setOnClickListener(v -> PortableKeyDialog.ask(this,
                "Create portable key","Use 12+ characters. Save a backup immediately: losing this P12 means losing the ability to update apps signed with it.",
                secret -> {
                    char[] pass=secret.clone();
                    new Thread(()->{
                        try{
                            SigningKey signer=portableKeys.generate(pass);
                            String fp=signer.fingerprint();
                            runOnUiThread(()->{
                                signingPanel.mode.setSelection(SignerManager.PORTABLE);
                                signerStatus.setText("Portable key created. SHA-256: "+fp+
                                        "\\nExport a backup now and retain its password.");
                            });
                        }catch(Exception ex){runOnUiThread(()->error(ex));}
                        finally{java.util.Arrays.fill(pass,'\0');}
                    }).start();
                }));
        uiSections.panels[3].addView(generatePortable);
        Button exportPortable=new Button(this);
        exportPortable.setText(R.string.backup_portable);
                FlatUi.button(this,exportPortable,R.drawable.ic_flat_download);
exportPortable.setOnClickListener(v -> {
            if(!portableKeys.exists()){status.setText(R.string.status_key_missing);return;}
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/x-pkcs12");
            intent.putExtra(Intent.EXTRA_TITLE,"refurbished-portable.p12");
            startActivityForResult(intent,EXPORT_PORTABLE);
        });
        uiSections.panels[3].addView(exportPortable);
        Button importPortable=new Button(this);
        importPortable.setText(R.string.restore_portable);
                FlatUi.button(this,importPortable,R.drawable.ic_flat_upload);
importPortable.setOnClickListener(v -> {
            if(portableKeys.exists()){
                new AlertDialog.Builder(this)
                        .setTitle("Existing portable key")
                        .setMessage("Backup and delete the current portable key before restoring another. This avoids silently changing certificates.")
                        .setPositiveButton("OK",null).show();
                return;
            }
            openFile(IMPORT_PORTABLE,"*/*");
        });
        uiSections.panels[3].addView(importPortable);
        Button deletePortable=new Button(this);
        deletePortable.setText(R.string.remove_portable);
                FlatUi.button(this,deletePortable,R.drawable.ic_flat_trash);
deletePortable.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Remove portable signing key?")
                .setMessage("Export a P12 backup first. Without the original certificate, apps signed by this key cannot be updated in place.")
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton("Remove key",(dialog,which)->{
                    try{portableKeys.delete();signerStatus.setText(R.string.status_key_removed);}
                    catch(Exception ex){error(ex);}
                }).show());
        uiSections.panels[3].addView(deletePortable);
        Button verifySigner=new Button(this);
        verifySigner.setText(R.string.verify_certificate);
                FlatUi.button(this,verifySigner,R.drawable.ic_flat_shield);
verifySigner.setOnClickListener(v->inspectSigningIdentity());
        uiSections.panels[3].addView(verifySigner);
        signerStatus=text(getString(R.string.certificate_hint),12);
        uiSections.panels[3].addView(signerStatus);

        clearConfigButton=new Button(this);
        clearConfigButton.setText(R.string.forget_style);
        clearConfigButton.setEnabled(false);
                FlatUi.button(this,clearConfigButton,R.drawable.ic_flat_trash);
clearConfigButton.setOnClickListener(v -> {
            if(currentPackage==null)return;
            String pkg=currentPackage;
            new AlertDialog.Builder(this).setTitle("Forget saved styles?")
                    .setMessage("Remove saved names and PNG references for "+pkg+"? This won't delete any images.")
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton("Forget",(dialog,which) -> {
                        styles.forget(pkg);
                        forgotStyles=true;
                        restoringStyles=true;
                        clearEditors();
                        restoringStyles=false;
                        memoryInfo.setText(getString(R.string.status_forgot,pkg));
                        status.setText(R.string.status_forgot_hint);
                    }).show();
        });
        uiSections.panels[2].addView(clearConfigButton);

        uiSections.panels[4].addView(sectionTitle(R.string.output_title));
        uiSections.panels[4].addView(text(getString(R.string.output_hint),13));
        patchButton=new Button(this);
        patchButton.setText(R.string.patch_package);
        patchButton.setEnabled(false);
                FlatUi.button(this,patchButton,R.drawable.ic_flat_package);
patchButton.setOnClickListener(v -> startPatch(false));
        uiSections.panels[4].addView(patchButton);
        singleButton=new Button(this);
        singleButton.setText(R.string.patch_single);
        singleButton.setEnabled(false);
                FlatUi.button(this,singleButton,R.drawable.ic_flat_merge);
singleButton.setOnClickListener(v -> startPatch(true));
        uiSections.panels[4].addView(singleButton);

        exportButton=new Button(this);
        exportButton.setText(R.string.save_result);
        exportButton.setEnabled(false);
                FlatUi.button(this,exportButton,R.drawable.ic_flat_save);
exportButton.setOnClickListener(v -> requestExport());
        uiSections.panels[4].addView(exportButton);
        Button installAny=new Button(this);
        installAny.setText(R.string.install_file);
                FlatUi.button(this,installAny,R.drawable.ic_flat_install);
installAny.setOnClickListener(v -> openFile(PICK_INSTALL,"*/*"));
        uiSections.panels[4].addView(installAny);
        installSavedButton=new Button(this);
        installSavedButton.setText(R.string.install_result);
        installSavedButton.setEnabled(false);
                FlatUi.button(this,installSavedButton,R.drawable.ic_flat_install);
installSavedButton.setOnClickListener(v -> installFromUri(lastSavedUri));
        uiSections.panels[4].addView(installSavedButton);

        status=text(getString(R.string.status_idle),13);
        screen.addView(status,5);
    }

    @Override protected void onPause() {
        // Preserve partial edits when leaving for a file picker or switching apps.
        if(!inspecting && currentPackage!=null && !forgotStyles)saveCurrentStyles();
        super.onPause();
    }

    private void openImages() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/png","image/jpeg"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_PNG);
    }

    private void openFile(int code,String mime) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,code);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri selected=data.getData();
        if(request==PICK_APK) {
            if(currentPackage!=null && !inspecting && !forgotStyles)saveCurrentStyles();
            grantReadAccess(data,selected);
            inspectSource(selected,null,displayName(selected));
        } else if(request==PICK_PNG) {
            grantReadAccess(data,selected);
            if(pendingStyle==null || !styleRows.contains(pendingStyle))return;
            pendingStyle.icon=selected;
            forgotStyles=false;
            displayIcon(pendingStyle,selected,false);
            if(currentPackage!=null)saveCurrentStyles();
        } else if(request==SAVE_APK) {
            exportSignedApk(selected);
        } else if(request==SAVE_ORIGINAL) {
            exportOriginalApk(selected);
        } else if(request==PICK_KEY) {
            grantReadAccess(data,selected);
            signingPanel.setCustomUri(selected);
            signerStatus.setText("Custom keystore selected. Enter alias/password, then check its certificate.");
        } else if(request==IMPORT_PORTABLE) {
            importPortableFrom(selected);
        } else if(request==EXPORT_PORTABLE) {
            exportPortableTo(selected);
        } else if(request==PICK_INSTALL) {
            installFromUri(selected);
        } else if(request==PICK_WEB) {
            String path=data.getStringExtra("download_path");
            if(path!=null) {
                File downloaded=new File(path);
                inspectSource(Uri.fromFile(downloaded),null,
                        "WebView: "+data.getStringExtra("download_name"));
            }
        }
    }

    private void grantReadAccess(Intent data,Uri uri) {
        try {
            if((data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)
                getContentResolver().takePersistableUriPermission(
                    uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(SecurityException ignored) {
            // Some document providers grant only temporary read access.
            // The saved URI will be marked missing if it becomes unavailable.
        }
    }

    private void inspectSource(Uri selected,File[] fileSources,String selectedName) {
        final int serial=++selectionSerial;
        // The previous signed result must not be offered under a new package.
        if(signedOutput!=null){signedOutput.delete();signedOutput=null;}
        signedOutputPackage=null;
        requestedExportName=null;
        lastSavedUri=null;
        exportButton.setEnabled(false);
        installSavedButton.setEnabled(false);
        inspecting=true;
        uiSections.busy(true);
        currentPackage=null;
        apkUri=null;
        installedApkSources=null;
        patchButton.setEnabled(false);
        singleButton.setEnabled(false);
        saveConfigButton.setEnabled(false);
        clearConfigButton.setEnabled(false);
        exportOriginalButton.setEnabled(false);
        apkInfo.setText(getString(R.string.status_inspecting,selectedName));
        memoryInfo.setText("");
        status.setText(R.string.status_input_reading);
        new Thread(() -> {
            File downloadedInput=null;
            try {
                File input=null;
                if(fileSources==null) {
                    downloadedInput=File.createTempFile("refurbished-input-",
                            packageSuffix(selectedName),getCacheDir());
                    try(InputStream stream=requireStream(selected);
                        OutputStream output=new FileOutputStream(downloadedInput)){
                        copy(stream,output);
                    }
                    input=downloadedInput;
                }
                PackageSets.Stage stage=PackageSets.prepare(input,fileSources,getCacheDir());
                try {
                    StyleStore.Saved remembered=styles.load(stage.packageName);
                    boolean existed=styles.exists(stage.packageName);
                    final String pkg=stage.packageName;
                    final String details=stage.describe();
                    final String[] availableLaunchers=stage.launcherComponents.clone();
                    final boolean split=stage.multi();
                    final boolean obb=stage.containsObb;
                    final String sourceExt=stage.extension();
                    final boolean singleAvailable=!".apk".equals(stage.extension())
                            && stage.canBuildSingle();
                    runOnUiThread(() -> {
                        if(serial!=selectionSerial)return;
                        apkUri=selected;
                        installedApkSources=fileSources==null?null:fileSources.clone();
                        currentPackage=pkg;
                        signingPanel.restoreMode(pkg);
                        signerStatus.setText(R.string.status_sign_prompt);
                        forgotStyles=false;
                        currentApkName=selectedName;
                        sourceIsSplit=split;
                        sourceExtension=sourceExt;
                        canBuildSingle=singleAvailable;
                        sourceHasObb=obb;
                        singleButton.setEnabled(singleAvailable);
                        inspecting=false;
                        uiSections.busy(false);
                        apkInfo.setText(getString(R.string.status_source_name,selectedName)+"\n"+details);
                        launcherOptions=availableLaunchers;
                        ArrayAdapter<String> launcherAdapter=new ArrayAdapter<>(this,
                                android.R.layout.simple_spinner_item,launcherOptions);
                        launcherAdapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item);
                        launcherPicker.setAdapter(launcherAdapter);
                        launcherPicker.setVisibility(launcherOptions.length>1?
                                View.VISIBLE:View.GONE);
                        String prior=getPreferences(MODE_PRIVATE).getString(
                                "launcher:"+pkg,null);
                        if(prior!=null){
                            for(int j=0;j<launcherOptions.length;j++)
                                if(prior.equals(launcherOptions[j])){
                                    launcherPicker.setSelection(j);break;
                                }
                        }
                        restoringStyles=true;
                        clearEditors();
                        while(styleRows.size()<remembered.size())addStyleRow();
                        int missing=0;
                        for(int i=0;i<remembered.size();i++){
                            StyleRow row=styleRows.get(i);
                            row.name.setText(remembered.names[i]==null?"":remembered.names[i]);
                            row.icon=remembered.icons[i];
                            row.background.setSelection(remembered.backgrounds[i]);
                            displayIcon(row,row.icon,remembered.missing[i]);
                            if(remembered.missing[i])missing++;
                        }
                        restoringStyles=false;
                        memoryInfo.setText(!existed?getString(R.string.status_new_package)
                                :missing==0?getString(R.string.status_restored)
                                :getString(R.string.status_missing_images,missing));
                        patchButton.setEnabled(true);
                        exportOriginalButton.setEnabled(fileSources!=null);
                        saveConfigButton.setEnabled(true);
                        clearConfigButton.setEnabled(existed);
                        uiSections.show(2);
                        status.setText(getString(R.string.status_input_ready,
                                sourceExt.substring(1).toUpperCase(java.util.Locale.ROOT)));
                    });
                }finally{stage.close();}
            }catch(Exception exception){
                runOnUiThread(() -> {
                    if(serial!=selectionSerial)return;
                    inspecting=false;
                    uiSections.busy(false);
                    currentPackage=null;
                    apkUri=null;
                    installedApkSources=null;
                    sourceIsSplit=false;
                    launcherOptions=new String[0];
                    launcherPicker.setVisibility(View.GONE);
                    canBuildSingle=false;
                    sourceHasObb=false;
                    singleButton.setEnabled(false);
                    exportOriginalButton.setEnabled(false);
                    clearEditors();
                    apkInfo.setText(R.string.status_import_failed);
                    memoryInfo.setText("");
                    status.setText(getString(R.string.status_error,exception.getMessage()));
                });
            }finally{if(downloadedInput!=null)downloadedInput.delete();}
        }).start();
    }

    private void exportPortableTo(Uri destination) {
        File key=portableKeys.file();
        if(!key.isFile()){status.setText("Portable P12 is missing.");return;}
        PortableKeyDialog.ask(this,"Verify backup password",
                "Enter your portable key password before exporting its private key.",
                secret -> {
                    try{
                        SigningKey check=portableKeys.signer(secret);
                        check.verify();
                        try(InputStream in=new FileInputStream(key);
                            OutputStream out=getContentResolver().openOutputStream(destination)){
                            if(out==null)throw new IOException("Cannot write P12 backup");
                            copy(in,out);
                        }
                        status.setText(R.string.status_backup_saved);
                    }catch(Exception e){error(e);}
                });
    }

    private void importPortableFrom(Uri source) {
        PortableKeyDialog.ask(this,"Restore portable P12",
                "Enter the backup P12 password. Existing keys cannot be replaced accidentally.",
                secret -> {
                    char[] pass=secret.clone();
                    new Thread(()->{
                        File input=null;
                        try{
                            input=File.createTempFile("asw-portable-", ".p12",getCacheDir());
                            try(InputStream in=requireStream(source);
                                OutputStream out=new FileOutputStream(input)){copy(in,out);}
                            portableKeys.importKey(input,pass,null);
                            String fingerprint=portableKeys.signer(pass).fingerprint();
                            runOnUiThread(()->{
                                signingPanel.mode.setSelection(SignerManager.PORTABLE);
                                signerStatus.setText("Restored portable signer SHA-256: "+fingerprint);
                            });
                        }catch(Exception e){runOnUiThread(()->error(e));}
                        finally{
                            java.util.Arrays.fill(pass,'\0');
                            if(input!=null)input.delete();
                        }
                    }).start();
                });
    }

    private SigningKey createSigner(SigningPanel.Choice choice,File temporary)
            throws Exception {
        if(choice.mode==SignerManager.DEFAULT){
            copyAsset("default.p12",temporary);
            char[] pass=DefaultKey.PASSWORD.toCharArray();
            try{return new SigningKey(temporary,"PKCS12","alias",pass,pass);}
            finally{java.util.Arrays.fill(pass,'\0');}
        }
        if(choice.mode==SignerManager.PRIVATE)
            return signerManager.privateSigner();
        if(choice.mode==SignerManager.PORTABLE)
            return portableKeys.signer(choice.portable);
        if(choice.mode==SignerManager.CUSTOM){
            char[] keyPass=choice.key.length==0?choice.store:choice.key;
            return signerManager.customSigner(choice.uri,choice.alias,
                    choice.store,keyPass,temporary);
        }
        throw new IOException("Select a signing mode");
    }

    private void inspectSigningIdentity() {
        final SigningPanel.Choice choice=signingPanel.snapshot();
        final String pkg=currentPackage;
        signerStatus.setText(R.string.status_key_checking);
        new Thread(()->{
            File temp=null;
            try{
                temp=File.createTempFile("signer-inspect-", ".p12",getCacheDir());
                SigningKey signer=createSigner(choice,temp);
                signer.verify();
                String fingerprint=signer.fingerprint();
                String compatibility=installedSignatureMatch(pkg,fingerprint);
                runOnUiThread(()->signerStatus.setText(
                        "SHA-256: "+fingerprint+"\n"+compatibility));
            }catch(Exception error){
                runOnUiThread(()->signerStatus.setText(
                        "Cannot load signing identity: "+error.getMessage()));
            }finally{
                choice.erase();
                if(temp!=null)temp.delete();
            }
        }).start();
    }

    private String installedSignatureMatch(String pkg,String fp) {
        if(pkg==null)return "No APK selected for certificate comparison.";
        try {
            android.content.pm.Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28){
                android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(
                        pkg,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if(info.signingInfo==null)return "Installed certificate unavailable.";
                signatures=info.signingInfo.hasMultipleSigners()
                        ?info.signingInfo.getApkContentsSigners()
                        :info.signingInfo.getSigningCertificateHistory();
            }else{
                android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(
                        pkg,android.content.pm.PackageManager.GET_SIGNATURES);
                signatures=info.signatures;
            }
            if(signatures==null)return "Installed signing certificates unavailable.";
            for(android.content.pm.Signature signature:signatures) {
                byte[] bytes=java.security.MessageDigest.getInstance("SHA-256")
                        .digest(signature.toByteArray());
                StringBuilder hex=new StringBuilder();
                for(byte b:bytes){
                    if(hex.length()>0)hex.append(':');
                    hex.append(String.format(java.util.Locale.ROOT,"%02X",b&0xff));
                }
                if(fp.equals(hex.toString()))
                    return "Certificate matches an installed signing certificate.";
            }
            return "WARNING: Different certificate from installed package; Android will reject an in-place update.";
        }catch(android.content.pm.PackageManager.NameNotFoundException e) {
            return "This package is not installed in the current user.";
        }catch(Exception e){
            return "Could not compare installed certificate: "+e.getMessage();
        }
    }

    private StyleRow addStyleRow(){
        final StyleRow row=new StyleRow();
        row.container=new LinearLayout(this);
        row.container.setOrientation(LinearLayout.VERTICAL);
        row.container.setPadding(0,dp(8),0,dp(12));
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        row.title=text("",18);
        row.title.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.addView(row.title,new LinearLayout.LayoutParams(0,-2,1));
        if(styleRows.size()>=INITIAL_STYLE_ROWS){
            Button remove=new Button(this);
            remove.setText(R.string.remove_style);
            FlatUi.button(this,remove,R.drawable.ic_flat_trash);
            remove.setOnClickListener(v->{
                // Rows are kept in list order; references are stable across removal.
                styleRows.remove(row);
                stylesContainer.removeView(row.container);
                refreshStyleNumbers();
                if(currentPackage!=null)saveCurrentStyles();
            });
            heading.addView(remove);
        }
        row.container.addView(heading);
        row.name=new EditText(this);
        row.name.setSingleLine(true);
        row.container.addView(row.name);
        LinearLayout previewLine=new LinearLayout(this);
        previewLine.setGravity(Gravity.CENTER_VERTICAL);
        row.preview=new ImageView(this);
        row.preview.setLayoutParams(new LinearLayout.LayoutParams(dp(64),dp(64)));
        row.preview.setBackgroundColor(Color.WHITE);
        previewLine.addView(row.preview);
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        row.choose=new Button(this);
        FlatUi.button(this,row.choose,R.drawable.ic_flat_image);
        row.choose.setOnClickListener(v->{
            pendingStyle=row;
            openImages();
        });
        controls.addView(row.choose);
        row.description=text(getString(R.string.no_image),12);
        controls.addView(row.description);
        previewLine.addView(controls);
        row.container.addView(previewLine);
        row.background=new Spinner(this);
        ArrayAdapter<String> modes=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.background_white),
                        getString(R.string.background_transparent)});
        modes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        row.background.setAdapter(modes);
        row.background.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(!restoringStyles && styleRows.contains(row))
                    displayIcon(row,row.icon,false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        row.container.addView(row.background);
        styleRows.add(row);
        stylesContainer.addView(row.container);
        refreshStyleNumbers();
        return row;
    }
    private void refreshStyleNumbers(){
        for(int i=0;i<styleRows.size();i++){
            StyleRow row=styleRows.get(i);
            row.title.setText(getString(R.string.style_number,i+1));
            row.name.setHint(getString(R.string.launcher_name,i+1));
            row.choose.setText(getString(R.string.choose_icon,i+1));
        }
    }
    private void saveCurrentStyles() {
        if(currentPackage==null)return;
        forgotStyles=false;
        int count=styleRows.size();
        String[] labels=new String[count];
        Uri[] iconUris=new Uri[count];
        int[] modes=new int[count];
        for(int i=0;i<count;i++){
            StyleRow row=styleRows.get(i);
            labels[i]=row.name.getText().toString();
            iconUris[i]=row.icon;
            modes[i]=row.background.getSelectedItemPosition()==1?1:0;
        }
        styles.save(currentPackage,labels,iconUris,modes);
        if(signingPanel!=null)signingPanel.remember(currentPackage);
        clearConfigButton.setEnabled(true);
    }

    private void clearEditors() {
        if(stylesContainer==null)return;
        pendingStyle=null;
        styleRows.clear();
        stylesContainer.removeAllViews();
        for(int i=0;i<INITIAL_STYLE_ROWS;i++)addStyleRow();
    }

    private void displayIcon(StyleRow row,Uri uri,boolean missing) {
        final int serial=++row.previewSerial;
        final int index=Math.max(0,styleRows.indexOf(row));
        final int mode=row.background.getSelectedItemPosition()==1?1:0;
        row.preview.setImageDrawable(null);
        row.preview.setBackgroundColor(mode==1?0xffdddddd:Color.WHITE);
        row.description.setText(
            missing?"Saved image unavailable — choose again"
            :uri==null?getString(R.string.no_image)
            :"Preparing preview: "+displayName(uri));
        if(uri==null)return;
        new Thread(() -> {
            try {
                byte[] input=readIconBytes(uri,index);
                byte[] normalized=IconNormalizer.normalize(input,index+1);
                Bitmap result=BitmapFactory.decodeByteArray(normalized,0,normalized.length);
                if(result==null)throw new IOException("Cannot decode preview");
                runOnUiThread(() -> {
                    if(!styleRows.contains(row)||serial!=row.previewSerial){
                        result.recycle();return;
                    }
                    row.preview.setImageBitmap(result);
                    row.description.setText("Preview: "+displayName(uri)+
                            (mode==1?" · transparent":" · white"));
                });
            } catch(Exception error) {
                runOnUiThread(() -> {
                    if(!styleRows.contains(row)||serial!=row.previewSerial)return;
                    row.description.setText("Image error: "+error.getMessage());
                });
            }
        }).start();
    }

    private void startPatch(boolean single) {
        startPatch(single,false);
    }

    private void startPatch(boolean single,boolean obbAcknowledged) {
        if(single && sourceHasObb && !obbAcknowledged){
            new AlertDialog.Builder(this)
                    .setTitle("XAPK contains OBB game data")
                    .setMessage("The APK modules can be merged into one APK, but OBB expansion data will remain outside it. The app may still need its OBB files installed separately. Continue exporting APK only?")
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton("Export APK",(dialog,which)->startPatch(true,true))
                    .show();
            return;
        }
        if(single && !canBuildSingle){
            status.setText("No compatible standalone APK exists in this package. Converting arbitrary splits to one APK needs a full resource/DEX/native merge.");
            return;
        }
        if(inspecting || (apkUri==null && installedApkSources==null)
                || currentPackage==null) {
            status.setText(R.string.status_need_package);
            return;
        }
        final String chosenPackage=currentPackage;
        final int patchSerial=selectionSerial;
        final String chosenInputName=currentApkName;
        final String chosenLauncher=launcherOptions.length==0?null:
                launcherOptions[Math.min(launcherPicker.getSelectedItemPosition()<0?0:
                        launcherPicker.getSelectedItemPosition(),launcherOptions.length-1)];
        if(chosenLauncher!=null)getPreferences(MODE_PRIVATE).edit().putString(
                "launcher:"+chosenPackage,chosenLauncher).apply();
        final Uri selectedApk=apkUri;
        final File[] chosenFiles=installedApkSources==null?null:installedApkSources.clone();
        // Only complete name/icon pairs are used. Gaps are compacted:
        // e.g. only Style 3 filled creates Alias0 and Alias1.
        final java.util.ArrayList<String> namesToPatch=new java.util.ArrayList<>();
        final java.util.ArrayList<Uri> iconsToPatch=new java.util.ArrayList<>();
        final java.util.ArrayList<Integer> backgroundsToPatch=new java.util.ArrayList<>();
        for(int i=0;i<styleRows.size();i++){
            StyleRow row=styleRows.get(i);
            String name=row.name.getText().toString().trim();
            Uri icon=row.icon;
            if(name.isEmpty() && icon==null)continue;
            if(name.isEmpty() || icon==null){
                status.setText(getString(R.string.status_incomplete_style,i+1));
                uiSections.show(2);
                return;
            }
            namesToPatch.add(name);
            iconsToPatch.add(icon);
            backgroundsToPatch.add(
                    row.background.getSelectedItemPosition()==1?1:0);
        }
        if(namesToPatch.isEmpty()){
            status.setText(R.string.status_need_styles);
            uiSections.show(2);
            return;
        }
        final String[] labels=namesToPatch.toArray(new String[0]);
        final Uri[] chosenIcons=iconsToPatch.toArray(new Uri[0]);
        final int[] selectedBackgrounds=new int[backgroundsToPatch.size()];
        for(int i=0;i<selectedBackgrounds.length;i++)
            selectedBackgrounds[i]=backgroundsToPatch.get(i);
        final SigningPanel.Choice signingChoice=signingPanel.snapshot();
        saveCurrentStyles();
        patchButton.setEnabled(false);
        singleButton.setEnabled(false);
        uiSections.busy(true);
        status.setText(R.string.status_patching);
        new Thread(() -> {
            File input=null, dex=null, key=null,output=null;
            try{
                if(chosenFiles==null){
                    input=File.createTempFile("package-input-",
                            packageSuffix(chosenInputName),getCacheDir());
                    try(InputStream in=requireStream(selectedApk);
                        OutputStream out=new FileOutputStream(input)){copy(in,out);}
                }
                dex=File.createTempFile("asw-module-", ".dex",getCacheDir());
                key=File.createTempFile("asw-key-", ".p12",getCacheDir());
                copyAsset("vance_core.dex",dex);
                byte[][] images=new byte[chosenIcons.length][];
                for(int i=0;i<chosenIcons.length;i++)images[i]=readIconBytes(chosenIcons[i],i);
                byte[] brandIcon;
                try(java.io.InputStream stream=getResources().openRawResource(R.drawable.brand_icon);
                    java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream()){
                    byte[] chunk=new byte[8192];
                    int n;
                    while((n=stream.read(chunk))!=-1)buffer.write(chunk,0,n);
                    brandIcon=buffer.toByteArray();
                }
                SigningKey signing=createSigner(signingChoice,key);
                signing.verify();
                final String signerFingerprint=signing.fingerprint();
                final String signingCompatibility=installedSignatureMatch(
                        chosenPackage,signerFingerprint);
                try(PackageSets.Stage set=PackageSets.prepare(input,chosenFiles,getCacheDir())){
                    if(!chosenPackage.equals(set.packageName))
                        throw new IOException("Package identity changed while preparing input");
                    output=File.createTempFile("refurbished-out-",
                            single?".apk":set.extension(),getCacheDir());
                    PackageSets.Output result;
                    if(single){
                        ApkPatcher.Result singleResult=set.patchSingle(dex,labels,images,
                                selectedBackgrounds,signing,output,chosenLauncher,brandIcon);
                        result=new PackageSets.Output(singleResult,".apk",1);
                    }else{
                        result=set.patch(dex,key,labels,images,
                                selectedBackgrounds,signing,output,chosenLauncher,brandIcon);
                    }
                    if(!chosenPackage.equals(result.result.packageName))
                        throw new IOException("Unexpected patched package name");
                    final File completed=output;
                    final String extension=result.extension;
                    final int modules=result.count;
                    final java.util.List<String> warnings=result.result.warnings;
                    output=null;
                    runOnUiThread(() -> {
                        if(patchSerial!=selectionSerial ||
                                !chosenPackage.equals(currentPackage)){
                            completed.delete();
                            return;
                        }
                        if(signedOutput!=null)signedOutput.delete();
                        signedOutput=completed;
                        signedOutputPackage=result.result.packageName;
                        signerStatus.setText("Signing SHA-256: "+signerFingerprint+
                                "\n"+signingCompatibility);
                        generatedExtension=extension;
                        patchButton.setEnabled(true);
                        singleButton.setEnabled(canBuildSingle);
                        uiSections.busy(false);
                        uiSections.show(4);
                        exportButton.setEnabled(true);
                        status.setText(getString(R.string.status_signed,modules,
                                extension.substring(1).toUpperCase(java.util.Locale.ROOT)));
                        requestExport();
                        if(!warnings.isEmpty())new AlertDialog.Builder(this)
                                .setTitle("Compatibility notes")
                                .setMessage(android.text.TextUtils.join("\n",warnings))
                                .setPositiveButton("OK",null).show();
                    });
                }
            }catch(Exception e){
                runOnUiThread(() -> {
                    if(patchSerial!=selectionSerial)return;
                    patchButton.setEnabled(true);
                    singleButton.setEnabled(canBuildSingle);
                    uiSections.busy(false);
                    error(e);
                });
            }finally{
                signingChoice.erase();
                if(input!=null)input.delete();
                if(dex!=null)dex.delete();
                if(key!=null)key.delete();
                if(output!=null)output.delete();
            }
        }).start();
    }

    private byte[] readIconBytes(Uri uri,int index) throws IOException {
        try(InputStream input=requireStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[16384];
            int n,total=0;
            while((n=input.read(buffer))!=-1) {
                total+=n;
                if(total>MAX_PNG_BYTES)
                    throw new IOException("Image "+(index+1)+" exceeds 24 MB");
                bytes.write(buffer,0,n);
            }
            return bytes.toByteArray();
        }
    }

    private void requestOriginalExport() {
        if(installedApkSources==null||installedApkSources.length==0){
            status.setText("No installed package source selected.");
            return;
        }
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType(packageMime(sourceExtension));
        requestedOriginalName=(currentPackage==null?"installed":currentPackage)+
                ".original"+sourceExtension;
        save.putExtra(Intent.EXTRA_TITLE,requestedOriginalName);
        startActivityForResult(save,SAVE_ORIGINAL);
    }

    private void exportOriginalApk(Uri destination) {
        destination=ensureDocumentName(destination,requestedOriginalName);
        final Uri destinationUri=destination;
        final File[] installed=installedApkSources==null?
                null:installedApkSources.clone();
        if(installed==null){
            status.setText("Installed package source unavailable.");
            return;
        }
        status.setText("Exporting original "+installed.length+" APK module(s)...");
        new Thread(() -> {
            File original=null;
            try(PackageSets.Stage stage=PackageSets.prepare(null,installed,getCacheDir())){
                original=File.createTempFile("original-package-",
                        stage.multi()?".apks":".apk",getCacheDir());
                stage.exportOriginal(original);
                try(InputStream input=new FileInputStream(original);
                    OutputStream output=getContentResolver().openOutputStream(destinationUri)){
                    if(output==null)throw new IOException("Cannot open output document");
                    copy(input,output);
                }
                runOnUiThread(() -> status.setText(
                        "Saved full original package: "+displayName(destinationUri)));
            } catch(Exception e){
                runOnUiThread(() -> error(e));
            } finally {
                if(original!=null)original.delete();
            }
        }).start();
    }

    private void requestExport() {
        if(signedOutput==null || !signedOutput.isFile()){
            status.setText(R.string.status_no_signed_package);
            exportButton.setEnabled(false);
            return;
        }
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType(packageMime(generatedExtension));
        requestedExportName=(signedOutputPackage==null?"base":signedOutputPackage)+
                ".refurbished"+generatedExtension;
        save.putExtra(Intent.EXTRA_TITLE,requestedExportName);
        startActivityForResult(save,SAVE_APK);
    }

    private void exportSignedApk(Uri destination) {
        File file=signedOutput;
        if(file==null)return;
        destination=ensureDocumentName(destination,requestedExportName);
        final Uri savedDestination=destination;
        try(InputStream input=new FileInputStream(file);
            OutputStream output=getContentResolver().openOutputStream(savedDestination)) {
            if(output==null)throw new IOException("Cannot open output document");
            copy(input,output);
            status.setText(getString(R.string.status_saving,displayName(savedDestination)));
            lastSavedUri=savedDestination;
            installSavedButton.setEnabled(true);
            file.delete();
            if(file==signedOutput)signedOutput=null;
            exportButton.setEnabled(false);
        } catch(Exception e) {
            // Keep the signed file for a retry via 'Save last generated APK'.
            error(e);
        }
    }

    private void installFromUri(Uri uri) {
        if(uri==null){status.setText(R.string.status_select_install);return;}
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this)
                .setTitle("Allow app installation")
                .setMessage("Android requires your approval before Refurbished can request installation. Enable 'Allow from this source', then retry Install.")
                .setPositiveButton("Open settings",(d,w)->{
                    Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+getPackageName()));
                    startActivity(settings);
                })
                .setNegativeButton(android.R.string.cancel,null).show();
            return;
        }
        status.setText(R.string.status_installing);
        uiSections.busy(true);
        new Thread(() -> {
            File input=null;
            try{
                input=File.createTempFile("asw-install-",
                        packageSuffix(displayName(uri)),getCacheDir());
                try(InputStream stream=requireStream(uri);
                    OutputStream out=new FileOutputStream(input)){copy(stream,out);}
                try(PackageSets.Stage stage=PackageSets.prepare(input,null,getCacheDir())){
                    PackageInstall.install(this,stage);
                    int count=stage.apks.size();
                    runOnUiThread(() -> {
                        uiSections.busy(false);
                        status.setText(getString(R.string.status_install_submitted,count));
                    });
                }
            }catch(Exception e){runOnUiThread(() -> {uiSections.busy(false);error(e);});}
            finally{if(input!=null)input.delete();}
        }).start();
    }

    private static String packageMime(String ext) {
        switch(ext){
            case ".apk":return "application/vnd.android.package-archive";
            case ".apks":return "application/x-apks";
            case ".xapk":return "application/x-xapk";
            case ".apkm":return "application/vnd.apkm";
            default:return "application/octet-stream";
        }
    }

    private Uri ensureDocumentName(Uri uri,String requested) {
        if(requested==null || uri==null)return uri;
        String actual=displayName(uri);
        if(actual.equalsIgnoreCase(requested))return uri;
        String suffix=actual.toLowerCase(java.util.Locale.ROOT);
        if(!suffix.endsWith(".zip") && !suffix.endsWith(".bin"))return uri;
        try{
            Uri renamed=DocumentsContract.renameDocument(getContentResolver(),uri,requested);
            return renamed==null?uri:renamed;
        }catch(Exception ignored){return uri;}
    }

    private static String packageSuffix(String name) {
        if(name==null)return ".apk";
        String lower=name.toLowerCase(java.util.Locale.ROOT);
        for(String suffix:new String[]{".apkm",".xapk",".apks",".apk"}){
            if(lower.endsWith(suffix))return suffix;
        }
        return ".apk";
    }

    private InputStream requireStream(Uri uri) throws IOException {
        if(uri==null)throw new IOException("Document not selected");
        InputStream stream=getContentResolver().openInputStream(uri);
        if(stream==null)throw new IOException("Document unavailable: "+uri);
        return stream;
    }

    private void copyAsset(String name,File target) throws IOException {
        try(InputStream input=getAssets().open(name);
            OutputStream output=new FileOutputStream(target)) {
            copy(input,output);
        }
    }
    private static void copy(InputStream input,OutputStream output) throws IOException {
        byte[] buffer=new byte[16384];
        int n;
        while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
    }
    private String displayName(Uri uri) {
        if(uri==null)return "(none)";
        try(Cursor c=getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())return c.getString(0);
        } catch(Exception ignored) { }
        return uri.getLastPathSegment()==null?uri.toString():uri.getLastPathSegment();
    }
    private TextView sectionTitle(int resId) {
        TextView view=text(getString(resId),20);
        view.setTypeface(null,android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView text(String value,int size) {
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextColor(UiSettings.dark(this)?Color.WHITE:Color.DKGRAY);
        view.setTextSize(size);
        view.setPadding(0,dp(5),0,dp(5));
        return view;
    }
    private void error(Exception failure) {
        status.setText(getString(R.string.status_failed,failure.getMessage()));
        new AlertDialog.Builder(this).setTitle("Refurbish failed")
            .setMessage(failure.toString())
            .setPositiveButton("OK",null).show();
    }
    private int dp(int value) {
        return (int)(value*getResources().getDisplayMetrics().density+0.5f);
    }

    private void addDashboardModule(LinearLayout host,String name,String detail,int iconId,Runnable action){
        boolean dark=UiSettings.dark(this);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(10),dp(12),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(dark?0xff1d1d1d:0xfffafafa);
        bg.setCornerRadius(dp(13));bg.setStroke(dp(1),dark?0xff666666:0xffc9c9c9);card.setBackground(bg);
        ImageView icon=new ImageView(this);icon.setImageResource(iconId);icon.setColorFilter(dark?Color.WHITE:Color.BLACK);
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(42),dp(42));iconLp.rightMargin=dp(14);card.addView(icon,iconLp);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading=text(name,16);heading.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView caption=text(detail,12);labels.addView(heading);labels.addView(caption);
        card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",26);card.addView(arrow);card.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);host.addView(card,lp);
    }
    private void moduleNotReady(String name){
        new AlertDialog.Builder(this).setTitle(name).setMessage(R.string.module_not_ready)
                .setPositiveButton(android.R.string.ok,null).show();
    }
    private void moreModules(){
        java.util.List<ModuleRegistry.Entry> entries=ModuleRegistry.moreModules();
        if(entries.isEmpty()){
            new AlertDialog.Builder(this).setTitle(R.string.more_modules).setMessage(R.string.more_modules_hint)
                    .setPositiveButton(android.R.string.ok,null).show();
            return;
        }
        String[] labels=new String[entries.size()];
        for(int i=0;i<entries.size();i++)labels[i]=entries.get(i).title;
        new AlertDialog.Builder(this).setTitle(R.string.more_modules).setItems(labels,(dialog,which)->
                startActivity(new Intent(this,entries.get(which).activity))).setNegativeButton(R.string.cancel,null).show();
    }

}
