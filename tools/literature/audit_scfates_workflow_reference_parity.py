# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare public adapter output against frozen independent original executions, preserving exact states."""
import argparse,json,math,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_ORIGINAL_REFERENCE_PARITY',cases=[],implementation_credit=False,whole_goal_complete=False);numeric=0;identities=0;arrays={};checked={}
    try:
        proof=json.loads((ROOT/'docs/literature/simpleppt-core-development/VALIDATION.json').read_bytes());expected=proof['logical_file_pins'];reference=ROOT/'build/literature-review/scfates-runtime/original-reference-2/original';original=json.loads((reference/'COMPLETED.json').read_bytes());assert original['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_CPU_ARITHMETIC_AND_WORKFLOWS_ONLY';known={v['id']:v for v in original['workflows'] if v['status']=='complete-original-workflow-only'};worker=json.loads((a.run/'worker/COMPLETED.json').read_bytes());request=json.loads((a.run/'request.original.json').read_bytes());assert worker['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_WORKFLOWS_ONLY' and len(worker['cases'])==len(request['analyses'])==9
        def frozen(path):
            name=path.relative_to(ROOT).as_posix();actual=pin(path);assert actual==expected[name],'Frozen original member changed: '+name;checked[name]=actual
        def read(path):
            if path.is_relative_to(reference):frozen(path)
            return json.loads(path.read_bytes())
        def compare(want,actual,left,right,context):
            nonlocal numeric,identities
            if isinstance(want,dict):
                assert isinstance(actual,dict),context+' type';kind=want.get('kind')
                if kind=='binary-numeric-array':
                    assert set(want)==set(actual),context+' array metadata family'
                    for key in ['kind','dtype','shape','bytes','sha256']:assert want[key]==actual[key],(context,key,want[key],actual[key])
                    l=left/want['path'];r=right/actual['path'];frozen(l);assert pin(r)==dict(bytes=actual['bytes'],sha256=actual['sha256']),context+' actual array content';numeric+=math.prod(want['shape']);arrays[context]=dict(dtype=want['dtype'],shape=want['shape'],bytes=want['bytes'],sha256=want['sha256']);return
                assert set(want)==set(actual),(context,'complete object keys',set(want),set(actual))
                for key in want:compare(want[key],actual[key],left,right,context+'/'+key)
            elif isinstance(want,list):
                assert isinstance(actual,list) and len(want)==len(actual),context+' complete sequence'
                for i,(w,v) in enumerate(zip(want,actual,strict=True)):compare(w,v,left,right,context+'/'+str(i))
            else:
                assert type(want) is type(actual) and want==actual,(context,want,actual)
                if isinstance(want,float):assert math.copysign(1,want)==math.copysign(1,actual),(context,'signed zero/sign state')
                identities+=1
        for case in worker['cases']:
            name=case['id'];assert name in known;left=reference/name;right=a.run/'worker'/name
            for member in ['tree-return.json','root-return.json','rng-before.json','rng-after.json']:compare(read(left/member),read(right/member),left,right,name+'/'+member)
            wanted=read(left/'pseudotime-return.json');actual=read(right/'pseudotime-return.json')
            for key in ['graph','pseudotime_list','assignments']:compare(wanted[key],actual[key],left,right,name+'/pseudotime/'+key)
            # The independent reference collector supplied an unused root_signal
            # covariate even for integer roots. The public request omits it.
            # Compare every scientific observation column plus the root covariate
            # when the actual request explicitly supplied that covariate.
            w=wanted['obs'];v=actual['obs'];assert w['kind']==v['kind']=='dataframe';compare(w['index'],v['index'],left,right,name+'/obs/index');compare(w['index_names'],v['index_names'],left,right,name+'/obs/index_names');compare(w['column_names'],v['column_names'],left,right,name+'/obs/column_names');fields=[c for c in w['columns'] if c!='root_signal' or c in v['columns']];assert fields==v['columns'],(name,'observation columns',fields,v['columns'])
            for column in fields:
                wi=w['columns'].index(column);vi=v['columns'].index(column);compare(w['dtypes'][wi],v['dtypes'][vi],left,right,name+'/obs/'+column+'/dtype');compare(w['column_data'][wi],v['column_data'][vi],left,right,name+'/obs/'+column+'/data')
            compare(known[name]['original_warnings'],read(right/'original-warnings.json'),left,right,name+'/original_warnings');state['cases'].append(dict(id=name,exact_original_arrays_and_scientific_observations=True,original_rng_before_after_exact=True))
        assert len(state['cases'])==9;state.update(status='PASS_NINE_SCFATES_PUBLIC_WORKFLOWS_EXACT_FROZEN_ORIGINAL_PARITY_ONLY',numeric_array_states=numeric,scalar_identity_states=identities,exact_array_fields=arrays,frozen_original_members_checked=checked,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},independent_original_execution_reference=True,independent_trajectory_refit=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
