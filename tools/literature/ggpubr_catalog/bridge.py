# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay the exact predecessor metadata while all scientific source remains live."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from ggpubr_catalog.model import META,DATA,EVIDENCE,rawpin
def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only the eight catalog metadata paths may be overlaid; scientific overlays are forbidden')
 return ArchivedCatalogView(root,before)
def load_before(root):
 wanted=json.loads((root/DATA/'rstatix-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Exact eight-path predecessor required')
  before={n:z.read(n) for n in META}
 if any(rawpin(before[n])!=wanted[n] for n in META):raise ValueError('Predecessor differs from admitted rstatix catalog')
 return before
def prior_admission(root,before):
 from rstatix_catalog.verify import verify
 result=verify(metadata_view(root,before))
 if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,432,2671,1148,108,265):raise ValueError('Preserved predecessor admission differs')
 return result
