# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only comparison of the saved successful R worker after a checker requested unused gene-set outputs."""
import argparse,gzip,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_dndscv_worker import table


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--probe',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';probe=a.probe.resolve();out=a.out.resolve();assert probe.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir();old=json.loads((probe/'COMPLETED.json').read_bytes());assert old['status']=='FAIL2_DNDSCV_WORKER_PROBE_INCOMPLETE' and 'globaldnds_geneset.json' in old['detail'];worker=probe/'stage/worker';process=json.loads((probe/'process.json').read_bytes());report=json.loads((worker/'worker-report.json').read_bytes());assert process['exit_code']==0 and report['status']=='completed-with-warnings';assert (probe/'LIBRARIES-BEFORE.json').read_bytes()==(probe/'LIBRARIES-AFTER.json').read_bytes();saved=base/'original-controls-1';comparisons=[];capture={p.relative_to(ROOT).as_posix():pin(p) for p in [probe/'COMPLETED.json',probe/'process.json',worker/'worker-report.json',worker/'parameters.json']}
    for case in json.loads((worker/'parameters.json').read_bytes())['analyses']:
        assert not case['gene_set_comparison'];home=worker/'cases'/case['id'];previous=saved/case['id'];n=0
        for name in ['globaldnds','sel_cv','sel_loc','annotmuts','genemuts','geneindels','mle_submodel','syncv','wrongmuts','exclmuts']:
            original=previous/(name+'.json')
            if not original.exists():assert not (home/(name+'.json')).exists();continue
            value=json.loads(original.read_bytes());expected=([c['name'] for c in value['columns']],value['row_names'],[[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])]);assert table(home/(name+'.json'))==expected,(case['id'],name);n+=1
        for name in ['N.f64be.gz','L.f64be.gz']:assert gzip.decompress((home/name).read_bytes())==gzip.decompress((previous/name).read_bytes())
        comparisons.append(dict(id=case['id'],complete_original_tables_compared=n,complete_N_and_L_arrays_exact=True))
    assert all(pin(ROOT/n)==v for n,v in capture.items());save(out/'COMPLETED.json',dict(status='passed-read-only-recovery-of-original-dndscv-worker-comparison',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},saved_attempt_pins=capture,reference_comparisons=comparisons,source_worker_cases=3,original_R_reexecuted=False,earlier_failed_checker_not_rewritten=True,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Read-only recovery passed3 complete original model comparisons; R was not rerun and the failed checker is retained')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    main()
