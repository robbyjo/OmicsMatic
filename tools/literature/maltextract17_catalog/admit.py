#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one explicitly scoped MaltExtract adapter; preserve all paper evidence."""
from pathlib import Path
from collections import Counter
import argparse,copy,json,re,shutil,zipfile,sys
from metadata import qualification,metadata,pin,WARNING,INTEGRATION
from dispatcher import TOOLS
from setup_deseq2_reference import ROOT,save

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--audit',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 data=ROOT/'docs/literature/omics-2023-2026';integration=ROOT/INTEGRATION;validation,replays=qualification(ROOT);audit=json.loads(a.audit.read_bytes())
 assert audit['status']=='PASS250_CURRENT_PRIMARY_SOURCES_WITH_REVIEWED_DISPATCH_AND_DOCS' and audit['primary_audit']['source_papers']==250 and audit['primary_audit']['observations']==2479 and audit['primary_audit']['reviewed_uses']==1052 and not audit['scientific_source_files_overridden'] and not audit['primary_files_overridden']
 protected={n:pin(ROOT/n) for n in ['docs/literature/omics-2023-2026/papers.json','tools/literature/reviewed-uses.json','build/cli/omicsmatic-0.3.7.jar']}
 inventory=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes());assert len(inventory)==405 and summary['implementation_status_counts']['upstream-adapter-validated']==80 and summary['implementation_status_counts']['missing']==247
 before=next(t for t in inventory if t['id']=='maltextract');assert before['implementation_status']=='missing' and before['papers']==['P002']
 page=ROOT/'site/literature-review.html';html=page.read_text(encoding='utf8');match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',html,re.S);assert match;embedded=json.loads(match[2]);assert embedded['tools']==inventory and embedded['summary']==summary
 current_papers=json.loads((data/'papers.json').read_bytes());scope_updates=[];assert len(embedded['papers'])==len(current_papers)==250
 for previous,current in zip(embedded['papers'],current_papers,strict=True):
  expected=copy.deepcopy(previous);assert len(expected['tools'])==len(current['tools'])
  for left,right in zip(expected['tools'],current['tools'],strict=True):
   assert left['tool_id']==right['tool_id']
   if left.get('implementation_scope_note')!=right.get('implementation_scope_note'):
    scope_updates.append(dict(paper_id=current['paper_id'],tool_id=right['tool_id'],before=left.get('implementation_scope_note'),after=right.get('implementation_scope_note')));left['implementation_scope_note']=right['implementation_scope_note']
  assert expected==current,'Only reviewed implementation-scope notes may be synchronized'
 assert len(scope_updates)==10
 preserved=['docs/literature/omics-2023-2026/'+n for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']]+['site/literature-review.html','site/literature-warnings.html']
 archive=integration/'catalog-before.zip';assert not archive.exists()
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name in preserved:z.writestr(name,(ROOT/name).read_bytes())
  for name in ['COMPLETED.json','SNAPSHOT.json','PRIMARY_AUDIT.json']:z.writestr('audit/'+name,(a.audit.parent/name).read_bytes())
  for name in ['audit.py','audit_snapshot.py']:z.writestr('audit-prototypes/'+name,(ROOT/'build/literature-review/nfcore-runtime/maltextract17-audit-procedural-prototypes-1'/name).read_bytes())
  for name in ['admit-source.py','FAILURE.json','RENDERED_PAPER_DIFFERENCES.json']:z.writestr('admission-preflight/'+name,(ROOT/'build/literature-review/nfcore-runtime/maltextract17-admission-1'/name).read_bytes())
  prepared=ROOT/'build/literature-review/nfcore-runtime/maltextract17-dispatch-review-1'
  for name in ['dispatcher-before.java','dispatcher-after.java','REVIEW.json','COMPLETED.json']:z.writestr('dispatch-review/'+name,(prepared/name).read_bytes())
  from audit_snapshot import BLOCK
  raw=(ROOT/'README.md').read_bytes();assert raw.count(BLOCK)==1;z.writestr('documentation-review/README-before.md',raw.replace(BLOCK,b'',1));z.writestr('documentation-review/README-after.md',raw)
 new=copy.deepcopy(inventory);row=next(t for t in new if t['id']=='maltextract');row.update(metadata(ROOT))
 assert [x for x in new if x['id']!='maltextract']==[x for x in inventory if x['id']!='maltextract'];counts=dict(Counter(t['implementation_status'] for t in new));expected=dict(summary['implementation_status_counts']);expected['upstream-adapter-validated']+=1;expected['missing']-=1;assert counts==expected
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=counts;assert updated['goal_status']=='implementation-in-progress'
 save(data/'tool-inventory.json',new);save(data/'summary.json',updated)
 readme=(data/'README.md').read_text(encoding='utf8');assert '**Current checkpoint: 99 new scoped integrations**' in readme
 readme=readme.replace('**Current checkpoint: 99 new scoped integrations**','**Current checkpoint: 100 new scoped integrations**',1).replace('subsets and 80 tested upstream adapters; 102 including','subsets and 81 tested upstream adapters; 103 including',1).replace('**247 named tools remain missing.**','**246 named tools remain missing.**',1)
 marker='Cell Ranger remains excluded at the user\'s request. The full goal is active.';assert marker in readme
 readme=readme.replace(marker,marker+'\n\n**MaltExtract target extraction and original summary/damage output are now available**\nthrough a scoped original1.7 standalone workflow. Read\n[MALTEXTRACT_INTEGRATION.md](../MALTEXTRACT_INTEGRATION.md). The authors\'\nversion/settings/resources remain unknown; manual assessment and the separate\nbacterial rescreen remain separate operations. Canonical/released executables\nare unchanged. Earlier MALT-family source-only notices below are historical;\nthis scoped badge does not qualify the whole package, MALT or HOPS.',1)
 (data/'README.md').write_text(readme,encoding='utf8',newline='\n')
 with (data/'WARNINGS.md').open('a',encoding='utf8',newline='\n') as f:f.write(WARNING)
 embedded['tools']=new;embedded['summary']=updated;embedded['papers']=current_papers;encoded=json.dumps(embedded,ensure_ascii=False).replace('<','\\u003c');html=html[:match.start(2)]+encoded+html[match.end(2):]
 notice='<section id="maltextract17-actions"><h2>MaltExtract: scoped workflow and required actions</h2><p><strong>Original target extraction and summary/damage tables are available through a standalone development JAR.</strong> Read <code>docs/literature/MALTEXTRACT_INTEGRATION.md</code>. The canonical executable is unchanged. Author version/settings/resources, manual assessment and the separate bacterial rescreen remain distinct.</p><p><strong>Stock sampling can hide behind an Off flag:</strong> a complete10001-read control examined10000; choose an explicit complete profile for full-population extraction. Missing/forbidden runtime SKIP3; invalid input, SEVERE logs or inconsistent output FAIL2. Preserve all inputs and warnings. NaN/Inf remain literal. Saved verification does not refit arbitrary damage/coverage statistics or authenticate a producer. Whole-paper/biological validation remains open.</p></section>'
 assert 'id="maltextract17-actions"' not in html;html=html.replace('<script id="review-data"',notice+'<script id="review-data"',1);page.write_text(html,encoding='utf8',newline='\n')
 warning_page=ROOT/'site/literature-warnings.html';warnings=warning_page.read_text(encoding='utf8');assert 'id="maltextract17-actions"' not in warnings;warnings=warnings.replace('</main>',notice+'</main>',1);warning_page.write_text(warnings,encoding='utf8',newline='\n')
 papers=json.loads((data/'papers.json').read_bytes());paper=next(p for p in papers if p['paper_id']=='P002');use=next(t for t in paper['tools'] if t['tool_id']=='maltextract');assert use['version_reported_near_name'] is None and use['evidence_source_kind']=='supplementary-docx' and use['evidence_paragraph_index']==246
 role=dict(status='PASS_ONE_SUPPLEMENTARY_MALTEXTRACT_OUTPUT_ROLE_SCOPED',paper_id='P002',pmcid=paper['pmcid'],doi=paper['doi'],source_observation=use,reviewed_roles=1,declared_provider_version='1.7',paper_version_reported=False,paper_settings_recovered=False,operations=['target-species RMA6 extraction','retain original summary/damage outputs for manual assessment'],manual_assessment_automated=False,separate_bacterial_rescreen_implemented=False,whole_package_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1)
 save(data/'maltextract17-adapter-role-coverage.json',role)
 source_names=['tools/literature/maltextract17_catalog/'+p.name for p in (ROOT/'tools/literature/maltextract17_catalog').glob('*.py')]
 after_names=[n for n in preserved if not n.endswith('/SHA256SUMS')]
 proof=dict(status='PASS_SCOPED_MALTEXTRACT17_UPSTREAM_ADAPTER_ADMISSION',before_archive=pin(archive),before_inventory=pin_bytes_from_zip(archive,'docs/literature/omics-2023-2026/tool-inventory.json'),before_entry=before,after_entry=row,after_files={n:pin(ROOT/n) for n in after_names},protected=protected,workflow_validation=pin(ROOT/'docs/literature/maltextract17-workflow-development/VALIDATION.json'),workflow_archive=validation['archive'],role_coverage=pin(data/'maltextract17-adapter-role-coverage.json'),primary_source_audit=audit,source_pins={**validation['sources'],**{n:pin(ROOT/n) for n in source_names}},candidate_builds={edition:json.loads((ROOT/'build/literature-review/nfcore-runtime'/('maltextract17-workflow-replay-win'+edition+'-1/replay/build/BUILD.json')).read_bytes()) for edition in ['21','25']},candidates={edition:pin(integration/('maltextract17-workflow-jdk'+edition+'.jar')) for edition in ['21','25']},old_counts=summary['implementation_status_counts'],new_counts=counts,changed_tool_ids=['maltextract'],refreshed_existing_scope_notes=scope_updates,unchanged_observations=2479,unchanged_reviews=1052,unchanged_named_tools=405,new_scoped_integration_credit=1,whole_tool_credit=0,whole_goal_complete=False)
 save(data/'maltextract17-adapter-validation.json',proof);save(out/'COMPLETED.json',proof)
 for name,value in protected.items():assert pin(ROOT/name)==value,name
 print('Admitted one scoped MaltExtract adapter;100 new scoped integrations;246 missing; full goal active',flush=True)

def pin_bytes_from_zip(archive,name):
 import hashlib
 with zipfile.ZipFile(archive) as z:raw=z.read(name)
 return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable admission/source checks')
 raise SystemExit(main())
