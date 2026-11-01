#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete report-boundary controls; never rerun or substitute an analysis."""
from pathlib import Path
import argparse,hashlib,json,zipfile,sys
ROOT=Path(__file__).resolve().parents[2]
SHA=lambda raw:hashlib.sha256(raw).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/iqtree-runtime');p.add_argument('--output',type=Path,required=True);p.add_argument('--report',type=Path,required=True);p.add_argument('--round',choices=['report-original-controls-1','report-original-controls-2','report-original-controls-3'],action='append');a=p.parse_args()
    if a.output.exists() or a.report.exists():raise ValueError('Preserve existing artifacts; choose NEW output/report paths')
    entries={};records=[]
    def add(name,raw):
        assert name not in entries;entries[name]=raw
    rounds=a.round or ['report-original-controls-1','report-original-controls-2']
    for folder in rounds:
        count={'report-original-controls-1':24,'report-original-controls-2':12,'report-original-controls-3':2}[folder]
        base=a.cache/folder;terminal=json.loads((base/'running.json').read_bytes());assert terminal['status']=='terminal' and terminal['protocols']==count and terminal['all_expected']
        observations=json.loads((base/'observed.json').read_bytes());assert len(observations)==count
        for name in ['observed.json','running.json','controller.py','runtime-lock.json','datasets.json']:add('capture/'+folder+'/'+name,(base/name).read_bytes())
        for r in observations:
            assert r['exit_code']==r['expected_exit_code'] and r['owned_live_after_cleanup']==[] and r['capacity_failure'] is None
            directory=base/'linux'/r['name'];assert {f.name for f in directory.iterdir()}==set(r['files'])
            for name,pin in r['files'].items():
                raw=(directory/name).read_bytes();assert SHA(raw)==pin['sha256'] and len(raw)==pin['bytes'];add('cases/'+folder+'/'+r['name']+'/'+name,raw)
            records.append(dict(folder=folder,**r))
    count=sum({'report-original-controls-1':24,'report-original-controls-2':12,'report-original-controls-3':2}[r] for r in rounds);assert len(records)==count
    add('protocols.json',(json.dumps(records,indent=2)+'\n').encode());add('collector/collect_iqtree_report_controls.py',Path(__file__).read_bytes())
    add('UPSTREAM-GPL-2.0.txt',(ROOT/'tools/literature/iqtree-runtime/GPL-2.0.txt').read_bytes())
    add('scope.json',(json.dumps(dict(protocols=count,completed_fits=sum(r['exit_code']==0 for r in records),retained_ASC_constant_site_refusals=sum(r['exit_code']!=0 for r in records),source_AICc_denominator_boundary_preserved=True,
        source_frequency_defaults_and_ignored_flags_preserved=True,model_checkpoint_is_selection_stage_not_final_parameter_authority=True,
        round3_dataset_note='The initial datasets.json predates the extra duplicate dataset. Complete case alignment.fasta files and their pinned hashes are authoritative for those rows; the controller retains its explicit construction.',
        scientific_analysis_admitted=False),indent=2)+'\n').encode())
    add('SHA256SUMS',(''.join(SHA(raw)+'  '+name+'\n' for name,raw in sorted(entries.items()))).encode())
    a.output.parent.mkdir(parents=True,exist_ok=True);a.report.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100444<<16;archive.writestr(info,raw)
    with zipfile.ZipFile(a.output) as archive:
        assert set(archive.namelist())==set(entries)
        for name,raw in entries.items():assert archive.read(name)==raw
    result=dict(status='frozen-complete-report-boundary-controls',protocols=count,completed_fits=sum(r['exit_code']==0 for r in records),refusals=sum(r['exit_code']!=0 for r in records),members=len(entries),decoded_bytes=sum(map(len,entries.values())),artifact_bytes=a.output.stat().st_size,artifact_sha256=SHA(a.output.read_bytes()),scientific_analysis_admitted=False)
    with a.report.open('x',encoding='utf-8') as report:report.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
if __name__=='__main__':
    try:main()
    except Exception as error:
        print('IQ-TREE REPORT CAPTURE FAIL2: '+str(error)+'\nRequired action: preserve all captures and partial artifacts, correct the completion/integrity problem and use NEW artifact/report paths. No analysis is admitted.',file=sys.stderr);raise
