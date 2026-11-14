# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay only exact historical catalog metadata; keep every scientific source and verifier live."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from cell2location_catalog.model import META,DATA,EVIDENCE,rawpin
def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only exact eight historical catalog metadata inputs may be replayed; scientific overlays forbidden')
 return ArchivedCatalogView(root,before)
def load_before(root):
 expected=json.loads((root/DATA/'rstatix-revision2-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Exact eight-path predecessor archive required')
  before={n:z.read(n) for n in META}
 if {n:rawpin(v) for n,v in before.items()}!=expected:raise ValueError('Exact admitted rstatix revision2 predecessor differs')
 return before
def prior_admission(root,before):
 from rstatix_revision2_catalog.verify import verify
 result=verify(metadata_view(root,before))
 if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,432,2671,1156,109,264):raise ValueError('Preserved predecessor scope differs')
 return result
