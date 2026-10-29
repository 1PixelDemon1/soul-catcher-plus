# SPDX-License-Identifier: MIT
"""Restore the existing Gradle wrapper and install Java 17 plus the Java 21 build toolchain."""
import concurrent.futures
import hashlib
import json
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
    print('Gradle wrapper restored', flush=True)


def java(version):
    if list(TOOLS.glob(f'jdk-{version}*/bin/javac.exe')):
        return
    request = urllib.request.Request(f'https://api.adoptium.net/v3/assets/latest/{version}/hotspot?architecture=x64&image_type=jdk&os=windows', headers={'User-Agent': 'SoulCatcherPlus-development'})
    with urllib.request.urlopen(request, timeout=60) as response:
        package = json.load(response)[0]['binary']['package']
    archive = TOOLS / f'jdk{version}.zip'
    download(package['link'], archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != package['checksum']:
        raise RuntimeError('Java archive checksum mismatch')
    with zipfile.ZipFile(archive) as source:
        source.extractall(TOOLS)
    print(f'Project-local Java {version} installed (SHA-256 verified)', flush=True)


with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for result in (pool.submit(mdk), pool.submit(java, 17), pool.submit(java, 21)):
        result.result()
