# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged MEME/ChIPseeker scientific gates in the exact 905+12 review context."""
import contextlib
import io
import json
from setup_deseq2_reference import ROOT,pin
from monocle_catalog_context import current_catalog_review_context,verify_current_review_extension


def verify():
    import verify_meme_discovery_current_catalog as meme
    import verify_chipseeker_catalog as chip
    data=ROOT/'docs/literature/omics-2023-2026';reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_current_review_extension(ROOT,data,reviews);results={}
    with current_catalog_review_context():
        for name,module in [('MEME',meme),('ChIPseeker',chip)]:
            captured=io.StringIO()
            with contextlib.redirect_stdout(captured):module.main()
            results[name]=json.loads(captured.getvalue())
    audit=json.loads((data/'monocle-source-audit.json').read_bytes());assert audit['status']=='passed-current-primary-source-pointer-audit' and audit['papers_pin']==pin(data/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json')
    assert (audit['source_xml_hashes_verified'],audit['tool_observation_pointers_verified'],audit['individually_reviewed_usage_summaries_verified'],audit['supporting_paragraph_pointers_verified'])==(250,2449,917,309)
    for n,v in audit['source_pins'].items():assert pin(ROOT/n)==v
    return dict(status='passed-current-review-extension-with-unchanged-scientific-gates',current_reviews=917,prior_reviews_preserved=905,new_reviews=12,scientific_sources_or_frozen_verifiers_changed=False,original_gates=results,whole_goal_complete=False)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact review derivations or frozen scientific gates')
    print(json.dumps(verify()))
