# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Primary BIGCAT uses, separate from HICAT, mapping and generic substitutes."""
import copy
from doubletfinder_catalog.source import reviews as curated_reviews
from arrow_catalog.source import scan as previous_scan

MODULE='tools/literature/bigcat_catalog'
TOOL='scrattch-bigcat'
NOTES={
 'P058':'Project availability: original build_dend and plot_constellation plus i_harmonize through explicitly selected source profiles. The selected 2023 source retains its ordinary-matrix harmonization indexing error; a separately selected unchanged 2022/R4.1 profile supports tested three-dataset integration with explicit caller sets. The optional at-least-five-SPN selection follows integration at a caller-chosen hierarchy level. It does not reproduce reciprocal correspondence mapping or the separate fewer-than-three-mapped-cells exclusion. P058 edition, author commit and unreported caller arguments remain unknown; synthetic inputs are project choices. Original objects, conditions, RNG and file-backed assets are retained; cross-process live object identity and paper reproduction are not claimed. Missing/forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result.',
 'P059':'Project availability: unchanged original iter_clust_big, find_doublet_by_marker, select_N_markers, plot_constellation, map_cells_knn_big and impute_cross_big; a separate native Java helper covers dense marker-doublet detection. Synthetic controls exercise the reported v2/v3 DE settings, 50000-cell/500-gene source bins, fivefold mapping with 8460/534/20x534 panels and all training cells per centroid, and declared coarse/fine FBM imputation partitions. Gene lists, folds, data and unreported caller options are project choices. Explicit source profiles are not recovered author commits. Original vector/default-variable/sparse-score/singleton-bin errors remain failures. Complete values, source distances, conditions, RNG and assets are retained. Live R class/environment/pointer identity, manual biological review, whole-package validity and paper reproduction are not claimed. Missing/forbidden prerequisites SKIP3 skip dependent analysis; FAIL2 admits no complete result.'}

def original_reviews(root):
    rows=[r for r in curated_reviews(root) if r['tool_id']==TOOL]
    if [(r['paper_id'],r['version_reported_near_name']) for r in rows]!=[('P058',None),('P059','0.0.5')]:
        raise ValueError('Exact primary BIGCAT editions/unknowns required')
    expected={'P058':[50,52,58,77,85],'P059':[6,87,88,93,96,102,107,109,114,155,162]}
    for row in rows:
        if [r['paragraph_index'] for r in row['supporting_evidence']]!=expected[row['paper_id']]:
            raise ValueError('Complete primary BIGCAT locators required')
    functions={f for row in rows for f in row['reported_settings']['functions']}
    if functions!={'build_dend','plot_constellation','i_harmonize','iter_clust_big','find_doublet_by_marker',
      'select_N_markers','map_cells_knn_big','impute_cross_big'}:raise ValueError('All eight named original roles required')
    return rows

def reviews(root):
    rows=copy.deepcopy(original_reviews(root))
    for row in rows:row['implementation_scope_note']=NOTES[row['paper_id']]
    return rows

def scan(root,papers):
    census=previous_scan(root,papers)
    actual={r['paper_id']:[a['paragraph_index'] for a in r['anchors'][TOOL]] for r in census if r['anchors'][TOOL]}
    if len(census)!=250 or actual!={'P058':[50,52,58,77,85],'P059':[6,87,88,93,96,102,107,109,114,155,162]}:
        raise ValueError('Complete BIGCAT primary-source population changed')
    return census
