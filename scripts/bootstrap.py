# SPDX-License-Identifier: MIT
"""Fetch the official Forge MDK wrapper and a project-local Java 25 toolchain."""
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
    archive = TOOLS / 'forge-mdk.zip'
    download('https://maven.minecraftforge.net/net/minecraftforge/forge/26.3-66.1.0/forge-26.3-66.1.0-mdk.zip', archive)
    with zipfile.ZipFile(archive) as source:
        for name in source.namelist():
            if name in ('gradlew', 'gradlew.bat') or name.startswith('gradle/wrapper/'):
                source.extract(name, ROOT)
    print('Official Forge wrapper installed', flush=True)


def java():
    if list(TOOLS.glob('jdk-25*/bin/javac.exe')):
        return
    request = urllib.request.Request('https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows', headers={'User-Agent': 'SoulCatcherPlus-development'})
    with urllib.request.urlopen(request, timeout=60) as response:
        package = json.load(response)[0]['binary']['package']
    archive = TOOLS / 'jdk25.zip'
    download(package['link'], archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != package['checksum']:
        raise RuntimeError('Java archive checksum mismatch')
    with zipfile.ZipFile(archive) as source:
        source.extractall(TOOLS)
    print('Project-local Java 25 installed (SHA-256 verified)', flush=True)


with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    for result in (pool.submit(mdk), pool.submit(java)):
        result.result()
