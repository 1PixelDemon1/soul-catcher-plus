# SPDX-License-Identifier: MIT
"""Install local Java 17 build tooling and Java 8 for the legacy Forge client."""
import concurrent.futures
import hashlib
import json
import time
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)


def download(url, path):
    request = urllib.request.Request(url, headers={'User-Agent': 'SoulCatcherPlus-development'})
    with urllib.request.urlopen(request, timeout=120) as response, path.open('wb') as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)


def mdk():
    if (ROOT / 'gradlew.bat').exists() and (ROOT / 'gradle/wrapper/gradle-wrapper.jar').exists():
        return
    archive = TOOLS / 'forge-mdk.zip'
    download('https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-mdk.zip', archive)
    with zipfile.ZipFile(archive) as source:
        for name in source.namelist():
            if name in ('gradlew', 'gradlew.bat') or name.startswith('gradle/wrapper/'):
                source.extract(name, ROOT)
    print('Official Forge wrapper installed', flush=True)


def java(version):
    pattern = 'jdk8*' if version == 8 else f'jdk-{version}*'
    if list(TOOLS.glob(pattern + '/bin/javac.exe')):
        return
    request = urllib.request.Request(
        f'https://api.github.com/repos/adoptium/temurin{version}-binaries/releases/latest',
        headers={'User-Agent': 'SoulCatcherPlus-development'})
    with urllib.request.urlopen(request, timeout=60) as response:
        release = json.load(response)
    prefix = f'OpenJDK{version}U-jdk_x64_windows_hotspot_'
    package = next(a for a in release['assets'] if a['name'].startswith(prefix) and a['name'].endswith('.zip'))
    checksum = next(a for a in release['assets'] if a['name'] == package['name'] + '.sha256.txt')
    checksum_path = TOOLS / f'jdk{version}.sha256'
    download(checksum['browser_download_url'] + '?t=' + str(time.time_ns()), checksum_path)
    archive = TOOLS / f'jdk{version}.zip'
    download(package['browser_download_url'] + '?t=' + str(time.time_ns()), archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != checksum_path.read_text(encoding='ascii').split()[0]:
        raise RuntimeError('Java archive checksum mismatch')
    with zipfile.ZipFile(archive) as source:
        source.extractall(TOOLS)
    print(f'Project-local Java {version} installed (SHA-256 verified)', flush=True)


with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for result in (pool.submit(mdk), pool.submit(java, 17), pool.submit(java, 8)):
        result.result()
