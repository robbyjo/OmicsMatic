# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact eight-path historical catalog view; all scientific sources remain live."""
import json,sys,zipfile
from pathlib import Path
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor129_catalog.model import DATA,EVIDENCE,META,rawpin
from run_mrmega_catalog_checks import ArchivedCatalogView

def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only the eight reviewed metadata paths may be overlaid; scientific paths are forbidden')
 return ArchivedCatalogView(root,before)

def load_before(root):
 expected=json.loads((root/DATA/'batchelor-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Predecessor archive membership differs')
  before={n:z.read(n) for n in META}
 if set(expected)!=set(META) or any(rawpin(before[n])!=expected[n] for n in META):raise ValueError('Predecessor differs from admitted Batchelor102 catalog')
 return before

def prior_admission(root,before):
 from batchelor120_catalog.verify import verify
 v=verify(metadata_view(root,before))
 if (v['papers'],v['named_tools'],v['observations'],v['reviewed_uses'],v['new_scoped_integrations'],v['missing'])!=(250,406,2481,1054,102,245):raise ValueError('Preserved predecessor admission differs')
 return v
