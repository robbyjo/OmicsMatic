# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive a metadata-only provider; immutable MEME providers/verifiers/scientific evidence remain unchanged."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    here=ROOT/'tools/literature';original=here/'meme_discovery_catalog.py';before=original.read_bytes()
    assert pin(original)['sha256']=='18926d04c9e36b2bf818e75ae0806dbcf23408fc9c821ef7383ac0a3c3f6a04c'
    edits=[(b'from meme_discovery_workflow_evidence import verify as verify_workflow\n',
            b'from meme_discovery_workflow_evidence import verify as verify_workflow\nfrom meme_discovery_provider_compatibility import verify_meme_review_extension\n'),
           (b"    assert len(reviews) == 898 and len([r for r in reviews if r['tool_id'] == 'meme']) == 8\n",
            b"    verify_meme_review_extension(root, data, reviews)\n    assert len([r for r in reviews if r['tool_id'] == 'meme']) == 8\n")]
    after=before
    for a,b in edits:assert after.count(a)==1;after=after.replace(a,b)
    derived=here/'meme_discovery_current_catalog.py';assert not derived.exists();derived.write_bytes(after)
    save(out/'COMPLETED.json',dict(status='derived-metadata-only-MEME-provider-frozen-original-unchanged',original=pin(original),derived=pin(derived),
        baseline_reviews=pin(here/'reviewed-uses.json'),deriver=pin(Path(__file__)),scientific_sources_modified=False))
    print('Derived MEME metadata provider; only global review-count gate replaced by exact prior-review preservation checks')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable provider derivation checks')
    main()
