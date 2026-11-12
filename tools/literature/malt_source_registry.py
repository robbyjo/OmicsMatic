# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit MALT/HOPS/MaltExtract names; source-role admission is separate."""
import copy

IDS = frozenset({'malt', 'hops', 'maltextract'})
ENTRIES = [
    dict(id='malt', name='MALT', pattern=r'(?<![A-Za-z0-9_])(?-i:MALT)(?![A-Za-z0-9_])',
         role='Metagenomic alignment, taxonomic classification and reference-index building', current_coverage='missing',
         existing_source='src/main/java/org/jlinalg/cli/MaltAlignCli.java',
         coverage_note='The source-tested original 0.4.1 BlastN controller and native geometry/naive-assignment components are development subsets. P002 separately reports a custom 0.5.2 database build/screen with maximum-alignments-per-reference 100000 and aligned-read output. Version-specific0.5.2 integration and the complete historical reference/data requirements remain open. Original0.4.1 Local clip defects and incomplete query consumption are refused. Read MALT_ALIGNMENT.md and MALT_HOPS_PAPER_ROLES.md. No whole-tool or biological credit.'),
    dict(id='hops', name='HOPS', pattern=r'(?<![A-Za-z0-9_])(?-i:HOPS)(?![A-Za-z0-9_])',
         role='Ancient-pathogen candidate assessment from metagenomic alignment evidence', current_coverage='missing',
         existing_source='src/main/java/org/jlinalg/cli/HopsEvidenceCli.java',
         coverage_note='Native source-bound evidence statistics and original R contracts are development components. Full HOPS execution, species extraction, damage profiles and paper-specific candidate authentication remain open. Insufficient-read candidates remain unassessable. Read HOPS_EVIDENCE.md and MALT_HOPS_PAPER_ROLES.md; no full-pipeline or biological admission.'),
    dict(id='maltextract', name='MaltExtract', pattern=r'(?<![A-Za-z0-9_])(?-i:MaltExtract)(?![A-Za-z0-9_])',
         role='Target-species RMA extraction and alignment/damage-related summaries', current_coverage='missing', existing_source=None,
         coverage_note='P002 supplement section 3.3 names MaltExtract but does not state its version/settings. Qualify a permitted original provider, reference taxonomy/resources and the declared extraction/filter choices. Manual candidate assessment and the separate 7508-genome bacterial rescreen remain distinct. The old standalone repository README explicitly refers users to HOPS for current instructions. Read MALT_HOPS_PAPER_ROLES.md; no extraction or biological result is admitted.'),
]


def extend(previous):
    assert not IDS.intersection(r['id'] for r in previous)
    result = copy.deepcopy(previous) + copy.deepcopy(ENTRIES)
    assert len(result) == len({r['id'] for r in result})
    return result
