"""Protect paper-specific boundaries, unchanged extractions and cumulative counts."""
import copy,json,subprocess,unittest
from scenic_catalog import model as m
from scenic_catalog.catalog import BASE

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META}
        cls.primary=m.p.frozen();cls.q=json.loads((m.ROOT/'docs/literature/scenic-workflow-checkpoint-20261123/STATUS.json').read_bytes())
    def after(self):return m.transition(self.before,self.primary,self.q)
    def use(self,id):return next(u for p in json.loads(self.after()[m.META[0]]) if p['paper_id']==id for u in p['tools'] if u['tool_id']==m.TARGET)

    def test_one_upstream_family_five_reviews_and_all_unrelated_records_preserved(self):
        result=m.check(self.before,self.after(),self.primary,self.q)
        self.assertEqual((155,29,126,1702,1020,2722),tuple(result[k] for k in ('scoped_families','native','upstream','reviewed_uses','pending_uses','observations')))
        self.assertEqual((1697,2717,445),tuple(result[k] for k in ('unchanged_prior_reviews','unchanged_unrelated_observations','unchanged_unrelated_inventory_rows')))
        self.assertEqual((0,1,0,0),tuple(result[k] for k in ('new_native_family_credit','new_upstream_family_credit','added_positive_observations','removed_positive_observations')))

    def test_historical_p070_is_reviewed_but_its_analysis_stays_skipped(self):
        use=self.use('P070');self.assertIsNone(use['version_reported_near_name'])
        role=use['source_role_qualification'];self.assertEqual('0.10.3',role['reported_package_edition']);self.assertIn('SKIP3',role['historical_edition_analysis_status'])
        self.assertEqual(.1,role['reported_settings']['minimum_cluster_expression_fraction']);self.assertFalse(role['author_environment_reproduced'])

    def test_p100_context_edition_does_not_overwrite_unknown_near_name(self):
        use=self.use('P100');self.assertIsNone(use['version_reported_near_name']);role=use['source_role_qualification']
        self.assertEqual('0.12.1',role['reported_package_edition']);self.assertEqual(.05,role['reported_settings']['downsample_fraction_per_broad_cell_type'])
        self.assertTrue(role['reported_settings']['mask_dropouts']);self.assertIn('motifs-v9',role['boundary']);self.assertIn('membership are not inferred',role['boundary'])

    def test_p145_separate_fit_and_activity_is_not_silently_sampled(self):
        role=self.use('P145')['source_role_qualification'];self.assertEqual(20000,role['reported_settings']['fitting_nuclei'])
        self.assertIn('full-dataset',role['summary']);self.assertIn('Training subset and full activity matrix stay distinct',role['boundary']);self.assertIn('motifs-v10nr',role['boundary'])

    def test_p058_and_p225_unknown_versions_and_comparator_limit_remain(self):
        for id in ('P058','P225'):self.assertIsNone(self.use(id)['source_role_qualification']['reported_package_edition'])
        role=self.use('P058')['source_role_qualification'];self.assertEqual(dict(nes_threshold=2.5,min_genes=10),role['reported_settings'])
        self.assertIn('not reproduced results',self.use('P225')['source_role_qualification']['boundary'])

    def test_raw_extraction_fields_are_unchanged_for_every_role(self):
        old=json.loads(self.before[m.META[0]])
        for id in m.p.POSITIVE:
            use=next(u for p in old if p['paper_id']==id for u in p['tools'] if u['tool_id']==m.TARGET);current=self.use(id)
            for field in ('matched_text','extraction_score','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','evidence_locator','version_reported_near_name'):self.assertEqual(use[field],current[field],(id,field))

    def test_scenicplus_and_p153_are_not_silently_credited(self):
        old=json.loads(self.before[m.META[0]]);new=json.loads(self.after()[m.META[0]])
        self.assertEqual([u for p in old for u in p['tools'] if u['tool_id']=='scenicplus'],[u for p in new for u in p['tools'] if u['tool_id']=='scenicplus'])
        self.assertFalse(any(u['tool_id'] in ('scenic','scenicplus') for p in new if p['paper_id']=='P153' for u in p['tools']))

    def test_draft_stays_unapplied_and_inventory_matches_applied_roles(self):
        self.assertFalse(any(r['source_role_review_applied'] for r in self.primary['roles']))
        target=next(r for r in json.loads(self.after()[m.META[1]]) if r['id']==m.TARGET)
        self.assertTrue(target['upstream_runtime_adapter_required']);self.assertTrue(target['native_component_available']);self.assertFalse(target['released_binary_contains_workflow'])
        for id in m.p.POSITIVE:
            role=self.use(id)['source_role_qualification'];self.assertTrue(role['source_role_review_applied']);self.assertEqual(role,target['paper_role_qualification'][id])

    def test_missing_original_checks_or_wrong_producer_is_refused(self):
        for key in ('original_comparisons','workflow_scenarios','integrity_cases','integrity_commands','unit_methods_per_host','example_modes'):
            q=copy.deepcopy(self.q);q['live_checks'][key]-=1
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
        q=copy.deepcopy(self.q);q['producer']['sha256']='0'*64
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_false_completion_scope_or_duplicate_review_is_refused(self):
        for key in ('whole_tool_qualified','whole_goal_complete','catalog_admitted','bounded_workflow_qualification'):
            q=copy.deepcopy(self.q);q[key]=not q[key]
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
        before=dict(self.before);ledger=json.loads(before[m.META[5]]);ledger['reviews'][0].update(tool_id='scenic',pmcid=self.primary['roles'][0]['pmcid']);before[m.META[5]]=m.encoded(ledger)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)

    def test_changed_literal_version_or_role_interpretation_is_refused(self):
        before=dict(self.before);papers=json.loads(before[m.META[0]]);next(u for p in papers if p['paper_id']=='P070' for u in p['tools'] if u['tool_id']=='scenic')['version_reported_near_name']='0.10.3';before[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)
        primary=copy.deepcopy(self.primary);primary['roles'][1]['reported_package_edition']='0.12.1'
        with self.assertRaises(ValueError):m.transition(self.before,primary,self.q)

    def test_renderer_retains_previous_version_helpers_and_prominent_remedies(self):
        before=self.before[m.META[6]].decode();after=self.after()[m.META[6]].decode();block=before[before.index('function smartpcaVersion'):before.index('function renderContent(){')]
        self.assertIn(block,after);self.assertIn('${scenicVersion(t)}',after);self.assertIn('return smartpcaVersion(t)',after);self.assertIn('esc(q.reported_package_edition)',after)
        for name in (m.META[6],m.META[7]):self.assertEqual(1,self.after()[name].decode().count('id="scenic-catalog-notice"'))
        for text in ('SKIP3','FAIL2','full VERIFY','per-field eligibility','Warnings and required actions'):self.assertIn(text,m.NOTICE)

if __name__=='__main__':unittest.main()
