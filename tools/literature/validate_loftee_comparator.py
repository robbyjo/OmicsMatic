# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate exact/multiplicity-preserving unordered-field comparison contracts on Windows and Linux Python."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/loftee-runtime') and not out.exists();out.mkdir(parents=True);sources=[Path(__file__).resolve(),ROOT/'tools/literature/test_compare_loftee_original.py',ROOT/'tools/literature/compare_loftee_original.py',ROOT/'tools/literature/probe_loftee_workflow.py'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources};test=ROOT/'tools/literature/test_compare_loftee_original.py';reports=[]
    for name,command in [('windows-python',[sys.executable,str(test)]),('linux-python',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3',linux(test)])]:
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0 and b'Ran 8 tests' in r.stderr and r.stderr.rstrip().endswith(b'OK');reports.append(dict(name=name,exit_code=r.returncode,tests=8,skipped=0))
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='passed-original-unordered-info-comparison-contracts',source_pins=pins,platforms=reports,raw_output_rewritten=False,duplicate_tokens_preserved=True,numeric_tolerance_used=False,implementation_credit=False));print('Passed8 strict/unordered comparison contracts on Windows and Linux Python')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable comparator contracts')
    main()
