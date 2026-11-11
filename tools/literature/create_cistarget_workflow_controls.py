# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete TSV controls with source-bound selected ranks and explicit dispositions."""
import argparse,csv,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def tsv(path,rows):
    with path.open('x',encoding='utf8',newline='') as f:csv.writer(f,delimiter='\t',lineterminator='\n').writerows(rows)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    source=json.loads((base/'controls-1/cases.json').read_bytes());reports=[];case_names={prefix+str(n) for prefix in ['ctx-','r-'] for n in [0,1,2,3,4,7,13,20]}
    for case in source:
        if case['id'] not in case_names:continue
        home=out/case['id'];home.mkdir();n=case['selected_features'];total=case['total_features'];features=['feature_'+str(i) for i in range(total)];order=[*range(2,total),0,1]
        rows=[['motif',*[features[i] for i in order]]]
        for motif,ranks in enumerate(case['rankings']):rows.append(['motif_'+str(motif),*[ranks[i] if i<n else (i+motif)%total for i in order]])
        tsv(home/'rankings.tsv',rows);tsv(home/'signature.tsv',[['feature','weight'],*[[features[i],case['weights'][i]] for i in range(n)],['missing_feature',1]])
        annotations=[['motif','tf','evidence'],['motif_0','TF_A','directAnnotation'],['motif_0','TF_A','inferredBy_Orthology'],['motif_1','TF_B','directAnnotation'],['absent_motif','TF_C','supplied-reference-only']]
        if case['id']=='ctx-3':annotations=annotations[:1]
        tsv(home/'annotations.tsv',annotations)
        save(home/'settings.json',dict(schema_version=1,profile=case['profile'],auc_parameter=case['auc_parameter'],recovery_bound=case['recovery_bound'],minimum_auc=0,minimum_nes=0,
            strict_auc=case['id'].endswith(('1','4','7')),strict_nes=case['id'].endswith(('2','3','0')),minimum_present_fraction=.5,write_recovery_curves=True))
        reports.append(dict(id=case['id'],profile=case['profile'],motifs=case['motifs'],total_features=total,present=n,missing=1,annotation_rows=len(annotations)-1,
            original_reference=(base/'reference-controls-1'/('ctxcore' if case['profile']=='CTXCORE_0_2_0' else 'rcistarget')/case['id']).relative_to(ROOT).as_posix()))
    assert len(reports)==16;save(out/'cases.json',reports)
    files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}
    save(out/'COMPLETED.json',dict(status='PASS16_COMPLETE_CISTARGET_TSV_CONTROL_FAMILIES_ONLY',cases=16,files=files,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False));print('Created16 complete TSV families')


if __name__=='__main__':main()
