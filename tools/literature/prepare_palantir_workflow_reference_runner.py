# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the exact existing owned runtime supervisor for the full-workflow source-discovery worker."""
from setup_deseq2_reference import ROOT

def expected():
    raw=(ROOT/'tools/literature/run_palantir_multiscale_reference_linux.py').read_text(encoding='utf8');assert raw.count('collect_palantir_multiscale_reference.py')==2
    return raw.replace('collect_palantir_multiscale_reference.py','collect_palantir_workflow_reference.py').replace('PASS_UNCHANGED_FULL_PACKAGE_PALANTIR_MULTISCALE_REFERENCES_ONLY','PASS_COMPLETE_ORIGINAL_PALANTIR_WORKFLOW_SOURCE_DISCOVERY_ONLY').replace('PALANTIR_MULTISCALE_ORIGINAL_REFERENCES','PALANTIR_FULL_WORKFLOW_ORIGINAL_REFERENCES').replace('PASS_TWO_FULL_ORIGINAL_PALANTIR_MULTISCALE_REFERENCE_FAMILIES_ONLY','PASS_TWO_FULL_ORIGINAL_PALANTIR_WORKFLOW_DISCOVERY_FAMILIES_ONLY')

def verify():assert (ROOT/'tools/literature/run_palantir_workflow_reference_linux.py').read_bytes()==expected().encode()

if __name__=='__main__':
    path=ROOT/'tools/literature/run_palantir_workflow_reference_linux.py';raw=expected().encode()
    if path.exists():assert path.read_bytes()==raw,'Existing full-workflow supervisor protected'
    else:path.write_bytes(raw)
    verify();print('Prepared exact owned full-workflow reference supervisor')
