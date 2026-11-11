# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the isolated Java 17 Sniffles VCF component without rebuilding the project."""
import argparse
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

SOURCES = ['src/main/java/org/jlinalg/genetics/SnifflesVcf.java',
           'src/main/java/org/jlinalg/cli/SnifflesJson.java',
           'src/main/java/org/jlinalg/cli/SnifflesVcfCli.java']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--javac', type=Path, default=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'))
    args = parser.parse_args()
    out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SNIFFLES_NATIVE_BUILD', implementation_credit=False, SV_calling_adapter_available=False)
    canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    try:
        sources = [ROOT/n for n in SOURCES]
        files = [Path(__file__).resolve(), *sources, ROOT/'LICENSE']
        pins = {p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for file in files:
            target = out/'source-snapshot'/file.relative_to(ROOT)
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(file.read_bytes())
        classes = out/'classes'
        classes.mkdir()
        command = [str(args.javac), '--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),*map(str,sources)]
        run = subprocess.run(command,capture_output=True,timeout=120)
        (out/'compile.stdout').write_bytes(run.stdout)
        (out/'compile.stderr').write_bytes(run.stderr)
        save(out/'compile.json',dict(command=command,exit_code=run.returncode,compiler=pin(args.javac.resolve())))
        assert run.returncode == 0, run.stderr.decode(errors='replace')
        jar = out/'sniffles-vcf-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
            def write(name,raw):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16
                opened.writestr(item,raw)
            write('META-INF/MANIFEST.MF',b'Manifest-Version: 1.0\r\nMain-Class: org.jlinalg.cli.SnifflesVcfCli\r\nImplementation-Version: 0.3.7-development\r\n\r\n')
            write('META-INF/LICENSE',(ROOT/'LICENSE').read_bytes())
            for file in sorted(classes.rglob('*.class')):
                raw=file.read_bytes()
                assert raw[:4]==b'\xca\xfe\xba\xbe' and int.from_bytes(raw[6:8],'big')==61
                write(file.relative_to(classes).as_posix(),raw)
        with zipfile.ZipFile(jar) as opened:
            assert opened.testzip() is None
            members={n:dict(bytes=len(opened.read(n)),sha256=__import__('hashlib').sha256(opened.read(n)).hexdigest()) for n in opened.namelist()}
        assert all(pin(ROOT/n)==value for n,value in pins.items())
        state.update(status='PASS_ISOLATED_JAVA17_SNIFFLES_VCF_COMPONENT_BUILD_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),
                     source_pins=pins,members=members,external_Java_dependencies=[],original_caller_executed=False)
    except Exception as failure:
        state['error']=repr(failure)
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    assert pin(canonical)==before
    state['canonical_executable_unchanged']=before
    save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''))
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated component build checks')
    raise SystemExit(main())
