#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain every native case for both named matching profiles; no installation.

Linux, an existing Java and the exact tested development JAR are required. Output
and archive paths must be NEW. Capture occurs inside the same live process and
uses the manifest census, never a guessed minimum file count.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import time
import zipfile

JAR_SHA='c2896bfdf8cc21c143beb7b4cb679e5391f75ef436d5f076e33d16791a7cbbad'
FIXTURE_SHA='3841d0fe4c0f8dc099dd76b9343fc84ff2952839f2303b65eb749d1b8fdfa686'
ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--archive',required=True,type=Path)
    parser.add_argument('--resume',action='store_true',help='Validate and retain terminal owned cases, then append missing cases; never overwrite a result.')
    args=parser.parse_args()
    if sys.platform!='linux' or not shutil.which('java'):parser.exit(3,'SKIP3: existing Linux Java is unavailable; no native analysis started.\n')
    jar=args.jar.resolve(strict=True);out=args.out.absolute();archive=args.archive.absolute()
    assert jar.is_file() and hashlib.sha256(jar.read_bytes()).hexdigest()==JAR_SHA
    assert out.parent.resolve(strict=True)==out.parent and archive.parent.resolve(strict=True)==archive.parent
    assert not archive.exists()
    if args.resume:assert out.is_dir() and out.resolve()==out
    else:assert not out.exists()
    fixture=(ROOT/'src/test/resources/org/jlinalg/sequence/pal2nal-original/controls.zip').read_bytes();assert hashlib.sha256(fixture).hexdigest()==FIXTURE_SHA
    with zipfile.ZipFile(io.BytesIO(fixture)) as source:
        cases=json.loads(source.read('cases.json'));assert len(cases)==85
        inputs={case['name']:{name:source.read(case['name']+'/'+name) for name in ['protein.aln','dna.fasta','stdout','stderr']} for case in cases}
    out.mkdir(mode=0o700,exist_ok=args.resume);results=[];resumed=0
    for version in ['14','14.1']:
        profile='native-pal2nal-v'+version+'-matching';home=out/('v'+version);home.mkdir(mode=0o700,exist_ok=args.resume)
        for case in cases:
            assert re.fullmatch('[A-Za-z0-9-]+',case['name']);work=home/case['name']
            flags=case['flags']
            recipe=dict(schema_version=1,profile=profile,protein=dict(path='protein.aln',format='CLUSTAL' if case['selection'] is not None else 'FASTA',headers='FIRST_FIELD'),
                dna=dict(paths=['dna.fasta'],headers='FIRST_FIELD'),selection=dict(mode='CLUSTAL_HASH' if case['selection'] is not None else 'NONE'),
                options=dict(codon_table=case['table'],pairing=case['pairing'],matching='ALLOW_V14_RELAXED',placement='SOURCE_LEFTMOST',gap_stop_filter='V14_NOGAP' if '-nogap' in flags else 'KEEP',remove_mismatch_columns='-nomismatch' in flags,normalize_documented_symbols=case['comparison']=='documented-normalization-departure',normalize_tfastx=case['name'].startswith('tfastx-'),minimum_verified_residues=0,minimum_verified_fraction=0.0))
            request=work/'recipe.json';command=['java','-Xmx128m','-XX:ActiveProcessorCount=2','-jar',str(jar),'pal2nal-tools','--recipe',str(request),'--out',str(work/'result')]
            expected=2 if case['comparison']=='native-refusal' else 0
            if work.exists():
                assert args.resume and work.resolve()==work and (work/'observed.json').is_file(),str(work)
                for name in ['protein.aln','dna.fasta']:assert (work/name).read_bytes()==inputs[case['name']][name]
                assert json.loads(request.read_bytes())==recipe
                row=json.loads((work/'observed.json').read_bytes());assert row['command']==command and row['exit_code']==row['expected_exit_code']==expected
                result=work/'result'
                if expected==0:
                    manifest=json.loads((result/'manifest.json').read_bytes());marker=json.loads((result/'COMPLETED.json').read_bytes())
                    assert marker['manifest_sha256']==hashlib.sha256((result/'manifest.json').read_bytes()).hexdigest()
                    assert {p.relative_to(result).as_posix() for p in result.rglob('*') if p.is_file()}==set(manifest['files'])|{'manifest.json','COMPLETED.json'}
                    for name,pin in manifest['files'].items():
                        raw=(result/name).read_bytes();assert len(raw)==pin['bytes'] and hashlib.sha256(raw).hexdigest()==pin['sha256']
                else:assert not (result/'COMPLETED.json').exists() and (result/'FAILURE.md').is_file()
                results.append(row);resumed+=1;continue
            work.mkdir(mode=0o700)
            for name in ['protein.aln','dna.fasta']:(work/name).write_bytes(inputs[case['name']][name])
            request.write_text(json.dumps(recipe,indent=2)+'\n')
            started=time.monotonic()
            with (work/'stdout').open('xb') as stdout,(work/'stderr').open('xb') as stderr:
                process=subprocess.Popen(command,stdout=stdout,stderr=stderr,start_new_session=True)
                try:code=process.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    (work/'live-process.json').write_text(json.dumps(dict(pid=process.pid,process_group=process.pid,command=command))+'\n')
                    raise RuntimeError('Observe this exact live process; do not restart: '+str(work))
            row=dict(version=version,name=case['name'],command=command,exit_code=code,expected_exit_code=expected,comparison=case['comparison'],elapsed_seconds=time.monotonic()-started)
            (work/'observed.json').write_text(json.dumps(row,indent=2)+'\n');results.append(row)
            assert code==expected,(version,case['name'],code)
            result=work/'result'
            if code==0:
                manifest=json.loads((result/'manifest.json').read_bytes());expected_files=set(manifest['files'])|{'manifest.json','COMPLETED.json'}
                actual={p.relative_to(result).as_posix() for p in result.rglob('*') if p.is_file()};assert expected_files==actual
            else:assert not (result/'COMPLETED.json').exists() and (result/'FAILURE.md').is_file()
        print('Completed and retained profile',profile,flush=True)
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==JAR_SHA
    record=dict(status='captured-170-terminal-native-catalog-protocols',protocols=170,completed=156,expected_failures=14,resumed_terminal_protocols=resumed,jar_sha256=JAR_SHA,source_fixture_sha256=FIXTURE_SHA,collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),all_owned_processes_terminal=True,results=results)
    (out/'summary.json').write_text(json.dumps(record,indent=2)+'\n')
    with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(out.rglob('*')):
            assert path.resolve()==path and not path.is_symlink()
            if path.is_file():
                attributes=path.stat();assert stat.S_ISREG(attributes.st_mode) and attributes.st_nlink==1 and attributes.st_size<=8388608
                bundle.write(path,path.relative_to(out).as_posix())
    pin=dict(status=record['status'],protocols=170,completed=156,expected_failures=14,archive_sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),archive_bytes=archive.stat().st_size)
    archive.with_suffix('.json').write_text(json.dumps(pin,indent=2)+'\n');print(json.dumps(pin))


if __name__=='__main__':main()
