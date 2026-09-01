import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const appDir = join('android', 'app', 'src', 'main');
const javaDir = join(appDir, 'java', 'com', 'meengook', 'bannap');
mkdirSync(javaDir, { recursive: true });
for (const name of ['BannapLockscreenPlugin.java', 'BannapLockscreenService.java']) {
  copyFileSync(join('native', 'android', name), join(javaDir, name));
}

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
writeFileSync(manifestPath, manifest);

if (!existsSync(mainActivity)) throw new Error('Android MainActivity was not generated.');
console.log('Prepared native lockscreen card sources.');
