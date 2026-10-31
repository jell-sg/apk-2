package com.ryuza.official;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.annotation.ActivityCallback;
import androidx.activity.result.ActivityResult;
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
        JSObject result = new JSObject(); result.put("selected", uri != null);
        if (uri != null) { result.put("uri", uri); result.put("name", folderName(Uri.parse(uri))); }
        call.resolve(result);
    }

    @PluginMethod
    public void listTargetFiles(PluginCall call) {
        DocumentFile folder = getTargetFolderDocument();
        if (folder == null || !folder.canRead()) { call.reject("Pilih folder tujuan terlebih dahulu."); return; }
        JSArray files = new JSArray();
        for (DocumentFile file : folder.listFiles()) {
            if (file.isFile()) {
                JSObject item = new JSObject(); item.put("name", file.getName()); item.put("size", file.length()); item.put("lastModified", file.lastModified()); files.put(item);
            }
        }
        JSObject result = new JSObject(); result.put("files", files); result.put("count", files.length()); call.resolve(result);
    }

    @PluginMethod
    public void replaceAsset(PluginCall call) {
        String fileName = call.getString("fileName");
        String base64 = call.getString("dataBase64");
        if (!isSafeFileName(fileName) || base64 == null) { call.reject("Nama file atau data file tidak valid."); return; }
        DocumentFile folder = getTargetFolderDocument();
        if (folder == null || !folder.canWrite()) { call.reject("Folder tujuan belum dipilih atau tidak memiliki izin tulis."); return; }
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            DocumentFile backupFolder = folder.findFile("Ryuza-Backup");
            if (backupFolder == null) backupFolder = folder.createDirectory("Ryuza-Backup");
            DocumentFile existing = folder.findFile(fileName);
            String backupName = null;
            if (existing != null && existing.isFile()) {
                backupName = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + "__" + fileName;
                DocumentFile backup = backupFolder.createFile("application/octet-stream", backupName);
                if (backup == null) throw new IOException("Tidak dapat membuat file backup.");
                copyUri(existing.getUri(), backup.getUri());
                if (!existing.delete()) throw new IOException("Gagal mengganti file lama; backup tetap disimpan.");
            }
            DocumentFile replacement = folder.createFile("application/octet-stream", fileName);
            if (replacement == null) throw new IOException("Tidak dapat membuat file pengganti.");
            try (OutputStream output = getContext().getContentResolver().openOutputStream(replacement.getUri(), "w")) {
                if (output == null) throw new IOException("Tidak dapat membuka file tujuan.");
                output.write(data); output.flush();
            }
            JSObject result = new JSObject(); result.put("success", true); result.put("fileName", fileName); result.put("bytes", data.length); result.put("backupName", backupName); call.resolve(result);
        } catch (Exception error) { call.reject("Penggantian file gagal: " + error.getMessage()); }
    }

    @PluginMethod
    public void listBackups(PluginCall call) {
        DocumentFile folder = getTargetFolderDocument();
        if (folder == null) { call.reject("Pilih folder tujuan terlebih dahulu."); return; }
        DocumentFile backupFolder = folder.findFile("Ryuza-Backup");
        JSArray files = new JSArray();
        if (backupFolder != null && backupFolder.isDirectory()) {
            for (DocumentFile file : backupFolder.listFiles()) if (file.isFile()) {
                JSObject item = new JSObject(); item.put("name", file.getName()); item.put("size", file.length()); item.put("lastModified", file.lastModified()); files.put(item);
            }
        }
        JSObject result = new JSObject(); result.put("files", files); result.put("count", files.length()); call.resolve(result);
    }

    @PluginMethod
    public void restoreBackup(PluginCall call) {
        String backupName = call.getString("backupName");
        if (backupName == null || backupName.contains("/") || backupName.contains("\\")) { call.reject("Backup tidak valid."); return; }
        DocumentFile folder = getTargetFolderDocument();
        DocumentFile backupFolder = folder == null ? null : folder.findFile("Ryuza-Backup");
        DocumentFile backup = backupFolder == null ? null : backupFolder.findFile(backupName);
        if (backup == null || !backup.isFile()) { call.reject("File backup tidak ditemukan."); return; }
        String originalName = backupName.substring(backupName.indexOf("__") >= 0 ? backupName.indexOf("__") + 2 : 0);
        if (!isSafeFileName(originalName)) { call.reject("Nama file backup tidak valid."); return; }
        try {
            DocumentFile current = folder.findFile(originalName);
            if (current != null) current.delete();
            DocumentFile restored = folder.createFile("application/octet-stream", originalName);
            if (restored == null) throw new IOException("Tidak dapat membuat file hasil restore.");
            copyUri(backup.getUri(), restored.getUri());
            JSObject result = new JSObject(); result.put("success", true); result.put("fileName", originalName); call.resolve(result);
        } catch (Exception error) { call.reject("Restore gagal: " + error.getMessage()); }
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
