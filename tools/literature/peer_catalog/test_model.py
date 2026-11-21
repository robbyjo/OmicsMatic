import copy,json,os,re,unittest
from pathlib import Path
from peer_catalog import primary as p,model as m,evidence as e

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture=os.environ.get('PEER_EXACT_BEFORE')
        if fixture:
            from peer_catalog.catalog import read_before
            cls.before=read_before(Path(fixture));expected=json.loads((e.ROOT/e.OLD_PROOF).read_bytes())['after']
            if {n:p.pin(raw) for n,raw in cls.before.items()}!=expected:raise ValueError('Portable exact-before fixture differs')
        else:cls.before={n:e.git(e.ROOT,'show',e.BASE+':'+n) for n in m.META}
        cls.primary=p.frozen();cls.qualification=e.qualification()['result'];cls.after=m.transition(cls.before,cls.primary,cls.qualification)
        cls.papers={r['paper_id']:r for r in json.loads(cls.after[m.META[0]])}
    def use(self,pid):return next(r for r in self.papers[pid]['tools'] if r['tool_id']=='peer')
    def test_expected_upstream_family_counts_no_native_credit(self):
        r=m.check(self.before,self.after,self.primary,self.qualification)
        self.assertEqual((143,26,117,1616,1104,2,0),(r['integrations'],r['native_integrations'],r['upstream_integrations'],r['reviewed_uses'],r['pending_uses'],r['validated_family_alias_labels'],r['native_family_credit_added']))
    def test_all_old_reviews_exact_and18_unique_appended(self):
        old=json.loads(self.before[m.META[5]]);new=json.loads(self.after[m.META[5]])
        self.assertEqual(old['reviews'],new['reviews'][:1598]);self.assertEqual(1616,len(new['reviews']))
        self.assertEqual(1616,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
    def test_unrelated_observations_and445_inventory_rows_exact(self):
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):self.assertEqual([r for r in a['tools'] if r['tool_id']!='peer'],[r for r in b['tools'] if r['tool_id']!='peer'])
        self.assertEqual([r for r in json.loads(self.before[m.META[1]]) if r['id']!='peer'],[r for r in json.loads(self.after[m.META[1]]) if r['id']!='peer'])
    def test_only_three_expanded_name_additions_no_removals(self):
        for old in json.loads(self.before[m.META[0]]):self.assertEqual([r['tool_id'] for r in old['tools']]+(['peer'] if old['paper_id'] in ['P074','P107','P169'] else []),[r['tool_id'] for r in self.papers[old['paper_id']]['tools']])
    def test_only_reported_edition_p212_not_qtltools(self):
        for role in self.primary['roles']:self.assertEqual('1.3' if role['paper_id']=='P212' else None,self.use(role['paper_id'])['version_reported_near_name'])
        self.assertIn('QTLtools',self.use('P219')['implementation_scope_note'])
    def test_supplied_factors_and_sensitivity_roles_retained(self):
        for pid in ['P108','P193','P221','P234']:
            role=self.use(pid)['source_role_qualification'];self.assertEqual(next(r for r in self.primary['roles'] if r['paper_id']==pid),role)
            self.assertFalse(role['author_runtime_executed']);self.assertFalse(role['author_preparation_complete'])
        self.assertIn('sensitivity',self.use('P193')['use_summary'].lower())
    def test_pc_discrepancy_residual_definitions_and_wording_retained(self):
        for pid,term in [('P213','four'),('P219','BMI'),('P203','BIC'),('P249','logistic')]:
            use=self.use(pid);self.assertIn(term.lower(),(use['use_summary']+' '+use['implementation_scope_note']).lower())
    def test_literal_factor_counts_and_stratified_inference_retained(self):
        for pid in ['P019','P107','P169','P201','P212']:
            self.assertEqual(next(r for r in self.primary['roles'] if r['paper_id']==pid)['reported_factor_counts'],self.use(pid)['source_role_qualification']['reported_factor_counts'])
    def test_registry_only_peer_changed_and_pattern_expanded(self):
        from limma_catalog.model import registry as preceding
        old,new=preceding(),m.registry();self.assertEqual(485,len(new));self.assertEqual([r for r in old if r['id']!='peer'],[r for r in new if r['id']!='peer'])
        pattern=re.compile(next(r for r in new if r['id']=='peer')['pattern'],re.I)
        for value in ['PEER','probabilistic estimation of expression residuals','Probabilistic Estimation of Gene Expression Residuals','probabilistic-estimations-of-expression-residual']:
            self.assertIsNotNone(pattern.search(value),value)
        for value in ['peer review','peer-reviewed','Dan Peer','XPEER','PEER_more']:
            self.assertIsNone(pattern.search(value),value)
    def test_all_literal_source_names_match_admitted_pattern(self):
        pattern=re.compile(m.PATTERN,re.I)
        for role in self.primary['roles']:self.assertIsNotNone(pattern.fullmatch(role['source_name_witness']['literal']))
    def test_modified_old_review_and_unrelated_use_refused(self):
        for name in [m.META[0],m.META[5]]:
            changed=dict(self.after);value=json.loads(changed[name])
            if isinstance(value,list):value[0]['tools'][0]['use_summary']+=' changed'
            else:value['reviews'][0]['use_summary']+=' changed'
            changed[name]=m.encoded(value)
            with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
    def test_native_credit_and_invalid_qualification_refused(self):
        changed=dict(self.after);rows=json.loads(changed[m.META[1]]);next(r for r in rows if r['id']=='peer')['native_family_credit_added']=1;changed[m.META[1]]=m.encoded(rows)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
        for field,value in [('JVMs',0),('legacy_native_Peer_stock_parity_qualified',True)]:
            qualification=copy.deepcopy(self.qualification);qualification[field]=value
            with self.assertRaises(ValueError):m.transition(self.before,self.primary,qualification)
    def test_double_admission_extra_use_and_dropped_observation_refused(self):
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualification)
        for op in ['extra','drop']:
            changed=dict(self.after);papers=json.loads(changed[m.META[0]])
            if op=='extra':papers[0]['tools'].append(dict(tool_id='peer'))
            else:next(r for r in papers if r['paper_id']=='P074')['tools']=[r for r in next(r for r in papers if r['paper_id']=='P074')['tools'] if r['tool_id']!='peer']
            changed[m.META[0]]=m.encoded(papers)
            with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)
    def test_changed_before_identity_and_inventory_membership_refused(self):
        for name in [m.META[0],m.META[1]]:
            changed=dict(self.before);rows=json.loads(changed[name])
            if name==m.META[0]:rows[0]['doi']='wrong'
            else:next(r for r in rows if r['id']=='peer')['paper_count']=16
            changed[name]=m.encoded(rows)
            with self.assertRaises(ValueError):m.transition(changed,self.primary,self.qualification)
    def test_source_role_and_generic_candidate_tampering_refused(self):
        for field,value in [('kind','new-whole-paper-inference'),('author_runtime_executed',True)]:
            primary=copy.deepcopy(self.primary);primary['roles'][0][field]=value
            with self.assertRaises(ValueError):m.transition(self.before,primary,self.qualification)
    def test_embedded_report_and_top_notices_consistent(self):
        for name in m.META[6:]:
            text=self.after[name].decode();self.assertEqual(1,text.count('id="peer-catalog-notice"'));self.assertLess(text.index('id="peer-catalog-notice"'),text.index('id="limma-catalog-notice"'))
            self.assertIn('Warnings and required actions:',text);self.assertIn('1,104 pending',text)
        page=self.after[m.META[6]].decode();data=json.loads(re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S)[1]);self.assertEqual(json.loads(self.after[m.META[0]]),data['papers']);self.assertEqual(2720,data['summary']['paper_tool_observations'])

if __name__=='__main__':unittest.main()
