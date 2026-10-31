package com.ryuza.official;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import androidx.documentfile.provider.DocumentFile;

import androidx.activity.result.ActivityResult;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rikka.shizuku.Shizuku;

@CapacitorPlugin(name = "RyuzaTools")
public class RyuzaToolsPlugin extends Plugin {
    private static final String PREFS = "ryuza_native_tools";
    private static final String TARGET_URI = "target_tree_uri";
    private static final String TARGET_PATH = "target_asset_path";
    private static final String DEFAULT_ASSET_PATH = "/storage/emulated/0/Android/data/com.mobile.legends/files/dragon2017/asset";
    private static final int SHIZUKU_REQUEST_CODE = 7421;

    @PluginMethod
    public void getDeviceInfo(PluginCall call) {
        JSObject result = new JSObject();
        result.put("manufacturer", Build.MANUFACTURER);
        result.put("model", Build.MODEL);
        result.put("androidVersion", Build.VERSION.RELEASE);
        result.put("sdkInt", Build.VERSION.SDK_INT);
        result.put("brand", Build.BRAND);
        call.resolve(result);
    }

    @PluginMethod
    public void getShizukuStatus(PluginCall call) {
        JSObject result = new JSObject();
        boolean installed = false;
        try {
            getContext().getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            installed = true;
        } catch (Exception ignored) { }
        boolean running = false;
        boolean granted = false;
        try {
            running = Shizuku.pingBinder();
            if (running) granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) { }
        result.put("installed", installed);
        result.put("running", running);
        result.put("permissionGranted", granted);
        result.put("supported", Build.VERSION.SDK_INT >= 30);
        call.resolve(result);
    }

    @PluginMethod
    public void requestShizukuPermission(PluginCall call) {
        try {
            if (!Shizuku.pingBinder()) {
                call.reject("Shizuku belum berjalan. Buka aplikasi Shizuku dan mulai servicenya terlebih dahulu.");
                return;
            }
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                JSObject result = new JSObject(); result.put("granted", true); call.resolve(result); return;
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
            JSObject result = new JSObject(); result.put("requested", true); call.resolve(result);
        } catch (Throwable error) {
            call.reject("Tidak dapat meminta izin Shizuku: " + error.getMessage());
        }
    }

    @PluginMethod
    public void chooseTargetFolder(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "folderPickerResult");
    }

    @ActivityCallback
    private void folderPickerResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("Pemilihan folder dibatalkan."); return;
        }
        Uri uri = result.getData().getData();
        int flags = result.getData().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContext().getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
        getContext().getSharedPreferences(PREFS, 0).edit().putString(TARGET_URI, uri.toString()).apply();
        JSObject response = new JSObject(); response.put("uri", uri.toString()); response.put("name", folderName(uri));
        call.resolve(response);
    }

    @PluginMethod
    public void getTargetFolder(PluginCall call) {
        String uri = getContext().getSharedPreferences(PREFS, 0).getString(TARGET_URI, null);
        String path = getContext().getSharedPreferences(PREFS, 0).getString(TARGET_PATH, DEFAULT_ASSET_PATH);
        JSObject result = new JSObject(); result.put("selected", true); result.put("path", path); result.put("name", path.substring(path.lastIndexOf("/") + 1));
        if (uri != null) result.put("uri", uri);
        call.resolve(result);
    }

    @PluginMethod
    public void listTargetFiles(PluginCall call) {
        try {
            String path = call.getString("path", getSavedTargetPath());
            validateAssetPath(path);
            String output = runShizuku("ls -la " + shellQuote(path));
            JSArray files = new JSArray();
            for (String line : output.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("total ") || trimmed.equals(".") || trimmed.equals("..")) continue;
                String[] parts = trimmed.split("\\s+", 9);
                if (parts.length < 9) continue;
                String name = parts[8];
                if (name.equals(".") || name.equals("..")) continue;
                JSObject item = new JSObject(); item.put("name", name); item.put("directory", parts[0].startsWith("d"));
                try { item.put("size", Long.parseLong(parts[4])); } catch (Exception ignored) { item.put("size", 0); }
                files.put(item);
            }
            JSObject result = new JSObject(); result.put("files", files); result.put("count", files.length()); result.put("path", path); call.resolve(result);
        } catch (Exception error) { call.reject("Tidak dapat membaca folder melalui Shizuku: " + error.getMessage()); }
    }

    @PluginMethod
    public void setTargetPath(PluginCall call) {
        try {
            String path = call.getString("path"); validateAssetPath(path);
            runShizuku("test -d " + shellQuote(path) + " && echo RYUZA_OK");
            getContext().getSharedPreferences(PREFS, 0).edit().putString(TARGET_PATH, path).apply();
            JSObject result = new JSObject(); result.put("path", path); result.put("name", path.substring(path.lastIndexOf("/") + 1)); call.resolve(result);
        } catch (Exception error) { call.reject("Folder tidak dapat dibuka: " + error.getMessage()); }
    }

    @PluginMethod
    public void replaceAsset(PluginCall call) {
        String fileName = call.getString("fileName"); String base64 = call.getString("dataBase64");
        if (!isSafeFileName(fileName) || base64 == null) { call.reject("Nama file atau data file tidak valid."); return; }
        try {
            String path = getSavedTargetPath(); validateAssetPath(path);
            String backupDir = path + "/Ryuza-Backup";
            runShizuku("mkdir -p " + shellQuote(backupDir));
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String target = path + "/" + fileName; String backup = backupDir + "/" + stamp + "__" + fileName;
            String exists = runShizuku("if [ -f " + shellQuote(target) + " ]; then echo YES; else echo NO; fi").trim();
            String backupName = null;
            if (exists.equals("YES")) { runShizuku("cp " + shellQuote(target) + " " + shellQuote(backup)); backupName = stamp + "__" + fileName; }
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            String encoded = Base64.encodeToString(data, Base64.NO_WRAP);
            runShizuku("echo " + shellQuote(encoded) + " | base64 -d > " + shellQuote(target));
            JSObject result = new JSObject(); result.put("success", true); result.put("fileName", fileName); result.put("bytes", data.length); result.put("backupName", backupName); call.resolve(result);
        } catch (Exception error) { call.reject("Penggantian file gagal: " + error.getMessage()); }
    }

    @PluginMethod
    public void listBackups(PluginCall call) {
        try {
            String path = getSavedTargetPath() + "/Ryuza-Backup";
            String output = runShizuku("ls -la " + shellQuote(path) + " 2>/dev/null || true");
            JSArray files = new JSArray();
            for (String line : output.split("\\r?\\n")) { String[] p = line.trim().split("\\s+", 9); if (p.length >= 9 && p[0].startsWith("-")) { JSObject item = new JSObject(); item.put("name", p[8]); try { item.put("size", Long.parseLong(p[4])); } catch (Exception ignored) {} files.put(item); } }
            JSObject result = new JSObject(); result.put("files", files); result.put("count", files.length()); call.resolve(result);
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void restoreBackup(PluginCall call) {
        String backupName = call.getString("backupName");
        if (!isSafeFileName(backupName) || !backupName.contains("__")) { call.reject("Backup tidak valid."); return; }
        try {
            String original = backupName.substring(backupName.indexOf("__") + 2); if (!isSafeFileName(original)) throw new IOException("Nama file backup tidak valid.");
            String path = getSavedTargetPath(); String backup = path + "/Ryuza-Backup/" + backupName; String target = path + "/" + original;
            runShizuku("test -f " + shellQuote(backup) + " && cp " + shellQuote(backup) + " " + shellQuote(target));
            JSObject result = new JSObject(); result.put("success", true); result.put("fileName", original); call.resolve(result);
        } catch (Exception error) { call.reject("Restore gagal: " + error.getMessage()); }
    }

    private String getSavedTargetPath() { return getContext().getSharedPreferences(PREFS, 0).getString(TARGET_PATH, DEFAULT_ASSET_PATH); }
    private void validateAssetPath(String path) throws IOException {
        String root = "/storage/emulated/0/Android/data/com.mobile.legends/files/dragon2017/asset";
        if (path == null || !(path.equals(root) || path.startsWith(root + "/")) || path.contains("..")) throw new IOException("Path harus berada di folder asset Mobile Legends.");
    }
    private String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private String runShizuku(String command) throws Exception {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) throw new IOException("Aktifkan Shizuku dan berikan izin kepada Ryuza terlebih dahulu.");
        Process process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (InputStream in = process.getInputStream(); InputStream err = process.getErrorStream()) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) output.write(b, 0, n); while ((n = err.read(b)) > 0) output.write(b, 0, n); }
        int code = process.waitFor(); String text = output.toString("UTF-8"); if (code != 0) throw new IOException(text.isEmpty() ? "Perintah Shizuku gagal (" + code + ")" : text); return text;
    }

    private DocumentFile getTargetFolderDocument() {
        String value = getContext().getSharedPreferences(PREFS, 0).getString(TARGET_URI, null);
        if (value == null) return null;
        return DocumentFile.fromTreeUri(getContext(), Uri.parse(value));
    }

    private String folderName(Uri uri) {
        DocumentFile folder = DocumentFile.fromTreeUri(getContext(), uri);
        return folder != null && folder.getName() != null ? folder.getName() : "Folder terpilih";
    }

    private boolean isSafeFileName(String name) {
        return name != null && !name.trim().isEmpty() && !name.contains("/") && !name.contains("\\") && !name.equals(".") && !name.equals("..");
    }

    private void copyUri(Uri source, Uri destination) throws IOException {
        try (InputStream input = getContext().getContentResolver().openInputStream(source);
             OutputStream output = getContext().getContentResolver().openOutputStream(destination, "w")) {
            if (input == null || output == null) throw new IOException("Tidak dapat membaca atau menulis file.");
            byte[] buffer = new byte[64 * 1024]; int length;
            while ((length = input.read(buffer)) > 0) output.write(buffer, 0, length);
            output.flush();
        }
    }
}
