package com.djishuiyin.watermark;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 在系统 WebView 上补一个原生保存通道：
 * 前端把导出好的图片以 base64 传过来，这里直接写进系统相册（Pictures/照片水印），
 * 避免 WebView 里 blob: 下载无效导致“保存没反应”。
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
        }

        WebView wv = this.bridge != null ? this.bridge.getWebView() : null;
        if (wv != null) {
            wv.addJavascriptInterface(new Saver(), "AndroidSaver");
        }
    }

    /** 返回键：先关面板/弹窗，都不在时才退出 App */
    @Override
    public void onBackPressed() {
        WebView wv = this.bridge != null ? this.bridge.getWebView() : null;
        if (wv == null) {
            exitApp();
            return;
        }
        String js = "(function(){"
                + "var ids=['saveMask','expModal','batchMask'];"
                + "for(var i=0;i<ids.length;i++){var m=document.getElementById(ids[i]);"
                + "if(m&&m.classList.contains('on')){m.classList.remove('on');return 'handled';}}"
                + "var s=document.getElementById('sheetCard');"
                + "if(s&&s.classList.contains('open')){if(window.__closeDrawer)window.__closeDrawer();return 'handled';}"
                + "return 'no';})()";
        wv.evaluateJavascript(js, value -> {
            if (value == null || "\"no\"".equals(value)) exitApp();
        });
    }

    private void exitApp() {
        super.onBackPressed();
    }

    private class Saver {

        @JavascriptInterface
        public String save(String base64, String name, String mime) {
            try {
                if (base64 == null || base64.isEmpty()) return "图片数据为空";
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                String safeName = (name == null || name.isEmpty()) ? "watermark.jpg" : name;
                String safeMime = (mime == null || mime.isEmpty()) ? "image/jpeg" : mime;

                ContentResolver cr = getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, safeName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, safeMime);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/照片水印");
                    cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv);
                    if (uri == null) return "无法创建相册文件";
                    OutputStream os = cr.openOutputStream(uri);
                    if (os == null) return "无法写入相册";
                    try {
                        os.write(data);
                    } finally {
                        os.close();
                    }
                    cv.clear();
                    cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                    cr.update(uri, cv, null, null);
                    return "ok";
                }

                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "照片水印");
                if (!dir.exists() && !dir.mkdirs()) return "无法创建相册目录";
                File f = new File(dir, safeName);
                FileOutputStream fos = new FileOutputStream(f);
                try {
                    fos.write(data);
                } finally {
                    fos.close();
                }
                MediaScannerConnection.scanFile(MainActivity.this, new String[]{f.getAbsolutePath()}, new String[]{safeMime}, null);
                return "ok";
            } catch (Exception e) {
                return "err:" + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
    }
}
