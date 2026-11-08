# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact original executables and complete private ELF library closures; no analysis or global installation."""
import argparse,json,re,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_smr_cmake import linux

PREFIX=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--']


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--write-resources',action='store_true');a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/smr-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);editions={};reports=[]
    for version,source_name in [('1.3.1','source-2023-initial-1'),('1.4.3','source-intake-1')]:
        source=base/source_name;receipt=json.loads((source/'SOURCE.json').read_bytes());build=base/('reference-'+version+'-build-2');record=json.loads((build/'COMPLETED.json').read_bytes());assert record['status']=='built-unchanged-original-SMR-reference-only' and record['original_source_and_dependencies_unchanged'];program=ROOT/record['program']['path'];assert pin(program)=={k:record['program'][k] for k in ['bytes','sha256']};home=out/version;home.mkdir();(home/'bin').mkdir();(home/'lib').mkdir();shutil.copyfile(program,home/'bin/smr');command=PREFIX+['/usr/bin/env','-u','LD_PRELOAD','-u','LD_AUDIT','-u','LD_LIBRARY_PATH','/usr/bin/ldd',linux(program)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=30);(home/'ldd.stdout').write_bytes(r.stdout);(home/'ldd.stderr').write_bytes(r.stderr);assert r.returncode==0 and b'not found' not in r.stdout;libraries={};origins={}
        for line in r.stdout.decode().splitlines():
            match=re.search(r'(?:=>\s+)?(/[^\s]+)\s+\(',line)
            if not match:continue
            path=match[1];name=Path(path).name;assert name not in libraries;target=home/'lib'/name;command=PREFIX+['/bin/cp','--dereference','--',path,linux(target)];subprocess.run(command,cwd=ROOT,check=True,capture_output=True,timeout=30);command=PREFIX+['/usr/bin/sha256sum',path];before=subprocess.check_output(command,cwd=ROOT,timeout=30).decode().split()[0];assert pin(target)['sha256']==before;libraries['lib/'+name]=pin(target);origins['lib/'+name]=path
        assert set(libraries)=={'lib/ld-linux-x86-64.so.2','lib/libz.so.1','lib/libgomp.so.1','lib/libstdc++.so.6','lib/libm.so.6','lib/libgcc_s.so.1','lib/libc.so.6'}
        loader=home/'lib/ld-linux-x86-64.so.2';command=PREFIX+['/usr/bin/env','-u','LD_PRELOAD','-u','LD_AUDIT','-u','LD_LIBRARY_PATH','-u','GLIBC_TUNABLES','LC_ALL=C','OMP_NUM_THREADS=1',linux(loader),'--inhibit-cache','--library-path',linux(home/'lib'),'--inhibit-rpath','',linux(home/'bin/smr'),'--help'];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=30);(home/'health.stdout').write_bytes(r.stdout);(home/'health.stderr').write_bytes(r.stderr);save(home/'health-process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==1 and ('Version '+version).encode() in r.stdout and b'flags include:' in r.stdout;assert pin(program)=={k:record['program'][k] for k in ['bytes','sha256']};assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());editions[version]=dict(source_commit=receipt['commit'],source_files=receipt['source_members'],runtime_files={'bin/smr':pin(home/'bin/smr'),**libraries},loader='lib/ld-linux-x86-64.so.2',program='bin/smr',library_directory='lib',native_backend='Eigen',source_version_identifies_paper_binary=False)
        save(home/'PREPARATION.json',dict(status='captured-exact-original-private-SMR-runtime-no-analysis',version=version,source_commit=receipt['commit'],program=record['program'],runtime_files=editions[version]['runtime_files'],library_origins=origins,source_receipt=pin(source/'SOURCE.json'),analysis_admitted=False,system_libraries_modified=False,required_action='Keep the original GPL-2 source and notices separate from Java. This private runtime uses an exact copied ELF loader/library closure; it is not a general host or paper environment. Missing/forbidden preparation means SKIP dependent analysis. Original help exit1 is metadata behavior, never model success.'));reports.append(dict(version=version,preparation=pin(home/'PREPARATION.json')))
    profile=dict(schema_version=1,profile='original-SMR-Linux-x86_64-1.3.1-1.4.3',upstream='https://github.com/JianYang-Lab/SMR',platform='Linux',architecture='amd64',analysis_installs_software=False,editions=editions)
    save(out/'SOURCE-PROFILE.json',profile)
    if a.write_resources:
        target=ROOT/'src/main/resources/org/jlinalg/omics/smr-workflow';assert not target.exists();target.mkdir(parents=True);(target/'SOURCE-PROFILE.json').write_bytes((out/'SOURCE-PROFILE.json').read_bytes());(target/'GPL-2.txt').write_bytes((base/'source-2023-initial-1/source/LICENSE').read_bytes());(target/'NOTICE.txt').write_text('Original SMR is a separate GPL-2 source program and is not linked into the Java JAR. Preserve original source/licenses and dependency notices with any runtime distribution. Packaged profile contains byte identities and supported options, not original source code or a general host guarantee. Caller reference/ancestry/build choices and historical paper binaries are not inferred.\n',encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',dict(status='captured-two-exact-original-private-SMR-ELF-profiles',profile=pin(out/'SOURCE-PROFILE.json'),editions=reports,resources_written=a.write_resources,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},analysis_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Captured both original programs plus7 exact ELF libraries each; private loaders health-checked; no analysis admitted')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable runtime/source checks')
    main()
