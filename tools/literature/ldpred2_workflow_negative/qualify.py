#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Owned negative controls; never modify genuine source bundles or runtimes."""
from pathlib import Path
import argparse,hashlib,json,re,shutil,subprocess,time

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,value):
    with p.open('x',encoding='utf-8') as f:json.dump(value,f,indent=2);f.write('\n')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
    source=a.source.resolve(strict=True);assert (source/'COMPLETED.properties').exists()
    before={str(p.relative_to(source)):sha(p) for p in source.rglob('*') if p.is_file()}
    cli=['/usr/bin/java','-Xmx256m','-cp',str(a.jar.resolve()),'org.jlinalg.cli.LdPred2WorkflowCli'];checks=[]
    def run(label,options,expected,cause):
        command=cli+list(map(str,options));start=time.monotonic()
        with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1200)
        record=dict(command=command,exit_code=r.returncode,expected_exit_code=expected,seconds=round(time.monotonic()-start,3));save(out/(label+'.process.json'),record);checks.append(record)
        assert r.returncode==expected,(label,r.returncode)
        assert cause in (out/(label+'.stderr')).read_text(),label
        print(label,r.returncode,flush=True)
    forged=out/'FORGED_CONTROL_SOURCE';shutil.copytree(source,forged)
    effect=next((forged/'results').glob('*/effects.tsv'));lines=effect.read_text().splitlines();fields=lines[1].split('\t');fields[-1]=format(float(fields[-1])+0.1,'.17g');lines[1]='\t'.join(fields);effect.write_text('\n'.join(lines)+'\n')
    (forged/'CONTROL_NOT_ANALYSIS.txt').write_text('Deliberately falsified owned copy for a negative verification test. Never use its weights or completion marker.\n')
    entries=[]
    for f in sorted(forged.rglob('*')):
        if f.is_file() and f.name not in ['FILES.tsv','COMPLETED.properties']:entries.append(f'{f.relative_to(forged).as_posix()}\t{f.stat().st_size}\t{sha(f)}\n')
    (forged/'FILES.tsv').write_text('path\tbytes\tsha256\n'+''.join(entries))
    marker=forged/'COMPLETED.properties';old=marker.read_text();new,count=re.subn(r'^manifest_sha256=.*$',lambda m:'manifest_sha256='+sha(forged/'FILES.tsv'),old,flags=re.M);assert count==1;marker.write_text(new)
    run('resealed-false-effects',['--verify',forged,'--out',out/'refused-replay'],2,'Genuine recomputation produced different complete scientific results')
    assert (out/'refused-replay/worker-exit.txt').read_text().strip()=='0'
    assert (out/'refused-replay/runtime-exit.txt').read_text().strip()=='0'
    assert not (out/'refused-replay/COMPLETED.properties').exists()
    marker.rename(forged/'INVALIDATED-CONTROL-COMPLETED.properties')
    effective=(source/'effective.properties').read_text()
    for key in ['summary','ld_variants','ld_pointers','ld_rows','ld_values','models']:
        match=re.search(r'^'+key+r'=(.*)$',effective,re.M)
        if match:effective=effective[:match.start(1)]+str(source/match.group(1))+effective[match.end(1):]
    missing=out/'missing.properties';missing.write_text(re.sub(r'^runtime=.*$',lambda m:'runtime='+str(out/'MISSING_RUNTIME'),effective,flags=re.M))
    run('missing-runtime',['--request',missing,'--out',out/'skipped-runtime'],3,'SKIP3')
    assert not (out/'skipped-runtime/COMPLETED.properties').exists()
    changed=out/'CHANGED_RUNTIME_CONTROL';shutil.copytree(source/'runtime-records',changed)
    prop=changed/'runtime.properties';s=prop.read_text();s,count=re.subn(r'^original_archive_sha256=.*$',lambda m:'original_archive_sha256='+'0'*64,s,flags=re.M);assert count==1;prop.write_text(s)
    request=out/'changed.properties';request.write_text(re.sub(r'^runtime=.*$',lambda m:'runtime='+str(changed),effective,flags=re.M))
    run('changed-original-source-record',['--request',request,'--out',out/'refused-runtime'],2,'Exact selected original source/edition required')
    assert not (out/'refused-runtime/worker-command.txt').exists()
    run('verify-skipped-analysis',['--verify',out/'skipped-runtime','--out',out/'refused-skipped-replay'],2,'FAIL2')
    assert before=={str(p.relative_to(source)):sha(p) for p in source.rglob('*') if p.is_file()}
    save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_WORKFLOW_NEGATIVE_CONTROLS',controls=checks,genuine_source_bundle_unchanged=True,original_runtime_unchanged=True,forged_marker_retained_but_invalidated=True,no_false_replay_completion=True,new_catalog_credit=0,whole_goal_complete=False));return 0
if __name__=='__main__':raise SystemExit(main())
