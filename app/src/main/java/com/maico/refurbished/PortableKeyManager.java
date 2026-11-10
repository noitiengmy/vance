package com.maico.refurbished;

import android.content.Context;
import com.maico.refurbished.patcher.SigningKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

/**
 * Portable, password-protected PKCS12 signer. Unlike AndroidKeyStore keys, this
 * can be backed up and imported. Never persists plaintext passwords.
 */
final class PortableKeyManager {
    static final String ALIAS="refurbished";
    private final File keyFile;

    PortableKeyManager(Context context){
        keyFile=new File(context.getFilesDir(),"portable_signing_v1.p12");
    }
    boolean exists(){return keyFile.isFile();}
    File file(){return keyFile;}
    SigningKey signer(char[] password) throws Exception {
        if(!exists())throw new IOException("Generate or import portable signing P12 first.");
        if(password==null||password.length<1)
            throw new IOException("Enter the private P12 password.");
        SigningKey key=new SigningKey(keyFile,"PKCS12",ALIAS,password,password);
        key.verify();
        return key;
    }
    synchronized SigningKey generate(char[] password) throws Exception {
        if(exists())throw new IOException("Portable signer already exists. Export a backup before replacing it.");
        if(password==null||password.length<12)
            throw new IOException("Use at least 12 characters for the portable P12 password.");
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072,new SecureRandom());
        KeyPair pair=generator.generateKeyPair();
        Date now=new Date();
        Calendar future=Calendar.getInstance();
        future.setTime(now);
        future.add(Calendar.YEAR,30);
        X500Name distinguished=new X500Name("CN=Refurbished Portable Signer,O=Refurbished");
        BigInteger serial=new BigInteger(128,new SecureRandom()).abs();
        if(serial.signum()==0)serial=BigInteger.ONE;
        JcaX509v3CertificateBuilder certBuilder=new JcaX509v3CertificateBuilder(
                distinguished,serial,now,future.getTime(),distinguished,pair.getPublic());
        ContentSigner certSigner=new JcaContentSignerBuilder("SHA256withRSA")
                .build(pair.getPrivate());
        X509CertificateHolder holder=certBuilder.build(certSigner);
        X509Certificate certificate=new JcaX509CertificateConverter()
                .getCertificate(holder);
        certificate.verify(pair.getPublic());

        KeyStore store=KeyStore.getInstance("PKCS12");
        store.load(null,password);
        store.setKeyEntry(ALIAS,pair.getPrivate(),password,
                new java.security.cert.Certificate[]{certificate});
        File pending=new File(keyFile.getParentFile(),keyFile.getName()+".pending");
        try{
            try(OutputStream out=new FileOutputStream(pending)){store.store(out,password);}
            SigningKey check=new SigningKey(pending,"PKCS12",ALIAS,password,password);
            check.verify();
            if(!pending.renameTo(keyFile))throw new IOException("Unable to commit private signing key");
            return signer(password);
        }finally{if(pending.exists())pending.delete();}
    }
    synchronized void importKey(File incoming,char[] password,String importedAlias) throws Exception {
        if(exists())throw new IOException("Portable key exists. Export a backup and remove it deliberately before importing another.");
        if(password==null||password.length==0)throw new IOException("Enter the imported P12 password.");
        KeyStore store=KeyStore.getInstance("PKCS12");
        try(InputStream in=new FileInputStream(incoming)){store.load(in,password);}
        String alias=importedAlias;
        if(alias==null||alias.isEmpty()){
            java.util.Enumeration<String> names=store.aliases();
            while(names.hasMoreElements()){
                String name=names.nextElement();
                if(store.isKeyEntry(name)){alias=name;break;}
            }
        }
        if(alias==null||!store.isKeyEntry(alias))
            throw new IOException("Imported P12 has no private key entry.");
        Key key=store.getKey(alias,password);
        java.security.cert.Certificate[] chain=store.getCertificateChain(alias);
        if(!(key instanceof PrivateKey)||chain==null||chain.length==0)
            throw new IOException("Imported P12 lacks a signing key/certificate.");
        KeyStore normalized=KeyStore.getInstance("PKCS12");
        normalized.load(null,password);
        normalized.setKeyEntry(ALIAS,(PrivateKey)key,password,chain);
        File pending=new File(keyFile.getParentFile(),keyFile.getName()+".pending");
        try{
            try(OutputStream out=new FileOutputStream(pending)){normalized.store(out,password);}
            new SigningKey(pending,"PKCS12",ALIAS,password,password).verify();
            if(!pending.renameTo(keyFile))throw new IOException("Cannot save imported key");
        }finally{if(pending.exists())pending.delete();}
    }
    synchronized void delete() throws IOException {
        if(!exists())return;
        if(!keyFile.delete())throw new IOException("Unable to remove portable signing key.");
    }
}
