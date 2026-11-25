"""Frozen-catalog semantic regression checks for scoped MetaXcan admission."""
import copy
import json
import re
import unittest
from pathlib import Path
import metaxcan_catalog as catalog
import metaxcan_catalog_model as model
from gemma_primary import encoded

ROOT = Path(__file__).resolve().parents[2]


class MetaXcanCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = catalog.before(ROOT)
        cls.roles = json.loads((ROOT/'docs/literature/metaxcan-source-audit/ROLES.json').read_bytes())
        cls.q = json.loads((ROOT/catalog.Q).read_bytes())
        cls.after = model.transition(cls.before, cls.roles, cls.q)

    def test_counts_derive_from_actual_populations_and_training_is_separate(self):
        result = model.check(self.before,self.after,self.roles,self.q)
        self.assertEqual((2726,1786,940,164,31,253),tuple(result[k]for k in
                         ('observations','reviewed_uses','pending_uses','scoped_families','native','missing')))
        rows = json.loads(self.after[model.META[1]])
        training = next(r for r in rows if r['id']=='predictdb')
        self.assertEqual('missing',training['implementation_status'])
        self.assertEqual(['P019','P074'],training['papers'])
        self.assertEqual(1,sum(r.get('family_credit',0)for r in rows if r['id']in model.TARGETS))

    def test_unrelated_inventory_and_raw_versions_cannot_be_changed(self):
        altered = dict(self.after); rows=json.loads(altered[model.META[1]])
        next(r for r in rows if r['id']=='fastqc')['implementation_status']='missing'
        altered[model.META[1]]=encoded(rows)
        with self.assertRaises(ValueError):model.check(self.before,altered,self.roles,self.q)
        altered=dict(self.before);papers=json.loads(altered[model.META[0]])
        next(u for p in papers if p['paper_id']=='P074'for u in p['tools']if u['tool_id']=='predixcan')['version_reported_near_name']='0.7.4'
        altered[model.META[0]]=encoded(papers)
        with self.assertRaises(ValueError):model.transition(altered,self.roles,self.q)

    def test_training_and_whole_paper_claims_cannot_grant_qualification(self):
        for key,value in(('training_implemented',True),('whole_paper_reproduced',True),('Windows_JUnit',51),
                          ('final_control_calls_each_OS',13),('original_absent_author_key_failures',0)):
            q=copy.deepcopy(self.q);q[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):model.transition(self.before,self.roles,q)

    def test_current_report_embeds_exact_catalog_and_prominent_actions(self):
        page=self.after[model.META[6]].decode();m=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S)
        payload=json.loads(m[1]);self.assertEqual(json.loads(self.after[model.META[0]]),payload['papers'])
        self.assertEqual(json.loads(self.after[model.META[1]]),payload['tools'])
        self.assertEqual(1,page.count('id="metaxcan-catalog-notice"'))
        self.assertIn('Before MetaXcan admission (historical)',page)
        for text in('SNP_ID','FAIL2','SKIP3','training pending'):self.assertIn(text,page)
        for p in payload['papers']:
            self.assertFalse(any(u['tool_id']in model.TARGETS|{'predictdb'}for u in p['tools'])and p['paper_id']in('P095','P238'))

    def test_registry_preserves_unrelated_definitions_and_adds_only_one_provider(self):
        old=json.loads((ROOT/model.ADMISSION/'PREVIOUS-REGISTRY.json').read_bytes());new=model.registry(ROOT)
        self.assertEqual(486,len(new))
        self.assertEqual([r for r in old if r['id']not in model.TARGETS],[r for r in new if r['id']not in model.TARGETS|{'predictdb'}])
        self.assertEqual('missing',new[-1]['current_coverage'])
        self.assertEqual(2,sum(r['current_coverage']=='validated-family-alias'for r in new if r['id']in model.TARGETS))


if __name__=='__main__':
    unittest.main()
