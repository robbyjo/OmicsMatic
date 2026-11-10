# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Place current DIAMOND/count/actions guidance first and retain the exact generated reference."""
import argparse, json
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    assert json.loads((BASE/'catalog-shared-gates-1/COMPLETED.json').read_bytes())['status'] == 'PASS41_UNCHANGED_PREDECESSOR_CELLCHAT_AND_DIAMOND_SCIENTIFIC_GATES'
    assert json.loads((BASE/'catalog-ui-2/COMPLETED.json').read_bytes())['status'] == 'PASS_UNCHANGED299_GENERAL_AND11_DIAMOND_BROWSER_CHECKS'
    render = json.loads((BASE/'catalog-render-1/COMPLETED.json').read_bytes())
    assert all(pin(ROOT/n) == v for n, v in render['rendered_files'].items())
    data = ROOT/'docs/literature/omics-2023-2026'
    path = data/'README.md'
    before = path.read_bytes()
    header = '''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: 95 new scoped integrations**: 19 native Java methods and
76 tested upstream adapters. There are **98 including three existing adapters**,
with **246 named tools still missing**. Cell Ranger remains excluded at the
user's request. The full toolbox goal remains active.

The fixed collection contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG articles from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 400 observed tools from 438 definitions, 2,466 paper-tool observations,
1,029 source-bound reviewed roles and 367 supporting pointers. Paper-wide
semantic review and implementation remain incomplete.

Read [DIAMOND integration](../DIAMOND_INTEGRATION.md) for the self-contained Java
command, `diamond-tools` root route, explicit Linux source/runtime setup and
complete requests. Five reviewed roles add one scoped upstream adapter while
preserving all 1,024 prior reviews and 399 unrelated tool entries. Original
2.0.0/2.0.15/2.1.8 alignment controls cover 78 source cases: 72 successful
comparisons and six preserved original failures. Complete saved-bundle checks
and native audits do not independently refit alignments or reproduce papers.

**Warnings and required actions:** SKIP3 means skip the dependent analysis until
the exact permitted prerequisite is available. Download/install only where
allowed; if unavailable or forbidden, keep the analysis skipped. FAIL2 admits
no result, including earlier successful steps: preserve the attempt, inspect
the named cause and original logs, correct it explicitly and retry new paths.
Analysis installs nothing. Require exit 0, result_admitted=true and successful
complete verification, then read every warning and original output.

DIAMOND source crashes, tested paired-FASTQ failures, cutoff precedence,
rounded/zero E-values and duplicate-ID ambiguity remain visible. P017 has no
reported version and uses different or unspecified coverage denominators.
P054's score100 does not resolve raw versus bit score or an external filter:
require that exact study interpretation before claiming the cutoff. Downstream
classification, abundance, DBSCAN/KDE, GraftM and mSynOrths remain separate.
Existing CellChat warnings and its unresolved P060 custom-score analysis remain
in force; Spyrrow remains missing.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md),
[DIAMOND source-role coverage](diamond-role-coverage.json) and
[validation checkpoint](diamond-catalog-validation.json).

The generated reference and earlier checkpoints below are retained exactly.
Their historical counts describe those checkpoints; current records above
describe this checkpoint.

<details>
<summary>Generated catalog reference and retained historical checkpoints</summary>

'''
    after = header.encode()+before+b'\n</details>\n'
    assert after[len(header.encode()):-len(b'\n</details>\n')] == before
    (out/'before-README.md').write_bytes(before)
    (out/'after-README.md').write_bytes(after)
    path.write_bytes(after)
    # The final catalog sealer refreshes the full index after adding its proof/archive.
    save(out/'COMPLETED.json', dict(status='PASS_CURRENT_DIAMOND_README_HEADER_EXACT_REFERENCE_RETAINED',
        before=pin(out/'before-README.md'), after=pin(path),
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve()]},
        scientific_data_or_page_changes=False, whole_goal_complete=False))
    print('Added current95/98/246 scope and action header; exact generated reference retained')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact retained-reference checks')
    main()
