#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all current primary anchors with strict scientific gates and reviewed metadata predecessors."""
from pathlib import Path
import argparse,json,sys,traceback
TOOLS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS/'maltextract17_catalog'));sys.path.insert(0,str(TOOLS))
from hops_mepo_catalog.model import ROOT,DATA,META,pin,rawpin
from hops_mepo_catalog.bridge import predecessor,prior_admission
from maltextract17_catalog.dispatcher import reviewed_root
from maltextract17_catalog.audit_snapshot import BLOCK,README_BEFORE,README_AFTER
from run_mrmega_catalog_checks import ArchivedCatalogView
import audit_malt_source_catalog as inherited

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--after',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 state=dict(status='FAIL2_HOPS_CURRENT_PRIMARY_SOURCE_AUDIT',whole_goal_complete=False)
 try:
  if a.after:root,review=predecessor()
  else:root=ROOT;review=dict(paths=[],primary_files_overridden=False,scientific_sources_overridden=False)
  before={name:(root/name).read_bytes() for name in META};old=prior_admission(ROOT,before)
  view,dispatch=reviewed_root(root);readme=(ROOT/'README.md').read_bytes()
  if rawpin(readme)['sha256']!=README_AFTER or readme.count(BLOCK)!=1:raise ValueError('README is outside the existing exact reviewed native-guide transition')
  restored=readme.replace(BLOCK,b'',1)
  if rawpin(restored)['sha256']!=README_BEFORE:raise ValueError('Existing exact README predecessor did not reconstruct')
  view=ArchivedCatalogView(view,{'README.md':restored})
  papers=json.loads((ROOT/DATA/'papers.json').read_bytes());protected=['docs/literature/omics-2023-2026/papers.json','tools/literature/reviewed-uses.json','build/cli/omicsmatic-0.3.7.jar']
  cache=[f'build/literature-review/{kind}/{p["pmcid"]}{suffix}' for p in papers for kind,suffix in [('xml','.xml.gz'),('extracted','.json')]]
  identities={n:pin(ROOT/n) for n in protected+cache}
  saved_root,saved_argv=inherited.ROOT,sys.argv
  try:inherited.ROOT=view;sys.argv=[inherited.__file__,'--out',str(out/'primary')];code=inherited.main()
  finally:inherited.ROOT=saved_root;sys.argv=saved_argv
  result=json.loads((out/'primary/COMPLETED.json').read_bytes())
  if code or result['source_papers']!=250 or result['observations']!=2479 or result['reviewed_uses']!=1052:raise ValueError('Full unchanged primary-source/scientific audit refused; inspect primary diagnostics')
  if any(pin(ROOT/n)!=v for n,v in identities.items()):raise ValueError('Live primary/canonical inputs changed during audit')
  state.update(status='PASS250_CURRENT_PRIMARY_SOURCES_FOR_SCOPED_HOPS_ADMISSION',primary_audit=result,previous_admission=old,primary_input_pins=identities,derived_metadata_predecessor=review,existing_reviewed_dispatch_predecessor=dispatch,existing_readme_predecessor=dict(before=rawpin(restored),after=rawpin(readme)),primary_files_overridden=False,scientific_sources_overridden=False,scientific_verifiers_changed=False,paper_wide_semantic_review_open=True,new_tool_credit=0)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc(),required_action='Retain every diagnostic. Supply matching complete source/evidence and resolve the named failure; never waive scientific hashes or replace primary records to admit a tool.')
 (out/'COMPLETED.json').write_text(json.dumps(state,indent=2)+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable full primary/source checks')
 raise SystemExit(main())
