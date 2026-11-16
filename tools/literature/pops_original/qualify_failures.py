#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify original convergence failures stay unadmitted and missing setup stays skipped."""
from pathlib import Path
import argparse,importlib.util,json,shutil,traceback
import numpy as np
import pandas as pd

spec=importlib.util.spec_from_file_location('original_adapter_qualification',Path(__file__).with_name('qualify_adapter.py'))
q=importlib.util.module_from_spec(spec);spec.loader.exec_module(q)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--convergence',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();s=None
    try:
        a.suite='failures';s=q.Suite(a);(s.out/'as-run-qualify_failures.py').write_bytes(Path(__file__).read_bytes())
        r=a.convergence.resolve();reference=json.loads((r/'REFERENCE.json').read_bytes());assert reference['status']=='ORIGINAL_LASSO_WARNING_WITH_FINITE_OUTPUTS' and reference['source_process']['convergence_warnings']>0
        cfg=dict(edition='0.2',task='fit',gene_annotation=str(r/'annotation.tsv'),feature_prefix=str(r/'features'),feature_chunks=str(reference['feature_chunks']),target_type='custom',scores=str(r/'scores.tsv'),method='lasso',p_cutoff='1.01')
        failed=s.request('real-convergence',cfg,2);raw=failed/'raw'
        process=q.properties(failed/'runtime-logs/fit.process.properties');assert process['exit']=='0' and int(process['convergence_warnings'])==reference['source_process']['convergence_warnings']
        assert 'ConvergenceWarning' in (failed/'FAILURE.txt').read_text() and 'UNADMITTED' in (failed/'WARNINGS.md').read_text()
        coef=pd.read_csv(raw/'original.coefs',sep='\t');scores=pd.read_csv(raw/'original.preds',sep='\t')
        assert np.isfinite(coef.beta.iloc[3:].astype(float)).all() and np.isfinite(scores.PoPS_Score).all()
        assert len(scores)==reference['prediction_genes']
        for suffix in ['coefs','preds','marginals']:assert (raw/('original.'+suffix)).read_bytes()==(r/('original.'+suffix)).read_bytes(),suffix
        q.save(s.out/'CONVERGENCE-CONTROL.json',dict(status='PASS_REAL_SOURCE_CONVERGENCE_WARNING_UNADMITTED',original_exit=0,adapter_exit=2,convergence_warnings=int(process['convergence_warnings']),finite_raw_reports_preserved=True,direct_original_reports_byte_identical=True,no_completion_marker=not (failed/'COMPLETED.properties').exists(),no_source_patch=True))
        # Copy only small adoption records. Never mutate the adopted software tree.
        missing=s.out/'runtime-record-omitted';missing.mkdir()
        for name in ['runtime.properties','runtime-files.tsv','SOURCE.json','READY.json','health.stdout','HEALTH-PROCESS.json','ELF-DEPENDENCIES.json','pixi.toml']:
            shutil.copyfile(s.runtime/name,missing/name)
        q.save(missing/'TEST-CONDITION.json',dict(condition='pixi.lock deliberately absent in a separate metadata-only copy',original_runtime_modified=False))
        request=s.out/'missing-runtime-record.properties';config=dict(profile='pops-original-v1',runtime=str(missing),genome_build='synthetic',feature_set_id='synthetic',target_source_id='synthetic',java_heap_mb='256',python_address_space_mb='1024',wall_ms='60000',max_input_bytes='1048576',max_work_bytes='8388608',max_files='1000',edition='0.2',task='munge',gene_annotation=str(a.reference.resolve()/'munger/inputs/annotation.tsv'),feature_directory=str(a.reference.resolve()/'munger/inputs/raw'),nan_policy='zero')
        request.write_text(''.join(k+'='+v+'\n' for k,v in sorted(config.items())),encoding='utf-8');uncreated=s.out/'missing-setup-uncreated'
        s.execute('missing-runtime-record',['--request',str(request),'--out',str(uncreated)],3)
        assert not uncreated.exists(),'Missing setup must be identified before any output capture'
        s.complete(dict(real_source_convergence_warnings=int(process['convergence_warnings']),finite_reports_unadmitted=True,missing_runtime_metadata_skipped_before_output_creation=True));return 0
    except Exception as error:
        if s is not None:q.save(s.out/'FAILURE.json',dict(status='FAIL_ADAPTER_FAILURE_CONTRACT_QUALIFICATION',reason=repr(error),traceback=traceback.format_exc(),processes=s.records))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
