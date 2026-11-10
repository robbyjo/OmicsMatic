# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Put current CellChat/count/warning guidance first while retaining exact generated catalog text."""
import argparse, json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    base = ROOT/'build/literature-review/cellchat-runtime'
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(base)
    out.mkdir(parents=True)
    data = ROOT/'docs/literature/omics-2023-2026'
    gates = json.loads((base/'catalog-shared-gates-2/COMPLETED.json').read_bytes())
    assert gates['status'] == 'PASS40_UNCHANGED_PREDECESSOR_AND_CELLCHAT_SCIENTIFIC_GATES'
    render = json.loads((base/'catalog-render-2/COMPLETED.json').read_bytes())
    assert all(pin(ROOT/n) == v for n, v in render['rendered_files'].items())
    path = data/'README.md'
    before = path.read_bytes()
    header = '''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: 94 new scoped integrations**: 19 native Java methods and
75 tested upstream adapters. There are **97 including three existing adapters**,
with **247 named tools still missing**. Cell Ranger remains excluded at the
user's request. The full toolbox goal remains active.

The fixed cohort contains 250 papers: 71 Nature, 104 Nature Genetics and 75 AJHG
articles from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 400 observed tools from 438 definitions, 2,466 paper-tool observations,
1,024 source-bound reviewed roles and 357 supporting pointers. Paper-wide
semantic review and implementation remain incomplete.

Read [CellChat integration](../CELLCHAT_INTEGRATION.md) for the `cellchat-tools`
root command, self-contained standalone alternatives, explicit original source
profiles, database exports and required actions. Six reviewed paper roles
distinguish resource use, original inference, network embedding/comparison and
sample-aware strengths. This adds one tested scoped adapter. All 1,018 previous
reviews and the 399 unrelated tool entries remain unchanged.

**Warnings and required actions:** SKIP3 means skip dependent analysis until the
exact permitted prerequisite is available; if installation is unavailable or
forbidden, keep it skipped. FAIL2 admits no new result: preserve the attempt,
fix the named cause and retry new paths. Analysis installs nothing. Require
exit 0, result_admitted=true and successful complete verification; read every
warning, original output, provenance and captured parent warning.

Original CellChat invalid p-fields, fallback zeros, FNN/SNN anomalies,
missing/nonfinite strengths, parallel RNG warnings and UMAP variability remain
visible. They are not repaired or made valid by transport success. P060's custom
score aggregation remains unresolved: skip that study-score analysis until the
exact implementation is supplied. CellChat database export remains available.
Spyrrow, CellTrek, LIANA and FlowSig are separate tool roles; Spyrrow remains
missing in the catalog. Historical paper reproduction is not claimed.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md) and
[CellChat source-role coverage](cellchat-role-coverage.json).

The generated reference and earlier checkpoints below are retained exactly.
Individual historical notes retain their own counts and availability limits;
the current records above are authoritative for this checkpoint.

<details>
<summary>Generated catalog reference and retained historical checkpoints</summary>

'''
    after = header.encode()+before+b'\n</details>\n'
    (out/'README.before.md').write_bytes(before)
    (out/'README.header.md').write_text(header, encoding='utf8', newline='\n')
    (out/'README.after.md').write_bytes(after)
    path.write_bytes(after)
    unchanged = {n: pin(ROOT/n) for n in render['rendered_files'] if n != path.relative_to(ROOT).as_posix()}
    assert all(value == render['rendered_files'][n] for n, value in unchanged.items())
    save(out/'COMPLETED.json', dict(status='PASS_CURRENT_CELLCHAT_README_HEADER_EXACT_REFERENCE_RETAINED',
        before=pin(out/'README.before.md'), header=pin(out/'README.header.md'), after=pin(path),
        source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix(): pin(Path(__file__))},
        post_gate_documentation_only=True, unchanged_scientific_JSON_pages_and_pre_gate_index=unchanged,
        whole_goal_complete=False))
    print('Updated current94/97/247 guidance; exact generated reference retained')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable current-count/reference preservation checks')
    main()
