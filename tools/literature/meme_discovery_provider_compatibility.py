# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact metadata-only MEME provider derivation and append-only ChIPseeker review context."""
import gzip
import hashlib
import json
from catalog_source_integrity import verify_source

ORIGINAL_SHA='18926d04c9e36b2bf818e75ae0806dbcf23408fc9c821ef7383ac0a3c3f6a04c'
VERIFIER_SHA='d37d16a9c3ec7cc6b46885b0cf18d6911664dc5f63320f73742a1aa90056e09a'
BASELINE_REVIEWS_SHA='7a4e6685c2c1cfe14e7ac87e41ca25684174d844dc4423b986eeae451291007d'
EDITS=[(b'from meme_discovery_workflow_evidence import verify as verify_workflow\n',
        b'from meme_discovery_workflow_evidence import verify as verify_workflow\nfrom meme_discovery_provider_compatibility import verify_meme_review_extension\n'),
       (b"    assert len(reviews) == 898 and len([r for r in reviews if r['tool_id'] == 'meme']) == 8\n",
        b"    verify_meme_review_extension(root, data, reviews)\n    assert len([r for r in reviews if r['tool_id'] == 'meme']) == 8\n")]


def sha(raw):return hashlib.sha256(raw).hexdigest()


def verify_provider_derivation(root):
    original=(root/'tools/literature/meme_discovery_catalog.py').read_bytes();assert sha(original)==ORIGINAL_SHA
    assert sha((root/'tools/literature/verify_meme_discovery_catalog.py').read_bytes())==VERIFIER_SHA
    expected=original
    for before,after in EDITS:
        assert expected.count(before)==1;expected=expected.replace(before,after)
    assert (root/'tools/literature/meme_discovery_current_catalog.py').read_bytes()==expected


def validate_review_extension(base_raw,current_raw,drafts):
    assert sha(base_raw)==BASELINE_REVIEWS_SHA
    base=json.loads(base_raw);current=json.loads(current_raw)
    assert {k:v for k,v in base.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'}
    assert len(base['reviews'])==898 and len(current['reviews'])==905
    assert current['reviews'][:898]==base['reviews']
    assert len(drafts)==7 and all(d['review']['tool_id']=='chipseeker' for d in drafts)
    assert current['reviews'][898:]==[d['review'] for d in drafts]
    old_meme=[r for r in base['reviews'] if r['tool_id']=='meme'];new_meme=[r for r in current['reviews'] if r['tool_id']=='meme']
    assert len(old_meme)==8 and new_meme==old_meme
    assert len({(r['pmcid'],r['tool_id']) for r in current['reviews']})==905
    return current['reviews']


def verify_meme_review_extension(root,data,reviews):
    verify_provider_derivation(root)
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes()
    if sha(raw)==BASELINE_REVIEWS_SHA:
        assert len(reviews)==898 and reviews==json.loads(raw)['reviews'];return
    roles=json.loads((data/'chipseeker-role-coverage.json').read_bytes())
    assert roles['status']=='passed-seven-ChIPseeker-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'])==(898,7,905)
    assert roles['before_reviews_sha256']==BASELINE_REVIEWS_SHA and roles['reviews_sha256']==sha(raw)
    archive=(data/'chipseeker-prior-reviews.json.gz').read_bytes()
    assert dict(bytes=len(archive),sha256=sha(archive))==roles['prior_review_archive']
    base=gzip.decompress(archive)
    drafts=json.loads((root/'tools/literature/chipseeker-use-reviews.json').read_bytes())
    assert sha((root/'tools/literature/chipseeker-use-reviews.json').read_bytes())==roles['draft_sha256']
    assert sha((root/'tools/literature/curate_chipseeker_uses.py').read_bytes())==roles['curator_sha256']
    assert reviews==validate_review_extension(base,raw,drafts)
    verify_source(root,data,'tools/literature/reviewed-uses.json',BASELINE_REVIEWS_SHA)
