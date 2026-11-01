#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original IQ-TREE inference captures, retaining failed attempts.

Launches no scientific program. Requires the completed eight-profile original
matrix, two completed typed standard-bootstrap retries, and complete native
output inspections. Does not certify the unfinished public analysis adapter.
"""
from pathlib import Path
import argparse,hashlib,json,os,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
SHA=lambda raw:hashlib.sha256(raw).hexdigest()

def collect(base,output,report):
    assert not output.exists() and not report.exists(),'Choose NEW artifact/report paths; preserve prior attempts'
    original=base/'inference-original-controls-1';typed=base/'typed-standard-bootstrap-1'
    terminal=json.loads((original/'running.json').read_bytes());assert terminal['status']=='terminal' and terminal['protocols']==82
    records=json.loads((original/'observed.json').read_bytes());assert len(records)==82
    lock=json.loads((ROOT/'tools/literature/iqtree-runtime-lock.json').read_bytes())
    assert set(r['version'] for r in records)=={p['version'] for p in lock['profiles'].values()}
    fits=[r for r in records if r['exit_code']==0];failed=[r for r in records if r['exit_code']!=0]
    assert len(fits)==80 and len(failed)==2
    assert {r['name'] for r in failed}=={'1.6.12-DNA-standard-1000','2.3.6-DNA-standard-1000'}
    assert all(r['exit_code']==-9 and r['expected_exit_code']==0 and r['owned_live_after_cleanup']==[] for r in failed)
    controls=json.loads((typed/'linux/observed.json').read_bytes())
    assert controls['status']=='passed-typed-owned-standard-bootstrap-controls' and controls['scientific_analysis_admitted'] is False
    assert [r['version'] for r in controls['protocols']]==['1.6.12','2.3.6']
    for r in controls['protocols']:
        assert r['replicates']==1000 and len(r['support_edges'])==18
        assert r['process']['exitCode']==0 and r['process']['ownedLiveAfterCleanup']==[] and r['scientific_analysis_admitted'] is False
    inspection=json.loads((base/'complete-output-probes-2/observed.json').read_bytes())
    assert inspection['completed_fits']==80 and inspection['bootstrap_replicates']==16800 and inspection['reported_support_edges']==432
    assert inspection['site_values']==33280 and inspection['support_mismatches']==0 and inspection['scientific_analysis_admitted'] is False
    entries={}
    def add(name,raw):
        assert name not in entries and not name.startswith('/') and '..' not in Path(name).parts,name
        entries[name]=raw
    for name in ['observed.json','running.json','controller.py','synthetic-data.json','runtime-lock.json']:
        add('capture/original/'+name,(original/name).read_bytes())
    for r in records:
        profile=lock['profiles'][r['profile_id']];assert profile['version']==r['version']
        assert r['program_sha256']==profile['files'][profile['program']]['sha256'] and r['owned_live_after_cleanup']==[]
        assert '-wsl' in r['command'] and r['command'][r['command'].index('-nt')+1]=='1'
        directory=original/'linux'/r['name'];files={p.relative_to(directory).as_posix():p for p in directory.rglob('*') if p.is_file()}
        assert set(files)==set(r['files'])
        for name,path in files.items():
            assert not path.is_symlink();raw=path.read_bytes();pin=r['files'][name]
            assert len(raw)==pin['bytes'] and SHA(raw)==pin['sha256'],(r['name'],name)
            add('original/'+r['name']+'/'+name,raw)
    for path in sorted((typed/'linux').rglob('*')):
        if path.is_file():
            assert not path.is_symlink();add('typed-standard/'+path.relative_to(typed/'linux').as_posix(),path.read_bytes())
    for name in ['support-observed.json','sites-observed.json','observed.json','original-observations.json','IqtreeProductionSiteProbe.java']:
        add('inspection/'+name,(base/'complete-output-probes-2'/name).read_bytes())
    for name in ['IqtreeTypedStandardProbe.java','IqtreeSupportProbe.java','IqtreeLikelihoodProbe.java']:
        add('controllers/'+name,(base/name).read_bytes())
    source_paths=sorted((ROOT/'src/main/java/org/jlinalg/phylogeny').glob('*.java'))+sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Iqtree*.java'))+[ROOT/'src/main/java/org/jlinalg/cli/SimpleJson.java']
    for path in source_paths:add('source/'+path.relative_to(ROOT).as_posix(),path.read_bytes())
    add('collector/collect_iqtree_inference_controls.py',Path(__file__).read_bytes())
    add('UPSTREAM-GPL-2.0.txt',(ROOT/'tools/literature/iqtree-runtime/GPL-2.0.txt').read_bytes())
    summary=dict(schema_version=1,scope='Complete synthetic original-output controls, not a public IQ-TREE scientific workflow or biological accuracy benchmark',
        original_protocols=82,completed_original_fits=80,failed_original_expected_success_attempts=2,completed_typed_standard_bootstrap_retries=2,
        original_bootstrap_replicates=16800,typed_standard_bootstrap_replicates=2000,original_reported_support_edges=432,typed_reported_support_edges=36,
        original_site_likelihood_cells=33280,source_signed_values_preserved=True,independent_non_JC_fitted_likelihood_validation=False,
        failed_attempts_retained=True,scientific_analysis_admitted=False,public_analysis_adapter_available=False)
    add('scope.json',(json.dumps(summary,indent=2)+'\n').encode())
    add('SHA256SUMS',(''.join(SHA(raw)+'  '+name+'\n' for name,raw in sorted(entries.items()))).encode())
    output.parent.mkdir(parents=True,exist_ok=True);report.parent.mkdir(parents=True,exist_ok=True)
    partial=output.with_name(output.name+'.partial');assert not partial.exists(),'Prior partial artifact is retained; choose a new path'
    with zipfile.ZipFile(partial,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100444<<16;archive.writestr(info,raw)
    with zipfile.ZipFile(partial) as archive:
        assert set(archive.namelist())==set(entries)
        for name,raw in entries.items():assert archive.read(name)==raw,name
    digest=SHA(partial.read_bytes());os.link(partial,output);partial.unlink()
    result=dict(**summary,status='frozen-complete-original-and-typed-inference-controls',artifact_sha256=digest,artifact_bytes=output.stat().st_size,
                members=len(entries),decoded_bytes=sum(len(raw) for raw in entries.values()))
    with report.open('x',encoding='utf-8') as target:target.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/iqtree-runtime')
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);args=parser.parse_args()
    try:collect(args.cache,args.output,args.report)
    except Exception as failure:
        print('IQ-TREE CAPTURE FAIL2: '+str(failure)+'\nRequired action: preserve all source captures and any partial artifact. Correct the named integrity/completion problem and use NEW output/report paths. No analysis is admitted.',file=sys.stderr)
        raise
if __name__=='__main__':main()
