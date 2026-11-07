# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject damaged/rehashed inconsistent HTSeq bundles and retain missing-runtime skips."""
import argparse
import concurrent.futures
import json
import shutil
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_htseq_reference import DISTRO,linux
from build_htseq_tools_development import JAR as TARGET
from validate_deseq2_inference import JAVA21,JAVA25

BASE=ROOT/'build/literature-review/htseq-runtime'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--source-run',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();source=args.source_run.resolve();assert out.is_relative_to(BASE) and source.is_relative_to(BASE) and not out.exists();out.mkdir()
    assert (source/'COMPLETED.json').is_file() and not (source/'INCOMPLETE.json').exists()
    artifact=pin(TARGET);original={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()}
    def modify(folder,name):
        if name=='unchanged':return
        if name=='missing-file':(folder/'summary.tsv').unlink();return
        if name=='extra-file':(folder/'unexpected.txt').write_text('Retain unexpected evidence\n');return
        if name=='incomplete':save(folder/'INCOMPLETE.json',dict(status='incomplete',result_admitted=False));return
        if name=='derived-counts':
            p=folder/'counts.tsv';p.write_text(p.read_text().replace('\t1\n','\t9\n',1));return
        if name=='arguments':
            p=folder/'prepared/arguments.json';value=json.loads(p.read_bytes());value[value.index('-a')+1]='19';save(p,value)
        elif name=='probe-stdout':
            p=folder/'probe/process.stdout';value=json.loads(p.read_bytes());value['numpy']='unreported-other-edition';save(p,value)
        elif name=='status':save(folder/'analysis-status.json',dict(status='FAILED',result_admitted=False))
        elif name=='warnings':(folder/'WARNINGS.md').write_text('silenced')
        elif name=='process-command':
            p=folder/'count/process.json';value=json.loads(p.read_bytes());value['command'][-2]='probe';save(p,value)
        elif name=='process-limits':
            p=folder/'count/process.json';value=json.loads(p.read_bytes());value['limits']['fileBytes']=1;save(p,value)
        elif name=='source-binding':
            p=folder/'runtime.json';value=json.loads(p.read_bytes());files=value['runtime']['snapshot']['package_files'];key=next(k for k in files if k=='HTSeq/scripts/count.py');files[key]['sha256']='0'*64;save(p,value)
        else:raise AssertionError(name)
        # Deliberately recompute all integrity hashes: VERIFY must still reject
        # contradictory options, probes, process records, sources or statuses.
        p=folder/'manifest.json';value=json.loads(p.read_bytes());value['files']={f.relative_to(folder).as_posix():pin(f) for f in folder.rglob('*') if f.is_file() and f.name not in ['manifest.json','COMPLETED.json']};save(p,value)
        done=json.loads((folder/'COMPLETED.json').read_bytes());done['manifest']=pin(p);save(folder/'COMPLETED.json',done)
    def invoke(folder,label,request,jvm):
        file=folder/(label+'.json');save(file,request)
        argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/java','-Xmx512m','-jar',linux(TARGET),'htseq-tools','--request',linux(file)] if jvm=='linux-java21' else [JAVA21 if jvm=='windows-java21' else JAVA25,'-Xmx512m','-jar',str(TARGET),'htseq-tools','--request',str(file)]
        result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=300)
        (folder/(label+'.stdout')).write_bytes(result.stdout);(folder/(label+'.stderr')).write_bytes(result.stderr);save(folder/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));return result.returncode
    names=['unchanged','missing-file','extra-file','incomplete','derived-counts','arguments','probe-stdout','status','warnings','process-command','process-limits','source-binding']
    jobs=[]
    for name in names:
        folder=out/name;folder.mkdir();run=folder/'source';shutil.copytree(source,run);modify(run,name)
        for jvm in ['windows-java21','windows-java25','linux-java21']:jobs.append((name,folder,run,jvm))
    def check(job):
        name,folder,run,jvm=job;dest=folder/('verify-'+jvm)
        request=dict(schema_version=1,operation='VERIFY',run=linux(run) if jvm.startswith('linux') else str(run),out=linux(dest) if jvm.startswith('linux') else str(dest))
        code=invoke(folder,jvm,request,jvm);assert code==(0 if name=='unchanged' else 2),(name,jvm,code)
        assert (dest/'COMPLETED.json').exists()==(code==0)
        if code!=0:
            assert (dest/'INCOMPLETE.json').exists() and 'FAIL2' in (dest/'WARNINGS.md').read_text()
        return dict(case=name,JVM=jvm,exit_code=code)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,jobs))
    # The same valid explicit request with unavailable dependencies must skip;
    # no alignment access/counting is attempted and recovery stays prominent.
    for jvm in ['windows-java21','windows-java25','linux-java21']:
        folder=out/('missing-runtime-'+jvm);folder.mkdir();request=json.loads((source/'prepared/request.json').read_bytes());dest=folder/'count'
        request.update(runtime_home=linux(folder/'absent-home') if jvm.startswith('linux') else str(folder/'absent-home'),out=linux(dest) if jvm.startswith('linux') else str(dest))
        assert invoke(folder,'request',request,jvm)==3,jvm
        assert not (dest/'COMPLETED.json').exists() and not (dest/'counts.tsv').exists()
        warning=(dest/'WARNINGS.md').read_text();assert 'SKIP3' in warning and 'skip dependent analysis' in warning and 'NEW paths' in warning
    assert original=={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()} and pin(TARGET)==artifact
    save(out/'COMPLETED.json',dict(status='passed-HTSeq-portable-integrity-source-command-and-skip-controls',artifact=artifact,cases=results,
        successful_portable_verifications=3,expected_failures=33,missing_runtime_skips=3,source_bundle_preserved=True,
        hashes_not_execution_authentication=True,catalog_credit=False))
    print('Passed3 positive portable checks,33 expected corruption/consistency refusals and3 missing-runtime skips')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable integrity/skip checks')
    main()
