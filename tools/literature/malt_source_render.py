# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explain typed supplementary provenance and unfinished MALT family roles."""
NOTE='''

## MALT family source types and required actions

Three source-reviewed tool additions retain separate provenance: MALT and HOPS have main-article JATS anchors; MaltExtract is named in the P002 supplementary DOCX, section 3.3. Its `evidence_source_kind` is `supplementary-docx`, and paragraph index 246 is zero-based over all Word paragraphs, including empty ones. It is not a main-article paragraph. MALT's four supplementary supporting pointers identify the separate custom 0.5.2 reference-building/screening role. The version is 0.5.2 followed by superscript citation 61; the concatenated text is not version 0.5.261.

These additions improve the catalog and add no whole-tool or biological credit. The source-tested MALT 0.4.1 development controller does not fulfill the separate 0.5.2 role. Complete HOPS and MaltExtract operations remain unfinished; their unreported versions, settings and resource choices remain unknown. See `MALT_ALIGNMENT.md` and `MALT_HOPS_PAPER_ROLES.md`.

**Required action:** match the declared source version and operation, qualify a permitted runtime, provide the complete dated reference/mapping and query populations, and retain original warnings and failures. If any prerequisite is unavailable or cannot be installed, skip its dependent analysis. A small software-control index does not reproduce the historical 91-spirochete or 7,508-genome bacterial analyses. Insufficient-read candidates remain unassessable.
'''


def append_source_notes(root,data):
    for name in ['README.md','WARNINGS.md']:
        path=data/name;text=path.read_text(encoding='utf8');assert '## MALT family source types and required actions' not in text
        path.write_text(text+NOTE,encoding='utf8')


def refresh_current_catalog(root,data):
    """Finalize current counts without rewriting any scientific tool/provider."""
    from collections import Counter
    import json
    from setup_deseq2_reference import pin
    from malt_source_catalog import verify_extension
    verify_extension(root)
    papers=json.loads((data/'papers.json').read_bytes());inventory=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes());reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    counts=Counter(t['implementation_status'] for t in inventory)
    assert summary['implementation_status_counts']==dict(counts) and summary['papers']==len(papers)==250
    assert summary['distinct_named_tools']==len(inventory)==405 and summary['paper_tool_observations']==sum(len(p['tools']) for p in papers)==2479
    assert len(reviews)==1052 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==377
    protected={n:pin(data/n) for n in ['papers.json','tool-inventory.json','summary.json']}
    native=counts['native-subset-validated'];upstream=counts['upstream-adapter-validated'];new=native+upstream;existing=counts['existing-adapter'];missing=counts['missing']
    prefix=f'''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: {new} new scoped integrations**: {native} native Java
subsets and {upstream} tested upstream adapters; {new+existing} including
{existing} existing adapters. **{missing} named tools remain missing.**
Cell Ranger remains excluded at the user's request. The full goal is active.

The fixed cohort contains 250 papers: 71 Nature, 104 Nature Genetics and 75
AJHG articles from 2023-10-23 through 2026-10-23. No extension was needed.
There are **405 named tools, 2,479 paper-tool observations and 1,052 reviewed
uses**, with 377 main-article supporting pointers, four supplementary supporting
pointers and one separately typed supplementary primary observation.

The three MALT-family additions retain all 2,476 prior observations and all
1,049 prior reviews. MALT and HOPS have main-article anchors; MaltExtract's
source is the P002 supplementary DOCX. Its Word paragraph index 246 is
zero-based and includes empty paragraphs. It is not a main-JATS pointer.
The custom MALT version is 0.5.2 followed by superscript citation 61.

These additions grant no new whole-tool or biological credit. Read
[MALT alignment](../MALT_ALIGNMENT.md) and
[distinct MALT/HOPS/MaltExtract roles](../MALT_HOPS_PAPER_ROLES.md).
The 0.4.1 controller and native components do not fulfill the separate 0.5.2
custom database-building/screening role. Full HOPS and MaltExtract operations
remain unfinished. Unreported versions, resources and settings remain unknown.

[scDRS](../SCDRS_INTEGRATION.md), [MTAG](../MTAG_INTEGRATION.md),
[GENESIS](../GENESIS_INTEGRATION.md) and [SNPRelate](../SNPRELATE_INTEGRATION.md)
retain their separately validated workflows and source-specific boundaries.
Coverage badges describe scoped integrations, not complete package, historical
environment, full-paper or biological reproduction.

**Warnings and required actions:** install or download only where permitted.
If an exact dependency cannot be supplied or installed, keep its dependent
analysis skipped with SKIP3. FAIL2 admits no result; retain complete inputs,
partial outputs, original logs and warnings, resolve the named cause, and retry
a NEW output directory. Do not repair identifiers, nonfinite values, clips or
settings to conceal a failure. Hashes establish integrity, not authorship;
saved replay does not independently refit or authenticate a producer.

Current records: [inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings](WARNINGS.md), and
[typed MALT source coverage](malt-source-role-coverage.json).

'''
    path=data/'README.md';before=path.read_bytes();history=before[before.index(b'<details>'):]
    path.write_bytes(prefix.encode()+history);assert path.read_bytes()[path.read_bytes().index(b'<details>'):]==history
    assert all(pin(data/n)==value for n,value in protected.items())
    (data/'SHA256SUMS').write_text(''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'),encoding='utf8',newline='\n')
