# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the exact eight predecessor metadata inputs; keep scientific sources live."""
import json, zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from palette_allelic_catalog.model import META, DATA, EVIDENCE, rawpin

def metadata_view(root, before):
    if set(before) != set(META):
        raise ValueError('Only the eight catalog metadata paths may be overlaid; scientific overlays are forbidden')
    return ArchivedCatalogView(root, before)

def load_before(root):
    expected = json.loads((root / DATA / 'imagej-catalog-validation.json').read_bytes())['after_files']
    with zipfile.ZipFile(root / EVIDENCE / 'catalog-before.zip') as archive:
        if len(archive.namelist()) != 8 or set(archive.namelist()) != set(META):
            raise ValueError('Exact eight-path predecessor required')
        before = {n:archive.read(n) for n in META}
    if any(rawpin(before[n]) != expected[n] for n in META):
        raise ValueError('Predecessor differs from admitted430-family catalog')
    return before

def prior_admission(root, before):
    from imagej_catalog.verify import verify
    result = verify(metadata_view(root, before))
    if (result['papers'],result['named_tools'],result['observations'],result['reviewed_uses'],result['new_scoped_integrations'],result['missing']) != (250,430,2669,1130,105,266):
        raise ValueError('Preserved predecessor admission differs')
    return result
