# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record verified source behaviors that a native chromVAR implementation must preserve."""
import argparse,json
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import pin
def scalar(v):return v[0] if isinstance(v,list) and len(v)==1 else v
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();source=BASE/'chromvar-deviations-original-3';proof=json.loads((source/'COMPLETED.json').read_bytes());data=json.loads((source/'original.json').read_bytes());assert proof['profile_case_protocols']==len(data['results'])==52
    assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items()) and pin(source/'original.json')==proof['original']
    profiles={}
    for v in data['results']:profiles.setdefault(scalar(v['profile']),{})[scalar(v['id'])]=v
    assert set(profiles)=={'1.16.0-source','1.34.1-package'};old=profiles['1.16.0-source'];current=profiles['1.34.1-package'];assert set(old)==set(current) and all(old[k][stage]['value']==current[k][stage]['value'] for k in old for stage in ['expectations','single','core'])
    counts={stage:dict(success=sum(not isinstance(v[stage]['value'],dict) or 'error' not in v[stage]['value'] for v in data['results']),source_failure=sum(isinstance(v[stage]['value'],dict) and 'error' in v[stage]['value'] for v in data['results'])) for stage in ['expectations','single','core']}
    base_sum=sum(map(float,old['baseline']['expectations']['value']));norm_sum=sum(map(float,old['norm-true']['expectations']['value']));group_norm_sum=sum(map(float,old['grouped-norm']['expectations']['value']));assert abs(base_sum-1)<1e-12 and abs(norm_sum-6)<1e-12 and abs(group_norm_sum-2)<1e-12
    assert 'error' in old['grouped']['expectations']['value'] and 'error' in old['one-cell-multiple-annotations']['core']['value'] and 'error' not in old['one-cell-multiple-annotations']['single']['value'] and 'error' in old['zero-peak']['core']['value']
    out.mkdir();name='tools/literature/audit_chromvar_deviation_controls.py';save(out/'SOURCE-AUDIT.json',dict(status='verified-original-chromVAR-deviation-source-behaviors-not-an-implementation',source_pins={name:pin(ROOT/name)},original_capture=pin(source/'COMPLETED.json'),profile_case_protocols=52,call_outcomes=counts,numerical_outputs_equal_between_source_profiles=True,default_expectation_sum=base_sum,norm_true_expectation_sum=norm_sum,group_norm_true_expectation_sum=group_norm_sum,source_requirements=['norm=TRUE uses row sums of per-cell fractions, not their mean','grouped norm=FALSE retains its singleton-column rowSums failure','one-cell multiple-annotation core retains its output-dimension failure; single calculation succeeds','core requires positive pooled fragments for every peak; single calculation does not enforce this gate','duplicate annotation indices retain multiplicity in observed and sampled sparse products','background matrix is peaks by iterations with explicit one-based peak indices','deviation centering uses background means and sample SD with B-1 denominator','expected<threshold is source NA; zero/empty populations retain their distinct NA/NaN/Inf kinds','single permits explicit threshold; full core calls single with its original default threshold','prepared background controls do not execute GC/intensity sampling'],historical_whole_packages_executed=False,current_dependencies_explicit=True,native_implementation_available=False,catalog_implementation_credit=False));print(json.dumps(dict(status='verified-original-chromVAR-deviation-source-behaviors',call_outcomes=counts)),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source behavior checks')
    main()
