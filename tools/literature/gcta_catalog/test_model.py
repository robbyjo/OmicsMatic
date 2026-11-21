import copy,json,os,unittest
from pathlib import Path
from gcta_catalog import primary as p,model as m,evidence as e

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture=os.environ.get('GCTA_EXACT_BEFORE')
        if fixture:
            from gcta_catalog.catalog import read_before
            cls.before=read_before(Path(fixture));expected=json.loads((e.ROOT/e.OLD_PROOF).read_bytes())['after']
            if {n:p.pin(raw) for n,raw in cls.before.items()}!=expected:raise ValueError('Portable exact-before fixture differs from qualified preceding catalog')
        else:cls.before={n:e.git(e.ROOT,'show',e.BASE+':'+n) for n in m.META}
        cls.primary=p.frozen();cls.qualification=e.qualification()['result'];cls.after=m.transition(cls.before,cls.primary,cls.qualification)
    def test_one_upstream_family_one_alias_and_expected_counts(self):
        result=m.check(self.before,self.after,self.primary,self.qualification);self.assertEqual((141,26,115,1575,1140),(result['integrations'],result['native_integrations'],result['upstream_integrations'],result['reviewed_uses'],result['pending_uses']));rows=json.loads(self.after[m.META[1]]);alias=next(r for r in rows if r['id']=='cojo');self.assertEqual('validated-family-alias',alias['implementation_status']);self.assertEqual(0,alias['new_scoped_integration_credit'])
    def test_all1548_old_reviews_exact_and27_unique_appended(self):
        old=json.loads(self.before[m.META[5]]);new=json.loads(self.after[m.META[5]]);self.assertEqual(old['reviews'],new['reviews'][:1548]);self.assertEqual(1575,len(new['reviews']));self.assertEqual(1575,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
    def test_all_unrelated_observations_and444_inventory_rows_exact(self):
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):self.assertEqual([r for r in a['tools'] if r['tool_id'] not in ['gcta','cojo']],[r for r in b['tools'] if r['tool_id'] not in ['gcta','cojo']])
        self.assertEqual([r for r in json.loads(self.before[m.META[1]]) if r['id'] not in ['gcta','cojo']],[r for r in json.loads(self.after[m.META[1]]) if r['id'] not in ['gcta','cojo']])
    def test_two_additions_and_comparison_only_removal_preserve_order(self):
        old={r['paper_id']:r for r in json.loads(self.before[m.META[0]])};new={r['paper_id']:r for r in json.loads(self.after[m.META[0]])}
        for pid in ['P112','P169']:self.assertEqual([r['tool_id'] for r in old[pid]['tools']]+['gcta'],[r['tool_id'] for r in new[pid]['tools']])
        self.assertEqual([r for r in old['P127']['tools'] if r['tool_id']!='cojo'],new['P127']['tools'])
    def test_p090_both_roles_p169_panel_warning_and_unconstrained_estimates(self):
        papers={r['paper_id']:r for r in json.loads(self.after[m.META[0]])};use=lambda pid:next(r for r in papers[pid]['tools'] if r['tool_id']=='gcta')
        self.assertEqual(24,use('P090')['evidence_paragraph_index']);self.assertIn('MTCOJO',use('P090')['analysis_role']);self.assertIn('respectively',use('P169')['implementation_scope_note']);self.assertIn('outside 0..1',use('P238')['implementation_scope_note'])
    def test_mixed_p248_edition_does_not_become_one_nearby_version(self):
        paper=next(r for r in json.loads(self.after[m.META[0]]) if r['paper_id']=='P248');use=next(r for r in paper['tools'] if r['tool_id']=='gcta');self.assertIsNone(use['version_reported_near_name']);self.assertIn('PCA edition: 1.94.1; REML edition label: 1.94',use['use_summary'])
    def test_modified_old_review_or_unrelated_observation_refused(self):
        for name in [m.META[0],m.META[5]]:
            changed=dict(self.after);value=json.loads(changed[name])
            # Alter a known protected record independently of formatting.
            if isinstance(value,list):value[0]['tools'][0]['use_summary']+=' changed'
            else:value['reviews'][0]['use_summary']+=' changed'
            changed[name]=m.encoded(value)
            with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
    def test_second_alias_credit_and_bad_qualification_refused(self):
        changed=dict(self.after);rows=json.loads(changed[m.META[1]]);next(r for r in rows if r['id']=='cojo')['new_scoped_integration_credit']=1;changed[m.META[1]]=m.encoded(rows)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
        qualification=copy.deepcopy(self.qualification);qualification['JVMs']=0
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,qualification)
    def test_unreviewed_extra_use_and_double_admission_refused(self):
        changed=dict(self.after);papers=json.loads(changed[m.META[0]]);papers[0]['tools'].append(dict(tool_id='gcta'));changed[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualification)
if __name__=='__main__':unittest.main()
