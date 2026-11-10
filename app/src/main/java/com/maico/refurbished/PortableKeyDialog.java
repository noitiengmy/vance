package com.maico.refurbished;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;

/** Explicit confirmations for creating/importing/exporting private signing identities. */
final class PortableKeyDialog {
    interface PasswordAction { void run(char[] password); }
    private PortableKeyDialog(){}

    static void ask(Activity activity,String title,String message,PasswordAction action){
        EditText password=new EditText(activity);
        password.setSingleLine(true);
        password.setHint("Portable P12 password");
        password.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout content=new LinearLayout(activity);
        content.setPadding(24,8,24,8);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView hint=new TextView(activity);hint.setText(message);
        content.addView(hint);
        content.addView(password);
        new AlertDialog.Builder(activity).setTitle(title).setView(content)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton("Continue",(dialog,which)->{
                    char[] secret=password.getText().toString().toCharArray();
                    try {action.run(secret);}
                    finally {Arrays.fill(secret,'\0');password.setText("");}
                }).show();
    }
}
