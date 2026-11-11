# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Put current verified counts first and retain the exact earlier checkpoint text as history."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/cistarget-runtime'
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';path=data/'README.md';before=path.read_bytes()
    gates=json.loads((base/'catalog-gate-execution-1/COMPLETED.json').read_bytes());assert gates['status']=='PASS33_CISTARGET_SCIENCE_GATES_WITH_UNCHANGED_EXECUTED_SOURCES_AND_METADATA'
    render=json.loads((base/'catalog-render-3/COMPLETED.json').read_bytes());assert pin(path)==render['rendered_files'][path.relative_to(ROOT).as_posix()]
    summary=json.loads((data/'summary.json').read_bytes());roles=json.loads((data/'cistarget-role-coverage.json').read_bytes())
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],roles['total_reviews'],roles['supporting_pointers'])==(250,400,2466,1018,356)
    assert summary['implementation_status_counts']['native-subset-validated']==19 and summary['implementation_status_counts']['upstream-adapter-validated']==74 and summary['implementation_status_counts']['missing']==248
    header='''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: 93 new scoped integrations** — 19 native Java methods and
74 tested upstream adapters. There are **96 including three existing adapters**,
with **248 named tools still missing**. These are scoped implementations with
explicit limits. Cell Ranger remains excluded at the user's request.

The unchanged fixed cohort contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG articles from 2023-10-23 through 2026-10-23. No time extension was needed.
The catalog contains 400 observed tools from 438 definitions, 2,466 paper–tool
observations, 1,018 reviewed roles and 356 supporting pointers.

Read [cisTarget integration](../CISTARGET_INTEGRATION.md) for the native
`cistarget-tools` command, explicit numerical profiles, complete ranked-feature
inputs, optional Feather conversion and required warning/failure actions.
The seven source roles distinguish algorithm execution, SCENIC/SCENIC+ inputs and
motif-collection use. P145 adds one missed “cis-target” Methods resource
observation. P061 has one explicit review enrichment, with its prior text
retained. All 1,011 other prior reviews and every unrelated observation remain
unchanged. The 399 unrelated tool entries also remain unchanged.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md), and
[cisTarget source-role coverage](cistarget-role-coverage.json).

Native TSV analysis installs nothing. Optional Feather conversion requires a
permitted private PyArrow 17.0.0 runtime: SKIP3 means skip the conversion and its
dependent analysis until that prerequisite is available. FAIL2 admits no result;
preserve partials, correct the named problem and retry new paths. Read every
warning and disposition, especially undefined or rounding-only NES values.

The full 250-paper implementation and paper-wide semantic review remain open.
The material below is retained historical checkpoint documentation; its opening
counts and availability statements describe that earlier checkpoint.

<details>
<summary>Historical MR-MEGA checkpoint and retained catalog material</summary>

'''
    suffix='\n</details>\n';after=header.encode()+before+suffix.encode();(out/'README.before.md').write_bytes(before);(out/'README.header.md').write_text(header,encoding='utf8',newline='\n');(out/'README.after.md').write_bytes(after);path.write_bytes(after)
    save(out/'COMPLETED.json',dict(status='PASS_CURRENT_CISTARGET_README_HEADER_WITH_EXACT_HISTORICAL_TEXT_RETAINED',before=pin(out/'README.before.md'),header=pin(out/'README.header.md'),after=pin(path),
        source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},post_gate_documentation_only=True,product_pages_and_scientific_inputs_changed=False,whole_goal_complete=False));print('Updated current93/96/248 headline; exact earlier checkpoint remains explicitly historical')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable current-count and historical-document preservation checks')
    main()
