# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent retained-PCA geometry and variance-state audit, with original auto/randomized solver semantics explicit."""
import argparse,json,math
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_leafcutter_phenotypes import audit_pca

def audit(source,pca):
    result=audit_pca(source,pca);result['variance_ratio_equations']=0
    if pca is None:return result
    values=[r['inverse_normal_scores'] for r in source['kept']];n=len(values);m=len(source['samples']);total=sum((r[j]-pca['mean'][j])**2 for r in values for j in range(m))/(n-1)
    for explained,ratio in zip(pca['explained_variance'],pca['explained_variance_ratio']):
        if total==0:assert ratio==dict(state='NaN'),'Source undefined variance ratio was replaced or hidden'
        else:assert isinstance(ratio,float) and math.isfinite(ratio) and math.isclose(ratio,explained/total,rel_tol=2e-10,abs_tol=2e-10)
        result['variance_ratio_equations']+=1
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);reference=a.reference.resolve();marker=json.loads((reference/'COMPLETED.json').read_bytes());assert marker['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_PHENOTYPE_AND_PCA_CONTROLS_ONLY';totals={}
    for c in json.loads((reference/'cases.json').read_bytes())['cases']:
        home=reference/c['id'];source=json.loads((home/'original-result.json').read_bytes());pca=json.loads((home/'original-PCA.json').read_bytes())
        for k,v in audit(source,pca).items():totals[k]=totals.get(k,0)+v
    save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_RETAINED_ORIGINAL_LEAFCUTTER_PCA_GEOMETRY_AND_VARIANCE_STATES_ONLY',checks=totals,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/validate_leafcutter_phenotypes.py']},reference_manifest=pin(reference/'COMPLETED.json'),implementation_credit=False,whole_goal_complete=False));print(totals)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable retained geometry checks')
    main()
