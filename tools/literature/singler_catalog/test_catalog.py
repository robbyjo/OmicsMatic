# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject borrowed releases, unsupported implementations and duplicate credits."""
from pathlib import Path
import copy,gzip,hashlib,json,re,sys,unittest,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from singler_catalog.model import *
from singler_catalog.source import reviews,definitions,census
from singler_catalog.catalog import compatibility,load_before,load_catalog_registry
from catalog_source_integrity import reverse_delta
from collect_omics_review import paragraphs,tool_matches

class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META}
        cls.after=transition(ROOT,cls.before)

    def test_single_family_credit_and_all_new_missing_work(self):
        result=check_transition(ROOT,self.before,self.after)
        self.assertEqual(tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing']),(250,441,2682,1187,1495,118,264))
        self.assertEqual((result['added_reviews'],result['added_observations'],result['new_registry_definitions'],result['native_family_credit_added']),(7,3,3,0))
        counts=json.loads(self.after[META[1]])['implementation_status_counts']
        self.assertEqual((counts['native-subset-validated'],counts['upstream-adapter-validated']),(20,98))
        inv=json.loads(self.after[META[0]])
        self.assertEqual([r['implementation_status'] for r in inv if r['id'] in {'celldex','spotlight','bd-rhapsody'}],['missing']*3)

    def test_distinct_versions_and_literal_unresolved_version(self):
        rows={r['paper_id']:r for r in reviews(ROOT) if r['tool_id']=='singler'}
        self.assertEqual([u['version_reported_at_this_anchor'] for u in rows['P051']['analysis_uses']],[None,'2.8.0','2.6.0'])
        self.assertIsNone(rows['P082']['version_reported_near_name'])
        self.assertEqual(rows['P233']['version_reported_near_name'],'2.41')
        self.assertIn('unpruned',rows['P233']['use_summary'])
        self.assertEqual([a['paragraph_index'] for a in rows['P192']['supporting_evidence']],[16,18])
        self.assertTrue(all('SKIP3' in r['implementation_scope_note'] for r in rows.values()))

    def test_unrelated_facts_and_reviews_preserved(self):
        oldp,newp=json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])
        for old,new in zip(oldp,newp):
            self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'})
            self.assertEqual([t for t in old['tools'] if t['tool_id']!='singler'],[t for t in new['tools'] if t['tool_id'] not in {'singler','celldex','spotlight','bd-rhapsody'}])
        self.assertEqual(json.loads(self.after[REVIEWS])['reviews'][:1180],json.loads(self.before[REVIEWS])['reviews'])
        self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='singler'],[r for r in json.loads(self.after[META[0]]) if r['id'] not in {'singler','celldex','spotlight','bd-rhapsody'}])

    def test_exact_review_inverse_and_complete_registry(self):
        self.assertEqual(reverse_delta(self.after[REVIEWS],compatibility(self.before,self.after)['paths'][0]),self.before[REVIEWS])
        self.assertEqual(len(load_catalog_registry(ROOT)),479)

    def test_false_positive_patterns(self):
        patterns={d['id']:d['pattern'] for d in definitions(ROOT)}
        for key,text in [('celldex','Celldex Therapeutics'),('spotlight','Spotlight Therapeutics'),('spotlight','we spotlight these results'),('bd-rhapsody','BD Rhapsody Express System')]:
            self.assertIsNone(re.search(patterns[key],text,re.I))
        for key,text in [('celldex','celldex package (v.1.14.0)'),('spotlight','SPOTlight (v.1.0.3)'),('spotlight','SPOTlight package (v.1.0.3)'),('bd-rhapsody','Rhapsody analysis pipeline v.1.9.1')]:
            self.assertIsNotNone(re.search(patterns[key],text,re.I))

    def test_borrowed_release_and_native_credit_rejected(self):
        for paper_id,version in [('P233','2.4.1'),('P051','2.8.0'),('P082','2.2.0')]:
            papers=json.loads(self.after[PAPERS]);next(t for p in papers if p['paper_id']==paper_id for t in p['tools'] if t['tool_id']=='singler')['version_reported_near_name']=version
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,PAPERS:encoded(papers)})
        for tool,key,value in [('singler','native_family_credit_added',1),('singler','whole_tool_qualified',True),('celldex','implementation_status','upstream-adapter-validated')]:
            inv=json.loads(self.after[META[0]]);next(r for r in inv if r['id']==tool)[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:encoded(inv)})

    def test_all250_original_sources(self):
        result=census(ROOT)
        self.assertEqual((result['papers'],result['singleR_papers'],result['singleR_named_anchors']),(250,4,9))
        self.assertNotIn('primary_anchor_texts',result)
        self.assertTrue(all('license' in r and 'fulltext_url' in r for r in result['census']))
        self.assertEqual(result,json.loads(json.dumps(result)))

    def test_fresh_extraction_all250_with_new_family_context_guards(self):
        definitions_=definitions(ROOT);ids={d['id'] for d in definitions_}
        preferred={r['tool_id']:r['evidence_paragraph_sha256'] for r in reviews(ROOT) if r['tool_id'] in ids}
        found=[]
        for p in json.loads(self.before[PAPERS]):
            raw=gzip.decompress((ROOT/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')).read_bytes())
            self.assertEqual(hashlib.sha256(raw).hexdigest(),p['xml_sha256'])
            matches=tool_matches(paragraphs(ET.fromstring(raw)),definitions_,preferred if p['paper_id']=='P051' else None)
            found.extend((p['paper_id'],m['tool_id'],m['evidence_paragraph_index'],m['version_reported_near_name']) for m in matches)
        # The generic extractor does not parse a release after the intervening
        # word "package". The independently curated source review supplies it.
        self.assertEqual(sorted(found),[('P051','bd-rhapsody',81,'1.9.1'),('P051','celldex',119,None),('P051','spotlight',114,'1.0.3')])
        curated=next(r for r in reviews(ROOT) if r['tool_id']=='celldex')
        observation=next(t for p in json.loads(self.after[PAPERS]) if p['paper_id']=='P051' for t in p['tools'] if t['tool_id']=='celldex')
        self.assertEqual(curated['version_reported_near_name'],'1.14.0')
        self.assertEqual(observation['version_reported_near_name'],'1.14.0')

if __name__=='__main__':unittest.main()
