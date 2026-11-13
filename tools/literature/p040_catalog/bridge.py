# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact predecessor metadata; scientific code and controls always remain live."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from p040_catalog.model import META,DATA,EVIDENCE,rawpin
def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Scientific or additional metadata overlays are forbidden')
 return ArchivedCatalogView(root,before)
def load_before(root):
 expected=json.loads((root/DATA/'batchelor129-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Predecessor must contain exactly eight metadata paths')
  before={n:z.read(n) for n in META}
 if any(rawpin(before[n])!=expected[n] for n in META):raise ValueError('Not the exact admitted407-tool predecessor')
 return before
def prior_admission(root,before):
 from batchelor129_catalog.verify import verify
 result=verify(metadata_view(root,before))
 if (result['papers'],result['named_tools'],result['observations'],result['reviewed_uses'],result['new_scoped_integrations'],result['missing'])!=(250,407,2482,1055,103,245):raise ValueError('Preserved predecessor admission differs')
 return result
