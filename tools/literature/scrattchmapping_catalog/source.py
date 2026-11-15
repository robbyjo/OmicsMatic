# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve primary mapping roles, reported/unknown editions and the full-source census."""
import copy
from doubletfinder_catalog.source import reviews as previous_reviews, scan as previous_scan

MODULE = 'tools/literature/scrattchmapping_catalog'
TOOL = 'scrattch-mapping'
NOTES = {
 'P058': 'Project availability: selected original v0.2 prepared-reference hierarchical mapping with an exact permitted Linux/WSL provider and complete caller-supplied taxonomy/Annoy assets. The paper edition, exact function, metric, markers and caller options remain unknown; v0.2 is not substituted as its reported edition. Paper-specific confusion-matrix/marker-correlation summarization and biological reproduction are not qualified. Missing/forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result.',
 'P059': 'Project availability: selected unchanged original v0.2 run_mapping_on_taxonomy for complete caller-prepared taxonomy/Annoy assets, correlation distance and serial query blocks. Eleven fresh original controls cover hierarchy traversal and bootstrap aggregation; the exact paper function, metric, markers, tie policy and caller arguments remain unreported. Scientific reference/marker discovery, MapMyCells service and biological/paper reproduction are not qualified. The selected R 4.2.3 profile is not an author environment. Missing/forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result.'
}


def original_reviews(root):
    rows = [r for r in previous_reviews(root) if r['tool_id'] == TOOL]
    if [(r['paper_id'], r['version_reported_near_name'], r['evidence_paragraph_index']) for r in rows] != [('P058', None, 57), ('P059', '0.2', 111)]:
        raise ValueError('Primary mapping editions/roles changed')
    return rows


def reviews(root):
    rows = copy.deepcopy(original_reviews(root))
    for row in rows:
        row['implementation_scope_note'] = NOTES[row['paper_id']]
    return rows


def scan(root, papers):
    # Reuse the unchanged source scanner, including every dot/hyphen mapping
    # anchor and all five previously added families/Arrow candidate distinctions.
    census = previous_scan(root, papers)
    if len(census) != 250:
        raise ValueError('All 250 source identities required')
    mapped = {row['paper_id']: [a['paragraph_index'] for a in row['anchors'][TOOL]]
              for row in census if row['anchors'][TOOL]}
    if mapped != {'P058': [57, 77, 85], 'P059': [17, 111, 155, 162]}:
        raise ValueError('Complete dot/hyphen primary mapping anchor population differs')
    return census
