# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the unchanged cumulative gate and live scientific sources; overlay eight metadata files only."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from qupath_catalog.model import META,DATA,EVIDENCE,rawpin

def metadata_view(root,before):
    if set(before) != set(META):
        raise ValueError('Only the eight catalog metadata paths may be overlaid; scientific overlays are forbidden')
    return ArchivedCatalogView(root,before)

def load_before(root):
    expected = json.loads((root / DATA / 'palette-allelic-catalog-validation.json').read_bytes())['after_files']
    with zipfile.ZipFile(root / EVIDENCE / 'catalog-before.zip') as archive:
        if len(archive.namelist()) != 8 or set(archive.namelist()) != set(META): raise ValueError('Exact eight-path predecessor required')
        before = {n:archive.read(n) for n in META}
    if any(rawpin(before[n]) != expected[n] for n in META): raise ValueError('Predecessor differs from admitted 432-family catalog')
    return before

def prior_admission(root,before):
    from palette_allelic_catalog.verify import verify
    result = verify(metadata_view(root,before))
    if (result['papers'],result['named_tools'],result['observations'],result['reviewed_uses'],result['new_scoped_integrations'],result['missing']) != (250,432,2671,1132,106,267):
        raise ValueError('Preserved predecessor admission differs')
    return result
