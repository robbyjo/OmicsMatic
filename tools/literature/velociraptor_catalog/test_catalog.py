# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Protect full review collections, scientific provenance and the one-wrapper admission boundary."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from velociraptor_catalog.model import *
from velociraptor_catalog.bridge import load_before,metadata_view
from velociraptor_catalog.verify import compatibility
from velociraptor_catalog.qualification import qualification
from catalog_source_integrity import reverse_delta

class VelociraptorCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT) if (ROOT/EVIDENCE/'catalog-before.zip').exists() else {n:(ROOT/n).read_bytes() for n in META};cls.after=transition(ROOT,cls.before)
 def reject(self,name,change):
  after=dict(self.after);value=json.loads(after[name]);change(value);after[name]=encoded(value)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_one_scoped_family_credit_without_population_changes(self):
  result=check_transition(ROOT,self.before,self.after);self.assertEqual((250,433,2672,1168,1504,112,262,1,0,0),tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing','new_scoped_integration_credit','added_reviews','native_family_credit_added']))
 def test_all1168_reviews_retained_and_other1167_unchanged(self):
  before=json.loads(self.before[REVIEWS])['reviews'];after=json.loads(self.after[REVIEWS])['reviews'];self.assertEqual(1168,len(after));self.assertEqual([r for r in before if r['tool_id']!='velociraptor'],[r for r in after if r['tool_id']!='velociraptor'])
  for a,b in zip(before,after):self.assertEqual({k:v for k,v in a.items() if k!='implementation_scope_note'},{k:v for k,v in b.items() if k!='implementation_scope_note'})
 def test_other432_families_unchanged(self):self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='velociraptor'],[r for r in json.loads(self.after[META[0]]) if r['id']!='velociraptor'])
 def test_all250_paper_identities_and_other2671_observations_unchanged(self):
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'});self.assertEqual([r for r in a['tools'] if r['tool_id']!='velociraptor'],[r for r in b['tools'] if r['tool_id']!='velociraptor'])
 def test_unknown_author_edition_not_substituted(self):self.reject(PAPERS,lambda ps:next(r for p in ps if p['paper_id']=='P228' for r in p['tools'] if r['tool_id']=='velociraptor').update(version_reported_near_name='1.20.0'))
 def test_native_or_bundled_or_whole_package_credit_forbidden(self):
  for changes in [dict(native_family_credit_added=1),dict(canonical_adapter_available=True),dict(whole_tool_qualified=True)]:self.reject(META[0],lambda rows:next(r for r in rows if r['id']=='velociraptor').update(changes))
 def test_cannot_drop_existing_review(self):self.reject(REVIEWS,lambda doc:doc['reviews'].pop(0))
 def test_cannot_rewrite_other_tool_or_primary_role(self):
  self.reject(REVIEWS,lambda doc:doc['reviews'][0].update(use_summary='altered original reading'))
  self.reject(META[0],lambda rows:next(r for r in rows if r['id']=='cell2location').update(selected_upstream_versions=['0.7a0']))
 def test_review_inverse_recovers_exact_full_predecessor_bytes(self):
  record=compatibility(self.before,self.after);self.assertEqual([REVIEWS],[r['path'] for r in record['paths']]);self.assertEqual(self.before[REVIEWS],reverse_delta(self.after[REVIEWS],record['paths'][0]))
 def test_scientific_overlays_forbidden(self):
  for name in [PROGRAM,WORKFLOW+'/evidence-001.zip','tools/literature/verify_velociraptor120.py',CURATION]:
   before=dict(self.before);before[name]=b'forged'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
 def test_current_warning_notice_precedes_overview(self):
  page=self.after[META[4]].decode();self.assertLess(page.index('id="velociraptor-required-actions"'),page.index('id="overview"'));self.assertEqual(1,page.count(NOTICE));self.assertNotIn('**',NOTICE)
  for word in ['112 scoped','92 original upstream','262 named','1,168','1,504','unresolved','unavailable, never zero','SKIP3','FAIL2','FINALIZATION_ERROR.json','NEW writable','not bundled Java CLI','Cell Ranger/ARC']:self.assertIn(word,NOTICE)
 def test_changed_runtime_source_refused_before_child_execution(self):
  proof=json.loads((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());root=Path(tempfile.mkdtemp(prefix='velociraptor-source-refusal-',dir=ROOT/'build/literature-review/monocle-runtime'));self.assertTrue(root.resolve().is_relative_to((ROOT/'build/literature-review/monocle-runtime').resolve()))
  for name in proof['sources']:
   path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/name).read_bytes())
  (root/WORKFLOW/'VALIDATION.json').write_bytes((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());(root/PROGRAM).write_bytes(b'# Deliberately changed source: must never execute.\n')
  with self.assertRaisesRegex(ValueError,'Qualified velociraptor source/example differs before execution'):qualification(root)
  (root/'CONTROL.json').write_bytes(encoded(dict(status='PASS_CHANGED_SOURCE_REFUSED_BEFORE_CHILD_EXECUTION',retained_without_deletion=True)))
if __name__=='__main__':unittest.main()
