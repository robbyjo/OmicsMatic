#!/usr/bin/env python3
"""Freeze complete original SHAPEIT5 scanner masks and explicit failure controls."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'build/literature-review/shapeit-runtime'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--capture',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();capture=args.capture.resolve()
    observed=json.loads((capture/'observed.json').read_bytes())
    assert observed['status']=='passed-original-common-stage-selection-and-joined-panel-controls'
    assert observed['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886'
    assert len(observed['cases'])==observed['original_scanner_calls']==50
    assert observed['source_protocol_sha256']==sha(ROOT/'tools/literature/shapeit-reference/SelectionProtocol.cpp')
    assert observed['fixture_generator_sha256']==sha(ROOT/'tools/literature/shapeit-reference/MakeSelectionFixture.cpp')
    source=Path('/opt/omicsmatic-shapeit-dev-1/source5-attempt-1')
    entries=json.loads((BASE/'upstream/5-c34d-selected-source.json').read_bytes())['files']
    reconciled=json.loads((BASE/'reader-protocol-attempt-1/build-reconciliation.json').read_bytes())
    outputs={row['path']:row['after'] for row in reconciled['allowed_changed_original_member']}
    for entry in entries:
        path=source/entry['path'];actual=hashlib.sha256(path.readlink().as_posix().encode()).hexdigest() if entry['mode']=='120000' else sha(path)
        assert actual==outputs.get(entry['path'],entry['sha256']),(entry['path'],actual)
    members={}
    for path in capture.iterdir():
        if path.is_file() and path.name!='running.json':members['capture/'+path.name]=path.read_bytes()
    void=BASE/'selection-void-boundary-1'
    boundary=json.loads((void/'observed.json').read_bytes())
    assert boundary['status']=='confirmed-original-void-file-gap-vector-boundary'
    for path in void.iterdir():
        if path.is_file() and path.name!='running.json':members['source-boundary/'+path.name]=path.read_bytes()
    index=['name\tmain\treference\tscaffold\tregion\tsnp\tmaf\texpected_error\texit_code\tresult']
    events=selected=complete=failed=0
    for item in observed['cases']:
        index.append('\t'.join(str(item[key]) if item[key] is not None else '-' for key in ['name','main','reference','scaffold','region'])+'\t'+('1' if item['snp'] else '0')+'\t'+'\t'.join(str(item[key]) if item[key] is not None else '-' for key in ['maf','expected_error','exit_code','result']))
        raw=(capture/item['result']).read_bytes();assert hashlib.sha256(raw).hexdigest()==item['sha256']
        if item['complete']:
            complete+=1;end=raw.decode().splitlines()[-1].split('\t');assert end[0]=='E'
            events+=int(end[1]);selected+=int(end[2]);assert item['exit_code']==0
        else:failed+=1;assert item['exit_code']!=0 and not any(line.startswith('E\t') for line in raw.decode().splitlines())
    assert (complete,failed)==(36,14)
    members['cases.tsv']=('\n'.join(index)+'\n').encode()
    index=['name\tsamples\tkind\tmode\tac_type\tan_type\tpath']
    for name,item in sorted(observed['fixtures'].items()):
        assert sha(capture/item['path'])==item['sha256']
        kind='BINARY' if item['mode']=='xcf' else 'BCF' if item['samples'] else 'VOID'
        index.append('\t'.join(map(str,[name,item['samples'],kind,item['mode'],item['ac_type'],item['an_type'],item['path']])))
    members['fixtures.tsv']=('\n'.join(index)+'\n').encode()
    original=ROOT/'src/test/resources/org/jlinalg/genetics/shapeit-reference/initial-reader.zip'
    assert sha(original)=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02'
    members['source/original-reader-source-closure.zip']=original.read_bytes()
    hts=BASE/'upstream/htslib-1.18'
    for name in ['bcf_sr_sort.c','bcf_sr_sort.h','synced_bcf_reader.c','htslib/synced_bcf_reader.h','LICENSE']:
        members['source/inspected-htslib-1.18/'+name]=(hts/name).read_bytes()
    for name in ['SelectionProtocol.cpp','MakeSelectionFixture.cpp','collect_selection_evidence.py']:
        members['protocol/'+name]=(ROOT/'tools/literature/shapeit-reference'/name).read_bytes()
    for name in ['run_selection_protocols_1.py','run_selection_protocols_2.py','selection-protocol-controller-1.log','selection-protocol-controller-2.log','check_selection_void_boundary_1.py','selection-void-boundary-controller-1.log']:
        members['history/'+name]=(BASE/name).read_bytes()
    for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']:
        members['licenses/'+name]=(ROOT/'LICENSES'/name).read_bytes()
    for name in ['pixi.toml','pixi.lock','observed.json']:
        members['toolchain/'+name]=(BASE/'toolchain-attempt-1'/name).read_bytes()
    objects=sorted((source/'phase_common/obj').glob('*.o'))
    prefix=Path('/opt/omicsmatic-shapeit-dev-1/.pixi/envs/default')
    provenance={'schema_version':1,'status':'complete-original-SHAPEIT5-selection-controls','original_scanner_calls':50,
                'complete_scanner_results':36,'expected_original_failures':14,'additional_checked_source_boundary_calls':1,
                'synchronized_events_in_complete_controls':events,'selected_sites_in_complete_controls':selected,
                'original_source_members_verified':len(entries),'original_linked_objects':[{'name':p.name,'sha256':sha(p)} for p in objects if p.name!='main.o'],
                'actual_conda_htslib_1_18_library_sha256':sha(prefix/'lib/libhts.so'),
                'inspected_official_htslib_1_18_commit':'d8ca374b1977601246ce4a20370c7b78441a01aa',
                'actual_runtime_library_is_conda_build_not_newly_compiled_from_inspected_source':True,
                'XCF_payloads_are_empty_scan_controls_not_genotype_or_phasing_validation':True,
                'declared_count_inconsistencies_are_deliberate_synthetic_boundary_inputs':True,
                'public_phasing_adapter_available':False}
    members['provenance.json']=(json.dumps(provenance,indent=2)+'\n').encode()
    members['SHA256SUMS']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(members.items())).encode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('xb') as stream,zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(members.items()):
            entry=zipfile.ZipInfo(name,(2026,11,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
    with zipfile.ZipFile(args.output) as archive:assert archive.testzip() is None
    print(json.dumps(dict(provenance,archive_sha256=sha(args.output),members=len(members),decoded_bytes=sum(map(len,members.values()))),indent=2))


if __name__=='__main__':main()
