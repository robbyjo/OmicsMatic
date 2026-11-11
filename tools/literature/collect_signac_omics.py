# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Guard the exact Signac LR caption selected by the collector's existing review support."""
import hashlib
import json
import re
from pathlib import Path
import collect_reviewed_omics as previous

HERE = Path(__file__).resolve().parent


def tool_matches(paragraphs_, registry, preferred_evidence=None):
    matches = previous.tool_matches(paragraphs_, registry, preferred_evidence)
    ledger = json.loads((HERE / 'signac-source-additions.json').read_bytes())
    assert ledger['status'] == 'checked-one-explicit-Signac-analysis-caption' and len(ledger['declarations']) == 1
    assert hashlib.sha256((HERE / 'collect_omics_review.py').read_bytes()).hexdigest() == ledger['frozen_base_collector_sha256']
    assert hashlib.sha256((HERE / 'collect_reviewed_omics.py').read_bytes()).hexdigest() == ledger['existing_reviewed_collector_sha256']
    selected = (preferred_evidence or {}).get('signac')
    row = ledger['declarations'][0]
    if selected != row['paragraph_sha256']:
        return matches
    source = [p for p in paragraphs_ if p['paragraph_index'] == row['paragraph_index']]
    assert len(source) == 1
    source = source[0]
    assert source['section'] == row['section'] and hashlib.sha256(source['text'].encode('utf8')).hexdigest() == selected
    tool = next(t for t in registry if t['id'] == 'signac')
    match = re.search(tool['pattern'], source.get('match_text', source['text']), re.I)
    assert match and re.search(r'logistic regression.{0,80}Signac', source['text'], re.I)
    found = [t for t in matches if t['tool_id'] == 'signac']
    assert len(found) == 1
    assert found[0]['evidence_paragraph_index'] == row['paragraph_index']
    assert hashlib.sha256(found[0]['evidence_text'].encode('utf8')).hexdigest() == selected
    assert found[0]['evidence_selection'] == 'individually-reviewed-paragraph'
    return matches


# No collector/process replacement is necessary. The unchanged existing collector
# already honors an exact named-tool/hash review for a Results analysis caption.
