#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped admission, exact predecessor metadata and unchanged primary evidence."""
from pathlib import Path
from collections import Counter
import argparse,json,re,zipfile,sys
from metadata import qualification,metadata,pin,INTEGRATION
from dispatcher import PATH,reviewed_root
from setup_deseq2_reference import ROOT,save

def verify(root=ROOT):
 data=root/'docs/literature/omics-2023-2026';proof=json.loads((data/'maltextract17-adapter-validation.json').read_bytes());assert proof['status']=='PASS_SCOPED_MALTEXTRACT17_UPSTREAM_ADAPTER_ADMISSION' and proof['new_scoped_integration_credit']==1 and proof['whole_tool_credit']==0 and not proof['whole_goal_complete'];qualification(root)
 for name,value in proof['source_pins'].items():assert pin(root/name)==value,name
 for name,value in proof['protected'].items():assert pin(root/name)==value,name
 for name,value in proof['after_files'].items():assert pin(root/name)==value,name
 archive=root/INTEGRATION/'catalog-before.zip';assert pin(archive)==proof['before_archive']
 with zipfile.ZipFile(archive) as z:
  before=json.loads(z.read('docs/literature/omics-2023-2026/tool-inventory.json'));oldsummary=json.loads(z.read('docs/literature/omics-2023-2026/summary.json'));oldpage=z.read('site/literature-review.html').decode('utf8')
  for name in ['papers.json']:
   assert pin(data/name)==proof['protected']['docs/literature/omics-2023-2026/'+name]
 inventory=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes());assert len(inventory)==len({t['id'] for t in inventory})==405;assert [t for t in inventory if t['id']!='maltextract']==[t for t in before if t['id']!='maltextract']
 expected=dict(next(t for t in before if t['id']=='maltextract'));expected.update(metadata(root));actual=next(t for t in inventory if t['id']=='maltextract');assert expected==actual==proof['after_entry'];assert next(t for t in before if t['id']=='maltextract')==proof['before_entry']
 oldcounts=dict(oldsummary['implementation_status_counts']);newcounts=dict(Counter(t['implementation_status'] for t in inventory));oldcounts['upstream-adapter-validated']+=1;oldcounts['missing']-=1;assert oldcounts==newcounts==summary['implementation_status_counts']==proof['new_counts'];assert {k:v for k,v in summary.items() if k!='implementation_status_counts'}=={k:v for k,v in oldsummary.items() if k!='implementation_status_counts'}
 papers=json.loads((data/'papers.json').read_bytes());reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];assert len(papers)==250 and sum(len(p['tools']) for p in papers)==2479 and len(reviews)==1052;role=json.loads((data/'maltextract17-adapter-role-coverage.json').read_bytes());assert pin(data/'maltextract17-adapter-role-coverage.json')==proof['role_coverage'];assert role['status']=='PASS_ONE_SUPPLEMENTARY_MALTEXTRACT_OUTPUT_ROLE_SCOPED';assert role['source_observation']==next(t for p in papers if p['paper_id']=='P002' for t in p['tools'] if t['tool_id']=='maltextract');assert not role['paper_version_reported'] and not role['manual_assessment_automated'] and not role['separate_bacterial_rescreen_implemented']
 for edition,value in proof['candidates'].items():assert pin(root/INTEGRATION/('maltextract17-workflow-jdk'+edition+'.jar'))==value
 for name in ['site/literature-review.html','site/literature-warnings.html']:assert 'id="maltextract17-actions"' in (root/name).read_text(encoding='utf8')
 page=(root/'site/literature-review.html').read_text(encoding='utf8');match=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S);embedded=json.loads(match[1]);assert embedded['tools']==inventory and embedded['summary']==summary and embedded['papers']==papers
 view,dispatch=reviewed_root(root);assert dispatch['scientific_sources_overridden'] is False
 return dict(status='PASS_ONE_SCOPED_MALTEXTRACT_ADMISSION_WITH_PRESERVED_PRIMARY_EVIDENCE',papers=250,observations=2479,reviewed_uses=1052,new_scoped_integrations=newcounts['upstream-adapter-validated']+newcounts['native-subset-validated'],missing=newcounts['missing'],scientific_sources_overridden=False,new_scoped_integration_credit=1,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 try:result=verify();save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as error:
  import traceback
  save(out/'FAILURE.json',dict(status='FAIL2_MALTEXTRACT_CATALOG_ADMISSION',cause=repr(error),traceback=traceback.format_exc(),required_action='Retain diagnostics; restore the matching complete evidence/source or review a new versioned change. Do not grant credit to a failed gate.'));print('FAIL2_MALTEXTRACT_CATALOG_ADMISSION',repr(error),flush=True);return 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete admission checks')
 raise SystemExit(main())
