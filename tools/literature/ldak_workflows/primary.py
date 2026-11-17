# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-bound LDAK/SumHer roles, including the missed SpliTWAS comparator."""
from pathlib import Path
import gzip
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs, publication_record, tool_matches
from ldak_original.audit_roles import audit as audit_roles
if not __debug__:
    raise RuntimeError('Primary-source assertions must remain enabled; rerun without -O')

EXPECTED = {'P090':23, 'P109':41, 'P120':26, 'P171':56, 'P238':22, 'P245':47}
CURATION = 'docs/literature/ldak-workflow-development/PAPER-ROLES.json'


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2)+'\n').encode()


def audit(cache, papers=None):
    papers = json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes()) if papers is None else papers
    checked = audit_roles(cache)
    assert checked['papers_scanned'] == 250 and checked['reviewed_roles'] == 6 and checked['named_paragraphs'] == 10
    curated = json.loads((ROOT/CURATION).read_bytes())
    assert curated['primary_files'] == [{key:paper[key] for key in ['paper_id', 'pmcid', 'doi', 'xml_sha256']} for paper in papers]
    roles = {row['paper_id']:row for row in curated['roles']}
    assert set(roles) == set(EXPECTED)
    definition = next(row for row in json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes()) if row['id'] == 'ldak')
    result, primary = [], []
    for paper in papers:
        raw = gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes())
        digest = hashlib.sha256(raw).hexdigest()
        assert digest == paper['xml_sha256'], paper['paper_id']
        primary.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], bytes=len(raw), sha256=digest))
        if paper['paper_id'] not in roles:
            continue
        role = roles[paper['paper_id']]
        body = paragraphs(ET.fromstring(raw))
        anchor = next(row for row in body if row['paragraph_index'] == EXPECTED[paper['paper_id']])
        anchor_hash = hashlib.sha256(anchor['text'].encode()).hexdigest()
        assert any(row['paragraph_index'] == anchor['paragraph_index'] and row['paragraph_sha256'] == anchor_hash for row in role['anchors'])
        existing = [row for row in paper['tools'] if row['tool_id'] == 'ldak']
        if paper['paper_id'] == 'P238':
            assert len(existing) in [0, 1]
            candidates = tool_matches(body, [definition], {'ldak':anchor_hash})
            assert len(candidates) == 1 and candidates[0]['evidence_paragraph_index'] == 22
            candidate_paper = dict(paper, tools=candidates)
            new_observation = publication_record(candidate_paper, reviewed_uses=[])['tools'][0]
            assert new_observation['evidence_paragraph_sha256'] == anchor_hash
        else:
            assert len(existing) == 1 and existing[0]['evidence_paragraph_sha256'] == anchor_hash
            new_observation = None
        result.append(dict(role, anchor_index=anchor['paragraph_index'], anchor_section=anchor['section'], anchor_sha256=anchor_hash, reported_engine_version_known=role['reported_engine_version'] is not None, selected_project_profile='5.2', selected_runtime_is_author_binary=False, new_observation=new_observation))
    assert len(result) == 6
    assert next(row for row in result if row['paper_id'] == 'P245')['usage_context'] == 'background-or-previous-work-not-current-ldak-execution'
    return dict(status='PASS_ALL250_PRIMARY_IDENTITIES_SIX_LDAK_ROLES_AND_MISSED_P238_COMPARATOR', papers_scanned=250, primary_files=primary, roles=result, named_body_hits=curated['named_body_hits'], observations_added=['P238'], background_or_previous_work_roles=['P245'], whole_goal_complete=False)
