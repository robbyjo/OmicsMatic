# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refresh the current scDRS catalog README and file manifest, preserving historical/reference bytes."""
import json
from setup_deseq2_reference import ROOT, pin


def refresh(root=ROOT, data=None):
    data = data or root / 'docs/literature/omics-2023-2026'
    summary = json.loads((data / 'summary.json').read_bytes())
    inventory = json.loads((data / 'tool-inventory.json').read_bytes())
    assert next(t for t in inventory if t['id'] == 'scdrs')['implementation_status'] == 'upstream-adapter-validated'
    counts = summary['implementation_status_counts']
    assert (counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing']) == (19, 80, 3, 244)
    reviews = json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len(reviews) == 1049 and sum(len(r.get('supporting_evidence', [])) for r in reviews) == 375
    protected = {p.name: pin(p) for p in [data / n for n in ['papers.json', 'tool-inventory.json', 'summary.json']]}
    path = data / 'README.md'
    before = path.read_bytes()
    history = before[before.index(b'<details>'):]
    prefix = '''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: 99 new scoped integrations**: 19 native Java methods and
80 tested upstream adapters. There are **102 including three existing adapters**,
with **244 named tools still missing**. Cell Ranger remains excluded at the
user's request. The full toolbox goal remains active.

The fixed collection contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG articles from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 402 observed tools, 2,476 paper-tool observations, 1,049 source-bound
reviewed roles and 375 supporting paragraph pointers. Paper-wide semantic
review and implementation remain incomplete.

Read [scDRS setup, analysis and recovery](../SCDRS_INTEGRATION.md) for the typed
`scdrs-tools` route, exact private Linux runtime preparation and complete JSON
requests. Original 1.0.0/1.0.1/1.0.2 functions implement gene-set munging,
single-dataset H5AD scoring and downstream workflows. Native numerical
components and explicit-family BH remain separately described and tested.

Six primary paper roles preserve distinct settings. P021 reports 1.0.1 and
200 controls; P085 reports 1.0.0 and 1000 controls; P055/P123 report 1.0.2.
P024 leaves the version unstated. P169 writes the plural scDRSs and reports
1.0.1 with separate atlas inputs, but no exact command, control count or
preprocessing. A source-reviewed registry overlay corrects that omitted
observation without modifying the historical base registry. Unreported
settings and historical author commits are not inferred.

The 23 original workflow controls cover 877998 byte-identical numerical and
gene-weight coordinates and 69 saved JVM checks. Two controls use 1000 disease
genes and 1000 controls. All 24 packaged bundles replay on Windows Java21/25
and Linux Java21. Native BH matches 67161 double q values and strict cutoff
decisions across three JVMs; 36 refusal/tamper controls admit no failed result.
These are synthetic controls, not biological replication or an atlas benchmark.

[MTAG](../MTAG_INTEGRATION.md), [GENESIS](../GENESIS_INTEGRATION.md) and
[SNPRelate](../SNPRELATE_INTEGRATION.md) retain their distinct workflows and
evidence. All 401 other tool objects and 1043 earlier review objects are
preserved. The root supplement requires the separately pinned canonical JAR;
the released executable alone has no new command. No release or Pages
deployment is performed by this catalog update.

**Warnings and required actions:** SKIP3 means skip dependent analysis when
exact permitted software or capabilities are unavailable or forbidden.
Download/install only where permitted, with explicit options and NEW private
paths; otherwise leave analysis skipped. Analysis installs nothing. FAIL2
admits no result, including partial original outputs. Require wrapper exit0,
result_admitted=true, COMPLETED.json and complete saved --verify, then review
every warning, original log, identifier and covariate disposition ledger.
Preserve each attempt, resolve its named cause and retry NEW paths. Do not
repair identifiers/NaN/Inf, silently encode covariates, replace historical
versions, reduce controls or pool correction families to conceal failure.
Logical resource checks are not OS RAM quotas. Hashes establish integrity,
not producer authenticity; saved verification does not independently refit
LOESS, covariates, scores or graphs. Original corr_mcp/corr_mcz are distinct
from ordinary Pearson r. Native BH uses one trait/annotation family within
this dataset, with strict q<declared cutoff. Read the integration guide.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md).

'''
    path.write_bytes(prefix.encode('utf8') + history)
    assert path.read_bytes()[path.read_bytes().index(b'<details>'):] == history
    assert all(pin(data / n) == wanted for n, wanted in protected.items())
    (data / 'SHA256SUMS').write_text(''.join(pin(p)['sha256'] + '  ' + p.name + '\n' for p in sorted(data.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'), encoding='utf8', newline='\n')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable current counts or preserved catalog/history checks')
    refresh()
    print('PASS_SCDRS_CURRENT_README_AND_MANIFEST_REFRESH_ONLY')
