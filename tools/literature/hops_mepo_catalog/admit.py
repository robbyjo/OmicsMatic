#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit exactly one scoped HOPS operation, preserving all primary evidence."""
from pathlib import Path
import argparse,json,sys,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from hops_mepo_catalog.model import ROOT,DATA,INTEGRATION,WORKFLOW,META,pin,rawpin,encoded,metadata,qualification,transition,check_transition
from hops_mepo_catalog.bridge import prior_admission
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--audit',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 audit=json.loads(a.audit.read_bytes());assert audit['status']=='PASS250_CURRENT_PRIMARY_SOURCES_FOR_SCOPED_HOPS_ADMISSION' and not audit['primary_files_overridden'] and not audit['scientific_sources_overridden']
 assert (audit['primary_audit']['source_papers'],audit['primary_audit']['observations'],audit['primary_audit']['reviewed_uses'])==(250,2479,1052)
 for name,value in audit['primary_input_pins'].items():assert pin(ROOT/name)==value,name
 before={n:(ROOT/n).read_bytes() for n in META};prior_admission(ROOT,before);v,public,comp=qualification(ROOT)
 protected_names=[DATA+'/papers.json','tools/literature/reviewed-uses.json','build/cli/omicsmatic-0.3.7.jar',DATA+'/maltextract17-adapter-validation.json']
 protected={n:pin(ROOT/n) for n in protected_names};entry=metadata(ROOT);after=transition(before,entry);check_transition(before,after,entry)
 integration=ROOT/INTEGRATION;archive=integration/'catalog-before.zip';assert not archive.exists()
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in before.items():z.writestr(name,raw)
  z.writestr(DATA+'/SHA256SUMS',(ROOT/DATA/'SHA256SUMS').read_bytes());z.writestr('audit/COMPLETED.json',a.audit.read_bytes())
 for name,raw in after.items():(ROOT/name).write_bytes(raw)
 papers=json.loads((ROOT/DATA/'papers.json').read_bytes());paper=next(p for p in papers if p['paper_id']=='P002');use=next(t for t in paper['tools'] if t['tool_id']=='hops')
 assert use['evidence_paragraph_index']==29 and use['evidence_paragraph_sha256']=='1f645f2ce00682d71111e245d3ea612de2a76106f25d7db551d4f4c3e88b1501' and use['version_reported_near_name'] is None
 role=dict(status='PASS_ONE_PRIMARY_HOPS_CANDIDATE_ASSESSMENT_ROLE_SCOPED',paper_id='P002',pmcid=paper['pmcid'],doi=paper['doi'],source_observation=use,
  declared_distribution_version='0.35',launcher_reported_version='0.33',declared_MaltExtract_version='1.7',declared_R_version='4.0.5',paper_version_reported=False,paper_settings_recovered=False,
  operations=['original target extraction from classified MALT RMA6','original summary/damage tables and candidate-profile PDFs','original heatmap/TSV/RData scientific-field checks'],
  manual_authentication_automated=False,historical_database_reconstructed=False,MALT_index_alignment_included=False,whole_package_qualified=False,whole_paper_reproduced=False,
  preserved_primary_scope_note_is_historical=True,current_scope=entry['implementation_scope'],new_scoped_integration_credit=1)
 (ROOT/DATA/'hops-mepo-adapter-role-coverage.json').write_bytes(encoded(role))
 scripts={p.relative_to(ROOT).as_posix():pin(p) for p in sorted((ROOT/'tools/literature/hops_mepo_catalog').glob('*.py'))}
 proof=dict(status='PASS_SCOPED_HOPS_MEPO_UPSTREAM_ADAPTER_ADMISSION',before_archive=pin(archive),before_entry=next(t for t in json.loads(before[META[0]]) if t['id']=='hops'),after_entry=next(t for t in json.loads(after[META[0]]) if t['id']=='hops'),after_files={n:pin(ROOT/n) for n in META},protected=protected,
  workflow_validation=pin(ROOT/WORKFLOW/'VALIDATION.json'),workflow_archive=v['archive'],role_coverage=pin(ROOT/DATA/'hops-mepo-adapter-role-coverage.json'),primary_source_audit=audit,
  source_pins={**v['sources'],**scripts,'docs/literature/HOPS_INTEGRATION.md':pin(ROOT/'docs/literature/HOPS_INTEGRATION.md')},
  candidates={edition:pin(ROOT/WORKFLOW/('hops-mepo-jdk'+edition+'.jar')) for edition in ['21','25']},old_counts=json.loads(before[META[1]])['implementation_status_counts'],new_counts=json.loads(after[META[1]])['implementation_status_counts'],changed_tool_ids=['hops'],unchanged_observations=2479,unchanged_reviews=1052,unchanged_named_tools=405,new_scoped_integration_credit=1,whole_tool_credit=0,whole_goal_complete=False)
 (ROOT/DATA/'hops-mepo-adapter-validation.json').write_bytes(encoded(proof));(out/'COMPLETED.json').write_bytes(encoded(proof))
 # Only changed/new derived entries receive new manifest identities.
 manifest=ROOT/DATA/'SHA256SUMS';updates={'tool-inventory.json','summary.json','README.md','WARNINGS.md','hops-mepo-adapter-validation.json','hops-mepo-adapter-role-coverage.json'};lines=[];seen=set()
 for line in manifest.read_text(encoding='utf8').splitlines():
  digest,name=line.split('  ',1);actual=pin(ROOT/DATA/name)['sha256']
  if name not in updates:assert actual==digest,('Unrelated catalog member changed',name)
  lines.append(actual+'  '+name);seen.add(name)
 for name in sorted(updates-seen):lines.append(pin(ROOT/DATA/name)['sha256']+'  '+name)
 manifest.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
 for name,value in protected.items():assert pin(ROOT/name)==value,name
 print('Admitted one scoped HOPS operation;101 new scoped integrations;245 missing;full goal active',flush=True)
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete admission checks')
 raise SystemExit(main())
