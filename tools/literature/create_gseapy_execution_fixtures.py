# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the checked original-class controls to complete Java RUN requests on fresh output paths."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--results',type=Path,required=True);a=p.parse_args();out=a.out.resolve();results=a.results.resolve();assert not out.exists() and not results.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime') and results.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);base=ROOT/'build/literature-review/gseapy-runtime/workflow-fixtures-2';records={}
    for edition in ['1.0.4','1.1.2','1.1.3','1.1.8']:
        original=json.loads((base/edition/'request.json').read_bytes());analyses=[]
        for row in original['analyses']:
            case=dict(row);case['input']=dict(path=case.pop('prepared'),format='prepared-json-float64',source='Explicit synthetic local-library original-class control; not an inferred paper input');case['policies']=dict(source_input_changes='retain-with-warning',nonfinite_diagnostics='retain-with-warning',empty_results='fail');analyses.append(case)
        request=dict(schema_version=1,mode='RUN',edition=edition,runtime=linux(ROOT/'build/literature-review/gseapy-runtime/workflow-setup-1/COMPLETED.json'),out=linux(results/edition),analyses=analyses,maximum_input_bytes=16777216,maximum_output_bytes=134217728,maximum_files=10000,timeout_seconds=300,maximum_audit_operations=100000000,maximum_matrix_cells=1000000,python_hash_seed=0);path=out/(edition+'.json');save(path,request);records[edition]=dict(request=pin(path),cases=len(analyses))
    save(out/'COMPLETED.json',dict(status='prepared-33-complete-GSEApy-Java-RUN-fixtures-only',requests=records,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},full_adapter_admitted=False,whole_goal_complete=False));print('Prepared33 complete Java requests; no output created or analysis admitted')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable fixture bindings')
    main()
