"""Check literal source roles, exact deltas and preservation of unrelated work."""
import copy,json,subprocess,unittest
from genomicsem_catalog import model as m,primary as p
BASE='9bf1aeb93ca5196dd1de321c640d02b5387d511f'

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT)for n in m.META};cls.roles=p.frozen();cls.q=m.qualification();cls.after=m.transition(cls.before,cls.roles,cls.q)
    def rows(self):return{r['paper_id']:r for r in self.roles['roles']}
    def test_exact_count_delta_and_unrelated_records(self):
        r=m.check(self.before,self.after,self.roles,self.q);self.assertEqual((r['scoped_families'],r['native'],r['upstream'],r['reviewed_uses'],r['pending_uses'],r['method_overlap'],r['missing']),(160,30,130,1765,959,17,254));self.assertEqual((r['unchanged_prior_reviews'],r['unchanged_unrelated_observations'],r['unchanged_unrelated_inventory_rows']),(1760,2719,445));self.assertFalse(r['whole_goal_complete'])
    def test_complete_registry_keeps_prior_adapters_and_user_exclusion(self):
        rows=m.registry();self.assertEqual(len(rows),len({r['id']for r in rows}));self.assertEqual(len(rows),485);lookup={r['id']:r for r in rows};self.assertEqual(lookup['genomicsem']['current_coverage'],'upstream-adapter-validated');self.assertEqual(lookup['fastqtl']['current_coverage'],'upstream-adapter-validated');self.assertEqual(lookup['cellranger'],next(r for r in m.previous.registry()if r['id']=='cellranger'));inventory=json.loads(self.after[m.META[1]]);self.assertEqual(next(r for r in inventory if r['id']=='cellranger')['implementation_status'],'excluded-by-user')
    def test_raw_versions_are_not_replaced_by_contextual_editions(self):
        papers=json.loads(self.after[m.META[0]]);uses=[next(u for u in next(x for x in papers if x['paper_id']==pid)['tools']if u['tool_id']=='genomicsem')for pid in p.POSITIVE];self.assertEqual([x['version_reported_near_name']for x in uses],[None,None,None,None,'0.0.5']);self.assertEqual([x['source_role_qualification']['reported_version']for x in uses],['0.5.0','unreported','0.0.5','0.0.3f','0.0.5'])
    def test_p035_strict_source_settings_and_separate_EFA_survive(self):
        r=self.rows()['P035'];s=r['reported_settings'];self.assertEqual(s['munge']['INFO_greater_than'],.9);self.assertEqual((s['stratified']['excluded_nonpositive_definite_annotations'],s['stratified']['retained_annotations']),(22,162));self.assertEqual(s['factor_selection']['external_EFA'],'stats::factanal');self.assertEqual(s['factor_selection']['EFA_chromosomes'],'even');self.assertEqual(s['factor_selection']['CFA_chromosomes'],'odd');self.assertIn('exact implementation/options not established',s['GWAS']['GC_description'])
    def test_p125_requires_conditional_subtype_models(self):
        s=self.rows()['P125']['reported_settings'];self.assertIn('joint subtype-adjusted associations',s['models']);self.assertEqual(s['sample_overlap'],'handled by LDSC-derived sampling dependencies')
    def test_p131_fusion_tsem_does_not_transfer_other_tool_editions(self):
        r=self.rows()['P131'];self.assertEqual(r['reported_version'],'0.0.5');self.assertEqual(r['reported_settings']['TSEM']['tissues'],45);self.assertEqual([x['literal']for x in r['reported_version_contexts']],['0.0.5','0.0.5']);self.assertIn('ACAT across tissues',r['separate_methods'])
    def test_p140_cholesky_is_separate_from_pca_and_historical_runtime(self):
        r=self.rows()['P140'];self.assertEqual((r['reported_settings']['edition'],r['reported_settings']['R']),('0.0.3f','4.3.2'));self.assertEqual(r['reported_settings']['subtraction']['latent_covariance_fixed'],0);self.assertIn('R eigen PCA',r['separate_methods']);self.assertFalse(r['paper_edition_qualified'])
    def test_p150_unknown_parallel_analysis_is_not_invented_paldsc(self):
        r=self.rows()['P150'];self.assertNotIn('paLDSC',r['reported_settings']['parallel_analysis']);self.assertIn('paLDSC',r['remaining_scope']);self.assertEqual(r['reported_settings']['GWAS']['retained_SNPs'],2265555)
    def test_no_historical_analysis_or_extra_native_credit(self):
        self.assertFalse(self.q['historical_paper_editions_qualified']);self.assertFalse(self.q['original_paper_analyses_reexecuted']);inventory=json.loads(self.after[m.META[1]]);r=next(x for x in inventory if x['id']=='genomicsem');self.assertEqual((r['new_native_family_credit'],r['new_upstream_family_credit']),(0,1));self.assertEqual(r['prior_method_overlap_source'],'src/main/java/org/jlinalg/xwas/GenomicFactor.java');self.assertFalse(r['released_binary_contains_workflow'])
    def test_fabricated_editions_or_source_settings_are_refused(self):
        q=copy.deepcopy(self.q);q['historical_paper_editions_qualified']=True
        with self.assertRaises(ValueError):m.transition(self.before,self.roles,q)
        roles=copy.deepcopy(self.roles);roles['roles'][0]['reported_version']='0.0.5'
        with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)
    def test_prior_review_raw_field_and_double_admission_are_refused(self):
        changed=dict(self.after);papers=json.loads(changed[m.META[0]]);next(u for u in next(x for x in papers if x['paper_id']=='P035')['tools']if u['tool_id']=='genomicsem')['version_reported_near_name']='0.0.5';changed[m.META[0]]=p.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
        changed=dict(self.after);ledger=json.loads(changed[m.META[5]]);ledger['reviews'][0]['use_summary']='altered earlier review';changed[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
        with self.assertRaises(ValueError):m.transition(self.after,self.roles,self.q)
    def test_notice_and_prior_renderers_are_preserved(self):
        for n in m.META[6:]:
            text=self.after[n].decode();self.assertEqual(text.count('id="genomicsem-catalog-notice"'),1);self.assertEqual(text.count('id="genomicsem-prior-catalog"'),1);self.assertIn('SKIP3',text);self.assertIn('FAIL2',text)
        text=self.after[m.META[6]].decode();self.assertEqual(text.count('${genomicsemVersion(t)}'),1);self.assertEqual(text.count('function fastqtlVersion(t)'),1);self.assertEqual(text.count('function cytometryVersion(t)'),1)

if __name__=='__main__':unittest.main()
