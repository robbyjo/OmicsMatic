# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Show current90/93/251 counts while retaining the entire preceding catalog document verbatim."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from render_palantir_catalog import refresh_manifest


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';before=(a.prepared/'before/docs/literature/omics-2023-2026/README.md').read_bytes();rendered=(data/'README.md').read_bytes();(out/'rendered-readme.md').write_bytes(rendered)
    counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,73,251)
    header='''# 250-paper omics/GWAS toolbox catalog

**Current checkpoint: 90 new integrations**: 17 native Java subsets and 73 tested
upstream adapters. There are **93 including three existing adapters**, with
**251 named tools still missing**. These are scoped workflows, not complete
packages or whole-paper reproduction. Cell Ranger remains excluded.

The fixed cohort contains 250 papers: Nature 71, Nature Genetics 104 and AJHG 75,
published from 2023-10-23 through 2026-10-23. No time extension was needed.
There are 400 observed tools from 438 definitions, 2,465 paper/tool observations,
1,003 reviewed roles and 349 supporting pointers. This update refines three
Rsubread reviews, adds three, and changes one availability row. All 997 other
reviews, 399 other inventory rows, paper identities and observed memberships
remain exact.

Rsubread bulk counting is available through standalone development
`org.jlinalg.cli.RsubreadWorkflowCli`, with `RsubreadRuntimeCli` for binding.
Read [the complete guide](../RSUBREAD_WORKFLOWS.md). Two explicit original Windows
profiles provide Rsubread 2.26.0/R4.6.1 and selected 2.23.2/R4.4.1 counting.
Typed per-library units/strand, exact GTF/SAF inputs and mappings, original RDS
objects and complete native value/relationship checks are retained. Original
read assignment and junction discovery/counting remain upstream. The canonical
executable is unchanged.

**Require exit 0 AND root COMPLETED.json analysis_admitted=true for the entire batch.**
Read [WARNINGS.md](WARNINGS.md), original errors/logs, objects and process receipts.
**SKIP3 skips dependent analysis** when permitted exact software/bindings are
unavailable or execution/installation is forbidden. **FAIL2 admits no case,
including earlier completed cases.** Preserve failed/provisional output, correct
the named cause and retry NEW paths. Original GPL package/source export requires
explicit permission; analysis installs nothing. Linux counting/binding skips,
while frozen native verification is portable.

Declare library units and settings explicitly. Fragment thresholds do not enable
filtering when checkFragLength=false; preserve the P133 ATAC omissions and later
separate sample rename. The 2.23.2 label appears in 22 recovered upstream commits;
the selected source retains development warnings and does not identify the paper
binary. Unreported APIs, editions and annotation/aggregation choices remain
unresolved. Do not replace missing or automatically coerced identifiers.

Both JDK21/25 compiler artifacts independently pass 58 complete separate-original
comparisons: 50 WindowsJVM21 and eight WindowsJVM25 per compiler. Real runtime
skips, late original/native failures, cancellation, timeout/output/interruption,
deeper corruptions and beginner package/data-export/bind/count/verify examples
pass. Four new CLI bytecode differences are recorded, while all 14 prior value
classes and R resources remain unchanged. VERIFY is structural consistency,
not independent refitting, source-input parsing parity or execution authentication.
No independent alignment/junction counting, full-package or paper claim is made.

The complete preceding document follows verbatim as history. Earlier counts and
missing badges describe their checkpoints; use the current inventory and guides.
The broader implementation goal remains active.

<details>
<summary>Historical prior catalog document, retained verbatim</summary>

'''.encode('utf8')
    after=header+before+b'\n</details>\n';(data/'README.md').write_bytes(after);report=dict(schema_version=1,status='passed-current-Rsubread-status-with-verbatim-prior-document',before=byte_pin(before),after=pin(data/'README.md'),rendered_intermediate=byte_pin(rendered),historical_document_retained_verbatim=before in after,scientific_source_exception_added=False,whole_goal_complete=False);save(data/'rsubread-readme-evolution.json',report);refresh_manifest(data,(data/'SHA256SUMS').read_bytes());save(out/'COMPLETED.json',report);print('Updated90/93/251 counts and1003 roles; entire previous README retained verbatim')


if __name__=='__main__':main()
