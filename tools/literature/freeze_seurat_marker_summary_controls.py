# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original summary functions and controlled callback evidence, not DE test claims."""
import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def main():
    out=ROOT/'src/test/resources/org/jlinalg/sequence/seurat-marker-summary';assert not out.exists();out.mkdir()
    cases=BASE/'differential-summary-cases-1/CASES.json';reference=BASE/'differential-summary-reference-1/original/RESULT.json';x=json.loads(reference.read_bytes());assert len(x['cases'])==162 and all(c['status']=='completed-source-summary-and-controlled-p-callback' and not c['statistical_test_executed'] for c in x['cases']);assert not x['source_statistical_tests_executed']
    files={'CASES.json':cases.read_bytes(),'ORIGINAL.json':reference.read_bytes()}
    names=['tools/literature/collect_seurat_marker_summaries.R','tools/literature/prepare_seurat_marker_summary_cases.py','tools/literature/probe_signac_differential_runtime.R','tools/literature/freeze_seurat_marker_summary_controls.py'];source_pins={n:pin(ROOT/n) for n in names}
    for n in names:files['scripts/'+Path(n).name]=(ROOT/n).read_bytes()
    for n in ['differential_expression.R','DESCRIPTION','LICENSE']:files['source/5.6.0/'+n]=(BASE/'differential-source-intake-1'/n).read_bytes()
    for version in ['4.1.0','4.3.0','5.0.0','5.1.0','5.2.1']:files['source/'+version+'/differential_expression.R']=(BASE/'differential-historical-source-intake-1'/(version+'.R')).read_bytes()
    for folder in ['differential-summary-reference-1','differential-runtime-probe-2']:
        for p in (BASE/folder).rglob('*'):
            if p.is_file():files['retained/'+folder+'/'+p.relative_to(BASE/folder).as_posix()]=p.read_bytes()
    with zipfile.ZipFile(out/'original-controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    record=dict(status='six-edition-original-summary-and-controlled-callback-fixtures-not-statistical-tests',archive=pin(out/'original-controls.zip'),members=len(files),source_pins=source_pins,original_summary_cases=162,source_profiles=6,current_installed_source_functions_compared=42,rounding_values=sum(len(r['values']) for r in x['rounding']),cases_with_missing_identity_placeholders=sum(any(not r['id'].startswith('f') for r in c['markers']) for c in x['cases']),actual_Wilcoxon_or_LR_tests_validated=False,historical_whole_packages_executed=False,prior_library_files_unchanged=27958)
    (out/'CAPTURE.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(record))
if __name__=='__main__':main()
