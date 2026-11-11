# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete original Monocle2 reference controls; no installation or user analysis admission."""
import json
import math
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source


def verify():
    folder=ROOT/'docs/literature/monocle2-reference-development';data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((folder/'VALIDATION.json').read_bytes())
    assert p['status']=='passed-original-Monocle2-reference-readiness-not-Java-adapter-admission'
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==digest
    assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for name,wanted in p['source_pins'].items():verify_source(ROOT,data,name,wanted['sha256']);assert byte_pin(files['current-source/'+name])==wanted
    base='build/literature-review/monocle-runtime/';get=lambda n:json.loads(files[base+n]);probe='monocle2-legacy-readiness-probe-6/'
    result=get(probe+'RESULT.json');assert result['original_package_loaded'] is True and result['status']=='COMPLETED_ORIGINAL_MONOCLE2_DIFFERENTIAL_READINESS_PROBE'
    assert len(result['cases'])==3 and all(c['all_status_OK'] and c['rows']==120 for c in result['cases'])
    before=get(probe+'RUNTIME-BEFORE.json');after=get(probe+'RUNTIME-AFTER.json');assert before==after and len(before)==34145
    assert get(probe+'ADDITIONAL-0-BEFORE.json')==get(probe+'ADDITIONAL-0-AFTER.json')
    guards=get(probe+'source-guards.json');assert guards['original_version']=='2.26.0' and guards['R']=='4.2.1' and guards['dplyr']=='1.1.4' and len(guards['guarded_functions'])==8
    audit=get('monocle2-readiness-audit-1/COMPLETED.json');assert audit['total_original_tests']==360 and len(audit['cases'])==3
    probabilities=get('monocle2-readiness-audit-1/probabilities.json');assert len(probabilities)==3
    for id,case in probabilities.items():
        assert case['gene_ids']==['gene-'+str(i).zfill(3) for i in range(1,121)] and case['statuses']==['OK']*120
        values=list(map(float,case['p']));q=list(map(float,case['q']));assert all(math.isfinite(x) and 0<=x<=1 for x in [*values,*q])
        order=sorted(range(120),key=lambda i:values[i]);expected=[0.0]*120;minimum=1.0
        for i in reversed(range(120)):minimum=min(minimum,120.0/(i+1)*values[order[i]]);expected[order[i]]=minimum
        assert all(abs(x-y)<=5e-13*max(abs(x),1e-300) for x,y in zip(expected,q))
        assert files[base+probe+id+'/original-tests.rds']==files[base+'monocle2-legacy-readiness-probe-5/'+id+'/original-tests.rds']
    source=get('monocle2-source-intake-2/SOURCE.json');assert source['version']=='2.26.0' and len(source['source_members'])==161
    for name,wanted in source['source_members'].items():assert byte_pin(files[base+'monocle2-source-intake-2/source/'+name])==wanted
    assert get('monocle2-hsmm-reference-setup-1/RESULT.json')['status']=='INSTALLED_ORIGINAL_HSMMSINGLECELL_REFERENCE_ONLY'
    assert get('monocle2-reference-setup-4/RESULT.json')['status']=='SKIP3_MONOCLE2_REFERENCE_UNAVAILABLE'
    assert p['Java_adapter_validated'] is False and p['implementation_credit'] is False and p['whole_goal_complete'] is False
    print('Verified 360 original Monocle2 tests, independent BH, exact R-source guards and 34,145 unchanged runtime nodes')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/readiness checks')
    verify()
