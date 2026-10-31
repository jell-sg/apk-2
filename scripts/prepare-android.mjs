import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const android = path.join(root, 'android');
if (!fs.existsSync(android)) throw new Error('Folder android belum dibuat. Jalankan npx cap add android terlebih dahulu.');
const app = path.join(android, 'app');
const javaDir = path.join(app, 'src/main/java/com/ryuza/official');
fs.mkdirSync(javaDir, { recursive: true });
fs.copyFileSync(path.join(root, 'native/android/app/src/main/java/com/ryuza/official/RyuzaToolsPlugin.java'), path.join(javaDir, 'RyuzaToolsPlugin.java'));
fs.writeFileSync(path.join(javaDir, 'MainActivity.java'), `package com.ryuza.official;\n\nimport android.os.Bundle;\nimport com.getcapacitor.BridgeActivity;\n\npublic class MainActivity extends BridgeActivity {\n  @Override\n  public void onCreate(Bundle savedInstanceState) {\n    registerPlugin(RyuzaToolsPlugin.class);\n    super.onCreate(savedInstanceState);\n  }\n}\n`);
const gradle = path.join(app, 'build.gradle');
let g = fs.readFileSync(gradle, 'utf8');
// Shizuku API 13.1.5 declares minSdk 24. Force the app to the same
// minimum so Android's manifest merger does not fail at :app:processDebugMainManifest.
if (/minSdkVersion\s+rootProject\.ext\.minSdkVersion/.test(g)) {
  g = g.replace(/minSdkVersion\s+rootProject\.ext\.minSdkVersion/g, 'minSdkVersion 24');
} else if (/minSdkVersion\s+\d+/.test(g)) {
  g = g.replace(/minSdkVersion\s+\d+/g, 'minSdkVersion 24');
} else {
  g = g.replace(/defaultConfig\s*\{/, match => `${match}\n        minSdkVersion 24`);
}
if (!g.includes('dev.rikka.shizuku:api')) {
  g = g.replace(/dependencies\s*\{/, match => `${match}\n    implementation 'dev.rikka.shizuku:api:13.1.5'\n    implementation 'dev.rikka.shizuku:provider:13.1.5'\n    implementation 'androidx.documentfile:documentfile:1.0.1'`);
}
fs.writeFileSync(gradle, g);

// Also align Capacitor's shared Android defaults for any other module that
// reads rootProject.ext.minSdkVersion.
const variables = path.join(android, 'variables.gradle');
if (fs.existsSync(variables)) {
  let v = fs.readFileSync(variables, 'utf8');
  if (/minSdkVersion\s*=\s*\d+/.test(v)) {
    v = v.replace(/minSdkVersion\s*=\s*\d+/g, 'minSdkVersion = 24');
    fs.writeFileSync(variables, v);
  }
}
const manifest = path.join(app, 'src/main/AndroidManifest.xml');
let m = fs.readFileSync(manifest, 'utf8');
if (!m.includes('rikka.shizuku.ShizukuProvider')) {
  m = m.replace('</application>', `    <provider\n      android:name="rikka.shizuku.ShizukuProvider"\n      android:authorities="\${applicationId}.shizuku"\n      android:multiprocess="false"\n      android:enabled="true"\n      android:exported="true"\n      android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />\n  </application>`);
}
if (!m.includes('moe.shizuku.privileged.api')) {
  m = m.replace('<application', ` <queries><package android:name="moe.shizuku.privileged.api" /></queries>\n  <application`);
}
fs.writeFileSync(manifest, m);
console.log('Ryuza native tools, Shizuku API and file backup/restore configured.');
