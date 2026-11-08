# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Expose current counts and source semantics while retaining the entire previous catalog document unchanged."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from render_palantir_catalog import refresh_manifest

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepared',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';before=(a.prepared/'before/docs/literature/omics-2023-2026/README.md').read_bytes();rendered=(data/'README.md').read_bytes();(out/'rendered-readme.md').write_bytes(rendered);summary=json.loads((data/'summary.json').read_bytes());assert (summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])==(17,70,254)
    header='''# 250-paper omics/GWAS toolbox catalog

**Current checkpoint: 87 new integrations** (17 native Java subsets and70 tested
upstream adapters), **90 including three existing adapters**, with **254 named
tools still missing**. These are explicitly scoped implementations, not claims
of complete packages or whole-paper reproduction. Cell Ranger remains excluded.

The frozen catalog contains250 papers (Nature71, Nature Genetics104 and AJHG75),
400 observed named tools from438 registry definitions,2465 paper/tool observations,
988 reviewed uses and340 supporting pointers. Its original review window is
2023-10-23 through2026-10-23; no extension was needed. All paper identities,
observations, reviewed roles and399 other tool rows are preserved by this update.

scFates is now available through the validated development
`org.jlinalg.cli.ScfatesWorkflowCli`, with separate `ScfatesSetupCli` setup.
It executes original scFates1.2.5/SimplePPT1.1.7 CPU tree/root/pseudotime on explicit
prepared coordinates. Read [the execution guide](../SCFATES_WORKFLOW.md) and
[catalog integration guide](../SCFATES_CATALOG.md). The canonical executable
is unchanged. P094's edition/root/iterations and biological results are not inferred.

**SKIP3 skips dependent analysis** if compatible software or installation/download
permission is unavailable. **FAIL2 admits no case from the entire requested batch**,
including earlier completed cases. Keep original inputs, failures, provisionals
and logs; correct the named cause and retry NEW paths. RUN installs nothing.
Read [WARNINGS.md](WARNINGS.md), console warnings, native audits and process receipts.
Original NaN/Inf, zero assignments, missing milestones, directional root rounding,
scalar-edge projection, initialization bookkeeping and NumPy ties remain explicit.
No palette, fallback probability, renormalization or annotation is fabricated.

The earlier Palantir checkpoint and all prior text follow verbatim as history.
Use the current summary/inventory and the current implementation guide for
availability; historical missing counts and pending statements describe those
earlier checkpoints. The250-paper implementation goal remains active.

<details>
<summary>Historical prior catalog document, retained verbatim</summary>

'''.encode();after=header+before+b'\n</details>\n';(data/'README.md').write_bytes(after);evolution=dict(schema_version=1,status='passed-current-scFates-status-with-verbatim-prior-document',before=byte_pin(before),after=pin(data/'README.md'),rendered_intermediate=byte_pin(rendered),historical_document_retained_verbatim=before in after,scientific_source_exception_added=False,whole_goal_complete=False);save(data/'scfates-readme-evolution.json',evolution);refresh_manifest(data,(data/'SHA256SUMS').read_bytes());save(out/'COMPLETED.json',evolution);print('Updated current87/90/254 counts; the entire previous README remains verbatim history')

if __name__=='__main__':main()
