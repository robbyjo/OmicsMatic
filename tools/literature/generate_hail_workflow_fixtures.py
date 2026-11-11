# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete literal Hail controls without executing an analysis; preserve BGZF decompression byte equality."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from collect_hail_workflow_controls import fixture_inputs,cases

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);runtime=json.loads((a.runtime/'COMPLETED.json').read_bytes());assert runtime['status']=='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY';inputs=fixture_inputs(out);save(out/'literal-inputs.json',{p.name:pin(p) for p in inputs.iterdir()});request=cases(out,inputs,runtime['runtime_python'],runtime['health']['hail']);assert len(request['cases'])==10;save(out/'cases.json',request);sources=[Path(__file__).resolve(),ROOT/'tools/literature/collect_hail_workflow_controls.py'];save(out/'COMPLETED.json',dict(status='PASS_TEN_LITERAL_HAIL_WORKFLOW_FIXTURES_ONLY_NO_ANALYSIS',source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources},case_count=10,input_files=len(list(inputs.iterdir())),bgzf_specification='https://samtools.github.io/hts-specs/SAMv1.pdf sections4.1 and4.1.2; complete decompression preserves the original VCF bytes',implementation_credit=False,whole_goal_complete=False));print('Created10 literal workflow fixtures, including BGZF and nontrivial row/column filters; no analysis launched')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable literal fixture completeness checks')
    main()
