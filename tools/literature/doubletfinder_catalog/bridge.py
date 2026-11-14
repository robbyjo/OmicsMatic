# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Only eight exact predecessor catalog metadata inputs may be replayed."""
import json, zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from velociraptor_catalog.model import META, DATA, rawpin

EVIDENCE = 'docs/literature/doubletfinder-catalog-integration'


def metadata_view(root, before):
    if set(before) != set(META):
        raise ValueError('Exactly eight catalog metadata paths required; scientific overlays forbidden')
    return ArchivedCatalogView(root, before)


def predecessor_pins(root):
    expected = json.loads((root / DATA / 'velociraptor-catalog-validation.json').read_bytes())['after_files']
    layout = json.loads((root / 'docs/literature/velociraptor-catalog-ui-revision2/VALIDATION.json').read_bytes())
    expected.update({n: p for n, p in layout['after_files'].items() if n in META})
    if set(expected) != set(META):
        raise ValueError('Complete exact current predecessor hashes required')
    return expected


def load_before(root):
    with zipfile.ZipFile(root / EVIDENCE / 'catalog-before.zip') as archive:
        if len(archive.namelist()) != 8 or set(archive.namelist()) != set(META):
            raise ValueError('Only eight exact catalog inputs may be replayed')
        before = {n: archive.read(n) for n in META}
    if {n: rawpin(raw) for n, raw in before.items()} != predecessor_pins(root):
        raise ValueError('Current historical predecessor differs')
    return before


def prior_admission(root, before, rescan=False):
    from velociraptor_catalog.ui_revision2 import verify
    result = verify(metadata_view(root, before), rescan=rescan)
    if tuple(result[k] for k in ['papers', 'named_tools', 'observations', 'reviewed_uses', 'new_scoped_integrations', 'missing']) != (250, 433, 2672, 1168, 112, 262):
        raise ValueError('The complete predecessor scope changed')
    return result
