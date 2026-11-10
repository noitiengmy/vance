package com.maico.refurbished.patcher;

import com.android.apksig.ApkSigner;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/** Signing identity for all APK modules in a package. */
public final class SigningKey {
    private final File file;
    private final String type, alias;
    private final char[] storePassword, keyPassword;
    private final boolean deviceKey;

    public SigningKey(File file,String type,String alias,char[] storePassword,char[] keyPassword) {
        this.file=file; this.type=type; this.alias=alias;
        this.storePassword=storePassword.clone();
        this.keyPassword=keyPassword==null?storePassword.clone():keyPassword.clone();
        this.deviceKey=false;
    }

    private SigningKey(String deviceAlias) {
        this.file=null;this.type="AndroidKeyStore";this.alias=deviceAlias;
        this.storePassword=new char[0];this.keyPassword=new char[0];this.deviceKey=true;
    }
    public static SigningKey androidKeyStore(String alias) {
        return new SigningKey(alias);
    }

    private KeyStore openStore() throws Exception {
        KeyStore store=KeyStore.getInstance(type);
        if(deviceKey)store.load(null);
        else try(FileInputStream in=new FileInputStream(file)){
            store.load(in,storePassword);
        }
        return store;
    }
    public String fingerprint() throws Exception {
        Certificate certificate=openStore().getCertificate(alias);
        if(certificate==null)throw new IOException("No certificate for alias: "+alias);
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder result=new StringBuilder();
        for(byte b:hash){
            if(result.length()!=0)result.append(':');
            result.append(String.format(java.util.Locale.ROOT,"%02X",b & 0xff));
        }
        return result.toString();
    }

    public void verify() throws Exception {
        KeyStore store=openStore();
        Key key=store.getKey(alias,deviceKey?null:keyPassword);
        if(!(key instanceof PrivateKey))
            throw new IOException("No private signing key for alias: "+alias);
        Certificate[] chain=store.getCertificateChain(alias);
        if(chain==null||chain.length==0)
            throw new IOException("Certificate chain missing for: "+alias);
    }

    public void sign(File unsigned,File signed) throws Exception {
        KeyStore store=openStore();
        Key key=store.getKey(alias,deviceKey?null:keyPassword);
        if(!(key instanceof PrivateKey))
            throw new IOException("Private key not found for alias "+alias);
        Certificate[] chain=store.getCertificateChain(alias);
        if(chain==null||chain.length==0)
            throw new IOException("Missing certificate chain for "+alias);
        List<X509Certificate> certs=new ArrayList<>();
        for(Certificate certificate:chain)certs.add((X509Certificate)certificate);
        ApkSigner.SignerConfig config=new ApkSigner.SignerConfig.Builder(
                alias,(PrivateKey)key,certs).build();
        new ApkSigner.Builder(Collections.singletonList(config))
                .setInputApk(unsigned).setOutputApk(signed)
                .setMinSdkVersion(14).setV1SigningEnabled(true)
                .setV2SigningEnabled(true).setV3SigningEnabled(true)
                .build().sign();
    }
}
