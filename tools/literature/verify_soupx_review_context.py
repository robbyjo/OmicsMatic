# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged MEME/ChIPseeker scientific gates with the exact918+7 metadata extension."""
import contextlib,io,json
from setup_deseq2_reference import ROOT,pin
from soupx_catalog import current_catalog_review_context,verify_review_extension


def verify():
    import verify_meme_discovery_current_catalog as meme
    import verify_chipseeker_catalog as chip
    data=ROOT/'docs/literature/omics-2023-2026';reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_review_extension(ROOT,data,reviews);results={}
    with current_catalog_review_context():
        for name,module in [('MEME',meme),('ChIPseeker',chip)]:
            captured=io.StringIO()
            with contextlib.redirect_stdout(captured):module.main()
            results[name]=json.loads(captured.getvalue())
    audit=json.loads((data/'soupx-source-audit.json').read_bytes());assert audit['status']=='passed-250-current-primary-source-and-925-role-audit' and audit['papers_pin']==pin(data/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json');assert (audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(2450,925,311)
    for name,value in audit['source_pins'].items():assert pin(ROOT/name)==value
    result=dict(status='passed-925-role-context-with-frozen-scientific-gates',current_reviews=925,prior_reviews_preserved=918,new_reviews=7,scientific_sources_or_frozen_verifiers_changed=False,original_gates=results,whole_goal_complete=False);print(json.dumps(result));return result


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact metadata or scientific checks')
    verify()
