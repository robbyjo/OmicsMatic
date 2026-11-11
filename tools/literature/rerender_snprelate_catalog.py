# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck that an archived-review context correction leaves every rendered catalog byte unchanged."""
import argparse
import json
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    previous=json.loads((BASE/'catalog-render-2/COMPLETED.json').read_bytes());assert previous['status']=='PASS_SNPRELATE_CATALOG_RENDER400_PREVIOUS_TOOL_OBJECTS_UNCHANGED'
    expected=previous['rendered_files'];assert all(pin(ROOT/n)==v for n,v in expected.items())
    source_pins={n:pin(ROOT/n) for n in ['tools/literature/render_review.py','tools/literature/snprelate_catalog.py']}
    for n in source_pins:
        f=out/'source-snapshot'/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes((ROOT/n).read_bytes())
    with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:
        run=subprocess.run([sys.executable,str(ROOT/'tools/literature/render_review.py')],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
    save(out/'process.json',dict(exit_code=run.returncode));assert run.returncode==0,'Inspect retained renderer logs'
    assert all(pin(ROOT/n)==v for n,v in expected.items()),'Review-context correction changed rendered output'
    assert all(pin(ROOT/n)==v for n,v in source_pins.items())
    save(out/'COMPLETED.json',dict(status='PASS_CURRENT_SNPRELATE_REVIEW_CONTEXT_RENDER_BYTES_IDENTICAL',source_pins=source_pins,
        rendered_files=expected,all402_tool_objects_unchanged=True,scientific_sources_changed=False,whole_goal_complete=False))
    print('PASS_CURRENT_SNPRELATE_REVIEW_CONTEXT_RENDER_BYTES_IDENTICAL',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact rerender identity checks')
    main()
