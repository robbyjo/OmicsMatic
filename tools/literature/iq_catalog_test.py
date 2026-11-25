"""Frozen-source semantic regression checks for iq and pending proteomics providers."""
import copy,json,re,unittest
from pathlib import Path
import iq_catalog as catalog
import iq_catalog_model as model
from gemma_primary import encoded

ROOT=Path(__file__).resolve().parents[2]


class IqCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=catalog.before(ROOT);cls.roles=json.loads((ROOT/'docs/literature/iq-proteomics-source-audit/ROLES.json').read_bytes());cls.q=json.loads((ROOT/catalog.Q).read_bytes());cls.after=model.transition(cls.before,cls.roles,cls.q)

    def test_counts_come_from_added_sources_and_only_one_native_family(self):
        r=model.check(self.before,self.after,self.roles,self.q)
        self.assertEqual((451,490,2731,1802,929,165,32,256),tuple(r[k]for k in('labels','provider_definitions','observations','reviewed_uses','pending_uses','scoped_families','native','missing')))
        self.assertEqual((4,5,16),tuple(r[k]for k in('new_named_providers','added_positive_observations','new_source_role_reviews')))

    def test_raw_search_and_bundled_components_remain_pending_and_MaxQuant_is_not_excluded(self):
        rows=json.loads(self.after[model.META[1]])
        for id in model.TARGETS-{'iq'}:self.assertEqual('missing',next(r for r in rows if r['id']==id)['implementation_status'])
        mq=next(r for r in rows if r['id']=='maxquant');self.assertEqual('SKIP3_USER_REQUESTED_KEEP_TOOL_PENDING',mq['runtime_analysis_status'])
        for id in('andromeda','maxdia'):self.assertEqual(0,next(r for r in rows if r['id']==id)['family_credit'])
        self.assertEqual(json.loads(self.before[model.META[2]])['user_excluded_tools'],json.loads(self.after[model.META[2]])['user_excluded_tools'])

    def test_unreported_component_editions_literal_MaxQuant_version_and_typo_are_preserved(self):
        for role in self.roles['roles']:
            if role['tool_id']in('iq','andromeda','maxdia'):
                altered=copy.deepcopy(self.roles);next(r for r in altered['roles']if(r['paper_id'],r['tool_id'])==(role['paper_id'],role['tool_id']))['literal_reported_version']='2.3.0'
                with self.assertRaises(ValueError):model.transition(self.before,altered,self.q)
        altered=copy.deepcopy(self.roles);next(r for r in altered['roles']if(r['paper_id'],r['tool_id'])==('P029','diann'))['reported_settings']['reported_intensity_name_literal']='MaxLFQ'
        with self.assertRaises(ValueError):model.transition(self.before,altered,self.q)
        altered=copy.deepcopy(self.roles);next(r for r in altered['roles']if(r['paper_id'],r['tool_id'])==('P032','maxquant'))['literal_reported_version']='2.4.7.0'
        with self.assertRaises(ValueError):model.transition(self.before,altered,self.q)

    def test_unrelated_existing_inventory_and_prior_reviews_cannot_change(self):
        altered=dict(self.after);rows=json.loads(altered[model.META[1]]);next(r for r in rows if r['id']=='iqtree')['implementation_status']='missing';altered[model.META[1]]=encoded(rows)
        with self.assertRaises(ValueError):model.check(self.before,altered,self.roles,self.q)
        altered=dict(self.after);ledger=json.loads(altered[model.META[5]]);ledger['reviews'][0]['use_summary']='unrelated changed';altered[model.META[5]]=encoded(ledger)
        with self.assertRaises(ValueError):model.check(self.before,altered,self.roles,self.q)

    def test_incomplete_or_expanded_qualification_cannot_grant_credit(self):
        for key,value in(('native_captures_each_OS',3),('whole_package_qualified',True),('DIA_NN_raw_search_qualified',True),('MaxQuant_runtime_qualified',True),('whole_paper_reproduced',True)):
            q=copy.deepcopy(self.q);q[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):model.transition(self.before,self.roles,q)

    def test_report_embeds_exact_data_and_distinguishes_iq_from_imaging_and_traits(self):
        page=self.after[model.META[6]].decode();m=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S);data=json.loads(m[1]);self.assertEqual(json.loads(self.after[model.META[0]]),data['papers']);self.assertEqual(1,page.count('id="proteomics-catalog-notice"'));self.assertIn('Before iq/proteomics admission (historical)',page)
        p051=next(p for p in data['papers']if p['paper_id']=='P051');self.assertTrue(any(t['tool_id']=='macs_iq_view'for t in p051['tools']));self.assertFalse(any(t['tool_id']=='iq'for t in p051['tools']))
        for literal in('SKIP3','FAIL2','keeping MaxQuant pending','Native prepared aggregation available','Reported version:'):self.assertIn(literal,page)

    def test_registry_adds_four_only_without_rebuilding_stale_prior_classifications(self):
        prior=json.loads((ROOT/model.ADMISSION/'PREVIOUS-REGISTRY.json').read_bytes());current=model.registry(ROOT);self.assertEqual(prior,current[:len(prior)]);self.assertEqual(490,len(current));self.assertEqual(1,sum(r.get('family_credit',0)for r in current[len(prior):]));self.assertEqual(model.NEW,{r['id']for r in current[len(prior):]})


if __name__=='__main__':unittest.main()
