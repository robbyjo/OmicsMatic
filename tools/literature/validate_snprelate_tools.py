# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise typed original SNPRelate workflows against complete separately collected original results."""
import argparse
import csv
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')


def rows(path):
    with path.open(encoding='utf8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))


def plans():
    result=[];protected={}
    def protect(folder,metadata):
        for name,value in metadata['complete_original_files'].items():
            path=folder/name;assert pin(path)==value,('Original reference changed',str(path));protected[str(path)]=value
        for ext,value in metadata['input_pins'].items():
            path=folder/'inputs'/('cohort.'+ext);assert pin(path)==value;protected[str(path)]=value
        protected[str(folder/'COMPLETED.json')]=pin(folder/'COMPLETED.json')
    for cohort,folder in [('mixed','original-workflow-mixed-3'),('zerohet','original-workflow-zerohet-2'),('allmissing','original-workflow-allmissing-2'),('wide','original-workflow-wide-1')]:
        reference=BASE/folder;done=json.loads((reference/'COMPLETED.json').read_bytes());assert done['status'].startswith('PASS_ORIGINAL_SNPRELATE146_LD_GRM_')
        protect(reference,done)
        for case in done['cases']:
            name=case['case'];layout='snp-first' if name.startswith('snp-first-') else 'sample-first';tail=name[len(layout)+1:]
            mode,method,*start=tail.split('-')
            options=dict(method=method)
            if mode=='ld':options.update(start=start[0],seed=20261110,window_bp=500000,window_variants=None,ld_threshold=0.2)
            result.append(dict(name=cohort+'-'+name,reference=reference/'original-results'/layout/tail,bfile=reference/'inputs/cohort',
                layout=layout,mode='ld-pruning' if mode=='ld' else 'grm',options=options,maf=0.005 if mode=='ld' else 'NaN',missing=0.01,
                autosomes=True,remove_mono=True,expected=0 if case['status']=='ORIGINAL_SUCCESS' and int(case['selected_variants'])>0 else 2,
                original_status=case['status']))
    for cohort,folder in [('baseline','original-king-controls-6'),('mixed','own-edge-controls-3/mixed-row-index'),
            ('zerohet','own-edge-controls-3/zero-heterozygosity-row-index'),('allmissing','own-edge-controls-3/all-missing-row-index')]:
        reference=BASE/folder;done=json.loads((reference/'COMPLETED.json').read_bytes());assert done['status'].startswith('PASS_ORIGINAL_SNPRELATE146_KING_')
        protect(reference,done)
        for container in sorted((reference/'original-results').glob('row-index-*/original-case-dispositions.tsv')):
            for case in rows(container):
                target=container.parent/case['case'];p=rows(target/'original-case-options.tsv')[0]
                def numeric(raw):return raw if raw in ['NaN','Inf','-Inf'] else float.fromhex(raw)
                result.append(dict(name=cohort+'-'+container.parent.name+'-king-'+case['case'],reference=target,bfile=reference/'inputs/cohort',
                    layout='snp-first' if container.parent.name.endswith('snp-first') else 'sample-first',mode='king-robust',
                    options=dict(family_mode='original-fid' if p['family_mode']=='original-FID' else 'singletons'),maf=numeric(p['maf_hex']),missing=numeric(p['missing_rate_hex']),
                    autosomes=p['autosome_only']=='TRUE',remove_mono=p['remove_monomorphic']=='TRUE',
                    expected=0 if case['status']=='ORIGINAL_SUCCESS' and int(case['selected_variants'])>0 else 2,original_status=case['status']))
    assert len(result)==240;return result,protected


def compare(reference,work,mode):
    expected=rows(reference/'original-selected-variants.tsv');actual=rows(work/'original-selected-variants.tsv')
    def selected(values):return [{key:row[key] for key in ['result_row','input_variant_row','original_snp_id']} for row in values]
    if mode=='king-robust':
        variant_map=rows(reference.parent/'original-variant-map.tsv');mapped=[]
        for r in expected:
            original=variant_map[int(r['input_row'])-1]
            assert original['input_row']==r['input_row'] and original['gds_snp_id']==r['original_snp_id']
            assert original['rs_id_node_present']=='TRUE' and original['rs_id_na']=='FALSE'
            mapped.append(dict(result_row=r['result_row'],input_variant_row=r['input_row'],original_snp_id=original['rs_id']))
        expected=mapped
    assert selected(expected)==selected(actual),'Complete original selected SNP identities/order differ'
    if mode=='ld-pruning':
        assert [r['chromosome'] for r in expected]==[r['chromosome'] for r in actual]
        return dict(selected_variants=len(actual),matrix_cells=0)
    if mode=='grm':
        assert rows(reference/'original-complete-matrix.tsv')==rows(work/'original-complete-matrix.tsv'),'Complete original GRM fields differ'
        assert (reference/'original-average.tsv').exists()==(work/'original-average.tsv').exists()
        if (reference/'original-average.tsv').exists():assert rows(reference/'original-average.tsv')==rows(work/'original-average.tsv'),'Original IndivBeta average differs'
    else:
        expected=rows(reference/'original-complete-matrices.tsv');actual=rows(work/'original-complete-matrix.tsv')
        for old,new in zip(expected,actual,strict=True):
            assert {k:old[k] for k in ['row_index','col_index']}=={k:new[k] for k in ['row_index','col_index']}
            for a,b in [('kinship_hex','value_hex'),('kinship_na','value_na'),('kinship_nan','value_nan'),('IBS0_hex','IBS0_hex'),('IBS0_na','IBS0_na'),('IBS0_nan','IBS0_nan')]:
                assert old[a]==new[b],(a,old[a],new[b])
    return dict(selected_variants=len(rows(work/'original-selected-variants.tsv')),matrix_cells=len(rows(work/'original-complete-matrix.tsv')))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--pilot',action='store_true');p.add_argument('--partition',help='Disjoint zero-based INDEX/PARTS, such as 0/4')
    p.add_argument('--no-linux-replay',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_ORIGINAL_WORKFLOW_VALIDATION',new_catalog_credit=False,whole_goal_complete=False);records=[]
    try:
        build=a.build.resolve();metadata=json.loads((build/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_ISOLATED_SNPRELATE_ADAPTER_BUILD_ONLY'
        jar=ROOT/metadata['artifact']['path'];assert pin(jar)=={k:metadata['artifact'][k] for k in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);profile=BASE/'workflow-runtime-profile-1/runtime-profile.json';profile_pin=pin(profile)
        producer=pin(Path(__file__));cases,original_pins=plans()
        if a.pilot:
            assert not a.partition
            names=['mixed-snp-first-ld-composite-random.f500','mixed-snp-first-grm-GCTA','mixed-row-index-snp-first-king-default-families']
            cases=[r for r in cases if r['name'] in names];assert len(cases)==3
        elif a.partition:
            part,total=map(int,a.partition.split('/'));assert 0<=part<total<=8;cases=cases[part::total]
        for case in cases:
            name=case['name'];bundle=out/name;request=dict(schema_version=1,version='1.46.0',mode=case['mode'],bfile=str(case['bfile']),runtime_profile=str(profile),out=str(bundle),
                layout=case['layout'],autosomes_only=case['autosomes'],remove_monomorphic=case['remove_mono'],maf=case['maf'],missing_rate=case['missing'],threads=1,options=case['options'],
                limits=dict(input_bytes=10000000,output_bytes=50000000,output_files=150,samples=100,variants=20000,rows=2000000,pair_variant_operations=1000000000,working_bytes=1000000000,wall_seconds=180))
            request_file=out/(name+'.request.json');save(request_file,request)
            command=[str(JAVA),'-Xmx1g','-jar',str(jar),'--request',str(request_file)]
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==case['expected'],(name,r.returncode,case['expected'],r.stdout[-2500:],r.stderr[-3500:])
            done=json.loads((bundle/'COMPLETED.json').read_bytes());assert done['result_admitted']==(case['expected']==0)
            comparison=compare(case['reference'],bundle/'worker',case['mode']) if case['expected']==0 else {}
            if case['expected']!=0:assert not done['original_analysis_executed'] and 'complete QC population is empty' in done['cause']
            hosts=['windows'] if a.no_linux_replay else ['windows','linux']
            for host in hosts:
                command=([str(JAVA),'-Xmx1g','-jar',str(jar),'--verify',str(bundle)] if host=='windows' else
                    ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1g','-jar',linux(jar),'--verify',linux(bundle)])
                replay=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
                (out/(name+'-'+host+'.stdout')).write_bytes(replay.stdout);(out/(name+'-'+host+'.stderr')).write_bytes(replay.stderr)
                assert replay.returncode==case['expected'],(name,host,replay.returncode,replay.stdout[-1800:],replay.stderr[-3000:])
            record=dict(case=name,mode=case['mode'],expected=case['expected'],actual=r.returncode,original_reference=case['reference'].relative_to(ROOT).as_posix(),
                original_status=case['original_status'],portable_checks=len(hosts),**comparison);records.append(record);print(json.dumps(record),flush=True)
        assert pin(canonical)==before and pin(profile)==profile_pin and pin(jar)=={k:metadata['artifact'][k] for k in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
        assert pin(Path(__file__))==producer and all(pin(Path(name))==value for name,value in original_pins.items())
        state.update(status='PASS_ORIGINAL_SNPRELATE_TYPED_WORKFLOW_PILOT' if a.pilot else 'PASS_ORIGINAL_SNPRELATE_COMPLETE_TYPED_WORKFLOW_REFERENCE_CENSUS',
            records=records,pilot=a.pilot,partition=a.partition,total_declared_cases=240,artifact=metadata['artifact'],source_pins=metadata['source_pins'],producer=producer,
            original_reference_files_unchanged=len(original_pins),canonical_executable_unchanged=before)
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3500],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full selected-population or matrix-reference checks')
    raise SystemExit(main())
