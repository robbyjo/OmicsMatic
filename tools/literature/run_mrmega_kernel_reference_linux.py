# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Probe unchanged original MR-MEGA public regression functions at full precision."""
import argparse,json,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_mrmega_reference_linux import ROOT,pin,save,owned


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_ORIGINAL_KERNEL_REFERENCE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        build=base/'reference-build-1';receipt=json.loads((build/'COMPLETED.json').read_bytes())
        assert receipt['status']=='PASS_PRIVATE_UNMODIFIED_ORIGINAL_MRMEGA_REFERENCE_BUILD_ONLY'
        source=build/'source';manifest=json.loads((build/'ORIGINAL-SOURCE-FILES.json').read_bytes())
        assert all(pin(source/name)==value for name,value in manifest.items())
        controls=base/'kernel-controls-1';prepared=json.loads((controls/'COMPLETED.json').read_bytes());assert prepared['cases']==54
        assert all(pin(controls/name)==value for name,value in prepared['files'].items())
        files='ap.cpp readFile.cpp chisquaredistr.cpp cohort.cpp filter.cpp gammafunc.cpp global.cpp ialglib.cpp ibetaf.cpp igammaf.cpp marker.cpp mds.cpp normaldistr.cpp regression.cpp structures.cpp studenttdistr.cpp tools.cpp'.split()
        probe=ROOT/'tools/literature/mrmega_reference_probe.cpp';binary=out/'mrmega-regression-reference'
        command=['/usr/bin/g++','-I',str(source),*[str(source/name) for name in files],str(probe),'-Wno-deprecated','-O1','-lz','-o',str(binary)]
        assert owned(command,out/'compile',out)==0,'Original probe compilation failed; preserve complete logs'
        assert owned([str(binary),str(controls/'controls.txt')],out/'run',out)==0,'Original kernel probe failed; preserve complete outputs'
        raw=(out/'run/stdout').read_bytes();rows=raw.decode().splitlines();expected=json.loads((controls/'controls.json').read_bytes())
        assert len(rows)==len(expected)==54
        for row,wanted in zip(rows,expected,strict=True):
            fields=row.split('\t');assert len(fields)==21 and fields[0]==wanted['id'] and int(fields[1])==len(wanted['effects']) and int(fields[2])==len(wanted['ancestry'][0])
        (out/'original-kernel.tsv').write_bytes(raw)
        assert all(pin(source/name)==value for name,value in manifest.items())
        assert all(pin(controls/name)==value for name,value in prepared['files'].items())
        state.update(status='PASS_FIFTY_FOUR_ORIGINAL_MRMEGA_REGRESSION_KERNEL_CONTROLS_ONLY',complete_cases=54,
            original_kernel_output=pin(out/'original-kernel.tsv'),original_sources_unchanged=manifest,
            original_full_file_workflow_executed=False,probability_columns='Unmodified original tools.cpp chisqr, including invalid/nonfinite tail values; never relabel as stable native or corrected R values.',
            source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),probe,ROOT/'tools/literature/setup_mrmega_reference_linux.py']})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/control completeness checks')
    raise SystemExit(main())
