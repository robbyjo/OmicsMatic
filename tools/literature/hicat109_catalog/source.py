# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve the single reported HICAT method and all predecessor-context anchors."""
import copy
from doubletfinder_catalog.source import reviews as curated_reviews
from bigcat_catalog.source import scan as previous_scan

MODULE='tools/literature/hicat109_catalog'
TOOL='scrattch-hicat'
NOTES={'P059':
 'Project availability: the unchanged original doubletFinder function from an explicitly selected prepublication scrattch.hicat 1.0.9 source commit, with privately source-built RANN 2.6.1 and a separate independent Java strict-score selector. The Java component applies the reported score>0.3 predicate to supplied values; equality retains the original row and an unavailable numeric comparison is UNKNOWN. It computes no HICAT score and adds no second family credit. Complete original score objects, literal and duplicate identifiers, conditions/errors and RNG remain intact. Dense/sparse behavior, the over-10000-real-cell sampled-PCA branch and original plotting/dimension/partial-match failures are tested without source repair. The 62 housekeeping genes describe a separate QC score, not an established doublet gene panel. The actual author commit, exact function, caller arguments, gene panel and environment remain unknown; the selected original function/profile and synthetic inputs are project choices. Later HICAT predecessor mentions do not establish additional current HICAT analyses. DESCRIPTION and LICENSE labels remain unresolved; upstream source/runtime and complete native compilation consoles stay private, while whole synthetic scientific records are retained. SKIP3 skips dependent analysis for unavailable/forbidden requirements; FAIL2 admits no complete result, including recording failure. Whole-package validity, predecessor clustering and paper reproduction remain unqualified.'}

def original_reviews(root):
    rows=[r for r in curated_reviews(root) if r['tool_id']==TOOL]
    if [(r['paper_id'],r['version_reported_near_name']) for r in rows]!=[('P059','1.0.9')]:
        raise ValueError('Exactly one primary reported HICAT 1.0.9 method required')
    row=rows[0]
    if row['reported_settings']!={'doublet_score_threshold':.3,'removal_comparator':'>'}:
        raise ValueError('The original strict paper threshold must remain unchanged')
    if [a['paragraph_index'] for a in row['supporting_evidence']]!=[85,87,88,91]:
        raise ValueError('All primary and predecessor-context anchors required')
    if row['named_anchor_classification']!=[
      {'paragraph_index':85,'kind':'primary-methods'},
      *[{'paragraph_index':i,'kind':'historical-predecessor-context'} for i in [87,88,91]]]:
        raise ValueError('Predecessor context cannot be promoted to additional analyses')
    return rows

def reviews(root):
    rows=copy.deepcopy(original_reviews(root))
    for row in rows:row['implementation_scope_note']=NOTES[row['paper_id']]
    return rows

def scan(root,papers):
    census=previous_scan(root,papers)
    actual={r['paper_id']:[a['paragraph_index'] for a in r['anchors'][TOOL]] for r in census if r['anchors'][TOOL]}
    if len(census)!=250 or actual!={'P059':[85,87,88,91]}:
        raise ValueError('Complete primary HICAT anchor population differs')
    return census
