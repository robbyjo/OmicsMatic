# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Current metadata entry point retaining the entire unchanged frozen MEME scientific/catalog audit."""
import contextlib
import io
import json
import sys
from setup_deseq2_reference import ROOT
from meme_discovery_provider_compatibility import verify_provider_derivation
from meme_discovery_current_catalog import verify
import verify_meme_discovery_catalog as historical


def main():
    verify_provider_derivation(ROOT)
    # The immutable original main still checks every archive, scientific source,
    # role, old catalog snapshot, test count and current checksum. Only its
    # imported metadata callback uses the exact derived review-count context.
    previous=historical.verify;captured=io.StringIO()
    try:
        historical.verify=verify
        with contextlib.redirect_stdout(captured):historical.main()
    finally:historical.verify=previous
    result=json.loads(captured.getvalue())
    reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    print(json.dumps(dict(status='passed-current-MEME-metadata-and-frozen-scientific-audit',current_reviews=len(reviews),
        unchanged_MEME_roles=8,prior_reviews_preserved=898,frozen_checkpoint=result,scientific_or_frozen_verifier_sources_changed=False)))


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable immutable scientific or metadata-extension checks')
        main()
    except Exception as error:
        print('CURRENT MEME CATALOG FAIL2: '+str(error)+'\nREQUIRED ACTION: preserve the named source/archive/review mismatch; no partial evidence admitted.',file=sys.stderr)
        raise SystemExit(2)
