package com.storm.wind.xpatch.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by Wind
 */
public class FileUtils {

    static final int BUFFER = 8192;

    /**
     * 解压文件
     *
     * @param zipPath 要解压的目标文件
     * @param descDir 指定解压目录
     * @return 解压结果：成功，失败
     */
    @SuppressWarnings("rawtypes")
    public static boolean decompressZip(String zipPath, String descDir) {
        File zipFile = new File(zipPath);
        boolean flag = false;
        if (!descDir.endsWith(File.separator)) {
            descDir = descDir + File.separator;
        }
        File pathFile = new File(descDir);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }

        ZipFile zip = null;
        try {
            try {
                // api level 24 才有此方法
                zip = new ZipFile(zipFile, Charset.forName("gbk"));//防止中文目录，乱码
            } catch (NoSuchMethodError e) {
                // api < 24
                zip = new ZipFile(zipFile);
            }
            for (Enumeration entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                String zipEntryName = entry.getName();
                InputStream in = zip.getInputStream(entry);

                //指定解压后的文件夹+当前zip文件的名称
                String outPath = (descDir + zipEntryName).replace("/", File.separator);
                //判断路径是否存在,不存在则创建文件路径
                File file = new File(outPath.substring(0, outPath.lastIndexOf(File.separator)));

                if (!file.exists()) {
                    file.mkdirs();
                }
                //判断文件全路径是否为文件夹,如果是上面已经上传,不需要解压
                if (new File(outPath).isDirectory()) {
                    continue;
                }
                //保存文件路径信息（可利用md5.zip名称的唯一性，来判断是否已经解压）
//                System.err.println("当前zip解压之后的路径为：" + outPath);
                OutputStream out = new FileOutputStream(outPath);
                byte[] buf1 = new byte[2048];
                int len;
                while ((len = in.read(buf1)) > 0) {
                    out.write(buf1, 0, len);
                }
                close(in);
                close(out);
            }
            flag = true;
            close(zip);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return flag;
    }

    private static InputStream getInputStreamFromFile(String filePath) {
        return FileUtils.class.getClassLoader().getResourceAsStream(filePath);
    }

    // copy an asset file into a path
    public static void copyFileFromJar(String inJarPath, String distPath) {

//        System.out.println("start copyFile  inJarPath =" + inJarPath + "  distPath = " + distPath);
        InputStream inputStream = getInputStreamFromFile(inJarPath);

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            in = new BufferedInputStream(inputStream);
            out = new BufferedOutputStream(new FileOutputStream(distPath));

            int len = -1;
            byte[] b = new byte[1024];
            while ((len = in.read(b)) != -1) {
                out.write(b, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(in);
        }
    }

    public static void copyFile(String sourcePath, String targetPath) {
        copyFile(new File(sourcePath), new File(targetPath));
    }

    public static void copyFile(File source, File target) {

        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            FileChannel iChannel = inputStream.getChannel();
            FileChannel oChannel = outputStream.getChannel();

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (true) {
                buffer.clear();
                int r = iChannel.read(buffer);
                if (r == -1) {
                    break;
                }
                buffer.limit(buffer.position());
                buffer.position(0);
                oChannel.write(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(inputStream);
            close(outputStream);
        }
    }

    public static void deleteDir(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    public static void compressToZip(String srcPath, String dstPath, ZipFile zf) {
        File srcFile = new File(srcPath);
        File dstFile = new File(dstPath);
        if (!srcFile.exists()) {
            System.out.println(srcPath + " does not exist ！");
            return;
        }

        FileOutputStream out = null;
        ZipOutputStream zipOut = null;
        try {
            out = new FileOutputStream(dstFile);
            CheckedOutputStream cos = new CheckedOutputStream(out, new CRC32());
            zipOut = new ZipOutputStream(cos);
            String baseDir = "";
            compress(srcFile, zipOut, baseDir, true, zf);
        } catch (IOException e) {
            System.out.println(" compress exception = " + e.getMessage());
        } finally {
            try {
                if (zipOut != null) {
                    zipOut.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            close(zipOut);
            close(out);
        }
    }

    private static void compress(File file, ZipOutputStream zipOut, String baseDir, boolean isRootDir, ZipFile zf) throws IOException {
        if (file.isDirectory()) {
            compressDirectory(file, zipOut, baseDir, isRootDir, zf);
        } else {
            compressFile(file, zipOut, baseDir, zf);
        }
    }

    /**
     * 压缩一个目录
     */
    private static void compressDirectory(File dir, ZipOutputStream zipOut, String baseDir, boolean isRootDir, ZipFile zf) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            String compressBaseDir = "";
            if (!isRootDir) {
                compressBaseDir = baseDir + dir.getName() + "/";
            }
            compress(files[i], zipOut, compressBaseDir, false, zf);
        }
    }


    /**
     * 压缩一个文件
     * 参数zf是原apk压缩文件，主要作用就是获取文件在原zip文件中是否被压缩还是未被压缩直接存储，如果文件在原zip只是存储
     * 这边也不对其进行压缩而直接进行存储，否则会导致异常报错。
     *
     * 来电秀重打包后，运行起来后就直接崩溃了，崩溃日志如下：
     *     --------- beginning of crash
     * 2020-07-23 11:52:35.964 22202-22202/? E/AndroidRuntime: FATAL EXCEPTION: main
     *     Process: com.hunting.matrix_callershow, PID: 22202
     *     android.content.res.Resources$NotFoundException: File r/j/loading_animation.gif from drawable resource ID #0x7f080021
     *         at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:317)
     *         at android.content.res.Resources.openRawResourceFd(Resources.java:1293)
     *         at pl.droidsonroids.gif.i$b.a(SourceFile:220)
     *         at pl.droidsonroids.gif.i.a(SourceFile:31)
     *         at pl.droidsonroids.gif.d.a(SourceFile:63)
     *         at com.cootek.dialer.base.ui.AnimationUtil.showLoading(SourceFile:102)
     *         .....
     *
     *         2020-07-23 11:52:35.964 22202-22202/? E/AndroidRuntime:     at android.view.ViewRootImpl.performTraversals(ViewRootImpl.java:1772)
     *         at android.view.ViewRootImpl.doTraversal(ViewRootImpl.java:1403)
     *         at android.view.ViewRootImpl$TraversalRunnable.run(ViewRootImpl.java:6877)
     *         at android.view.Choreographer$CallbackRecord.run(Choreographer.java:966)
     *         at android.view.Choreographer.doCallbacks(Choreographer.java:778)
     *         at android.view.Choreographer.doFrame(Choreographer.java:713)
     *         at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:952)
     *         at android.os.Handler.handleCallback(Handler.java:790)
     *         at android.os.Handler.dispatchMessage(Handler.java:99)
     *         at android.os.Looper.loop(Looper.java:164)
     *         at android.app.ActivityThread.main(ActivityThread.java:6612)
     *         at java.lang.reflect.Method.invoke(Native Method)
     *         at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438)
     *         at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
     *      Caused by: java.io.FileNotFoundException: This file can not be opened as a file descriptor; it is probably compressed
     *         at android.content.res.AssetManager.openNonAssetFdNative(Native Method)
     *         at android.content.res.AssetManager.openNonAssetFd(AssetManager.java:487)
     *         at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:315)
     *         	... 66 more
     *
     *    错误上看就是加载loading_animation.gif图片是找不到资源出错了，然后前面有提示可能资源压缩了，
     *   然后在网上搜了一下发现类似的错误：https://github.com/koral--/android-gif-drawable/issues/253
     *   回复里面也提到是资源压缩问题：
     *   The reason is: This file can not be opened as a file descriptor; it is probably compressed.
     * By default if file has .gif extension it should not be compressed by aapt.
     * Does file which you try to load has .gif extension? If not then you can either add it or exclude it from compression like that (in build.gradle):
     *
     * android {
     *     aaptOptions {
     *         noCompress "<your file extension>"
     *     }
     * }
     *
     * 然后用unzip查看一下，确实原来的gif图片没有压缩，而重新打包好偶变成压缩了，如下图
     * unzip -lv callershow-release-6714-A0000X-2665-07231125.apk | grep loading
     *    30270  Defl:N    18150  40% 07-23-2020 11:29 90e83441  r/j/ksad_detail_loading_amin_top_2.json
     *     3947  Defl:N      787  80% 07-23-2020 11:29 30b8ee98  r/j/ksad_detail_loading_amin_top.json
     *   133153  Stored   133153   0% 07-23-2020 11:29 d98caaf4  r/j/loading_animation.gif
     *     1936  Defl:N      753  61% 07-23-2020 11:29 e694d894  r/a5/tt_playable_loading_layout.xml
     * unzip -lv test.apk | grep loading
     *         3947  Defl:N      787  80% 07-23-2020 11:42 30b8ee98  r/j/ksad_detail_loading_amin_top.json
     *   133153  Defl:N   127318   4% 07-23-2020 11:42 d98caaf4  r/j/loading_animation.gif
     *     2137  Defl:N      546  75% 07-23-2020 11:42 adc464cd  r/j/ksad_detail_loading_amin_bottom.json
     *
     *  问题找到后就好解决了，最简单就是原来没压缩的就不压缩就可以了。
     *
     *  为了溯源拿了工程的build.gradle配置文件，没发现有aapt的noCompress选项，又拿了git图片所在的aar库文件，发现gif文件是在aar的/res/raw目录下，
     *  网上搜了一下：/res/raw目录里的文件会原封不动的存储到设备上，不会被编译为二进制形式，访问的方式也是通过R类。
     *  InputStream inputStream = context.getResources().openRawResource(R.raw.rawtext);
     *
     *  工程估计使用了一下资源混淆或压缩工具，最后图片的目录变成了/r/j形式了。
     *
     *  与/assets目录的异同是：/assets这个目录下的文件也不会被编译成二进制，访问是通过文件名而不是资源id，这个目录下可以建议任意子目录，在/res目录下则不能
     *  随意建立子目录。
     *  来电秀报错的行：
     *  GifDrawable gifDrawable = (new GifDrawableBuilder()).from(context.getResources(), raw.loading_animation).build();
     */
    private static void compressFile(File file, ZipOutputStream zipOut, String baseDir, ZipFile zf) throws IOException {
        if (!file.exists()) {
            return;
        }

        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            ZipEntry entry = new ZipEntry(baseDir + file.getName());
            ZipEntry entry1 = null;
            if (null != zf) {
                entry1 = zf.getEntry(baseDir + file.getName());
            }
            if (null != entry1 && entry1.getMethod() == ZipEntry.STORED) {
//            if (file.getName().endsWith(".gif") || file.getName().endsWith(".ogg") || file.getName().endsWith(".webp")) {
                int bytesRead;
                byte[] buffer = new byte[1024];
                CRC32 crc = new CRC32();
                try (
                        BufferedInputStream bisTmp = new BufferedInputStream(new FileInputStream(file));
                ) {
                    crc.reset();
                    while ((bytesRead = bisTmp.read(buffer)) != -1) {
                        crc.update(buffer, 0, bytesRead);
                    }
                    if (null != bisTmp) {
                        bisTmp.close();
                    }
                }
                entry.setMethod(ZipEntry.STORED);
                entry.setCompressedSize(file.length());
                entry.setSize(file.length());
                entry.setCrc(crc.getValue());
            }
            zipOut.putNextEntry(entry);
            int count;
            byte data[] = new byte[BUFFER];
            while ((count = bis.read(data, 0, BUFFER)) != -1) {
                zipOut.write(data, 0, count);
            }

        } finally {
            if (null != bis) {
                bis.close();
            }
        }
    }

    public static void writeFile(String filePath, String content) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        if (content == null || content.isEmpty()) {
            return;
        }

        File dstFile = new File(filePath);

        if (!dstFile.getParentFile().exists()) {
            dstFile.getParentFile().mkdirs();
        }

        FileOutputStream outputStream = null;
        BufferedWriter writer = null;
        try {
            outputStream = new FileOutputStream(dstFile);
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(outputStream);
            close(writer);
        }
    }

    private static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

}
