import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const appDir = join('android', 'app', 'src', 'main');
const javaDir = join(appDir, 'java', 'com', 'meengook', 'bannap');
const layoutDir = join(appDir, 'res', 'layout');
const xmlDir = join(appDir, 'res', 'xml');
mkdirSync(javaDir, { recursive: true });
mkdirSync(layoutDir, { recursive: true });
mkdirSync(xmlDir, { recursive: true });
for (const name of ['BannapLockscreenPlugin.java', 'BannapLockscreenService.java', 'BannapWidgetProvider.java']) {
  copyFileSync(join('native', 'android', name), join(javaDir, name));
}
copyFileSync(join('native', 'android', 'bannap_widget.xml'), join(layoutDir, 'bannap_widget.xml'));
copyFileSync(join('native', 'android', 'bannap_widget_info.xml'), join(xmlDir, 'bannap_widget_info.xml'));

const mainActivity = join(javaDir, 'MainActivity.java');
let main = readFileSync(mainActivity, 'utf8');
if (!main.includes('BannapLockscreenPlugin')) {
  main = main.replace('import com.getcapacitor.BridgeActivity;', 'import com.getcapacitor.BridgeActivity;\nimport com.meengook.bannap.BannapLockscreenPlugin;');
  main = main.replace('public class MainActivity extends BridgeActivity {', 'public class MainActivity extends BridgeActivity {\n  @Override\n  public void onCreate(android.os.Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n    registerPlugin(BannapLockscreenPlugin.class);\n  }');
  writeFileSync(mainActivity, main);
}

const manifestPath = join(appDir, 'AndroidManifest.xml');
let manifest = readFileSync(manifestPath, 'utf8');
if (!manifest.includes('SYSTEM_ALERT_WINDOW')) {
  const permissions = [
    '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />',
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />',
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
  ].join('\n') + '\n\n';
  manifest = manifest.replace('<application', `${permissions}    <application`);
}
if (!manifest.includes('BannapLockscreenService')) {
  manifest = manifest.replace('</application>', '        <service android:name=".BannapLockscreenService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>');
}
if (!manifest.includes('BannapWidgetProvider')) {
  manifest = manifest.replace('</application>', '        <receiver android:name=".BannapWidgetProvider" android:exported="false"><intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter><meta-data android:name="android.appwidget.provider" android:resource="@xml/bannap_widget_info" /></receiver>\n    </application>');
}
writeFileSync(manifestPath, manifest);

const gradlePath = join('android', 'app', 'build.gradle');
let gradle = readFileSync(gradlePath, 'utf8');
if (!gradle.includes('bannap-release.jks')) {
  gradle = gradle.replace('android {', `android {
    signingConfigs {
        release {
            storeFile file('bannap-release.jks')
            storePassword System.getenv('BANNAP_KEYSTORE_PASSWORD')
            keyAlias System.getenv('BANNAP_KEY_ALIAS')
            keyPassword System.getenv('BANNAP_KEY_PASSWORD')
        }
    }`);
  gradle = gradle.replace(/(buildTypes\s*\{\s*release\s*\{)/, '$1\n            signingConfig signingConfigs.release');
  writeFileSync(gradlePath, gradle);
}

if (!existsSync(mainActivity)) throw new Error('Android MainActivity was not generated.');
console.log('Prepared native lockscreen card sources.');
