# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recover only the reviewed HT-seq spelling at P005's exact named source anchor."""
import hashlib
import json
from pathlib import Path
import collect_reviewed_omics as previous

HERE=Path(__file__).resolve().parent


def tool_matches(paragraphs_,registry,preferred_evidence=None):
    matches=previous.tool_matches(paragraphs_,registry,preferred_evidence)
    ledger=json.loads((HERE/'htseq-source-additions.json').read_bytes())
    assert ledger['status']=='checked-one-exact-HTSeq-hyphenated-software-name' and len(ledger['declarations'])==1
    assert hashlib.sha256((HERE/'collect_omics_review.py').read_bytes()).hexdigest()==ledger['frozen_collector_sha256']
    assert hashlib.sha256((HERE/'collect_reviewed_omics.py').read_bytes()).hexdigest()==ledger['reviewed_collector_sha256']
    row=ledger['declarations'][0]
    if (preferred_evidence or {}).get('htseq')!=row['paragraph_sha256']:return matches
    assert not any(t['tool_id']=='htseq' for t in matches)
    sources=[p for p in paragraphs_ if p['paragraph_index']==row['paragraph_index']]
    assert len(sources)==1
    source=sources[0]
    assert source['section']==row['section'] and hashlib.sha256(source['text'].encode('utf8')).hexdigest()==row['paragraph_sha256']
    assert 'HT-seq (v.0.6.0)' in source['text']
    tool=dict(next(r for r in registry if r['id']=='htseq'))
    tool['pattern']=r'(?<![A-Za-z0-9_])HT-seq(?![A-Za-z0-9_])'
    recovered=previous.tool_matches([source],[tool],{'htseq':row['paragraph_sha256']})
    assert len(recovered)==1 and recovered[0]['matched_text']=='HT-seq'
    return sorted(matches+recovered,key=lambda t:t['tool_id'])
