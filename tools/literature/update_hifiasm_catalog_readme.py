# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Show current scoped availability while retaining the complete preceding README verbatim."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from render_palantir_catalog import refresh_manifest


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';before=(a.prepared/'before/docs/literature/omics-2023-2026/README.md').read_bytes();rendered=(data/'README.md').read_bytes();(out/'rendered-readme.md').write_bytes(rendered);counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,72,252)
    header='''# 250-paper omics/GWAS toolbox catalog

**Current checkpoint: 89 new integrations**: 17 native Java subsets and 72 tested
upstream adapters. There are **92 including three existing adapters**, with
**252 named tools still missing**. These are scoped workflows, not complete
packages or whole-paper reproduction. Cell Ranger remains excluded.

The fixed cohort contains 250 papers: Nature 71, Nature Genetics 104 and AJHG 75,
published from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 400 observed tools from 438 definitions, 2,465 paper/tool observations,
1,000 reviewed roles and 347 supporting pointers. This update adds seven hifiasm
roles and one availability row. All 993 previous reviews, other 399 inventory
rows, paper identities and observed-tool memberships remain exact.

Hifiasm is available through development `org.jlinalg.cli.HifiasmWorkflowCli`,
with separate `HifiasmSetupCli` and `HifiasmRuntimeCli`. Read
[the complete guide](../HIFIASM.md). Six exact original source profiles provide
the declared HiFi, trio, Hi-C, UL and ONT modes. Native Java inspects all reads,
parental entries, complete graphs and literal FASTA exports. Original correction,
overlap construction, consensus and phasing remain upstream. The canonical
executable is unchanged.

**Require exit 0 AND COMPLETED.json analysis_admitted=true for the entire batch.**
Read [WARNINGS.md](WARNINGS.md), graph audits, original stderr and process receipts.
**SKIP3 skips dependent analysis** when required permitted software or installation
is unavailable. **FAIL2 admits no case, including earlier completed cases.**
Preserve failed/provisional outputs and named causes; correct the problem and
retry NEW paths. Analysis installs nothing.

Old 0.14.2/0.15.1 Hi-C is excluded after retained original crashes. ONT-only
0.22.0 requires genuine FASTQ qualities. Unreported paper settings and revision
suffixes remain unresolved. Small-control defaults need explicit capacity
overrides for real genomes; streaming parental checks do not reduce the
assembler's own memory needs. Graph integrity does not certify consensus,
phasing, completeness or biological quality. Keep scaffolding and QC separate.

The full preceding document follows verbatim as history. Earlier counts and
missing badges describe their checkpoints. Use the current inventory and guides
for availability. The broader implementation goal remains active.

<details>
<summary>Historical prior catalog document, retained verbatim</summary>

'''.encode();after=header+before+b'\n</details>\n';(data/'README.md').write_bytes(after);report=dict(schema_version=1,status='passed-current-hifiasm-status-with-verbatim-prior-document',before=byte_pin(before),after=pin(data/'README.md'),rendered_intermediate=byte_pin(rendered),historical_document_retained_verbatim=before in after,scientific_source_exception_added=False,whole_goal_complete=False);save(data/'hifiasm-readme-evolution.json',report);refresh_manifest(data,(data/'SHA256SUMS').read_bytes());save(out/'COMPLETED.json',report);print('Updated89/92/252 counts; full preceding README retained verbatim')


if __name__=='__main__':main()
