"""Protect six different roles, skipped dependencies and the missed positive."""
import copy,json,subprocess,unittest
from scenicplus_catalog import model as m
from scenicplus_catalog.catalog import BASE

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META}
        cls.primary=m.p.frozen();cls.q=json.loads((m.ROOT/m.QUALIFICATION).read_bytes());cls.after=m.transition(cls.before,cls.primary,cls.q)
    def use(self,id):return next(u for p in json.loads(self.after[m.META[0]]) if p['paper_id']==id for u in p['tools'] if u['tool_id']==m.TARGET)

    def test_exact_counts_and_unrelated_prior_reviews_preserved(self):
        r=m.check(self.before,self.after,self.primary,self.q)
        self.assertEqual((156,29,127,1707,1016,2723,1701,2717,445),tuple(r[k] for k in ('scoped_families','native','upstream','reviewed_uses','pending_uses','observations','unchanged_prior_reviews','unchanged_unrelated_observations','unchanged_unrelated_inventory_rows')))
        self.assertEqual((5,1,6),(r['new_reviews'],r['existing_reviews_enriched'],r['source_roles_reviewed']))
        self.assertEqual((0,1,1,0),tuple(r[k] for k in ('new_native_family_credit','new_upstream_family_credit','added_positive_observations','removed_positive_observations')))

    def test_resource_comparison_does_not_become_local_fitting(self):
        role=self.use('P021')['source_role_qualification'];self.assertIn('no-local-SCENICplus-fit',role['available_profile_role']);self.assertEqual(582,role['reported_settings']['external_eRegulons']);self.assertEqual(44,role['reported_settings']['key_TFs']);self.assertFalse(role['author_environment_reproduced'])

    def test_scanning_stays_skipped_and_extra_tf_annotation_and_NES_roles_exist(self):
        for id in ('P047','P061'):
            role=self.use(id)['source_role_qualification'];self.assertIn('ClusterBuster-scanning-SKIP3',role['available_profile_role']);self.assertIn('SKIP3',role['historical_edition_analysis_status']);self.assertEqual(8045,role['reported_settings']['resource_clusters']);self.assertEqual(1,len(role['additional_supporting_paragraphs']))
        self.assertIn('TF annotations',self.use('P047')['use_summary']);self.assertEqual(3,self.use('P061')['source_role_qualification']['reported_settings']['cisTarget_NES_threshold'])

    def test_random_metacells_and_SEACells_are_distinct(self):
        a=self.use('P008')['source_role_qualification'];b=self.use('P094')['source_role_qualification']
        self.assertEqual(150000,a['reported_settings']['reported_gene_window_bp']);self.assertIn('random same-label',a['reported_settings']['cell_matching'])
        self.assertEqual(75,b['reported_settings']['SEACells_target_cells_per_metacell']);self.assertEqual(15,b['reported_settings']['SEACells_initial_eigenvalues']);self.assertEqual(2000000,b['reported_settings']['search_space_upstream_bp']);self.assertIn('distinct',b['boundary'])

    def test_unknown_editions_and_reported_dependency_versions_stay_distinct(self):
        for id in m.p.POSITIVE:
            use=self.use(id);self.assertIsNone(use['version_reported_near_name']);self.assertIsNone(use['source_role_qualification']['reported_SCENICplus_edition']);self.assertFalse(use['source_role_qualification']['whole_paper_reproduced'])
        versions=self.use('P094')['source_role_qualification']['reported_version_contexts'];self.assertEqual(['0.3.3','2.0a0','1.8.2'],[r['version'] for r in versions]);self.assertFalse(any(r['SCENICplus_edition'] for r in versions))

    def test_P153_is_one_manual_benchmark_positive_with_no_fabricated_score_or_version(self):
        use=self.use('P153');self.assertIsNone(use['extraction_score']);self.assertEqual(11,use['evidence_paragraph_index']);self.assertIn('manual',use['evidence_selection']);self.assertEqual('SCENIC+',use['matched_text'])
        r=use['source_role_qualification'];self.assertEqual('v1.2',r['reported_settings']['author_analysis_release']);self.assertIsNone(r['reported_settings']['author_SCENICplus_version']);self.assertIsNone(r['reported_settings']['author_seed']);self.assertEqual([1000000,150000],r['reported_settings']['author_notebook_search_bounds_bp']);self.assertIn('does not define',r['boundary'])

    def test_existing_raw_extractions_unchanged(self):
        for r in self.primary['roles'][:-1]:
            use=self.use(r['paper_id'])
            for key in ('matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):self.assertEqual(r['original_observation'][key],use[key])

    def test_false_qualification_and_interpretation_are_refused(self):
        for key,value in [('main_route_checks',24),('scientific_export_comparisons',53),('boundary_controls',19),('global_dispatch_integrated',False),('whole_goal_complete',True),('whole_SCENICplus_tool_qualified',True),('catalog_admitted',True)]:
            q=copy.deepcopy(self.q);q[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
        roles=copy.deepcopy(self.primary);roles['roles'][-1]['reported_SCENICplus_edition']='1.2'
        with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)

    def test_duplicate_positive_or_review_is_refused(self):
        before=dict(self.before);papers=json.loads(before[m.META[0]]);next(p for p in papers if p['paper_id']=='P153')['tools'].append(m.added_observation(self.primary['roles'][-1]));before[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)
        before=dict(self.before);ledger=json.loads(before[m.META[5]]);ledger['reviews'][0]['tool_id']=m.TARGET;before[m.META[5]]=m.encoded(ledger)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)

    def test_unrelated_observation_and_prior_review_tampering_is_refused(self):
        after=dict(self.after);papers=json.loads(after[m.META[0]]);papers[0]['tools'][0]['use_summary']='changed unrelated';after[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.q)
        after=dict(self.after);ledger=json.loads(after[m.META[5]]);ledger['reviews'][0]['use_summary']='changed prior';after[m.META[5]]=m.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.q)

    def test_registry_inventory_and_review_objects_agree(self):
        row=next(r for r in json.loads(self.after[m.META[1]]) if r['id']==m.TARGET);self.assertEqual(6,row['paper_count']);self.assertEqual(m.p.POSITIVE,row['papers']);self.assertTrue(row['global_CLI_dispatch_available']);self.assertFalse(row['released_binary_contains_workflow']);self.assertFalse(row['native_component_available'])
        for id in m.p.POSITIVE:self.assertEqual(row['paper_role_qualification'][id],self.use(id)['source_role_qualification'])

    def test_old_renderers_are_preserved_and_current_warning_notice_is_unambiguous(self):
        before=self.before[m.META[6]].decode();after=self.after[m.META[6]].decode();block=before[before.index('function scenicVersion'):before.index('function renderContent(){')];self.assertIn(block,after);self.assertIn('${scenicplusVersion(t)}',after);self.assertIn('return scenicVersion(t)',after)
        for name in (m.META[6],m.META[7]):self.assertEqual(1,self.after[name].decode().count('id="scenicplus-catalog-notice"'))
        self.assertIn('Before SCENIC+ admission (historical)',after)
        for text in ('SKIP3','FAIL2','full VERIFY','NaN/Inf','Warnings and required actions'):self.assertIn(text,m.NOTICE)

if __name__=='__main__':unittest.main()
