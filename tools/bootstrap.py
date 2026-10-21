#!/usr/bin/env python3
import concurrent.futures, hashlib, pathlib, stat, urllib.request, zipfile
ROOT=pathlib.Path('/workspace/android-task-tools')
ROOT.mkdir(exist_ok=True)
def get(url, path, digest):
    if path.exists() and hashlib.sha1(path.read_bytes()).hexdigest()==digest:return
    tmp=path.with_suffix('.download')
    urllib.request.urlretrieve(url,tmp)
    if hashlib.sha1(tmp.read_bytes()).hexdigest()!=digest:raise RuntimeError('Checksum mismatch: '+url)
    tmp.replace(path)
def sdk(name,url,digest,prefix,marker):
    dst=ROOT/name
    if (dst/marker).exists():return
    archive=ROOT/(name+'.zip');get(url,archive,digest)
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            rel=item.filename.removeprefix(prefix)
            out=dst/rel
            if not out.resolve().is_relative_to(dst.resolve()):raise RuntimeError('Unsafe zip path')
            if item.is_dir():out.mkdir(parents=True,exist_ok=True);continue
            out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(z.read(item))
            mode=item.external_attr>>16
            if mode:out.chmod(mode & 0o777)
    print('Verified and installed '+name,flush=True)
jobs=[('platform','https://dl.google.com/android/repository/platform-36_r02.zip','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576','android-36/','android.jar'),('build-tools','https://dl.google.com/android/repository/build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc','android-15/','aapt2')]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(lambda x:sdk(*x),jobs))
u='https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar'
sha=urllib.request.urlopen(u+'.sha1').read().decode().strip()
if len(sha)!=40:raise RuntimeError('Invalid Maven checksum')
get(u,ROOT/'ecj.jar',sha)
print('Compiler ready',flush=True)
