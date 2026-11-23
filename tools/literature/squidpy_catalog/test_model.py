"""Source-role, historical-edition and cumulative-credit admission regressions."""
import copy,json,subprocess,unittest
from squidpy_catalog import model as m

BASE='483805ba165efd89e632a32394f23d25ae822cac'


class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META}
        cls.primary=m.frozen_primary()
        cls.q=json.loads((m.ROOT/'docs/literature/squidpy-workflow-qualification/STATUS.json').read_bytes())

    def after(self):return m.transition(self.before,self.primary,self.q)

    def use(self,paper):
        return next(u for p in json.loads(self.after()[m.META[0]]) if p['paper_id']==paper for u in p['tools'] if u['tool_id']=='squidpy')

    def test_one_family_six_reviews_without_extra_native_or_observations(self):
        result=m.check(self.before,self.after(),self.primary,self.q)
        self.assertEqual((152,27,125,1685,1037,2722),tuple(result[k] for k in ('scoped_families','native','upstream','reviewed_uses','pending_uses','observations')))
        self.assertEqual((1679,2716,445),tuple(result[k] for k in ('unchanged_prior_reviews','unchanged_unrelated_observations','unchanged_unrelated_inventory_rows')))
        self.assertEqual((1,0,0,0),tuple(result[k] for k in ('new_upstream_family_credit','new_native_family_credit','added_positive_observations','removed_positive_observations')))

    def test_broader_edition_does_not_rewrite_near_name_unknown(self):
        row=self.use('P009');self.assertIsNone(row['version_reported_near_name'])
        role=row['source_role_qualification'];self.assertEqual(('1.1.2','3.8.13'),(role['reported_squidpy'],role['reported_python']))
        self.assertEqual(118,role['broader_version_evidence'][0]['paragraph_index'])
        self.assertFalse(role['original_author_environment_reproduced'])
        self.assertIn('3.8.20',row['implementation_scope_note'])
        self.assertIn(118,[a['paragraph_index'] for a in row['supporting_evidence']])

    def test_available_reader_is_not_an_established_paper_caller(self):
        row=self.use('P161');role=row['source_role_qualification']
        self.assertIsNone(role['reported_squidpy']);self.assertIn('historical reader/caller and edition are unreported',role['scope_limit'])
        self.assertIn('not an established historical invocation',role['scope_limit'])
        self.assertIn('FOV/sample partitioning',row['implementation_scope_note'])

    def test_unspecified_options_databases_and_custom_methods_stay_separate(self):
        self.assertIn('exact Squidpy functions and edition are not reported',self.use('P008')['use_summary'])
        self.assertIn('interaction snapshot',self.use('P010')['implementation_scope_note'])
        self.assertIn('temporal Spearman',self.use('P010')['implementation_scope_note'])
        self.assertIn('per nucleus and sample',self.use('P024')['use_summary'])
        self.assertIn('per-sample',self.use('P024')['implementation_scope_note'])
        self.assertIn('I_spatial minus sqrt(I_expression)',self.use('P094')['implementation_scope_note'])
        self.assertEqual('1.2.2',self.use('P094')['source_role_qualification']['reported_squidpy'])

    def test_other_provider_edition_renderer_is_preserved(self):
        before=self.before[m.META[6]].decode();after=self.after()[m.META[6]].decode()
        old=before[before.index('function gprofilerVersion'):before.index('function renderContent(){')]
        self.assertIn(old,after);self.assertIn('${squidpyVersion(t)}',after)
        self.assertIn("return gprofilerVersion(t)",after)
        self.assertIn('broader software paragraph; near-name edition ',after)
        self.assertIn('edition not reported',after)

    def test_missing_qualification_or_substituted_producer_refused(self):
        for key in ('normal_tests','packaged_cases','independent_original_comparisons','offline_controls','runtime_controls'):
            q=copy.deepcopy(self.q);q[key]-=1
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
        q=copy.deepcopy(self.q);q['producer']['sha256']='0'*64
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_broader_completion_claims_refused(self):
        for key in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'):
            q=copy.deepcopy(self.q);q[key]=True
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_changed_extraction_anchor_unknown_or_source_role_refused(self):
        for field,value in (('evidence_paragraph_sha256','0'*64),('version_reported_near_name','1.1.2')):
            before=dict(self.before);papers=json.loads(before[m.META[0]])
            next(u for p in papers if p['paper_id']=='P009' for u in p['tools'] if u['tool_id']=='squidpy')[field]=value
            before[m.META[0]]=m.encoded(papers)
            with self.subTest(field=field),self.assertRaises(ValueError):m.transition(before,self.primary,self.q)
        primary=copy.deepcopy(self.primary);primary['roles'][-1]['reported_squidpy']='1.8.4'
        with self.assertRaises(ValueError):m.transition(self.before,primary,self.q)

    def test_preexisting_target_review_cannot_be_credited_twice(self):
        before=dict(self.before);ledger=json.loads(before[m.META[5]])
        ledger['reviews'][0].update(tool_id='squidpy',pmcid=self.primary['roles'][0]['pmcid']);before[m.META[5]]=m.encoded(ledger)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)

    def test_unrelated_metadata_or_prior_review_tampering_refused(self):
        for filename,mutate in ((m.META[0],lambda x:x[0].update(doi='changed')),
                                (m.META[5],lambda x:x['reviews'][0].update(use_summary='changed'))):
            after=self.after();value=json.loads(after[filename]);mutate(value);after[filename]=m.encoded(value)
            with self.subTest(filename=filename),self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.q)


if __name__=='__main__':unittest.main()
