"""Check paper-specific settings, existing-review refinement and exact catalog deltas."""
import copy,json,subprocess,unittest
from prsice_catalog import model as m,primary as p

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',p.BASE+':'+n],cwd=m.ROOT)for n in m.META};cls.roles=p.frozen();cls.q=m.qualification();cls.after=m.transition(cls.before,cls.roles,cls.q)
    def rows(self):return{r['paper_id']:r for r in self.roles['roles']}
    def test_exact_count_delta_and_unchanged_population(self):
        r=m.check(self.before,self.after,self.roles,self.q);self.assertEqual((r['scoped_families'],r['native'],r['upstream'],r['reviewed_uses'],r['pending_uses'],r['method_overlap'],r['missing']),(161,30,131,1769,955,16,254));self.assertEqual((r['unchanged_prior_reviews'],r['new_reviews'],r['refined_existing_reviews'],r['unchanged_unrelated_observations'],r['unchanged_unrelated_inventory_rows']),(1764,4,1,2719,445));self.assertFalse(r['whole_goal_complete'])
    def test_registry_preserves_prior_families_and_user_exclusion(self):
        rows=m.registry();self.assertEqual(len(rows),len({r['id']for r in rows}));self.assertEqual(len(rows),485);lookup={r['id']:r for r in rows};self.assertEqual(lookup['prsice']['current_coverage'],'upstream-adapter-validated');self.assertEqual(lookup['genomicsem']['current_coverage'],'upstream-adapter-validated');self.assertEqual(lookup['cellranger'],next(r for r in m.previous.registry()if r['id']=='cellranger'));inventory=json.loads(self.after[m.META[1]]);self.assertEqual(next(r for r in inventory if r['id']=='cellranger')['implementation_status'],'excluded-by-user')
    def test_product_names_are_not_patch_editions(self):
        papers=json.loads(self.after[m.META[0]]);uses=[next(u for u in next(x for x in papers if x['paper_id']==pid)['tools']if u['tool_id']=='prsice')for pid in p.POSITIVE];self.assertEqual([x['version_reported_near_name']for x in uses],[None,'2',None,'2',None]);self.assertEqual([x['source_role_qualification']['reported_version']for x in uses],['PRSice-2 v.2','PRSice v2','unreported','PRSice version 2','unreported'])
    def test_p076_panel_selection_and_partitions_remain_separate(self):
        r=self.rows()['P076'];self.assertIn('two study population panels',r['reported_settings']['LD_panel_selection']);self.assertIn('optimization/test and validation',r['reported_settings']['data_partitions']);self.assertIn('METAL meta-analysis',r['separate_methods']);self.assertFalse(r['paper_edition_qualified'])
    def test_p115_strict_filter_and_fold_semantics_survive(self):
        r=self.rows()['P115'];self.assertEqual(r['reported_settings']['external_GWAS_filters'],'INFO > 0.9 and MAF > 5 percent.');self.assertEqual(r['reported_settings']['covariates'],'20 genomic PCs and genotyping array.');self.assertIn('90 percent',r['reported_settings']['cross_validation']);self.assertIn('inclusive',m.NOTE);self.assertIn('explicit preselection',m.NOTE)
    def test_p128_ct_grid_and_prepared_weights_are_distinct(self):
        r=self.rows()['P128'];s=r['reported_settings'];self.assertEqual(s['clumping_R_squared'],[.1,.2,.3]);self.assertEqual(s['clumping_kb'],[100,250,500]);self.assertEqual(s['P_threshold_range'],[5e-8,.5]);self.assertIn('exact flags are absent',s['Bayesian_weight_scoring']);self.assertIn('LDpred2 posterior inference',r['separate_methods']);self.assertEqual(r['reported_version'],'unreported')
    def test_p147_cross_validation_is_not_inferred_from_association(self):
        r=self.rows()['P147'];self.assertIn('90 percent',r['reported_settings']['cross_validation']);self.assertIn('PRS pleiotropy',r['reported_settings']['outcomes']);self.assertIn('Fold construction and pleiotropy evaluation',r['separate_methods'])
    def test_p150_refinement_has_no_duplicate_review_credit(self):
        before=json.loads(self.before[m.META[5]])['reviews'];after=json.loads(self.after[m.META[5]])['reviews'];old=[r for r in before if r['tool_id']=='prsice'];new=[r for r in after if r['tool_id']=='prsice'];self.assertEqual((len(old),len(new)),(1,5));refined=next(r for r in new if r['pmcid']=='PMC11549047');self.assertEqual(refined['prior_review'],old[0]);self.assertEqual(self.rows()['P150']['reported_version'],'unreported');self.assertIn('Supplementary Table 39',self.rows()['P150']['reported_settings']['details'])
    def test_no_extra_native_or_historical_credit(self):
        self.assertFalse(self.q['historical_paper_editions_qualified']);self.assertFalse(self.q['original_paper_analyses_reexecuted']);r=next(x for x in json.loads(self.after[m.META[1]])if x['id']=='prsice');self.assertEqual((r['new_native_family_credit'],r['new_upstream_family_credit'],r['native_component_family_credit']),(0,1,0));self.assertEqual(r['prior_method_overlap_source'],'src/main/java/org/jlinalg/xwas/PredictionScores.java');self.assertFalse(r['released_binary_contains_workflow']);self.assertTrue(r['source_build_required'])
    def test_fabricated_patch_or_settings_are_refused(self):
        roles=copy.deepcopy(self.roles);roles['roles'][2]['reported_version']='2.3.5'
        with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)
        roles=copy.deepcopy(self.roles);roles['roles'][1]['reported_settings']['external_GWAS_filters']='INFO >= 0.9'
        with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)
    def test_expanded_convergence_or_historical_qualification_refused(self):
        q=copy.deepcopy(self.q);q['source_convergence_certificate']=True
        with self.assertRaises(ValueError):m.transition(self.before,self.roles,q)
        q=copy.deepcopy(self.q);q['historical_paper_editions_qualified']=True
        with self.assertRaises(ValueError):m.transition(self.before,self.roles,q)
    def test_prior_review_and_raw_source_changes_refused(self):
        changed=dict(self.after);papers=json.loads(changed[m.META[0]]);next(u for u in next(x for x in papers if x['paper_id']=='P128')['tools']if u['tool_id']=='prsice')['version_reported_near_name']='2.3.5';changed[m.META[0]]=p.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
        changed=dict(self.after);ledger=json.loads(changed[m.META[5]]);ledger['reviews'][0]['use_summary']='altered unrelated review';changed[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
    def test_double_admission_and_old_p150_loss_refused(self):
        with self.assertRaises(ValueError):m.transition(self.after,self.roles,self.q)
        changed=dict(self.after);ledger=json.loads(changed[m.META[5]]);next(r for r in ledger['reviews']if r['tool_id']=='prsice'and r['pmcid']=='PMC11549047')['prior_review']['use_summary']='lost previous bytes';changed[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
    def test_notices_and_previous_version_renderers_survive(self):
        for name in m.META[6:]:
            text=self.after[name].decode();self.assertEqual(text.count('id="prsice-catalog-notice"'),1);self.assertEqual(text.count('id="prsice-prior-catalog"'),1);self.assertIn('SKIP3',text);self.assertIn('FAIL2',text)
        text=self.after[m.META[6]].decode();self.assertEqual(text.count('${prsiceVersion(t)}'),1);self.assertEqual(text.count('function genomicsemVersion(t)'),1);self.assertEqual(text.count('function fastqtlVersion(t)'),1)

if __name__=='__main__':unittest.main()
