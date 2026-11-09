# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged historical GSEApy gates with their exact archived catalog inputs and live scientific sources."""
import contextlib,json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from run_scfates_catalog_checks import ArchivedCatalogView


@contextlib.contextmanager
def historical_catalog_inputs():
    data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((data/'scfates-catalog-validation.json').read_bytes());assert pin(data/'scfates-catalog-support.zip')==p['archive'];files=unpack(data/'scfates-catalog-support.zip',p);overrides={'tools/literature/reviewed-uses.json':files['current-source/tools/literature/reviewed-uses.json']}
    for name in ['papers.json','tool-inventory.json','summary.json']:overrides['docs/literature/omics-2023-2026/'+name]=files['catalog-snapshot/'+name]
    assert len(json.loads(overrides['tools/literature/reviewed-uses.json'])['reviews'])==988;summary=json.loads(overrides['docs/literature/omics-2023-2026/summary.json']);assert summary['implementation_status_counts']['upstream-adapter-validated']==70 and summary['implementation_status_counts']['missing']==254
    # Only four exact historical metadata inputs are virtual. Every scientific
    # source, runtime, artifact, numerical verifier and parent proof stays live.
    import gseapy_statistics_evidence as statistics
    saved=statistics.ROOT
    try:
        statistics.ROOT=ArchivedCatalogView(ROOT,overrides);yield {n:byte_pin(raw) for n,raw in overrides.items()}
    finally:statistics.ROOT=saved


def verify(emit=True):
    import gseapy_execution_evidence as execution
    with historical_catalog_inputs():p=execution.verify(False)
    if emit:print('Verified unchanged complete GSEApy execution gates: exact archived988-role catalog inputs, all live scientific sources strict. This historical workflow check grants no current catalog credit.')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable historical input or scientific source gates')
    verify()
