# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the exact966-role extension and run unchanged MEME/ChIPseeker scientific gates under that metadata context."""
import contextlib,io,json
from setup_deseq2_reference import ROOT,pin
from leafcutter_catalog import current_catalog_review_context,verify_review_extension

def verify():
    data=ROOT/'docs/literature/omics-2023-2026';reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_review_extension(ROOT,data,reviews);results={}
    with current_catalog_review_context():
        import verify_meme_discovery_current_catalog as meme
        import verify_chipseeker_catalog as chip
        for name,module in [('MEME',meme),('ChIPseeker',chip)]:
            captured=io.StringIO()
            with contextlib.redirect_stdout(captured):module.main()
            results[name]=json.loads(captured.getvalue())
    audit=json.loads((data/'leafcutter-source-audit.json').read_bytes());assert audit['status']=='passed-250-current-primary-source-and-966-role-audit' and audit['papers_pin']==pin(data/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json') and (audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(2450,966,334)
    for name,want in audit['source_pins'].items():assert pin(ROOT/name)==want
    return dict(status='passed-966-role-context-with-unchanged-scientific-gates',current_reviews=966,prior_reviews_preserved=960,new_reviews=6,original_gates=results,scientific_sources_or_frozen_verifiers_changed=False,whole_goal_complete=False)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact context checks')
    print(json.dumps(verify(),indent=2))
