#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
tool_dir=/workspace/android-task-tools
python tools/bootstrap.py
mkdir -p build/classes build/test-classes build/dex build/gen dist
find app/src/main/java -name '*.java' -print > build/sources.txt
java -jar "$tool_dir/ecj.jar" -8 -encoding UTF-8 -warn:none -bootclasspath "$tool_dir/platform/android.jar:$tool_dir/build-tools/core-lambda-stubs.jar" -d build/classes @build/sources.txt
java -jar "$tool_dir/ecj.jar" -8 -encoding UTF-8 -warn:none -classpath build/classes -d build/test-classes tests/RulesTest.java
java -cp build/classes:build/test-classes RulesTest
"$tool_dir/build-tools/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$tool_dir/build-tools/aapt2" link -o build/base.apk -I "$tool_dir/platform/android.jar" --manifest app/src/main/AndroidManifest.xml --java build/gen build/resources.zip
python - <<'PY'
import pathlib,zipfile
with zipfile.ZipFile('build/classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for p in pathlib.Path('build/classes').rglob('*.class'):z.write(p,p.relative_to('build/classes').as_posix())
PY
java -cp "$tool_dir/build-tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$tool_dir/platform/android.jar" --min-api 26 --output build/dex build/classes.jar
python - <<'PY'
import pathlib,shutil,zipfile
shutil.copyfile('build/base.apk','build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for p in pathlib.Path('build/dex').glob('*.dex'):z.write(p,p.name)
PY
"$tool_dir/build-tools/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/local-testing.p12 ]; then
 keytool -genkeypair -keystore build/local-testing.p12 -storepass android -keypass android -alias local -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Browse Helper Local Testing' -noprompt
fi
java -jar "$tool_dir/build-tools/lib/apksigner.jar" sign --ks build/local-testing.p12 --ks-key-alias local --ks-pass pass:android --out dist/browse-helper-0.9.0.apk build/aligned.apk
java -jar "$tool_dir/build-tools/lib/apksigner.jar" verify --verbose dist/browse-helper-0.9.0.apk
"$tool_dir/build-tools/aapt2" dump badging dist/browse-helper-0.9.0.apk > build/apk-info.txt
python - <<'PY'
import zipfile
with zipfile.ZipFile('dist/browse-helper-0.9.0.apk') as z:
 assert z.testzip() is None
 assert 'classes.dex' in z.namelist()
 assert 'res/xml/accessibility.xml' in z.namelist()
info=open('build/apk-info.txt').read()
assert "minSdkVersion:'26'" in info and "targetSdkVersion:'35'" in info
assert 'cn.local.browsehelper.MainActivity' in info
assert 'android.permission.INTERNET' not in info
print('APK package, entry activity, resources, SDK and absence of Internet permission verified.')
PY
sha256sum dist/browse-helper-0.9.0.apk > dist/SHA256SUMS.txt
