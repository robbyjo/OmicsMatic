#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original common-reader/fitting controls, including retained failures."""
from pathlib import Path
import argparse,base64,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[3]
PEER='/mnt/d/projects/JLinAlg/'
sha=lambda raw:hashlib.sha256(raw).hexdigest()
def local(path):assert path.startswith(PEER);return ROOT/path.removeprefix(PEER)
def collect(report,output):
    observed=json.loads((report/'observed.json').read_bytes());assert observed['status']=='captured-original-common-fitting-controls'
    assert observed['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886' and observed['program_sha256']=='74d6f951f6103e709ee3e754397a86b6b2cb4e0ab0bf543c1378cb4b7d894689'
    members={};cases=[];total_rows=total_cells=ref_cells=0
    def add(name,raw):
        assert not name.startswith('/') and '..' not in Path(name).parts
        if name in members:assert members[name]==raw,name
        else:members[name]=raw
    for case in observed['cases']:
        join=(report/case['original_join']).read_bytes();lines=[line.split('\t') for line in join.decode().splitlines()];assert lines[-1]==['Z']
        bindings=[]
        for row in lines:
            if row[:2]!=['F','B']:continue
            peer=base64.b64decode(row[4]).decode();source=local(peer);raw=source.read_bytes();assert sha(raw)==row[5] and len(raw)==int(row[6]);name='inputs/'+sha(raw)[:16]+'-'+source.name;add(name,raw)
            bindings.append(dict(panel=int(row[2]),kind=row[3],peer_path=peer,member=name,sha256=sha(raw),bytes=len(raw)))
        roles=['TARGET']+(['REFERENCE'] if case['reference'] else [])+(['SCAFFOLD'] if case['scaffold'] else [])
        add('capture/'+case['original_join'],join)
        if case['original_initial']:
            raw=(report/case['original_initial']).read_bytes();add('capture/'+case['original_initial'],raw)
            rows=[line.split('\t') for line in raw.decode().splitlines()];assert rows[0]==['V','OMICS-SHAPEIT-COMMON-DATA-1']
            header=next(r for r in rows if r[0]=='H');target,reference,variants=map(int,header[1:]);end=rows[-1]
            assert end==['E',str(variants),str(variants*target),str(variants*2*reference)]
            assert sum(r[0]=='G' for r in rows)==variants*target and sum(r[0]=='A' for r in rows)==variants*2*reference
            total_rows+=variants;total_cells+=variants*target;ref_cells+=variants*2*reference
        if case['complete']:
            assert case['initial_exit_code']==case['fit_exit_code']==0
            raw=(report/case['output']).read_bytes();assert sha(raw)==case['output_sha256'];add('outputs/'+case['output'],raw)
            oracle=report/(case['name']+'.output-wire.tsv');assert oracle.read_text().splitlines()[-1].startswith('E\t');add('capture/'+oracle.name,oracle.read_bytes())
        else:assert case['initial_exit_code']==case['fit_exit_code']==1 and case['expected_source_failure'] in ['missing-reference','unphased-reference']
        for prefix in ['initial-','fit-','output-query-','output-wire-','join-']:
            for suffix in ['stdout','stderr']:
                path=report/(prefix+case['name']+'.'+suffix)
                if path.exists():add('capture/'+path.name,path.read_bytes())
        map_member=None
        if case['map']:
            raw=local(case['map']).read_bytes();map_member='inputs/complete.map.tsv';add(map_member,raw)
        cases.append(dict(case,bindings=bindings,roles=roles,join_member='capture/'+case['original_join'],join_sha256=sha(join),initial_member='capture/'+case['original_initial'] if case['original_initial'] else None,
                          output_member='outputs/'+case['output'] if case['output'] else None,map_member=map_member))
    for fixture in observed['fixtures'].values():
        add('capture/'+fixture['wire_oracle'],(report/fixture['wire_oracle']).read_bytes())
    for name in ['observed.json','runs.json','output-observations.json']:add('capture/'+name,(report/name).read_bytes())
    for name in ['MakeCommonFitFixture.cpp','CommonDataProtocol.cpp','BcfWireProtocol.cpp','JoinedWireProtocol.cpp','collect_common_fit_evidence.py']:
        raw=(Path(__file__).parent/name).read_bytes();add('protocol/'+name,raw)
        if name in observed['protocol_sha256']:assert sha(raw)==observed['protocol_sha256'][name]
    original=ROOT/'src/test/resources/org/jlinalg/genetics/shapeit-reference/initial-reader.zip';assert sha(original.read_bytes())=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02';add('source/original-reader-source-closure.zip',original.read_bytes())
    previous=report.parent/'common-fit-controls-1'
    for name in ['runs.json','wire-target-50-main50-base.stdout','wire-target-50-main50-base.stderr']:add('history/first-controller/'+name,(previous/name).read_bytes())
    add('cases.json',(json.dumps(cases,indent=2)+'\n').encode())
    add('README.txt',b'Original c34d4db common-stage target/reference/scaffold reader and actual phasing controls. Ten complete runs and two source failures retained. Default MCMC, source seed, options, raw BCF inputs/outputs, GT getter captures, complete initial states and native-independent output observations retained. Original reference-mode AN counts target plus reference haplotypes while AC counts target only; preserve this source result and use independently verified target counts for target frequency. Unknown scaffold samples can yield zero applied constraints despite exit zero. No managed runtime or public phasing adapter is admitted by this fixture.\n')
    add('SHA256SUMS',''.join(sha(raw)+'  '+name+'\n' for name,raw in sorted(members.items())).encode())
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('xb') as stream:
        with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(members.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
    print(json.dumps(dict(status='frozen-original-common-reader-and-fitting-controls',cases=len(cases),complete=sum(c['complete'] for c in cases),expected_failures=sum(not c['complete'] for c in cases),selected_rows=total_rows,target_cells=total_cells,reference_haplotype_cells=ref_cells,members=len(members),decoded_bytes=sum(map(len,members.values())),fixture_sha256=sha(output.read_bytes())),indent=2))
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--report',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();collect(args.report,args.output)
