# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refresh only the current MTAG README prefix and manifest; preserve historical/generated reference bytes."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from signac_scan_evidence import byte_pin


def refresh(root=ROOT,data=None):
    data=data or root/'docs/literature/omics-2023-2026';summary=json.loads((data/'summary.json').read_bytes());inventory=json.loads((data/'tool-inventory.json').read_bytes())
    assert next(t for t in inventory if t['id']=='mtag')['implementation_status']=='upstream-adapter-validated'
    counts=summary['implementation_status_counts'];native=counts['native-subset-validated'];adapters=counts['upstream-adapter-validated'];existing=counts['existing-adapter'];missing=counts['missing']
    assert (native,adapters,existing,missing)==(19,79,3,245)
    protected={p.name:pin(p) for p in [data/'papers.json',data/'tool-inventory.json',data/'summary.json']}
    reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];assert len(reviews)==1043 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==374
    path=data/'README.md';before=path.read_bytes();history=before[before.index(b'<details>'):]
    prefix=f'''# 250 paper omics and GWAS toolbox catalog

**Current checkpoint: {native+adapters} new scoped integrations**: {native} native Java methods and
{adapters} tested upstream adapters. There are **{native+adapters+existing} including three existing adapters**,
with **{missing} named tools still missing**. Cell Ranger remains excluded at the
user's request. The full toolbox goal remains active.

The fixed collection contains 250 papers: 71 Nature, 104 Nature Genetics and
75 AJHG articles from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 402 observed tools, {summary['paper_tool_observations']:,} paper-tool observations,
1,043 source-bound reviewed roles and 374 supporting paragraph pointers.
Paper-wide semantic review and implementation remain incomplete.

Read [MTAG setup, analysis and recovery](../MTAG_INTEGRATION.md) for the typed
`mtag-tools` development route and exact private Linux runtime preparation.
The current original1.0.8 profile supports explicit two-to-eight-trait common-SNP
analysis with stated native audits. Five paper roles preserve their distinct
trait screens, QC and downstream sample-size choices. P088's author equation
has a separate [primary-source receipt](mtag-P088-equation-source-review.json);
it is not attributed to the adjacent paragraph alone. Three papers report1.0.8;
P112/P115 leave the version unstated. A version label does not prove the source
commit or reconstruct author commands.

The original option matrix has25 cases:20 admitted workflows,four expected
scientific refusals and one skip, covering172625 trait-result rows. Additional
original4-through8-trait controls include the complete default4-trait10-interval
maxFDR calculation,30000 trait-result rows and15 saved replays. Source fitting,
full QC and maxFDR formulas remain upstream. Reused synthetic populations are
not independent biological datasets or paper reproductions.

[GENESIS workflows](../GENESIS_INTEGRATION.md) and
[SNPRelate workflows](../SNPRELATE_INTEGRATION.md) remain distinct integrations.
All401 other tool objects and1,038 earlier review objects are preserved.
The development root supplement needs the separately pinned canonical JAR;
the released executable alone has no new command. Saved MTAG verification
works without Python or fitting. No released executable is overwritten.

**Warnings and required actions:** SKIP3 means skip dependent analysis when
exact software or capabilities are unavailable or forbidden. Install/download
only where permitted into NEW private paths; otherwise leave analysis skipped.
FAIL2 admits no result, including partial original output. Require wrapper
exit0, result_admitted=true, COMPLETED.json and complete saved --verify, then
inspect every warning, source log and native ledger before interpretation.
Original MTAG can return process exit0 after a scientific error. Default freq
can bypass internal MAF munging; violating retained SNPs are refused. No-flip,
n_max and custom-grid source limitations are not silently repaired. Source
P=0 underflow and distinct per-SNP/GWAS-equivalent/case-control/imputation N
definitions remain visible. Preserve each attempt and resolve its named cause
before retrying NEW paths. Do not reduce a grid or change scientific settings
merely to hide failure. Analysis installs nothing.

Current records: [tool inventory](tool-inventory.json), [papers](papers.json),
[summary](summary.json), [warnings and required actions](WARNINGS.md),
[MTAG primary roles](mtag-source-role-coverage.json),
[MTAG catalog validation](mtag-catalog-validation.json),
[typed workflow evidence](../mtag-workflow-development/VALIDATION.json),
[dimension evidence](../mtag-dimensions-development/VALIDATION.json) and
[the earlier GENESIS checkpoint](genesis-catalog-validation.json).

The generated reference and earlier checkpoints below are retained exactly.
Their historical counts describe those checkpoints; the records above describe
the current checkpoint.

'''
    raw=prefix.encode('utf8');raw=raw.replace(b'\n',b'\r\n') if before.count(b'\n')==before.count(b'\r\n') else raw
    after=raw+history;path.write_bytes(after);assert path.read_bytes().endswith(history)
    assert all(pin(data/n)==wanted for n,wanted in protected.items())
    manifest={p.name:pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    old=(data/'SHA256SUMS').read_text(encoding='utf8').splitlines();order=[r.split('  ',1)[1] for r in old];order=[n for n in order if n in manifest]+[n for n in manifest if n not in order]
    assert len(order)==len(set(order));(data/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order),encoding='utf8',newline='\n')
    return dict(status='PASS_MTAG_CURRENT_README_PREFIX_ONLY_HISTORY_PRESERVED',before=byte_pin(before),after=byte_pin(after),retained_history=byte_pin(history),scientific_catalog_data_unchanged=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    save(out/'COMPLETED.json',refresh());print('PASS_MTAG_CURRENT_README_PREFIX_ONLY_HISTORY_PRESERVED')
