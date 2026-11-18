#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-bound Dunnett component crosswalk; no paper or tool-family admission."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
ORDINARY={'P026','P029','P031','P051','P084','P087'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--xml-cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
 checker=ROOT/'tools/literature/common_statistics/review_sources.py'
 command=[sys.executable,'-B',str(checker),'--xml-cache',str(a.xml_cache),'--out',str(a.out/'source-census')]
 (a.out/'command.json').write_text(json.dumps(command,indent=2)+'\n',encoding='utf8')
 with (a.out/'stdout').open('xb') as so,(a.out/'stderr').open('xb') as se:
  process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
 if process.returncode:
  (a.out/'FAILURE.json').write_text(json.dumps({'source_check_exit':process.returncode})+'\n');raise SystemExit(2)
 source=json.loads((a.out/'source-census/SOURCE_REVIEW.json').read_bytes());assert len(source['primary_sources'])==250 and len(source['dunnett_roles'])==9
 roles=[]
 for role in source['dunnett_roles']:
  paper=role['paper_id'];roles.append(dict(paper_id=paper,source_pointers=role['source_pointers'],original_review_summary=role['use_summary'],ordinary_component_candidate=paper in ORDINARY,component_profile='dunnett-multcomp1429-oneway-v1' if paper in ORDINARY else None,paper_reproduction_complete=False,remaining='Author observations, independent units, exact control/treatment family, analysis settings and historical behavior must be established before paper attribution.' if paper in ORDINARY else 'P006 menu does not establish the model; P034 repeated measures/Geisser-Greenhouse and P217 unequal-variance/variant ambiguity require separate qualified methods.'))
 guards={}
 for name in ['tools/literature/common_statistics/review_sources.py','tools/literature/common_statistics/dunnett_sources.py']:
  raw=(ROOT/name).read_bytes();guards[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
 report=dict(status='PASS250_SOURCE_IDENTITIES_AND_NINE_DUNNETT_ROLES',primary_sources=source['primary_sources'],dunnett_name_papers=9,ordinary_component_candidates=sorted(ORDINARY),roles=roles,source_guards=guards,scope='Java ordinary one-way model audit plus original multcomp 1.4-29 inference is a bounded component. No whole Prism/multcomp or paper workflow admission. The nested unchanged source census preserves its historical pending-implementation labels.',new_catalog_integrations=0,whole_goal_complete=False)
 (a.out/'SOURCE_REVIEW.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print('PASS250 primary identities;9 Dunnett papers;6 ordinary component candidates;3 unresolved/unsupported contexts')

if __name__=='__main__':main()
