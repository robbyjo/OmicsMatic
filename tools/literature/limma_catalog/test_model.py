import copy,json,os,unittest
from pathlib import Path
from limma_catalog import primary as p,model as m,evidence as e

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture=os.environ.get('LIMMA_EXACT_BEFORE')
        if fixture:
            from limma_catalog.catalog import read_before
            cls.before=read_before(Path(fixture))
            expected=json.loads((e.ROOT/e.OLD_PROOF).read_bytes())['after']
            if {n:p.pin(raw) for n,raw in cls.before.items()}!=expected:raise ValueError('Portable exact-before fixture differs')
        else:cls.before={n:e.git(e.ROOT,'show',e.BASE+':'+n) for n in m.META}
        cls.primary=p.frozen();cls.qualification=e.qualification()['result'];cls.after=m.transition(cls.before,cls.primary,cls.qualification)
        cls.papers={r['paper_id']:r for r in json.loads(cls.after[m.META[0]])}
    def use(self,pid,tool='limma'):return next(r for r in self.papers[pid]['tools'] if r['tool_id']==tool)
    def test_expected_family_counts_and_zero_alias_credit(self):
        result=m.check(self.before,self.after,self.primary,self.qualification)
        self.assertEqual((142,26,116,1598,1119,2),(result['integrations'],result['native_integrations'],result['upstream_integrations'],result['reviewed_uses'],result['pending_uses'],result['validated_family_alias_labels']))
        row=next(r for r in json.loads(self.after[m.META[1]]) if r['id']=='voom')
        self.assertEqual(('validated-family-alias','limma',0),(row['implementation_status'],row['family_alias_of'],row['new_scoped_integration_credit']))
    def test_all_old_reviews_exact_and23_unique_appended(self):
        old=json.loads(self.before[m.META[5]]);new=json.loads(self.after[m.META[5]])
        self.assertEqual(old['reviews'],new['reviews'][:1575]);self.assertEqual(1598,len(new['reviews']))
        self.assertEqual(1598,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
    def test_all_unrelated_observations_and444_inventory_rows_exact(self):
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):
            self.assertEqual([r for r in a['tools'] if r['tool_id'] not in ['limma','voom']],[r for r in b['tools'] if r['tool_id'] not in ['limma','voom']])
        self.assertEqual([r for r in json.loads(self.before[m.META[1]]) if r['id'] not in ['limma','voom']],[r for r in json.loads(self.after[m.META[1]]) if r['id'] not in ['limma','voom']])
    def test_three_additions_historical_removal_scrattch_and_edger_retained(self):
        old={r['paper_id']:r for r in json.loads(self.before[m.META[0]])}
        for pid in ['P088','P183','P203']:self.assertEqual([r['tool_id'] for r in old[pid]['tools']]+['limma'],[r['tool_id'] for r in self.papers[pid]['tools']])
        self.assertEqual([r for r in old['P059']['tools'] if r['tool_id']!='limma'],self.papers['P059']['tools'])
        self.assertEqual(old['P237'],self.papers['P237'])
    def test_two_methylation_editions_and_uninvented344_label(self):
        use=self.use('P086');self.assertIsNone(use['version_reported_near_name'])
        self.assertEqual(['3.58.1','3.50.3'],[r['literal'] for r in use['source_role_qualification']['reported_editions']])
        self.assertEqual('3.44',self.use('P195')['version_reported_near_name'])
    def test_unresolved_author_provider_and_spelling_retained(self):
        use=self.use('P228');self.assertTrue(use['source_role_qualification']['provider_attribution_unresolved'])
        self.assertIn('voomLmFit',use['use_summary']);self.assertIn('lmfit',use['use_summary'])
        self.assertFalse(use['source_role_qualification']['author_runtime_executed'])
    def test_preprocesscore_and_custom_residualization_not_credited_as_limma(self):
        self.assertIn('preprocessCore',self.use('P203')['use_summary'])
        self.assertIn('not credited to limma',self.use('P203')['implementation_scope_note'])
        self.assertIn('residual',self.use('P183')['implementation_scope_note'])
        for pid in ['P183','P203']:
            role=self.use(pid)['source_role_qualification']
            self.assertFalse(role['preparation_and_author_settings_complete']);self.assertFalse(role['linked_code_source']['paper_executed_commit_correspondence_proven'])
    def test_voom_labels_exclude_family_batch_credit(self):
        role=self.use('P182','voom')['source_role_qualification']
        self.assertNotIn('BATCH',role['qualified_related_current_operations']);self.assertNotIn('batch',self.use('P182','voom')['analysis_role'].lower())
        self.assertIn('BATCH',self.use('P182')['source_role_qualification']['qualified_related_current_operations'])
    def test_modified_old_review_or_unrelated_observation_refused(self):
        for name in [m.META[0],m.META[5]]:
            changed=dict(self.after);value=json.loads(changed[name])
            if isinstance(value,list):value[0]['tools'][0]['use_summary']+=' changed'
            else:value['reviews'][0]['use_summary']+=' changed'
            changed[name]=m.encoded(value)
            with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
    def test_second_alias_credit_and_bad_qualification_refused(self):
        changed=dict(self.after);rows=json.loads(changed[m.META[1]])
        next(r for r in rows if r['id']=='voom')['new_scoped_integration_credit']=1;changed[m.META[1]]=m.encoded(rows)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
        qualification=copy.deepcopy(self.qualification);qualification['JVMs']=0
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,qualification)
    def test_unreviewed_extra_use_and_double_admission_refused(self):
        changed=dict(self.after);papers=json.loads(changed[m.META[0]]);papers[0]['tools'].append(dict(tool_id='limma'));changed[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualification)
    def test_registry_changes_only_limma_voom_keeps_other_definitions_exact(self):
        from gcta_catalog.model import registry as preceding
        old,new=preceding(),m.registry();self.assertEqual(485,len(new))
        self.assertEqual([r for r in old if r['id'] not in ['limma','voom']],[r for r in new if r['id'] not in ['limma','voom']])
        self.assertEqual('validated-family-alias',next(r for r in new if r['id']=='voom')['current_coverage'])

if __name__=='__main__':unittest.main()
