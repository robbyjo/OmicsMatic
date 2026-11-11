# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original nearest-feature controls; source failures remain failures."""
import argparse
import gzip
import json
from pathlib import Path
import shutil
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from setup_deseq2_reference import pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--controls',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();controls=args.controls.resolve();out=args.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists()
    proof=json.loads((controls/'COMPLETED.json').read_bytes());plan=json.loads((controls/'PLAN.json').read_bytes());result=json.loads((controls/'ORIGINAL.json').read_bytes())
    assert proof['original_calls']==652 and proof['original_successes']==644 and proof['original_failures']==8 and pin(controls/'ORIGINAL.json')==proof['original_result']
    by_case={p['id']:p for p in plan['cases']};eligible={name for name,c in by_case.items() if name.startswith(('unstranded-','random-unstranded-')) and not c['options']['same_strand']}
    assert len(eligible)==32
    unsupported={name for name,c in by_case.items() if name.startswith('overflow-unstranded-') and not c['options']['same_strand']}
    assert len(unsupported)==16
    references={}
    for row in result['cases']:
        key=row['case_id'];common={k:v for k,v in row.items() if k!='version'}
        if key in references:assert common==references[key],(row['version'],key)
        else:references[key]=common
    fixture=dict(schema_version=1,scope='Complete unchanged original source-function controls with named current dependency versions; not whole-package or historical dependency-environment parity.',
        plan=plan,original=result,completion=proof,native_eligible_cases=sorted(eligible),native_original_comparisons=128,
        native_unsupported_boundary_cases=sorted(unsupported),native_boundary_refusals=64,
        source_control_pins={n:pin(controls/n) for n in ['PLAN.json','ORIGINAL.json','COMPLETED.json']},preparation_source=pin(Path(__file__)))
    raw=gzip.compress((json.dumps(fixture,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf8'),mtime=0)
    target=ROOT/'src/test/resources/org/jlinalg/genetics/chipseeker-nearest/original-controls-v2.json.gz'
    assert not target.exists();out.mkdir();target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    (out/'original-controls.json.gz').write_bytes(raw);shutil.copyfile(__file__,out/'preparation-source.py')
    save(out/'COMPLETED.json',dict(status='frozen-complete-original-ChIPseeker-component-controls',fixture=dict(path=target.relative_to(ROOT).as_posix(),**pin(target)),
        original_calls=652,original_successes=644,original_failures=8,native_comparisons=128,native_boundary_refusals=64,source_proof=pin(controls/'COMPLETED.json'),native_implementation_admitted=False))
    print(json.dumps(dict(fixture=target.relative_to(ROOT).as_posix(),**pin(target))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original reference checks')
    main()
