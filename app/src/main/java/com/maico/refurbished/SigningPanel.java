package com.maico.refurbished;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Arrays;

/** Signing-mode controls; passwords are kept in memory only, never in preferences. */
final class SigningPanel {
    interface ImportKey { void choose(); }
    private final SignerManager manager;
    final Spinner mode;
    private final EditText alias,storePassword,keyPassword,portablePassword;
    private final TextView customInfo,details;
    private Uri customUri;
    private boolean restoring;

    SigningPanel(Activity activity,SignerManager manager,ImportKey importKey) {
        this.manager=manager;
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(activity);
        heading.setText(R.string.signing_identity);
        heading.setTextSize(18);
        layout.addView(heading);
        mode=new Spinner(activity);
        ArrayAdapter<String> modes=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,new String[]{
                activity.getString(R.string.sign_mode_default),
                activity.getString(R.string.sign_mode_device),
                activity.getString(R.string.sign_mode_custom),
                activity.getString(R.string.sign_mode_portable)});
        modes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(modes);
        layout.addView(mode);
        Button selectCustom=new Button(activity);
        selectCustom.setText(R.string.select_custom_key);
        FlatUi.button(activity,selectCustom,R.drawable.ic_flat_key);
        selectCustom.setOnClickListener(v->importKey.choose());
        layout.addView(selectCustom);
        customInfo=new TextView(activity);
        layout.addView(customInfo);
        alias=new EditText(activity);
        alias.setSingleLine(true);
        alias.setHint(R.string.key_alias);
        alias.setText(manager.customAlias());
        layout.addView(alias);
        storePassword=new EditText(activity);
        storePassword.setSingleLine(true);
        storePassword.setHint(R.string.store_password);
        storePassword.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(storePassword);
        portablePassword=new EditText(activity);
        portablePassword.setSingleLine(true);
        portablePassword.setHint(R.string.portable_password);
        portablePassword.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(portablePassword);
        keyPassword=new EditText(activity);
        keyPassword.setSingleLine(true);
        keyPassword.setHint(R.string.key_password);
        keyPassword.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(keyPassword);
        details=new TextView(activity);
        layout.addView(details);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view,int position,long id){
                boolean custom=position==SignerManager.CUSTOM;
                selectCustom.setVisibility(custom?android.view.View.VISIBLE:android.view.View.GONE);
                customInfo.setVisibility(custom?android.view.View.VISIBLE:android.view.View.GONE);
                alias.setVisibility(custom?android.view.View.VISIBLE:android.view.View.GONE);
                storePassword.setVisibility(custom?android.view.View.VISIBLE:android.view.View.GONE);
                keyPassword.setVisibility(custom?android.view.View.VISIBLE:android.view.View.GONE);
                portablePassword.setVisibility(position==SignerManager.PORTABLE?
                        android.view.View.VISIBLE:android.view.View.GONE);
                details.setText(position==SignerManager.DEFAULT
                        ?activity.getString(R.string.key_desc_default)
                        :position==SignerManager.PRIVATE
                        ?activity.getString(R.string.key_desc_device)
                        :position==SignerManager.CUSTOM
                        ?activity.getString(R.string.key_desc_custom)
                        :activity.getString(R.string.key_desc_portable));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        String saved=manager.customUri();
        if(saved!=null)customUri=Uri.parse(saved);
        activityPanel=layout;
        updateUri();
    }
    private final LinearLayout activityPanel;
    LinearLayout view(){return activityPanel;}
    int selectedMode(){return mode.getSelectedItemPosition();}
    void restoreMode(String pkg){mode.setSelection(manager.rememberedMode(pkg));}
    void remember(String pkg){
        manager.rememberMode(pkg,selectedMode());
        if(selectedMode()==SignerManager.CUSTOM)
            manager.rememberCustomAlias(alias.getText().toString().trim());
    }
    void setCustomUri(Uri uri){
        customUri=uri;manager.rememberCustomUri(uri);updateUri();
        mode.setSelection(SignerManager.CUSTOM);
    }
    private void updateUri(){
        customInfo.setText(customUri==null?activityPanel.getContext().getString(R.string.custom_key_missing)
                :"Selected keystore: "+customUri.getLastPathSegment());
    }
    static final class Choice {
        final int mode;
        final Uri uri;
        final String alias;
        final char[] store,key,portable;
        Choice(int mode,Uri uri,String alias,char[] store,char[] key,char[] portable){
            this.mode=mode;this.uri=uri;this.alias=alias;this.store=store;this.key=key;this.portable=portable;
        }
        void erase(){Arrays.fill(store,'\0');Arrays.fill(key,'\0');Arrays.fill(portable,'\0');}
    }
    Choice snapshot(){
        return new Choice(selectedMode(),customUri,alias.getText().toString().trim(),
                storePassword.getText().toString().toCharArray(),
                keyPassword.getText().toString().toCharArray(),
                portablePassword.getText().toString().toCharArray());
    }
}
