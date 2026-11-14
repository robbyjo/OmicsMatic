# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve the exact eight predecessor metadata inputs; never overlay scientific files."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from rstatix_revision2_catalog.model import META,DATA,EVIDENCE,rawpin

def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only eight historical catalog metadata paths may be replayed; scientific overlays forbidden')
 return ArchivedCatalogView(root,before)

def load_before(root):
 expected=json.loads((root/DATA/'ggpubr-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Exact eight-path predecessor required')
  before={n:z.read(n) for n in META}
 if {n:rawpin(v) for n,v in before.items()}!=expected:raise ValueError('Predecessor differs from admitted ggpubr catalog')
 return before
