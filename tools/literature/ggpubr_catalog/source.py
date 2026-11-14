# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind nine curated roles to the qualified complete12-anchor primary-source census."""
import json
from ggpubr_workflow.source import EXPECTED,VERSIONS,scan as original_scan,validate as original_validate
CURATION='tools/literature/ggpubr-paper-use-reviews.json'
INTAKE='tools/literature/ggpubr/paper-use-intake.json'
QUALIFIED_CENSUS='docs/literature/ggpubr-workflow-development/CENSUS.json'
def reviews(root):
 before=json.loads((root/INTAKE).read_bytes())['reviews'];rows=json.loads((root/CURATION).read_bytes())['reviews']
 if len(rows)!=9 or [r['paper_id'] for r in rows]!=list(EXPECTED):raise ValueError('Incomplete individual ggpubr source readings')
 for old,row in zip(before,rows,strict=True):
  if any(row.get(k)!=v for k,v in old.items()):raise ValueError('Qualified original source reading changed')
  anchor=old['supporting_evidence'][0]
  if (row['tool_id'],row['evidence_paragraph_index'],row['evidence_paragraph_sha256'])!=('ggpubr',anchor['paragraph_index'],anchor['paragraph_sha256']):raise ValueError('Primary individual review anchor differs')
 return rows
def validate(census,papers,rows,root=None):
 result=original_validate(census,papers,dict(reviews=rows))
 for row in rows:
  a=row['supporting_evidence'][0]
  if row['tool_id']!='ggpubr' or row['evidence_paragraph_index']!=a['paragraph_index'] or row['evidence_paragraph_sha256']!=a['paragraph_sha256']:raise ValueError('Curated primary analysis/visualization anchor differs')
 if root is not None and census!=json.loads((root/QUALIFIED_CENSUS).read_bytes()):raise ValueError('Exact qualified ggpubr source census differs')
 return result
def scan(root,papers):
 rows=reviews(root);result=original_scan(root,papers,dict(reviews=rows));validate(result,papers,rows,root);return result
