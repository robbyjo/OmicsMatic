#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Challenge complete original bundles, genuine refitting and declared resource bounds."""
from pathlib import Path
import argparse,csv,importlib.util,json,shutil,traceback
import numpy as np
import pandas as pd

spec=importlib.util.spec_from_file_location('original_adapter_qualification',Path(__file__).with_name('qualify_adapter.py'))
q=importlib.util.module_from_spec(spec);spec.loader.exec_module(q)

def write_properties(path,values):path.write_text(''.join(k+'='+v+'\n' for k,v in sorted(values.items())),encoding='utf-8')
def reseal(root):
    rows=[]
    for p in sorted(root.rglob('*')):
        relative=p.relative_to(root).as_posix()
        if relative in ['FILES.tsv','COMPLETED.properties']:continue
        assert not p.is_symlink()
        rows.append((relative+'/' if p.is_dir() else relative,0 if p.is_dir() else p.stat().st_size,'-' if p.is_dir() else q.sha(p)))
    (root/'FILES.tsv').write_text('path\tbytes\tsha256\n'+''.join('%s\t%d\t%s\n'%row for row in rows),encoding='utf-8')
    marker=q.properties(root/'COMPLETED.properties');marker['manifest_sha256']=q.sha(root/'FILES.tsv');write_properties(root/'COMPLETED.properties',marker)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--bundle',type=Path,required=True);p.add_argument('--previous-artifact-bundle',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();s=None
    try:
        a.suite='integrity';s=q.Suite(a);own_sha=q.sha(__file__);(s.out/'as-run-qualify_integrity.py').write_bytes(Path(__file__).read_bytes());original=a.bundle.resolve();before={p.relative_to(original).as_posix():q.sha(p) for p in original.rglob('*') if p.is_file()}
        q.save(s.out/'ORIGINAL-BUNDLE-BEFORE.json',before)
        def clone(name):
            root=s.out/(name+'-altered-copy');shutil.copytree(original,root);return root
        def rejected(name,root,diagnostic,creates_output=False):
            output=s.out/(name+'-unadmitted');folder=s.execute(name,['--verify',str(root),'--out',str(output)],2)
            assert not (output/'COMPLETED.properties').exists() and output.exists()==creates_output,(name,'Unexpected output/admission state')
            assert diagnostic in (folder/'stderr').read_text(),(name,diagnostic)
            return output
        root=clone('unlisted-member');(root/'results/UNREVIEWED.txt').write_text('Deliberately unlisted fixture member\n');rejected('unlisted-member',root,'membership differ')
        root=clone('wrong-edition');marker=q.properties(root/'COMPLETED.properties');marker['edition']='0.1';write_properties(root/'COMPLETED.properties',marker);rejected('wrong-edition',root,'completion declaration differs')
        root=clone('unknown-declaration');marker=q.properties(root/'COMPLETED.properties');marker['unknown_field']='ignored-is-not-allowed';write_properties(root/'COMPLETED.properties',marker);rejected('unknown-declaration',root,'completion declaration fields differ')
        root=clone('resealed-wrong-score');scores=pd.read_csv(root/'results/predictions.tsv',sep='\t',dtype=str,keep_default_na=False);scores.loc[0,'PoPS_Score']=str(float(scores.loc[0,'PoPS_Score'])+1);scores.to_csv(root/'results/predictions.tsv',sep='\t',index=False);reseal(root);rejected('resealed-wrong-score',root,'independently accumulated')
        root=clone('resealed-self-consistent-forgery');coefficients=pd.read_csv(root/'results/coefficients.tsv',sep='\t',dtype=str,keep_default_na=False);weights=coefficients.beta.iloc[3:].astype(float).to_numpy();weights[0]+=.3;coefficients.loc[coefficients.index[3:],'beta']=['%.17g'%value for value in weights];coefficients.to_csv(root/'results/coefficients.tsv',sep='\t',index=False)
        matrix=np.load(str(root/'audit/features.npy'));values=matrix.dot(weights);scores=pd.read_csv(root/'results/predictions.tsv',sep='\t',dtype=str,keep_default_na=False);scores['PoPS_Score']=['%.17g'%value for value in values];scores.to_csv(root/'results/predictions.tsv',sep='\t',index=False)
        shutil.copyfile(root/'results/coefficients.tsv',root/'raw/original.coefs');shutil.copyfile(root/'results/predictions.tsv',root/'raw/original.preds');reseal(root)
        output=rejected('resealed-self-consistent-forgery',root,'Genuine original-source refit changed',True);assert q.properties(output/'worker-process.properties')['exit']=='0' and (output/'WORKER_DONE.properties').is_file()
        rejected('previous-artifact',a.previous_artifact_bundle.resolve(),'Original adapter implementation differs')
        r=a.reference.resolve()/'v02-lasso';cfg=dict(edition='0.2',task='fit',gene_annotation=str(r/'annotation.tsv'),feature_prefix=str(r/'features'),feature_chunks='5',target_type='custom',scores=str(r/'scores.tsv'),method='lasso',p_cutoff='1.01')
        for name,overrides,diagnostic in [('input-capacity',dict(max_input_bytes='1'),'max_input_bytes'),('worker-timeout',dict(wall_ms='1'),'wall_ms'),('selection-capacity',dict(max_selected_features='1'),'capacity'),('python-address-space',dict(python_address_space_mb='1'),'python_address_space_mb')]:
            bundle=s.request(name,dict(cfg,**overrides),2);assert diagnostic in (bundle/'FAILURE.txt').read_text(),name
            if name=='selection-capacity':assert len(pd.read_csv(bundle/'raw/original.coefs',sep='\t'))==152,'The source must not silently thin the 149 model features'
            if name=='worker-timeout':
                identity=q.properties(bundle/'worker-identity.properties');pid=int(identity['pid']);cmdline=Path('/proc/%d/cmdline'%pid)
                live=cmdline.read_bytes() if cmdline.exists() else b'';assert not (b'PopsOriginalWorker' in live and str(bundle).encode() in live),'Owned timed-out worker remains live'
        assert q.sha(__file__)==own_sha and before=={p.relative_to(original).as_posix():q.sha(p) for p in original.rglob('*') if p.is_file()}
        s.complete(dict(negative_cases=10,complete_original_bundle_preserved=True,self_consistent_resealed_forgery_rejected_only_after_actual_original_refit=True,wrong_completion_declarations_rejected=True,previous_artifact_not_rebound=True,resource_bounds_enforced=True,no_unadmitted_root_has_completion_marker=True));return 0
    except Exception as error:
        if s is not None:q.save(s.out/'FAILURE.json',dict(status='FAIL_ORIGINAL_BUNDLE_INTEGRITY_QUALIFICATION',reason=repr(error),traceback=traceback.format_exc(),processes=s.records))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
