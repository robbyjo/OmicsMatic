# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay exactly eight historical metadata inputs; scientific code stays live."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from velociraptor_catalog.model import META,DATA,EVIDENCE,rawpin

def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only exact eight metadata inputs may be replayed; scientific overlays forbidden')
 return ArchivedCatalogView(root,before)

def load_before(root):
 expected=json.loads((root/DATA/'scvelo-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as archive:
  if len(archive.namelist())!=8 or set(archive.namelist())!=set(META):raise ValueError('Exact eight-path predecessor archive required')
  before={n:archive.read(n) for n in META}
 if {n:rawpin(v) for n,v in before.items()}!=expected:raise ValueError('Exact admitted scVelo predecessor differs')
 return before

def prior_admission(root,before):
 from scvelo_catalog.verify import verify
 result=verify(metadata_view(root,before))
 if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,433,2672,1168,111,263):raise ValueError('Preserved predecessor scope differs')
 return result
