# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay only eight exact preceding metadata paths; never scientific source files."""
import json,zipfile
from scrattchmapping_catalog.bridge import metadata_view
from scrattchmapping_catalog.model import META,DATA,rawpin

EVIDENCE='docs/literature/arrow-catalog-integration'

def predecessor_pins(root):
    expected=json.loads((root/DATA/'scrattchmapping-catalog-validation.json').read_bytes())['after_files']
    if set(expected)!=set(META):raise ValueError('Exactly eight preceding catalog metadata pins required')
    return expected

def load_before(root):
    with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as archive:
        if len(archive.namelist())!=8 or set(archive.namelist())!=set(META):
            raise ValueError('Only eight exact preceding metadata inputs may be replayed')
        before={name:archive.read(name) for name in META}
    if {n:rawpin(v) for n,v in before.items()}!=predecessor_pins(root):
        raise ValueError('Complete preceding mapping catalog metadata differs')
    return before

def prior_admission(root,before,rescan=False,runtime=None,doubletfinder_runtime=None,wsl_distro=None):
    from scrattchmapping_catalog.catalog import verify
    result=verify(metadata_view(root,before),rescan=rescan,runtime=runtime,
                  prior_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,438,2679,1180,114,265):
        raise ValueError('Complete preceding mapping catalog scope differs')
    return result
