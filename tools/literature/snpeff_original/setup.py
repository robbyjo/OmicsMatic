#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare pinned original SnpEff runtimes privately; never download during annotation."""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import shutil
import subprocess
import sys
import traceback
import urllib.error
import urllib.parse
import urllib.request
import zipfile

MODERN_URL = 'https://snpeff-public.s3.amazonaws.com/versions/snpEff_v5_2_core.zip'
MODERN_ZIP = '60ad2eec66c4f086b8cc7812e5654dce2dd500dd218774da490326e6a4e585f7'
MODERN_JAR = '3d630f73332cff635f64301d06e8cc3c12889c794903b8b9be3a82227e47a291'
LEGACY_URL = 'https://sourceforge.net/projects/snpeff/files/snpEff_v3_1_core.zip/download'
LEGACY_ZIP = 'd4c07409972e9d3bde08e0102b2e4a2787d3c1bb37f128ffdecfc3e998b60be5'
LEGACY_VENDOR = '72c62978bf6248ef66eb2660927cda7a1ef79215b1b2bf05b910ca523284c071'
NOTICE = '''# Warnings and required actions

This is a development runtime preparation helper, not an annotation analysis.
Supply the exact permitted assembly, transcript release, complete SnpEff data
directory and original config before analysis. Do not substitute hg38kg,
Ensembl85, wheat annotations or the P002 TpallidumNC0214902 database.

Modern5.2 uses its official MIT-licensed JAR. Legacy uses selected official SVN
r354 sources labeled3.1i (build2012-12-12), without source edits. Their author
paper revision and original binary are unknown. Its external dependency classes
come from the official3.1m distribution; every SnpEff/Flanagan class is excluded
from that container and recompiled from r354. This is not a3.1m annotation engine.
The pinned compiler is javac21.0.4 with explicit Java8 source/target and the
original ISO-8859-1 encoding. Sorted STORED ZIP members avoid zlib-dependent
archive variation. This is a selected source build, not the authors' binary.

Legacy is for PRIVATE ACADEMIC NONCOMMERCIAL USE. SnpEff's historical POM declares
LGPL3; bundled Michael Thomas Flanagan sources have noncommercial terms, require
acknowledgment, and restrict redistribution. Acknowledge Michael Thomas Flanagan
at www.ee.ucl.ac.uk/~mflanaga. Retain every source notice. Do not redistribute
legacy runtimes or containing evidence bundles without the required permissions.
This helper does not grant permissions or bundle legacy code into OmicsMatic.

Missing/forbidden downloads or compiler prerequisites mean SKIP3: skip dependent
analysis, obtain the named permitted prerequisite and retry a NEW output path.
Hash contradictions, compilation or recording failures mean FAIL2: admit no
prepared runtime/result. Retain all downloaded bytes, compiler consoles and failed
attempts. Never refresh a source/runtime hash to turn a failure into success.
'''

class Prerequisite(RuntimeError):
    pass

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def save(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')

def obtain(url, target, sha, size_limit, allowed, cached=None):
    if cached is not None:
        if not cached.is_file():
            raise Prerequisite('Missing supplied cached input: ' + str(cached))
        shutil.copyfile(cached, target)
    else:
        if not allowed:
            raise Prerequisite('Download/install permission is disabled. Supply cached permitted files or rerun with --allow-download: ' + url)
        try:
            request = urllib.request.Request(url, headers={'User-Agent':'OmicsMatic-SnpEff-private-runtime-setup'})
            with urllib.request.urlopen(request, timeout=30) as response, target.open('xb') as stream:
                count = 0
                while block := response.read(1024 * 1024):
                    count += len(block)
                    if count > size_limit:
                        raise ValueError('Upstream input exceeds the pinned/declared size limit: ' + url)
                    stream.write(block)
        except (urllib.error.URLError, PermissionError, TimeoutError) as error:
            raise Prerequisite('Required permitted upstream input could not be obtained: ' + url + ': ' + str(error)) from error
    if target.stat().st_size > size_limit or digest(target) != sha:
        raise ValueError('Original input hash/size contradiction; retain bytes and inspect, do not refresh pins: ' + str(target))

def prepare_legacy(args, out, archive):
    if not args.research_use:
        raise Prerequisite('Legacy includes noncommercial and redistribution restrictions. Use --research-use only for permitted private academic noncommercial use; otherwise skip this edition.')
    profile = json.loads(Path(__file__).with_name('source-profile.json').read_bytes())
    javac = args.javac or shutil.which('javac')
    if not javac:
        raise Prerequisite('Supply --javac pointing to permitted JDK21.0.4 javac; no supported compiler found')
    try:
        probe = subprocess.run([str(javac), '-version'], capture_output=True, text=True, timeout=15, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise Prerequisite('Required javac could not execute: ' + str(error)) from error
    save(out/'COMPILER.json', dict(command=[str(javac), '-version'], exit_code=probe.returncode, stdout=probe.stdout, stderr=probe.stderr))
    if probe.returncode or probe.stdout.strip() != profile['compiler_version']:
        raise Prerequisite('The selected source-build profile requires exactly ' + profile['compiler_version'] + '; supply that permitted compiler, without changing the profile')
    obtain(LEGACY_URL, archive, LEGACY_ZIP, 80486334, args.allow_download, args.archive)
    with zipfile.ZipFile(archive) as vendor:
        raw = vendor.read('snpEff/snpEff.jar')
    if hashlib.sha256(raw).hexdigest() != LEGACY_VENDOR:
        raise ValueError('Official legacy dependency-container JAR contradicts its pin')
    original = out/'upstream-3.1m-dependency-container.jar'
    original.write_bytes(raw)
    source = out/'source-r354'
    source.mkdir()
    def fetch(item):
        name, pin = item
        target = source/name
        target.parent.mkdir(parents=True, exist_ok=True)
        cached = args.source_directory/name if args.source_directory else None
        obtain(profile['source_root']+urllib.parse.quote(name, safe='/'), target, pin['sha256'], pin['bytes'], args.allow_download, cached)
        if target.stat().st_size != pin['bytes']:
            raise ValueError('Pinned source size differs: ' + name)
        return name
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as workers:
        # Consume every future so an individual download failure is never hidden.
        futures = [workers.submit(fetch, item) for item in sorted(profile['source_files'].items())]
        errors = []
        for future in futures:
            try:
                future.result()
            except Exception as error:
                errors.append(error)
        if errors:
            save(out/'SOURCE-ERRORS.json', [repr(error) for error in errors])
            raise errors[0]
    dependencies = {}
    with zipfile.ZipFile(original) as vendor:
        for name in vendor.namelist():
            if name.endswith('/') or name.startswith(('ca/mcgill/mcb/pcingola/', 'flanagan/')) or name.upper() == 'META-INF/MANIFEST.MF' or name.upper().endswith(('.SF','.RSA','.DSA')):
                continue
            if name in dependencies:
                raise ValueError('Duplicate dependency member: ' + name)
            dependencies[name] = vendor.read(name)
    vendor_path = out/'vendor-dependencies.jar'
    with zipfile.ZipFile(vendor_path, 'x', zipfile.ZIP_STORED) as vendor:
        for name, raw in sorted(dependencies.items()):
            vendor.writestr(name, raw)
    classes = out/'classes'
    classes.mkdir()
    sources = sorted((source/'src/main/java').rglob('*.java'), key=lambda path:str(path).lower())
    arguments = out/'javac.args'
    arguments.write_text('\n'.join('"'+str(path.resolve()).replace('\\','/')+'"' for path in sources)+'\n', encoding='utf-8')
    command = [str(javac), '-encoding', 'ISO-8859-1', '-source', '8', '-target', '8', '-classpath', str(vendor_path), '-d', str(classes), '@'+str(arguments)]
    with (out/'compile.stdout').open('xb') as so, (out/'compile.stderr').open('xb') as se:
        result = subprocess.run(command, stdout=so, stderr=se, stdin=subprocess.DEVNULL, timeout=180, check=False)
    save(out/'COMPILE.json', dict(command=command, exit_code=result.returncode, sources=len(sources), upstream_sources_modified=False))
    if result.returncode:
        raise ValueError('Original source compilation failed; inspect retained compile.stderr')
    members = {name:raw for name,raw in dependencies.items() if not name.endswith('.ftl')}
    for prefix in [classes, source/'src/main/resources']:
        for path in prefix.rglob('*'):
            if path.is_file():
                name = path.relative_to(prefix).as_posix()
                if name in members:
                    raise ValueError('Duplicate original source-build member: ' + name)
                members[name] = path.read_bytes()
    members['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\nMain-Class: ca.mcgill.mcb.pcingola.snpEffect.commandLine.SnpEff\r\nOmicsMatic-Selected-Svn-Revision: 354\r\nOmicsMatic-Author-Binary: false\r\n\r\n'
    members['UPSTREAM-SOURCE-NOTICES/pom.xml'] = (source/'pom.xml').read_bytes()
    for path in (source/'src/main/java/flanagan').rglob('*.java'):
        members['UPSTREAM-SOURCE-NOTICES/'+path.relative_to(source/'src/main/java').as_posix()] = path.read_bytes()
    with zipfile.ZipFile(out/'snpeff.jar', 'x', zipfile.ZIP_STORED) as target:
        for name, raw in sorted(members.items()):
            info = zipfile.ZipInfo(name, date_time=(1980,1,1,0,0,0))
            info.compress_type = zipfile.ZIP_STORED
            info.create_system = 0
            info.external_attr = 0o100644 << 16
            target.writestr(info, raw)
    if digest(out/'snpeff.jar') != profile['legacy_jar_sha256'] or (out/'snpeff.jar').stat().st_size != profile['legacy_jar_bytes']:
        raise ValueError('Selected source-build binary differs from qualified profile; retain compiler/source evidence and investigate without refreshing hashes')
    shutil.copyfile(source/'snpEff.config', out/'snpEff.config')

def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=NOTICE)
    parser.add_argument('--edition', choices=['5.2','3.1i-r354-source'], required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-download', action='store_true')
    parser.add_argument('--research-use', action='store_true')
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--source-directory', type=Path)
    parser.add_argument('--javac', type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    try:
        out.mkdir(parents=True, exist_ok=False)
        owned = True
        (out/'WARNINGS.md').write_text(NOTICE, encoding='utf-8')
        archive = out/'upstream-core.zip'
        if args.edition == '5.2':
            obtain(MODERN_URL, archive, MODERN_ZIP, 66465247, args.allow_download, args.archive)
            with zipfile.ZipFile(archive) as original:
                for name, member in [('snpeff.jar','snpEff.jar'),('snpEff.config','snpEff.config'),('LICENSE.md','LICENSE.md')]:
                    (out/name).write_bytes(original.read('snpEff/'+member))
            if digest(out/'snpeff.jar') != MODERN_JAR:
                raise ValueError('Official5.2 JAR contradicts its pin')
        else:
            prepare_legacy(args, out, archive)
        save(out/'COMPLETED.json', dict(status='PASS_PREPARED_PINNED_ORIGINAL_RUNTIME_NOT_AN_ANALYSIS', edition=args.edition, jar_sha256=digest(out/'snpeff.jar'), jar_bytes=(out/'snpeff.jar').stat().st_size, private_noncommercial_legacy=args.edition!='5.2', official_upstream_binary=args.edition=='5.2', paper_author_binary_known=False, complete_paper_reproduction=False))
        print('PASS prepared '+args.edition+'; supply its exact reference before annotation. WARNINGS: '+str(out/'WARNINGS.md'))
        return 0
    except Exception as error:
        status = 3 if isinstance(error, (Prerequisite, PermissionError)) else 2
        message = ('SKIP3: skip dependent analysis. ' if status==3 else 'FAIL2: no prepared runtime/result admitted. ')+str(error)+'\nRetain failed attempts; resolve the named cause and retry a NEW output directory.\n'
        if owned:
            (out/'FAILURE.txt').write_text(message+traceback.format_exc(), encoding='utf-8')
        print(message, file=sys.stderr)
        return status

if __name__ == '__main__':
    raise SystemExit(main())
