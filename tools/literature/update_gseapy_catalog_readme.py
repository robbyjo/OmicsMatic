# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Expose the current GSEApy checkpoint while retaining the preceding catalog document verbatim."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from render_palantir_catalog import refresh_manifest


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';before=(a.prepared/'before/docs/literature/omics-2023-2026/README.md').read_bytes();rendered=(data/'README.md').read_bytes();(out/'rendered-readme.md').write_bytes(rendered);summary=json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,71,253)
    header='''# 250-paper omics/GWAS toolbox catalog

**Current checkpoint: 88 new integrations**: 17 native Java subsets and 71 tested
upstream adapters. There are **91 including three existing adapters**, with
**253 named tools still missing**. These counts describe scoped workflows,
not complete packages or whole-paper reproduction. Cell Ranger is excluded.

The catalog retains 250 papers: Nature 71, Nature Genetics 104 and AJHG 75.
Its review window is 2023-10-23 through 2026-10-23; no extension was needed.
There are 400 observed tools from 438 definitions, 2,465 paper/tool observations,
993 reviewed roles and 344 supporting pointers. This update changes only the
GSEApy availability row and six GSEApy paper roles. All other 399 tool rows,
987 reviews, paper identities and observed-tool memberships remain exact.

GSEApy is available through development `org.jlinalg.cli.GseapyWorkflowCli`
and separate `GseapyExecutionSetupCli`. It runs explicit original-class local
Enrichr, prerank, GSEA, ssGSEA and GSVA workflows for four exact editions.
Read [the execution guide](../GSEAPY_EXECUTION.md) and
[current catalog guide](../GSEAPY_CATALOG.md). The canonical executable is unchanged.
Original preprocessing, permutations/RNG and GSVA fitting remain upstream.

**SKIP3 skips dependent analysis** when required software, permitted storage or
installation/download permission is unavailable. **FAIL2 admits no case from
the whole requested batch, including earlier completed cases.** Preserve all
original failures and provisionals, correct the named cause and retry NEW paths.
Require exit 0 AND `analysis-status.json result_admitted=true`. RUN installs nothing.
Read [WARNINGS.md](WARNINGS.md), native audits and original/process logs.

Do not infer historical remote library contents, unspecified GSEA/GSVA modes or
paper settings. P098 reports a two-tailed test; the original local statistics
uses a one-sided upper tail. That discrepancy remains unresolved. Preserve
version-specific curves, SymRank no-op, zero-permutation placeholders and
nonfinite diagnostics. Native normalized-null auditing requires finite sort
inputs; unsupported families fail the whole batch.

The full preceding document follows verbatim as history. Its earlier counts
and missing badges describe those checkpoints. Use the current summary,
inventory and guides for availability. The implementation goal remains active.

<details>
<summary>Historical prior catalog document, retained verbatim</summary>

'''.encode();after=header+before+b'\n</details>\n';(data/'README.md').write_bytes(after);report=dict(schema_version=1,status='passed-current-GSEApy-status-with-verbatim-prior-document',before=byte_pin(before),after=pin(data/'README.md'),rendered_intermediate=byte_pin(rendered),historical_document_retained_verbatim=before in after,scientific_source_exception_added=False,whole_goal_complete=False);save(data/'gseapy-readme-evolution.json',report);refresh_manifest(data,(data/'SHA256SUMS').read_bytes());save(out/'COMPLETED.json',report);print('Updated current88/91/253 counts; entire preceding README retained verbatim')


if __name__=='__main__':main()
