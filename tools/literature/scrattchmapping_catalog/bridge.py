# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Only the eight exact preceding catalog metadata files may be replayed."""
import json, zipfile
from doubletfinder_catalog.bridge import metadata_view
from doubletfinder_catalog.model import META, DATA, rawpin

EVIDENCE = 'docs/literature/scrattchmapping-catalog-integration'


def predecessor_pins(root):
    expected = json.loads((root / DATA / 'doubletfinder-catalog-validation.json').read_bytes())['after_files']
    if set(expected) != set(META):
        raise ValueError('Exactly eight preceding catalog metadata pins required')
    return expected


def load_before(root):
    with zipfile.ZipFile(root / EVIDENCE / 'catalog-before.zip') as archive:
        if len(archive.namelist()) != 8 or set(archive.namelist()) != set(META):
            raise ValueError('Only eight exact predecessor metadata inputs may be replayed')
        before = {name: archive.read(name) for name in META}
    if {name: rawpin(raw) for name, raw in before.items()} != predecessor_pins(root):
        raise ValueError('The complete exact DoubletFinder predecessor differs')
    return before


def prior_admission(root, before, rescan=False, runtime=None, wsl_distro=None):
    from doubletfinder_catalog.catalog import verify
    result = verify(metadata_view(root, before), rescan=rescan, runtime=runtime, wsl_distro=wsl_distro)
    if tuple(result[k] for k in ['papers', 'named_tools', 'observations', 'reviewed_uses', 'new_scoped_integrations', 'missing']) != (250, 438, 2679, 1180, 113, 266):
        raise ValueError('Complete preceding catalog scope differs')
    return result
