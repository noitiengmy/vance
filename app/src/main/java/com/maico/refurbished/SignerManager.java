package com.maico.refurbished;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.maico.refurbished.patcher.SigningKey;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.util.Calendar;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

/** Manages independent signing identities without persisting keystore passwords. */
final class SignerManager {
    static final int DEFAULT=0, PRIVATE=1, CUSTOM=2, PORTABLE=3;
    static final String PRIVATE_ALIAS="asw_private_signer_v1";
    private final Context context;
    private final SharedPreferences prefs;

    SignerManager(Context app){
        context=app.getApplicationContext();
        prefs=context.getSharedPreferences("asw_signing_choices",Context.MODE_PRIVATE);
    }
    int rememberedMode(String pkg){
        return pkg==null?DEFAULT:prefs.getInt("mode:"+pkg,DEFAULT);
    }
    void rememberMode(String pkg,int mode){
        if(pkg!=null)prefs.edit().putInt("mode:"+pkg,mode).apply();
    }
    String customUri(){
        return prefs.getString("custom:uri",null);
    }
    void rememberCustomUri(Uri uri){
        prefs.edit().putString("custom:uri",uri.toString()).apply();
    }
    String customAlias(){
        return prefs.getString("custom:alias","alias");
    }
    void rememberCustomAlias(String alias){
        prefs.edit().putString("custom:alias",alias).apply();
    }
    static String guessType(String filename){
        if(filename==null)return "PKCS12";
        String name=filename.toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jks")||name.endsWith(".keystore")?"JKS":"PKCS12";
    }

    synchronized SigningKey privateSigner() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if(!store.containsAlias(PRIVATE_ALIAS)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            Calendar until=Calendar.getInstance();
            until.add(Calendar.YEAR,25);
            KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(
                    PRIVATE_ALIAS,KeyProperties.PURPOSE_SIGN)
                    .setKeySize(3072)
                    .setDigests(KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(new X500Principal(
                            "CN=Refurbished private signing key, O=Refurbished"))
                    .setCertificateSerialNumber(BigInteger.valueOf(3L))
                    .setCertificateNotBefore(new Date())
                    .setCertificateNotAfter(until.getTime())
                    .build();
            generator.initialize(spec);
            generator.generateKeyPair();
        }
        SigningKey signing=SigningKey.androidKeyStore(PRIVATE_ALIAS);
        signing.verify();
        return signing;
    }

    SigningKey customSigner(Uri uri,String alias,char[] storePassword,
                            char[] keyPassword,File temporary) throws Exception {
        if(uri==null)throw new IOException("Choose a JKS or PKCS12 keystore");
        if(alias==null||alias.trim().isEmpty())
            throw new IOException("Enter keystore alias");
        try(InputStream in=context.getContentResolver().openInputStream(uri);
            OutputStream out=new FileOutputStream(temporary)){
            if(in==null)throw new IOException("Keystore URI is no longer accessible");
            byte[] buffer=new byte[16384];int n;long total=0;
            while((n=in.read(buffer))!=-1){
                total+=n;
                if(total>32*1024*1024)
                    throw new IOException("Keystore exceeds 32 MB");
                out.write(buffer,0,n);
            }
        }
        String name=uri.getLastPathSegment();
        String preferred=guessType(name);
        Exception failure=null;
        for(String type:new String[]{preferred,
                "PKCS12".equals(preferred)?"JKS":"PKCS12"}){
            try {
                SigningKey key=new SigningKey(temporary,type,alias.trim(),
                        storePassword,keyPassword);
                key.verify();
                return key;
            }catch(Exception e){failure=e;}
        }
        throw new IOException("Cannot unlock signing key / alias: "+
                (failure==null?"Unknown error":failure.getMessage()),failure);
    }
}
