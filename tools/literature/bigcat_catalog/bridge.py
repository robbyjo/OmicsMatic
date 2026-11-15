# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay exact preceding metadata only; scientific code/evidence cannot be overlaid."""
import json,zipfile
from arrow_catalog.bridge import metadata_view
from arrow_catalog.model import META,DATA,rawpin

EVIDENCE='docs/literature/bigcat-catalog-integration'

def predecessor_pins(root):
    pins=json.loads((root/DATA/'arrow-catalog-validation.json').read_bytes())['after_files']
    if set(pins)!=set(META):raise ValueError('Exactly eight complete preceding metadata pins required')
    return pins

def load_before(root):
    with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as archive:
        if len(archive.namelist())!=8 or set(archive.namelist())!=set(META):raise ValueError('Only eight exact metadata files may be replayed')
        before={name:archive.read(name) for name in META}
    if {n:rawpin(v) for n,v in before.items()}!=predecessor_pins(root):raise ValueError('The complete R Arrow predecessor differs')
    return before

def prior_admission(root,before,rescan=False,arrow_runtime=None,mapping_runtime=None,doubletfinder_runtime=None,wsl_distro=None):
    from arrow_catalog.catalog import verify
    from bigcat_catalog.dispatcher import private_help_fixture
    with private_help_fixture(root) as (view,help_review):
        result=verify(metadata_view(view,before),rescan=rescan,runtime=arrow_runtime,prior_runtime=mapping_runtime,
          doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,438,2679,1180,115,264):
        raise ValueError('Complete preceding Arrow catalog scope differs')
    return dict(result,help_only_dispatcher_review=dict(help_review,private_source_restored_exactly=True))
