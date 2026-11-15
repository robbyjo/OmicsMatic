# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay only the eight complete predecessor metadata inputs; overlay no science."""
import json,zipfile
from bigcat_catalog.bridge import metadata_view
from bigcat_catalog.model import META,DATA,rawpin

EVIDENCE='docs/literature/hicat109-catalog-integration'

def predecessor_pins(root):
    pins=json.loads((root/DATA/'bigcat-catalog-validation.json').read_bytes())['after_files']
    if set(pins)!=set(META):raise ValueError('Exactly eight complete preceding metadata pins required')
    return pins

def load_before(root):
    with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as archive:
        if len(archive.namelist())!=8 or set(archive.namelist())!=set(META):raise ValueError('Only eight exact metadata files may be replayed')
        before={name:archive.read(name) for name in META}
    if {n:rawpin(v) for n,v in before.items()}!=predecessor_pins(root):raise ValueError('The complete preceding BIGCAT catalog differs')
    return before

def prior_admission(root,before,rescan=False,runtime2023=None,runtime2022=None,arrow_runtime=None,
    mapping_runtime=None,doubletfinder_runtime=None,wsl_distro=None):
    from bigcat_catalog.catalog import verify
    result=verify(metadata_view(root,before),rescan=rescan,runtime2023=runtime2023,runtime2022=runtime2022,
      arrow_runtime=arrow_runtime,mapping_runtime=mapping_runtime,doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,438,2679,1180,116,263):
        raise ValueError('Complete preceding BIGCAT catalog scope differs')
    return result
