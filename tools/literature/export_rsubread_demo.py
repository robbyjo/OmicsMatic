# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export the small synthetic original SAM/GTF/SAF counting fixtures; no software install or R execution."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,save
from signac_scan_evidence import byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists()
    from rsubread_matrix_evidence import verify
    proof,files=verify(False);assert proof['native_matrix_component_available'];prefix='build/literature-review/rsubread-runtime/original-controls-3/inputs/';payload={n[len(prefix):]:raw for n,raw in files.items() if n.startswith(prefix)}
    assert set(payload)=={'single.sam','paired.sam','genes.gtf','genes.gtf.gz','peaks.saf','genome.fasta','one/same.sam','two/same.sam'};out.mkdir(parents=True)
    for name,raw in payload.items():path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    save(out/'DEMO.json',dict(status='exported-synthetic-original-Rsubread-control-inputs-only',files={n:byte_pin(raw) for n,raw in payload.items()},biological_data=False,R_executed=False,analysis_admitted=False,whole_goal_complete=False))
    (out/'README.txt').write_bytes(b'Synthetic original counting controls, not biological study data. Literal gene NA deliberately exercises original R missing-name behavior; overlapping transcripts/features and paired/unpaired/multimapping/duplicate/junction observations are intentional. Do not silently clean or relabel these observations. No analysis has run. Read docs/literature/RSUBREAD_WORKFLOWS.md.\n');print('Exported eight synthetic original counting input files to',out)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original fixture population checks')
    main()
