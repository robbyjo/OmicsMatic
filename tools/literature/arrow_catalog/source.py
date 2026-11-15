# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the reported R interface distinct from PyArrow and ordinary figure arrows."""
import copy
from doubletfinder_catalog.source import reviews as curated_reviews
from scrattchmapping_catalog.source import scan as preceding_scan

MODULE='tools/literature/arrow_catalog'
TOOL='arrow-r'
NOTES={'P059': 'Project availability: selected original R Arrow 12.0.1 Parquet chunk reads and lazy Dataset filter/select/arrange/collect queries with caller-declared local assets and options. The pinned C++ h32ed672_2_cpu recipe declares no source patches; same-version patched builds are rejected. Thirteen original controls cover ten complete returns and three original failures, including a 50,000-by-500 project fixture. The paper storage schema, exact Arrow calls, memory-map settings and query options remain unreported. Sparse-matrix conversion, live Dataset/Table pointer persistence, biological validity and paper reproduction are not qualified. Complete original conditions are retained; the missing-column trace records actual caller context. Missing/forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result, including recording failure.'}

def original_reviews(root):
    rows=[r for r in curated_reviews(root) if r['tool_id']==TOOL]
    if len(rows)!=1 or (rows[0]['paper_id'],rows[0]['version_reported_near_name'],rows[0]['evidence_paragraph_index'])!=('P059','12.0.1',87):
        raise ValueError('Exact primary R Arrow edition and locator required')
    if [r['paragraph_index'] for r in rows[0]['supporting_evidence']]!=[87,91]:
        raise ValueError('Both primary Arrow role anchors required')
    if rows[0]['reported_settings']!=dict(expression_chunk_cells=50000,expression_chunk_genes=500,format='Parquet'):
        raise ValueError('Primary chunk dimensions/format changed')
    return rows

def reviews(root):
    rows=copy.deepcopy(original_reviews(root))
    rows[0]['implementation_scope_note']=NOTES['P059']
    return rows

def scan(root,papers):
    census=preceding_scan(root,papers)
    anchors={r['paper_id']:[a['paragraph_index'] for a in r['anchors'][TOOL]] for r in census if r['anchors'][TOOL]}
    if len(census)!=250 or anchors!={'P059':[87,91]}:
        raise ValueError('Complete R Arrow primary-source anchor population differs')
    return census
