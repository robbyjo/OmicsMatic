#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit exactly one scoped complete MALT DNA workflow after the full primary audit."""
from pathlib import Path
import argparse,json,sys,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from malt052_catalog.model import ROOT,DATA,INTEGRATION,WORKFLOW,META,pin,encoded,metadata,qualification,transition,check_transition
from malt052_catalog.bridge import prior_admission
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--audit',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 audit=json.loads(a.audit.read_bytes());assert audit['status']=='PASS250_CURRENT_PRIMARY_SOURCES_FOR_SCOPED_MALT052_ADMISSION' and not audit['primary_files_overridden'] and not audit['scientific_sources_overridden'];assert (audit['primary_audit']['source_papers'],audit['primary_audit']['observations'],audit['primary_audit']['reviewed_uses'])==(250,2479,1052)
 for name,value in audit['primary_input_pins'].items():assert pin(ROOT/name)==value,name
 before={n:(ROOT/n).read_bytes() for n in META};prior_admission(ROOT,before);v,public=qualification(ROOT)
 protected_names=[DATA+'/papers.json','tools/literature/reviewed-uses.json','build/cli/omicsmatic-0.3.7.jar',DATA+'/hops-mepo-adapter-validation.json'];protected={n:pin(ROOT/n) for n in protected_names};entry=metadata(ROOT);after=transition(before,entry);check_transition(before,after,entry)
 integration=ROOT/INTEGRATION;archive=integration/'catalog-before.zip';assert not archive.exists()
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in before.items():z.writestr(name,raw)
  z.writestr(DATA+'/SHA256SUMS',(ROOT/DATA/'SHA256SUMS').read_bytes());z.writestr('audit/COMPLETED.json',a.audit.read_bytes())
 for name,raw in after.items():(ROOT/name).write_bytes(raw)
 papers=json.loads((ROOT/DATA/'papers.json').read_bytes());paper=next(p for p in papers if p['paper_id']=='P002');use=next(t for t in paper['tools'] if t['tool_id']=='malt')
 assert use['evidence_paragraph_index']==29 and use['evidence_paragraph_sha256']=='1f645f2ce00682d71111e245d3ea612de2a76106f25d7db551d4f4c3e88b1501' and use['version_reported_near_name'] is None
 assert len(use['supplementary_evidence'])==4 and all(r['document_sha256']=='cefa7be7b2bb5313cce9ddb23d5369020c2ef74a90b99daf78a889270db23451' for r in use['supplementary_evidence'])
 role=dict(status='PASS_TWO_PRIMARY_MALT_DNA_SCREENING_ROLES_SCOPED',paper_id='P002',pmcid=paper['pmcid'],doi=paper['doi'],source_observation=use,main_pipeline_version_recovered=False,supplement_reported_custom_version='0.5.2',declared_adapter_version='0.5.2',declared_mapping_method='native-accession-alternative',original_accession_mapping_qualified=False,
  source_roles=[dict(role='main eager/HOPS ancient-pathogen screen',version_reported=False,operations=['DNA reference index input','BlastN screening and naive taxonomic classification'],dated_database_reproduced=False),dict(role='custom91-spirochete reference index construction and36214-dataset screen',version='0.5.2',supplement_paragraphs=[235,236,237,238],represented_settings=dict(alignment='SemiGlobal',minimum_identity_percent=90,maximum_alignments_per_query=25,maximum_alignments_per_reference=100000),operations=['owned original DNA seed building','explicit separate native accession mapping','original complete-query BlastN with SAM/RMA/aligned FASTA','native complete-index/geometry/naive-assignment audits'])],
  historical_database_reconstructed=False,paper_resource_request_executed=False,paper_resource_request_representable=True,paper_resource_request=dict(heap_mib=133120,threads=16),whole_package_qualified=False,whole_paper_reproduced=False,biological_authentication_automated=False,preserved_primary_scope_note_is_historical=True,current_scope=entry['implementation_scope'],new_scoped_integration_credit=1)
 (ROOT/DATA/'malt052-adapter-role-coverage.json').write_bytes(encoded(role));scripts={p.relative_to(ROOT).as_posix():pin(p) for p in sorted((ROOT/'tools/literature/malt052_catalog').glob('*.py'))}
 proof=dict(status='PASS_SCOPED_MALT052_BUILD_BLASTN_UPSTREAM_ADAPTER_ADMISSION',before_archive=pin(archive),before_entry=next(t for t in json.loads(before[META[0]]) if t['id']=='malt'),after_entry=next(t for t in json.loads(after[META[0]]) if t['id']=='malt'),after_files={n:pin(ROOT/n) for n in META},protected=protected,workflow_validation=pin(ROOT/WORKFLOW/'VALIDATION.json'),workflow_archive=v['archive'],role_coverage=pin(ROOT/DATA/'malt052-adapter-role-coverage.json'),primary_source_audit=audit,
  source_pins={**v['sources'],**scripts,'docs/literature/MALT052_INTEGRATION.md':pin(ROOT/'docs/literature/MALT052_INTEGRATION.md')},candidates={edition:pin(ROOT/WORKFLOW/('malt052-workflow-jdk'+edition+'.jar')) for edition in ['21','25']},old_counts=json.loads(before[META[1]])['implementation_status_counts'],new_counts=json.loads(after[META[1]])['implementation_status_counts'],changed_tool_ids=['malt'],unchanged_observations=2479,unchanged_reviews=1052,unchanged_named_tools=405,new_scoped_integration_credit=1,whole_tool_credit=0,whole_goal_complete=False)
 (ROOT/DATA/'malt052-adapter-validation.json').write_bytes(encoded(proof));(out/'COMPLETED.json').write_bytes(encoded(proof))
 manifest=ROOT/DATA/'SHA256SUMS';updates={'tool-inventory.json','summary.json','README.md','WARNINGS.md','malt052-adapter-validation.json','malt052-adapter-role-coverage.json'};lines=[];seen=set()
 for line in manifest.read_text(encoding='utf8').splitlines():
  digest,name=line.split('  ',1);actual=pin(ROOT/DATA/name)['sha256']
  if name not in updates:assert actual==digest,('Unrelated catalog member changed',name)
  lines.append(actual+'  '+name);seen.add(name)
 for name in sorted(updates-seen):lines.append(pin(ROOT/DATA/name)['sha256']+'  '+name)
 manifest.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
 for name,value in protected.items():assert pin(ROOT/name)==value,name
 print('Admitted one scoped MALT workflow;102 new scoped integrations;244 missing;full goal active',flush=True)
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete admission checks')
 raise SystemExit(main())
