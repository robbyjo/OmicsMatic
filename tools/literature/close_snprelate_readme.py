# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Put current counts and required actions first; preserve every byte of the generated historical reference."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/snprelate-runtime'
HEADER='''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: 96 new scoped integrations**: 19 native Java methods and
77 tested upstream adapters. There are **99 including three existing adapters**,
with **247 named tools still missing**. Cell Ranger remains excluded at the
user's request. The full toolbox goal remains active.

The fixed collection contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG articles from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 402 observed tools from 440 definitions, 2,475 paper-tool observations,
1,038 source-bound reviewed roles and 369 supporting pointers. Paper-wide
semantic review and implementation remain incomplete.

Read [SNPRelate workflows](../SNPRELATE_INTEGRATION.md) for the development JAR,
`snprelate-tools` root route, explicit private Windows runtime preparation,
complete requests and portable saved verification. KING-robust, LD pruning,
GRM and exact/randomized PCA use original SNPRelate 1.46.0. Native Java checks
complete genotypes, QC, identities and KING coefficients. Source LD/GRM/PCA
estimators execute upstream; mathematical invariant checks do not refit them.
The released executable remains a separately pinned dependency of the root
supplement; it has no new route by itself.

The source census adds SNPRelate and GENESIS definitions and nine observations,
preserving all 1,029 earlier reviews, 2,466 earlier observations and 400 earlier
tool objects. Citation formatting hid P170 from the earlier SNPRelate search.
The corrected SNPRelate list is P130, P170, P191 and P216. GENESIS has five
reviewed papers and remains missing; its private runtime preparation is not
an implemented analysis. Read [GENESIS status](../GENESIS_DEVELOPMENT.md).
The missing-tool count increased because two omitted packages were catalogued
and one received a scoped adapter.

**Warnings and required actions:** SKIP3 means skip the dependent analysis until
the exact permitted prerequisite is available. Download/install only where
allowed; if unavailable or forbidden, keep analysis skipped. FAIL2 admits no
result: preserve the attempt, inspect its named cause and original logs,
correct it explicitly and retry new paths. Analysis installs nothing.
Require exit 0, `result_admitted=true` and successful complete verification,
then review every warning, raw output and `downstream_eligible` disposition.

Never fill source NaNs, rename identities, change calls, clamp eigenvalues or
switch versions to suppress a failure. Empty QC populations are refused.
Duplicate variant IDs and unassessable source values block downstream use.
Historical paper versions and unspecified settings remain unresolved.
SNPRelate 1.46.0 does not reproduce P130's reported 1.30.1. GENESIS PC-AiR,
PC-Relate and relatedness-adjusted models remain separate analyses.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md),
[source-role coverage](snprelate-role-coverage.json) and
[validation checkpoint](snprelate-catalog-validation.json).

The generated reference and earlier checkpoints below are retained exactly.
Their historical counts describe those checkpoints; the records above describe
the current checkpoint.

<details>
<summary>Generated catalog reference and retained historical checkpoints</summary>

'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    prerequisites={'catalog-render-3':'PASS_CURRENT_SNPRELATE_REVIEW_CONTEXT_RENDER_BYTES_IDENTICAL',
        'catalog-source-audit-2':'PASS250_PRIMARY_SOURCES_2475_OBSERVATIONS_1038_REVIEWS_AND_NINE_NEW_PACKAGE_ROLES',
        'catalog-shared-gates-2':'PASS46_SNPRELATE_AND_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',
        'catalog-ui-2':'PASS299_PREDECESSOR_AND_SNPRELATE_BROWSER_CHECKS'}
    assert all(json.loads((BASE/n/'COMPLETED.json').read_bytes())['status']==value for n,value in prerequisites.items())
    path=ROOT/'docs/literature/omics-2023-2026/README.md';before=path.read_bytes();after=HEADER.encode()+before+b'\n</details>\n'
    assert after[len(HEADER.encode()):-len(b'\n</details>\n')]==before
    (out/'before-README.md').write_bytes(before);path.write_bytes(after)
    save(out/'COMPLETED.json',dict(status='PASS_CURRENT_SNPRELATE_README_HEADER_EXACT_REFERENCE_RETAINED',before=pin(out/'before-README.md'),after=pin(path),
        producer=pin(Path(__file__)),scientific_data_or_page_changes=False,whole_goal_complete=False));print('Current96/99/247 header added; exact historical reference retained')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable current prerequisite and historical reference preservation checks')
    main()
