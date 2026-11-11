# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a private exact original SMR runtime from retained source evidence and matching local ELF libraries; never install globally."""
import argparse,json,os,platform,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin

ACTION='SKIP3 means no dependent analysis is admitted. Inspect WARNINGS.md and the named source/library/permission cause. Supply the exact permitted Linux x86_64 runtime closure, or skip dependent SMR/HEIDI analysis; never substitute an unprofiled binary/library or run analysis with a partial runtime. Retry a NEW output path. This setup does not modify shared libraries or canonical JARs.'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',required=True,choices=['1.3.1','1.4.3']);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-prepare',action='store_true');p.add_argument('--wsl-distribution',default='JLinAlg-Omics-SAIGE-20261026');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_DEPENDENCY_UNAVAILABLE',version=a.version,analysis_admitted=False,shared_runtime_modified=False,required_action=ACTION);base='build/literature-review/smr-runtime/'
    def run(args,**kwargs):return subprocess.run(args,cwd=ROOT,capture_output=True,timeout=60,**kwargs)
    try:
        assert a.allow_prepare,'Private runtime preparation not enabled; no installation attempted'
        prefix=[] if sys.platform.startswith('linux') else ['wsl','-d',a.wsl_distribution,'--'];assert prefix or platform.machine().lower() in ['amd64','x86_64'],'Require Linux x86_64 or an explicitly selected permitted Linux x86_64 WSL distribution'
        def local(path):
            if not prefix:return str(path)
            drive=path.drive[0].lower();return '/mnt/'+drive+path.as_posix()[2:]
        profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/smr-workflow/SOURCE-PROFILE.json').read_bytes());edition=profile['editions'][a.version];folder=ROOT/'docs/literature/smr-statistics-development';proof=json.loads((folder/'VALIDATION.json').read_bytes());assert pin(folder/'controls.zip')==proof['archive'];files=unpack(folder/'controls.zip',proof);source=base+('source-2023-initial-1' if a.version=='1.3.1' else 'source-intake-1')+'/source/'
        for name,expected in edition['source_files'].items():
            raw=files[source+name];assert byte_pin(raw)==expected;target=out/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        program=files[base+'reference-'+a.version+'-build-2/build/smr'];assert byte_pin(program)==edition['runtime_files']['bin/smr'];(out/'runtime/bin').mkdir(parents=True);(out/'runtime/lib').mkdir();(out/'runtime/bin/smr').write_bytes(program)
        origins={'lib/ld-linux-x86-64.so.2':'/lib64/ld-linux-x86-64.so.2',**{'lib/'+n:'/lib/x86_64-linux-gnu/'+n for n in ['libz.so.1','libgomp.so.1','libstdc++.so.6','libm.so.6','libgcc_s.so.1','libc.so.6']}};captures=[]
        for name,expected in edition['runtime_files'].items():
            if name=='bin/smr':continue
            target=out/'runtime'/name
            if a.version=='1.4.3' and name=='lib/libz.so.1':
                raw=files[base+'reference-1.4.3-build-2/build/_deps/zlib-build/libz.so.1'];assert byte_pin(raw)==expected;target.write_bytes(raw);origin='source-built-zlib-with-corresponding-source-in-evidence'
            else:
                origin=origins[name];before=run(prefix+['/usr/bin/sha256sum',origin]);assert before.returncode==0 and before.stdout.decode().split()[0]==expected['sha256'],'Exact local ELF library unavailable/different: '+origin
                copied=run(prefix+['/bin/cp','--dereference','--',origin,local(target)]);assert copied.returncode==0,copied.stderr.decode(errors='replace');after=run(prefix+['/usr/bin/sha256sum',origin]);assert after.returncode==0 and after.stdout==before.stdout,'Local ELF library changed during capture'
            assert pin(target)==expected;captures.append(dict(member=name,origin=origin,**expected))
        for name in ['bin/smr','lib/ld-linux-x86-64.so.2']:
            r=run(prefix+['/bin/chmod','u+x','--',local(out/'runtime'/name)]);assert r.returncode==0,r.stderr.decode(errors='replace')
        command=prefix+['/usr/bin/env','-i','PATH=/usr/bin:/bin','LC_ALL=C','OMP_NUM_THREADS=1',local(out/'runtime/lib/ld-linux-x86-64.so.2'),'--inhibit-cache','--library-path',local(out/'runtime/lib'),'--inhibit-rpath','',local(out/'runtime/bin/smr'),'--help'];r=run(command);(out/'health.stdout').write_bytes(r.stdout);(out/'health.stderr').write_bytes(r.stderr);save(out/'health-process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==1 and ('Version '+a.version).encode() in r.stdout and b'flags include:' in r.stdout,'Exact private source/runtime help profile cannot execute'
        assert all(pin(out/'runtime'/n)==v for n,v in edition['runtime_files'].items()) and all(pin(out/'source'/n)==v for n,v in edition['source_files'].items());state.update(status='PASS_PREPARED_EXACT_ORIGINAL_SMR_RUNTIME_ONLY',source_commit=edition['source_commit'],profile=profile['profile'],source=local(out/'source'),runtime=local(out/'runtime'),captured_libraries=captures,health_exit1_is_usage_only=True,original_program_is_separate_GPL2=True,corresponding_source_evidence=dict(path=folder.relative_to(ROOT).as_posix(),**proof['archive']),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__)), 'src/main/resources/org/jlinalg/omics/smr-workflow/SOURCE-PROFILE.json':pin(ROOT/'src/main/resources/org/jlinalg/omics/smr-workflow/SOURCE-PROFILE.json')})
    except Exception as error:state['cause']=repr(error)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Exact original source/runtime prepared; no model was fitted and no analysis result was admitted.')+'\n\n**REQUIRED ACTION:** '+ACTION+'\n',encoding='utf8',newline='\n');print(state['status']);print(state.get('cause','Declare the saved source/runtime paths in a typed request and execute Linux x86_64 Java. Original help exit1 is expected usage behavior, not model success.'));return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/runtime identity checks')
    raise SystemExit(main())
