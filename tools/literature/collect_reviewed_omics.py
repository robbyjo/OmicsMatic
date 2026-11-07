# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extend frozen Methods intake only for two explicitly reviewed software declarations.

The original collector remains byte-pinned. This entrypoint uses its unchanged
process/main code with an explicit extraction function in a separate globals
dictionary. It never makes arbitrary availability statements eligible. A review
must select an exact checked declaration hash, index and section. No inference
of a differential-expression test follows from a software list.
"""
import hashlib
import json
import re
import types
from pathlib import Path
import collect_omics_review as base

HERE=Path(__file__).resolve().parent


def declarations():
    ledger=json.loads((HERE/'deseq2-source-additions.json').read_bytes())
    if hashlib.sha256(Path(base.__file__).read_bytes()).hexdigest()!=ledger['frozen_collector_sha256']:
        raise ValueError('Frozen collector changed; review its behavior before extracting declarations')
    if ledger['status']!='checked-two-named-DESeq2-software-declarations' or len(ledger['declarations'])!=2:
        raise ValueError('Unexpected reviewed-declaration ledger')
    return ledger['declarations']


def tool_matches(paragraphs_,registry,preferred_evidence=None):
    matches=base.tool_matches(paragraphs_,registry,preferred_evidence)
    selected=(preferred_evidence or {}).get('deseq2')
    rows=[r for r in declarations() if r['paragraph_sha256']==selected]
    if not rows:return matches
    if len(rows)!=1 or any(t['tool_id']=='deseq2' for t in matches):
        raise ValueError('Ambiguous reviewed declaration')
    row=rows[0]
    source=[p for p in paragraphs_ if p['paragraph_index']==row['paragraph_index']]
    if len(source)!=1:raise ValueError('Missing reviewed declaration paragraph')
    p=source[0]
    if p['section']!=row['section'] or hashlib.sha256(p['text'].encode('utf8')).hexdigest()!=selected:
        raise ValueError('Stale reviewed declaration section/hash')
    tool=next(t for t in registry if t['id']=='deseq2')
    searchable=p.get('match_text',p['text']);match=re.search(tool['pattern'],searchable,re.I)
    if not match:raise ValueError('Reviewed declaration does not name DESeq2')
    candidate=dict(tool_id='deseq2',tool=tool['name'],analysis_role=row['analysis_role'],
        evidence_section=p['section'],evidence_paragraph_index=p['paragraph_index'],
        evidence_text=p['text'],evidence_sentence=searchable,matched_text=match.group(),
        extraction_score=0,usage_context=row['usage_context'],review_status='candidate-reviewed-software-declaration',
        version_reported_near_name=None,evidence_selection='exact-reviewed-software-declaration')
    return sorted(matches+[candidate],key=lambda t:t['tool_id'])


def _entrypoints():
    # Reuse frozen process/main implementations without modifying their module.
    namespace=dict(vars(base));namespace['tool_matches']=tool_matches
    process=types.FunctionType(base.process.__code__,namespace,'process',base.process.__defaults__)
    namespace['process']=process
    return process,types.FunctionType(base.main.__code__,namespace,'main',base.main.__defaults__)


process,main=_entrypoints()
if __name__=='__main__':main()
