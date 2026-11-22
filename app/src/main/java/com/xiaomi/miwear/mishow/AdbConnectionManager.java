package com.xiaomi.miwear.mishow;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/** Gestor de conexión ADB. La llave se guarda en disco: se empareja UNA vez y queda autorizada. */
public class AdbConnectionManager extends AbsAdbConnectionManager {
    private static AbsAdbConnectionManager INSTANCE;

    public static synchronized AbsAdbConnectionManager getInstance(Context ctx) throws Exception {
        if (INSTANCE == null) INSTANCE = new AdbConnectionManager(ctx.getApplicationContext());
        return INSTANCE;
    }

    private PrivateKey mPrivateKey;
    private Certificate mCertificate;

    private AdbConnectionManager(Context ctx) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        File keyFile = new File(ctx.getFilesDir(), "adb_key.pk8");
        File certFile = new File(ctx.getFilesDir(), "adb_cert.der");

        if (keyFile.exists() && certFile.exists()) {
            try {
                mPrivateKey = KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(Files.readAllBytes(keyFile.toPath())));
                mCertificate = CertificateFactory.getInstance("X.509").generateCertificate(
                        new ByteArrayInputStream(Files.readAllBytes(certFile.toPath())));
            } catch (Exception e) {
                mPrivateKey = null;
                mCertificate = null;
            }
        }

        if (mPrivateKey == null || mCertificate == null) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            PublicKey publicKey = kp.getPublic();
            mPrivateKey = kp.getPrivate();

            X500Name name = new X500Name("CN=MiShow Guard");
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now - 86400000L);
            Date notAfter = new Date(now + 3650L * 86400000L); // ~10 años
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(now), notBefore, notAfter, name, publicKey);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(mPrivateKey);
            mCertificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

            Files.write(keyFile.toPath(), mPrivateKey.getEncoded());
            Files.write(certFile.toPath(), mCertificate.getEncoded());
        }
    }

    @Override
    public PrivateKey getPrivateKey() { return mPrivateKey; }

    @Override
    public Certificate getCertificate() { return mCertificate; }

    @Override
    public String getDeviceName() { return "MiShowGuard"; }
}
