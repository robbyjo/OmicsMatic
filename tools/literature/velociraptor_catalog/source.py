# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy one existing reading and apply one coverage field; retain all primary source facts."""
import copy,hashlib,json
from scvelo_catalog.source import scan as previous_scan,reviews as previous_reviews
CURATION='tools/literature/velociraptor_catalog/implementation-coverage.json'

def review(root):
 patch=json.loads((root/CURATION).read_bytes());previous=previous_reviews(root)[-1]
 if (patch['paper_id'],patch['pmcid'],patch['tool_id'],patch['changed_field'])!=('P228','PMC12696504','velociraptor','implementation_scope_note'):raise ValueError('Only one existing wrapper coverage field may change')
 if hashlib.sha256((root/patch['primary_review_source']).read_bytes()).hexdigest()!=patch['primary_review_source_sha256']:raise ValueError('Existing complete source-review collection changed')
 row=copy.deepcopy(previous);row['implementation_scope_note']=patch['implementation_scope_note']
 if {k:v for k,v in row.items() if k!='implementation_scope_note'}!={k:v for k,v in previous.items() if k!='implementation_scope_note'}:raise ValueError('Primary source role, author setting or uncertainty changed')
 if row['version_reported_near_name'] is not None or row['reported_settings']!={'interface':'R package velociraptor','wrapper_edition':None,'Python_backend_edition':None,'velocity_mode':None}:raise ValueError('Unknown author settings were substituted')
 return row

def scan(root,papers):
 review(root);return previous_scan(root,papers)
