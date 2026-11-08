# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare real late source/policy failures and lightweight supervision cases without patching original software."""
import argparse,copy,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
def local(path):return ROOT/Path(path).relative_to('/mnt/d/projects/JLinAlg')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--results',type=Path,required=True);a=p.parse_args();out=a.out.resolve();results=a.results.resolve();assert not out.exists() and not results.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime') and results.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);base=json.loads((ROOT/'build/literature-review/gseapy-runtime/execution-fixtures-2/1.1.8.json').read_bytes());by={r['id']:r for r in base['analyses']};records={}
    def prepared(name,case,edit):
        raw=json.loads(local(case['input']['path']).read_bytes());edit(raw);path=out/(name+'.prepared.json');save(path,raw);case['input']['path']=linux(path)
    def save_case(name,cases,expected,changes=None):
        request=copy.deepcopy(base);request.update(changes or {});request['analyses']=cases;request['out']=linux(results/name);path=out/(name+'.json');save(path,request);records[name]=dict(request=pin(path),expected=expected,earlier_complete_cases=1 if name.startswith('late-') else 0)
    def early():case=copy.deepcopy(by['enrichr-local']);case['id']='earlier-complete';return case
    late=copy.deepcopy(by['enrichr-local']);late['id']='numeric-background-source-error';prepared('numeric-background',late,lambda v:v.__setitem__('background',80));save_case('late-numeric-background',[early(),late],'original-TypeError-no-partial-admission')
    late=copy.deepcopy(by['prerank']);late['id']='no-matching-library';prepared('no-matching-library',late,lambda v:v.__setitem__('gene_sets',{'unmatched':['ABSENT_A','ABSENT_B','ABSENT_C']}));save_case('late-empty-library',[early(),late],'original-LookupError-no-partial-admission')
    late=copy.deepcopy(by['gsva-poisson']);late['id']='negative-input-policy';late['policies']['source_input_changes']='fail';prepared('negative-input-policy',late,lambda v:v['values'][0].__setitem__(0,-2.0));save_case('late-clipping-policy',[early(),late],'complete-source-output-rejected-by-declared-policy')
    late=copy.deepcopy(by['gsea-gene_set']);late['id']='source-gene-filter-policy';late['policies']['source_input_changes']='fail'
    def constant(value):value['ids'].append('G_SOURCE_CONSTANT');value['values'].append([7.0]*len(value['columns']));value['gene_sets']['up'].append('G_SOURCE_CONSTANT')
    prepared('source-gene-filter-policy',late,constant);save_case('late-filtering-policy',[early(),late],'complete-source-output-rejected-by-declared-policy')
    save_case('timeout',copy.deepcopy(base['analyses']),'real-timeout-cleanup',dict(timeout_seconds=1))
    save_case('output-capacity',copy.deepcopy(base['analyses']),'real-sampled-output-capacity-cleanup',dict(maximum_output_bytes=32768))
    save_case('cancellation',copy.deepcopy(base['analyses']),'confirmed-live-owned-process-cancellation')
    save(out/'COMPLETED.json',dict(status='prepared-seven-real-source-policy-and-supervision-fixtures',requests=records,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},analysis_admitted=False,whole_goal_complete=False));print('Prepared four late source/policy failures and real timeout/capacity/cancellation fixtures')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/failure fixture checks')
    main()
