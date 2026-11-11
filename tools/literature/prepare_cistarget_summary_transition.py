# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the explicit new observation count before predecessor metadata checks render availability."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';path=data/'summary.json';raw=path.read_bytes();summary=json.loads(raw);papers=json.loads((data/'papers.json').read_bytes());roles=json.loads((data/'cistarget-role-coverage.json').read_bytes())
    assert roles['applied'] and roles['observations']==2466 and summary['paper_tool_observations']==2465 and len(papers)==250 and sum(len(p['tools']) for p in papers)==2466
    (out/'summary.before.json').write_bytes(raw);summary['paper_tool_observations']=2466;save(out/'summary.after.json',summary);path.write_bytes((out/'summary.after.json').read_bytes())
    save(out/'COMPLETED.json',dict(status='PASS_ONE_SOURCE_BOUND_OBSERVATION_COUNT_TRANSITION_WITHOUT_AVAILABILITY_CHANGE',before=pin(out/'summary.before.json'),after=pin(path),field='paper_tool_observations',old_value=2465,new_value=2466,
        source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False));print('Bound2466 observations for coherent predecessor metadata checks; availability unchanged until render passes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-count transition checks')
    main()
