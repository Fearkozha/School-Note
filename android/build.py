"""Build a signed, dependency-free Android APK with official Android SDK tools.
Requires Python 3, a JDK 17+, and Android SDK platform 35/build-tools 35.0.0.
Set JAVA_HOME and ANDROID_SDK_ROOT. Signing key remains outside the source tree.
"""
import pathlib,subprocess,os,zipfile,secrets,shutil,sys
ROOT=pathlib.Path(__file__).resolve().parent
WORK=ROOT.parent
SDK=pathlib.Path(os.environ.get('ANDROID_SDK_ROOT',str(WORK/'tools/android-sdk')))
JAVA=os.environ.get('JAVA_HOME')
if not JAVA:JAVA=str(next((WORK/'tools/java').iterdir()))
JAVA=pathlib.Path(JAVA)
BT=SDK/'build-tools/35.0.0'; ANDROID=SDK/'platforms/android-35/android.jar'
ENV=os.environ.copy();ENV['JAVA_HOME']=str(JAVA)
def run(*args):
    print('>',pathlib.Path(str(args[0])).name,flush=True)
    subprocess.run([str(x) for x in args],env=ENV,check=True)
SIGN=WORK/'signing';SIGN.mkdir(exist_ok=True)
KEY=SIGN/'journal-release.jks';PASSWORD=SIGN/'password.txt'
if not KEY.exists():
    PASSWORD.write_text(secrets.token_urlsafe(24),encoding='ascii')
    run(JAVA/'bin/keytool.exe','-genkeypair','-keystore',KEY,'-storepass:file',PASSWORD,'-keypass:file',PASSWORD,'-alias','journal','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=School Journal,OU=Local App,O=School Journal,C=KZ')
BUILD=ROOT/'build';BUILD.mkdir(exist_ok=True)
def package(test=False):
    b=BUILD/('test' if test else 'release');b.mkdir(exist_ok=True)
    gen=b/'generated';gen.mkdir(exist_ok=True);classes=b/'classes';classes.mkdir(exist_ok=True);dex=b/'dex';dex.mkdir(exist_ok=True)
    if not test:run(BT/'aapt2.exe','compile','--dir',ROOT/'res','-o',b/'resources.zip')
    link=[BT/'aapt2.exe','link','-I',ANDROID,'--manifest',ROOT/('tests/AndroidManifest.xml' if test else 'AndroidManifest.xml'),'--java',gen,'--min-sdk-version','26','--target-sdk-version','35','-o',b/'base.apk']
    if not test:link += [b/'resources.zip']
    run(*link)
    sources=list((ROOT/('tests/src' if test else 'src')).rglob('*.java'))+list(gen.rglob('*.java'))
    cp=str(ANDROID)+(os.pathsep+str(BUILD/'release/classes') if test else '')
    args=['-encoding','UTF-8','--release','8','-classpath',cp,'-d',classes]
    # An argument file avoids the Windows command length limit.
    argfile=b/'javac.args';argfile.write_text('\n'.join('"'+str(x).replace('\\','/')+'"' for x in args+sources),encoding='utf-8')
    run(JAVA/'bin/javac.exe','@'+str(argfile))
    run(JAVA/'bin/java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',ANDROID,'--output',dex,*classes.rglob('*.class'))
    shutil.copyfile(b/'base.apk',b/'unsigned.apk')
    with zipfile.ZipFile(b/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        for f in dex.glob('*.dex'):z.write(f,f.name)
    run(BT/'zipalign.exe','-f','4',b/'unsigned.apk',b/'aligned.apk')
    output=b/('journal-tests.apk' if test else 'School-Note-1.1.1.apk')
    run(JAVA/'bin/java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',KEY,'--ks-key-alias','journal','--ks-pass','file:'+str(PASSWORD),'--out',output,b/'aligned.apk')
    run(JAVA/'bin/java.exe','-jar',BT/'lib/apksigner.jar','verify','--verbose',output)
    run(BT/'zipalign.exe','-c','4',output)
    run(BT/'aapt2.exe','dump','badging',output)
    print('SIGNED APK:',output,flush=True)
package()
if '--tests' in sys.argv:package(True)
