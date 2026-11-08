# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Cover actual version-specific options, report placeholders and source transformations with explicit synthetic inputs."""
import argparse,copy,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
def local(path):return ROOT/Path(path).relative_to('/mnt/d/projects/JLinAlg')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--results',type=Path,required=True);a=p.parse_args();out=a.out.resolve();results=a.results.resolve();assert not out.exists() and not results.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime') and results.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);records={}
    for edition in ['1.0.4','1.1.2','1.1.3','1.1.8']:
        baseline=json.loads((ROOT/'build/literature-review/gseapy-runtime/execution-fixtures-2'/(edition+'.json')).read_bytes());cases=copy.deepcopy(baseline['analyses']);by={c['id']:c for c in cases};home=out/edition;home.mkdir()
        def add(name,template,changes=None,edit=None):
            case=copy.deepcopy(by[template]);case['id']=name;case['options'].update(changes or {})
            if edit:
                value=json.loads(local(case['input']['path']).read_bytes());edit(value);path=home/(name+'.prepared.json');save(path,value);case['input']['path']=linux(path)
            cases.append(case)
        add('prerank-classic-zero','prerank',dict(weight=0.0,permutation_num=0,ascending=True))
        add('prerank-power','prerank',dict(weight=1.5,permutation_num=17))
        add('gsea-difference-ascending','gsea-gene_set',dict(method='diff_of_classes',ascending=True,weight=2.0,permutation_num=17))
        add('gsea-phenotype-classic-zero','gsea-phenotype',dict(weight=0.0,permutation_num=0))
        add('ssgsea-custom-classic','ssgsea-rank',dict(sample_norm_method='custom',weight=0.0))
        add('ssgsea-zscore','ssgsea-rank',dict(correl_norm_type='zscore',weight=1.0))
        add('ssgsea-symrank-log','ssgsea-rank',dict(correl_norm_type='symrank',sample_norm_method='log'))
        add('ssgsea-permuted','ssgsea-rank',dict(permutation_num=17))
        add('enrichr-union-background','enrichr-local',edit=lambda v:v.__setitem__('background',None))
        add('method-term-collision','prerank',dict(permutation_num=17),lambda v:v['gene_sets'].__setitem__('prerank',v['ids'][:15]))
        def quoted(value):
            changes={value['ids'][0]:' gene with\tTAB ',value['ids'][1]:'"quoted"\nline',value['ids'][2]:'β',value['ids'][3]:'😀'};value['ids']=[changes.get(g,g) for g in value['ids']];value['gene_sets']={term:[changes.get(g,g) for g in genes] for term,genes in value['gene_sets'].items()}
        add('literal-quoted-identities','prerank',dict(permutation_num=17),quoted)
        if edition!='1.1.8':
            def numeric(value):value['background']=80;value['query']=value['query'][:8];value['gene_sets'].pop('outside')
            add('enrichr-numeric-population','enrichr-local',edit=numeric)
        if edition!='1.0.4':
            add('ssgsea-no-sample-normalization','ssgsea-rank',dict(sample_norm_method=None))
            add('gsva-direct-alternative-flags','gsva-none',dict(mx_diff=False,abs_rnk=True,weight=2.0))
            add('gsva-poisson-negative-input','gsva-poisson',edit=lambda v:v['values'][0].__setitem__(0,-2.0))
        if edition=='1.1.8':
            def mixed(value):value['query']=[g.lower() for g in value['query'] if g!='OUTSIDE'];value['background']=[g.lower() for g in value['background']]
            add('enrichr-original-case-conversion','enrichr-local',edit=mixed)
        request=copy.deepcopy(baseline);request['analyses']=cases;request['out']=linux(results/edition);request['maximum_files']=30000;request['maximum_output_bytes']=268435456;request['maximum_audit_operations']=1000000000;path=out/(edition+'.json');save(path,request);records[edition]=dict(request=pin(path),cases=len(cases),case_ids=[c['id'] for c in cases])
    total=sum(v['cases'] for v in records.values());assert total==90;save(out/'COMPLETED.json',dict(status='prepared-ninety-explicit-extended-original-GSEApy-controls',requests=records,total_cases=total,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},whole_goal_complete=False));print('Prepared90 explicit original-class controls; includes legacy/modern and source-transformation differences')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete fixture checks')
    main()
