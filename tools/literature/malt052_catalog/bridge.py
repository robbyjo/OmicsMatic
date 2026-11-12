# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact HOPS101 predecessor; scientific/primary paths always stay live."""
from pathlib import Path
import json,sys,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS/'maltextract17_catalog'));sys.path.insert(0,str(TOOLS))
from malt052_catalog.model import ROOT,DATA,INTEGRATION,META,pin,rawpin,metadata,check_transition
from run_mrmega_catalog_checks import ArchivedCatalogView
from hops_mepo_catalog import verify as prior
def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only six exact derived metadata paths may have predecessors')
 return ArchivedCatalogView(root,before)
def protected(root,expected):
 for n,value in expected.items():
  if pin(root/n)!=value:raise ValueError('Protected source/evidence changed: '+n)
def prior_admission(root,before):
 result=prior.verify(metadata_view(root,before))
 if result['status']!='PASS_ONE_SCOPED_HOPS_ADMISSION_WITH_PRESERVED_PRIMARY_AND_SCIENTIFIC_EVIDENCE' or result['new_scoped_integrations']!=101 or result['missing']!=245:raise ValueError('Unchanged previous HOPS/scientific admission failed')
 return result
def predecessor(root=ROOT):
 proof=json.loads((root/DATA/'malt052-adapter-validation.json').read_bytes());archive=root/INTEGRATION/'catalog-before.zip'
 if pin(archive)!=proof['before_archive']:raise ValueError('Exact MALT predecessor archive differs')
 with zipfile.ZipFile(archive) as z:before={n:z.read(n) for n in META}
 old=json.loads((root/DATA/'hops-mepo-adapter-validation.json').read_bytes())
 if set(old['after_files'])!=set(META) or any(rawpin(before[n])!=old['after_files'][n] for n in META):raise ValueError('MALT predecessor differs from exact admitted HOPS101 state')
 protected(root,proof['protected']);current={n:(root/n).read_bytes() for n in META};review=check_transition(before,current,metadata(root))
 return metadata_view(root,before),dict(paths=list(META),before={n:rawpin(raw) for n,raw in before.items()},transition=review,primary_files_overridden=False,scientific_sources_overridden=False)
