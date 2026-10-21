#!/usr/bin/env python3
"""Package verified build output for repository downloads; never include signing keys."""
from pathlib import Path
import hashlib
import re
import shutil
import zipfile

root=Path(__file__).resolve().parents[1]
version=re.search(r'android:versionName="([^"]+)"',(root/'app/src/main/AndroidManifest.xml').read_text()).group(1)
name=f'browse-helper-{version}.apk'
dst=root/'downloads'
dst.mkdir(exist_ok=True)
shutil.copy2(root/'dist'/name,dst/name)
with zipfile.ZipFile(dst/'browse-helper-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for folder in ['app','tools','tests']:
        for p in sorted((root/folder).rglob('*')):
            if p.is_file():z.write(p,p.relative_to(root))
    for p in ['README.md','.gitignore']:z.write(root/p,p)
with zipfile.ZipFile(dst/'browse-helper-download.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.write(dst/name,name)
    z.writestr('安装说明.txt',f'浏览小助手 {version} 试用版\n解压安装 APK，可覆盖安装同签名旧版。\n开启无障碍服务，选择平台，手动打开活动任务列表后点悬浮条开始。\n按实时文字识别浏览时长及进度；预约、答题、搜索、跨应用等任务需手动处理。\n尚未真机验证；没有承诺所有任务都能完成。\n')
with (dst/'SHA256SUMS.txt').open('w') as f:
    for p in sorted(dst.iterdir()):
        if p.suffix in ['.apk','.zip']:
            if p.suffix=='.zip':
                with zipfile.ZipFile(p) as z:assert z.testzip() is None
            f.write(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
print('Packaged APK, source, fallback ZIP and checksums:',version)
