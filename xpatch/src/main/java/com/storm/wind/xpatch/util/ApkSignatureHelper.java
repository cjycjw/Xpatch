package com.storm.wind.xpatch.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
/**
 * Created by Wind
 */
public class ApkSignatureHelper {

    public static char[] toChars(byte[] mSignature) {
        byte[] sig = mSignature;
        final int N = sig.length;
        final int N2 = N * 2;
        char[] text = new char[N2];
        for (int j = 0; j < N; j++) {
            byte v = sig[j];
            int d = (v >> 4) & 0xf;
            text[j * 2] = (char) (d >= 10 ? ('a' + d - 10) : ('0' + d));
            d = v & 0xf;
            text[j * 2 + 1] = (char) (d >= 10 ? ('a' + d - 10) : ('0' + d));
        }
        return text;
    }

    private static Certificate[] loadCertificates(JarFile jarFile, JarEntry je, byte[] readBuffer) {
        try {
            InputStream is = jarFile.getInputStream(je);
            while (is.read(readBuffer, 0, readBuffer.length) != -1) {
            }
            is.close();
            return (Certificate[]) (je != null ? je.getCertificates() : null);
        } catch (Exception e) {
            System.out.println("Exception reading " + je.getName() + " in "
                    + jarFile.getName() + ": " + e);
        }
        return null;
    }

    /*
    默认编译的版本，在java8（jdk8）上运行良好，但在java13(jdk13)和java14(jdk14)上运行会报错，
    报错行 System.out.println("  getApkSignInfo  result -->  " + certs[0]); 这个certs为空，
    尝试网上拷贝了一份PackageVerifyer.java来获取证书，也都是为空，最后放弃用JarEntry的方式获取证书，直接读取META-INF/下的*.RSA证书，这个在java14上可以运行成功，
    见getApkSignInfo2方法，获取的证书是JarEntry获取的是一样的。
    报错信息如下：
    \f0\fs22 \cf2 \CocoaLigature0 yongqiangdeMacBook-Air:~ yongqiangzhu$ java -jar /Users/yongqiangzhu/Desktop/patch.jar /Users/yongqiangzhu/Desktop/2020.6.30/HiThin.feature218.release.000000.20200628195609.aligned.apk -f -l -o haishou1.apk\
 currentDir = /Users/yongqiangzhu/. \
  apkPath = /Users/yongqiangzhu/Desktop/2020.6.30/HiThin.feature218.release.000000.20200628195609.aligned.apk\
 !!!!! output apk path -->  haishou1.apk  disableCrackSignature --> false\
 !!!!! outputApkFileParentPath  =  /Users/yongqiangzhu\
 unzipApkFilePath = /Users/yongqiangzhu/2020-07-02-17-57-53-tmp/HiThin.feature218.release.000000.20200628195609.aligned-apk-unzip-files/\
java.lang.NullPointerException\
	at com.storm.wind.xpatch.util.ApkSignatureHelper.getApkSignInfo(ApkSignatureHelper.java:75)\
	at com.storm.wind.xpatch.task.SaveApkSignatureTask.run(SaveApkSignatureTask.java:26)\
	at com.storm.wind.xpatch.MainCommand.doCommandLine(MainCommand.java:151)\
	at com.storm.wind.xpatch.base.BaseCommand.doMain(BaseCommand.java:125)\
	at com.storm.wind.xpatch.MainCommand.main(MainCommand.java:83)\
 Get original signature failed !!!!\
 decompress apk cost time:  2538\
 --- dexFileCount = 3\
 Get application name cost time:  21\
 Get the application name --> com.hi.shou.enjoy.health.cn.MainApplication\
SaveOriginalApplicationNameTask  cost time:  0\
SoAndDexCopyTask  cost time:  34\
 sign apk time is :9s\
\
  result=jar
\f1 \'d2\'d1\'c7\'a9\'c3\'fb\'a1\'a3
\f0 \
\

\f1 \'be\'af\'b8\'e6
\f0 : \

\f1 \'c7\'a9\'c3\'fb\'d5\'df\'d6\'a4\'ca\'e9\'ce\'aa\'d7\'d4\'c7\'a9\'c3\'fb\'d6\'a4\'ca\'e9\'a1\'a3
\f0 \
\
BuildAndSignApkTask  cost time:  16132}
    * */
    public static String getApkSignInfo(String apkFilePath) {
        byte[] readBuffer = new byte[8192];
        Certificate[] certs = null;
        try {
            JarFile jarFile = new JarFile(apkFilePath);
            Enumeration<?> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry je = (JarEntry) entries.nextElement();
                if (je.isDirectory()) {
                    continue;
                }
                if (je.getName().startsWith("META-INF/")) {
                    continue;
                }
                Certificate[] localCerts = loadCertificates(jarFile, je, readBuffer);
                if (localCerts == null) {
                    System.err.println("Package has no certificates at entry "
                            + je.getName() + "; ignoring!");
                    jarFile.close();
                    return null;
                } else if (certs == null) {
                    certs = localCerts;
                } else { // Ensure all certificates match.
                    for (int i = 0; i < certs.length; i++) {
                        boolean found = false;
                        for (int j = 0; j < localCerts.length; j++) {
                            if (certs[i] != null && certs[i].equals(localCerts[j])) {
                                found = true;
                                break;
                            }
                        }
                        if (!found || certs.length != localCerts.length) {
                            jarFile.close();
                            return null;
                        }
                    }
                }
            }
            jarFile.close();
            if (certs != null && certs.length > 0) {
                System.out.println("  getApkSignInfo  result -->  " + certs[0]);
                String signResult = new String(toChars(certs[0].getEncoded()));
                System.out.println("signResult-->  "  + signResult);
                return signResult;
            } else {
                System.out.println("Package "
                        + " has no certificates; ignoring!");
                System.out.println("INSTALL_PARSE_FAILED_NO_CERTIFICATES");
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getApkSignInfo2(String apkFilePath) {
        try {
            ZipFile zf = new ZipFile(apkFilePath);
            InputStream in = new BufferedInputStream(new FileInputStream(apkFilePath));
            ZipInputStream zis = new ZipInputStream(in);
            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.toUpperCase().endsWith(".RSA") || name.toUpperCase().endsWith(".DSA")) {
                    System.out.println(name);
                    //byte[] bytes = new byte[(int) entry.getSize()];
                    InputStream inputStream = zf.getInputStream(entry);
                   // inputStream.read(bytes);
                  //  ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

//                    Collection collection;
//                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
//                    collection = certificateFactory.generateCertificates(zf.getInputStream(entry));
//                    Certificate[] certs = new Certificate[collection.size()];
//                    String signResult = new String(toChars(certs[0].getEncoded()));

                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate c = (X509Certificate) cf.generateCertificates(inputStream).toArray()[0];
                    byte[] rawCert = c.getEncoded();
                    String signResult = new String(toChars(rawCert));
                    System.out.println("signResult2-->  "  + signResult);
                    return signResult;
                }
            }
        } catch (Throwable th) {
            System.out.println("getApkSignInfo2 failed! " + th);
            return null;
        }
        return null;
    }
}
