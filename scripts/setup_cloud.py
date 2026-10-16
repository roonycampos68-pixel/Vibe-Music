#!/usr/bin/env python3
"""Install a verified Android development toolchain outside the checkout; safe to rerun."""
import concurrent.futures
import hashlib
import os
from pathlib import Path
import shlex
import subprocess
import tarfile
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

root = Path(os.environ.get('VIBE_TOOLS_DIR', '/workspace/.vibe-tools')).resolve()
root.mkdir(parents=True, exist_ok=True)

def download(url, path):
    if path.exists():
        return
    tmp = path.with_name(path.name + '.download')
    with urllib.request.urlopen(url, timeout=120) as response, tmp.open('wb') as out:
        while block := response.read(1024 * 1024):
            out.write(block)
    tmp.replace(path)

def verify(path, expected, algorithm='sha256'):
    digest = hashlib.new(algorithm)
    with path.open('rb') as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    if digest.hexdigest() != expected:
        raise RuntimeError(f'Checksum failed: {path.name}; remove the failed archive and retry')

def gradle():
    if (root / 'gradle-8.10.2/bin/gradle').exists():
        return
    url = 'https://downloads.gradle.org/distributions/gradle-8.10.2-bin.zip'
    checksum = urllib.request.urlopen(url + '.sha256', timeout=60).read().decode().split()[0]
    archive = root / 'gradle.zip'
    download(url, archive); verify(archive, checksum)
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(root)
    (root / 'gradle-8.10.2/bin/gradle').chmod(0o755)

def jdk():
    if (root / 'jdk/bin/javac').exists():
        return
    url = 'https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz'
    checksum = urllib.request.urlopen(url + '.sha256', timeout=60).read().decode().split()[0]
    archive = root / 'jdk.tar.gz'
    download(url, archive); verify(archive, checksum)
    with tarfile.open(archive) as bundle:
        directory = bundle.getnames()[0].split('/')[0]
        bundle.extractall(root, filter='data')
    (root / 'jdk').symlink_to(root / directory, target_is_directory=True)

def android_tools():
    target = root / 'android-sdk/cmdline-tools/latest'
    if (target / 'bin/sdkmanager').exists():
        return
    url = 'commandlinetools-linux-11076708_latest.zip'
    manifest = urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml', timeout=60).read()
    checksum = None
    for item in ET.fromstring(manifest).iter('archive'):
        if item.findtext('complete/url') == url:
            checksum = item.find('complete/checksum')
            break
    if checksum is None:
        raise RuntimeError('No official checksum for Android command line tools')
    archive = root / 'sdk-tools.zip'
    download('https://dl.google.com/android/repository/' + url, archive)
    verify(archive, checksum.text, checksum.attrib.get('type', 'sha1'))
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(target.parent)
    (target.parent / 'cmdline-tools').rename(target)
    for file in (target / 'bin').iterdir():
        file.chmod(0o755)

with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for result in [pool.submit(task) for task in (gradle, jdk, android_tools)]:
        result.result()

exports = {
    'JAVA_HOME': str(root / 'jdk'),
    'ANDROID_HOME': str(root / 'android-sdk'),
    'ANDROID_SDK_ROOT': str(root / 'android-sdk'),
    'ANDROID_USER_HOME': str(root / 'android-user'),
    'ANDROID_EMULATOR_HOME': str(root / 'android-user'),
    'ANDROID_AVD_HOME': str(root / 'avd'),
    'GRADLE_USER_HOME': str(root / 'gradle-home'),
}
for name in ('ANDROID_USER_HOME', 'GRADLE_USER_HOME'):
    Path(exports[name]).mkdir(parents=True, exist_ok=True)
(root / 'env.sh').write_text('\n'.join(f'export {key}={shlex.quote(value)}' for key, value in exports.items()) + '\nexport PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"\n')
environ = dict(os.environ, **exports)
trust = Path('/etc/ssl/certs/java/cacerts')
if trust.exists():
    environ['JAVA_TOOL_OPTIONS'] = (environ.get('JAVA_TOOL_OPTIONS', '') + f' -Djavax.net.ssl.trustStore={trust}').strip()
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
properties = []
if trust.exists():
    properties.append(f'systemProp.javax.net.ssl.trustStore={trust}')
args = [str(root / 'android-sdk/cmdline-tools/latest/bin/sdkmanager'), '--sdk_root=' + exports['ANDROID_HOME']]
if proxy.hostname:
    args += ['--proxy=http', '--proxy_host=' + proxy.hostname, '--proxy_port=' + str(proxy.port or 80)]
    for protocol in ('http', 'https'):
        properties += [f'systemProp.{protocol}.proxyHost={proxy.hostname}', f'systemProp.{protocol}.proxyPort={proxy.port or 80}']
    properties.append('systemProp.http.nonProxyHosts=localhost|127.*|[::1]')
property_file = root / 'gradle-home/gradle.properties'
existing = property_file.read_text().splitlines() if property_file.exists() else []
keys = {line.split('=', 1)[0] for line in properties}
property_file.write_text('\n'.join([line for line in existing if line.split('=', 1)[0] not in keys] + properties) + '\n')
packages = ['platform-tools', 'platforms;android-35', 'build-tools;35.0.0']
subprocess.run(args + ['--licenses'], input='y\n' * 100, text=True, env=environ, check=True)
subprocess.run(args + packages, env=environ, check=True)
project = Path(__file__).resolve().parents[1]
(project / 'local.properties').write_text('sdk.dir=' + exports['ANDROID_HOME'] + '\n')
print('Toolchain prepared. Source ' + str(root / 'env.sh'))
