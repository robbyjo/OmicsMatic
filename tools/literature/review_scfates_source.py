# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review exact official sources and the generated version-only difference before original runtime execution."""
import argparse,ast,copy,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def version_neutral(raw):
 tree=ast.parse(raw)
 for item in tree.body:
  if isinstance(item,ast.Assign) and all(isinstance(t,ast.Name) and t.id in {'__version__','version','__version_tuple__','version_tuple'} for t in item.targets):item.value=ast.Constant(value=None)
 return ast.dump(tree,include_attributes=False)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();intake=a.intake.resolve();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);source=json.loads((intake/'SOURCE.json').read_bytes());assert source['status']=='PASS_OFFICIAL_SCFATES_SIMPLEPPT_SOURCE_ONLY';differences=[];modules=0
 for name,package in source['packages'].items():
  for member,want in package['wheel_members'].items():assert pin(intake/name/'wheel-source'/member)==want
  for member,want in package['git_source_members'].items():assert pin(intake/name/'git-source'/member)=={k:want[k] for k in ['bytes','sha256']}
  for member,comparison in package['scientific_source_comparison'].items():
   modules+=1
   if comparison['status']=='identical-wheel-and-tag':continue
   assert name=='simpleppt' and member=='simpleppt/_version.py' and comparison['git_candidates']==[member];wheel=intake/name/'wheel-source'/member;git=intake/name/'git-source'/member;assert version_neutral(wheel.read_text())==version_neutral(git.read_text());differences.append(dict(package=name,member=member,wheel=pin(wheel),git_tag=pin(git),classification='setuptools-scm generated version and version_tuple constants only; retain both unchanged; execute the original wheel',scientific_function_changed=False))
  for member in package['license_members']:
   raw=(intake/name/'wheel-source'/member).read_text();assert raw.startswith('BSD 3-Clause License') and 'Redistribution and use in source and binary forms' in raw
 assert len(differences)==1;save(out/'COMPLETED.json',dict(status='PASS_SCFATES_SIMPLEPPT_SCIENTIFIC_SOURCES_AND_GENERATED_VERSION_REVIEW_ONLY',source=pin(intake/'SOURCE.json'),python_modules=modules,generated_metadata_differences=differences,licenses_retained=['scFates BSD-3-Clause','SimplePPT BSD-3-Clause'],original_function_patched=False,software_installed=False,implementation_credit=False,whole_goal_complete=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))}));print('Reviewed',modules,'original modules; one retained generated version-only difference; no scientific source replacement')
if __name__=='__main__':main()
