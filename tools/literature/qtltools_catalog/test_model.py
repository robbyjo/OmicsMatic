"""Independent preservation/count checks and false-admission controls for the catalog transition."""
import copy,json,re,unittest,zipfile
from qtltools_catalog import primary as p,model as m,evidence as e
from catalog_source_integrity import reverse_delta
from wire_signac_catalog import delta

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        folder=p.ROOT/e.CONTROLS;fixture=json.loads((folder/'FIXTURE.json').read_bytes())
        with zipfile.ZipFile(folder/'metadata-before.zip') as archive:cls.before={n:archive.read(n) for n in archive.namelist()}
        assert fixture['before']=={n:p.pin(raw) for n,raw in cls.before.items()}
        cls.primary=p.frozen();cls.qualification=json.loads((folder/'QUALIFICATION-BASE.json').read_bytes())['result'];cls.after=m.transition(cls.before,cls.primary,cls.qualification)

    def test_only_one_upstream_family_is_added(self):
        summary=json.loads(self.after[m.META[2]]);self.assertEqual(dict(scoped=146,native=26,upstream=120,including_existing_adapters=149,validated_alias_labels_excluded_from_extra_credit=2),summary['integration_family_counts']);self.assertEqual(28,summary['implementation_status_counts']['method-overlap']);self.assertEqual(258,summary['implementation_status_counts']['missing'])

    def test_prior1630_reviews_are_an_exact_unchanged_prefix(self):
        old=json.loads(self.before[m.META[5]])['reviews'];new=json.loads(self.after[m.META[5]])['reviews'];self.assertEqual(old,new[:1630]);self.assertEqual(1639,len(new));self.assertEqual(9,sum(r['tool_id']=='qtltools' for r in new));self.assertEqual(len(new),len({(r['pmcid'],r['tool_id']) for r in new}))

    def test_101_earlier_review_marker_spellings_remain_unchanged(self):
        marker='individually-reviewed-primary-source-roles; whole-paper-method-review-open'
        keys=lambda raw:{(paper['pmcid'],r['tool_id']) for paper in json.loads(raw) for r in paper['tools'] if r['review_status']==marker}
        before=keys(self.before[m.META[0]]);self.assertEqual(101,len(before));self.assertEqual(before,keys(self.after[m.META[0]]))

    def test_paper_identities_order_and_2713_other_observations_are_preserved(self):
        old=json.loads(self.before[m.META[0]]);new=json.loads(self.after[m.META[0]]);unrelated=0
        for a,b in zip(old,new,strict=True):
            self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'});self.assertEqual([r['tool_id'] for r in a['tools']],[r['tool_id'] for r in b['tools']]);aa=[r for r in a['tools'] if r['tool_id']!='qtltools'];bb=[r for r in b['tools'] if r['tool_id']!='qtltools'];self.assertEqual(aa,bb);unrelated+=len(aa)
        self.assertEqual(2713,unrelated);self.assertEqual(2722,sum(len(paper['tools']) for paper in new))

    def test_other445_inventory_rows_and484_definitions_stay_exact(self):
        a,b=json.loads(self.before[m.META[1]]),json.loads(self.after[m.META[1]]);self.assertEqual([r for r in a if r['id']!='qtltools'],[r for r in b if r['id']!='qtltools'])
        from monocle_catalog.model import registry as previous
        a,b=previous(),m.registry();self.assertEqual(485,len(b));self.assertEqual([r for r in a if r['id']!='qtltools'],[r for r in b if r['id']!='qtltools']);row=next(r for r in b if r['id']=='qtltools');self.assertEqual(m.PROGRAM,row['existing_source']);self.assertEqual('upstream-adapter-validated',row['current_coverage'])

    def role(self,pid):return next(r for r in self.primary['roles'] if r['paper_id']==pid)

    def test_p219_does_not_borrow_mapping_edition_for_quan(self):
        role=self.role('P219');self.assertIsNone(role['reported_edition']);self.assertEqual([None,'1.2','1.2'],[r['edition'] for r in role['operations']]);self.assertIn('two residual',role['implementation_scope_note']);self.assertEqual('method-details',role['supporting_evidence'][-1]['evidence_role']);self.assertEqual(29,role['supporting_evidence'][-1]['named_anchor']['paragraph_index'])

    def test_p190_supplement_resolves_execution_without_inventing_edition(self):
        role=self.role('P190');self.assertIsNone(role['reported_edition']);self.assertEqual(p.PDF_PIN,role['auxiliary_evidence'][0]['pdf']);self.assertIn('--grp-best',role['usage_summary']);self.assertIn('--permute 1000',role['usage_summary']);self.assertIn('adaptive',role['implementation_scope_note']);self.assertIn('S1.4',role['implementation_scope_note']);self.assertIn('S1.5',role['implementation_scope_note']);self.assertFalse(role['author_pipeline_executed'])

    def test_p227_citation27_is_not_part_of_edition(self):
        role=self.role('P227');self.assertEqual('1.2',role['reported_edition']['literal']);self.assertEqual('27',role['citation_version_distinction']['bibliography_label']);self.assertNotIn('1.2.27',role['reported_edition']['package_and_edition_witness'])

    def test_p073_bare_group_is_not_replaced_by_best(self):
        role=self.role('P073');self.assertIn('does not identify best/pca1/mean',role['implementation_scope_note']);self.assertIsNone(role['operations'][1]['edition']);self.assertEqual('1.2',role['operations'][0]['edition'])

    def test_p104_does_not_claim_annotation_multiplicity_or_enrichment_test(self):
        role=self.role('P104');self.assertIn('not annotation multiplicity',role['implementation_scope_note']);self.assertIn('explicit radius',role['implementation_scope_note']);row=next(r for r in json.loads(self.after[m.META[1]]) if r['id']=='qtltools');self.assertEqual(0,row['native_helpers_additional_family_credit']);self.assertFalse(row['whole_tool_qualified'])

    def test_unknown_modes_and_match_are_retained(self):
        self.assertEqual([],self.role('P074')['operations'][0]['operations']);self.assertEqual('literal-match',self.role('P203')['operations'][1]['role']);self.assertEqual([],self.role('P203')['operations'][1]['operations']);self.assertIn('fixed',self.role('P201')['implementation_scope_note'])

    def test_no_role_claims_author_or_whole_paper_execution(self):
        for role in self.primary['roles']:
            self.assertFalse(role['author_pipeline_executed']);self.assertFalse(role['whole_paper_reproduced']);self.assertFalse(role['paper_executed_commit_confirmed']);self.assertTrue(all(not op['paper_command_or_binary_reproduced'] for op in role['operations']))

    def test_embedded_report_equals_full_canonical_payload(self):
        html=self.after[m.META[6]].decode();match=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',html,re.S);self.assertIsNotNone(match);self.assertEqual(dict(papers=json.loads(self.after[m.META[0]]),tools=json.loads(self.after[m.META[1]]),summary=json.loads(self.after[m.META[2]])),json.loads(match[1]));self.assertEqual(1,html.count('id="qtltools-catalog-notice"'));self.assertIn('Before QTLtools admission (historical)',html)

    def test_warnings_give_skip_failure_and_private_transfer_actions(self):
        for name in [m.META[4],m.META[7]]:
            text=self.after[name].decode()
            for token in ['SKIP3','FAIL2','NO COMPLETE RESULT','private','NEW output','Cell Ranger']:self.assertIn(token,text)

    def test_review_inverse_restores_every_predecessor_byte(self):
        a,b=self.before[m.META[5]],self.after[m.META[5]];inverse=delta(m.META[5],a,b);self.assertEqual(a,reverse_delta(b,inverse))
        with self.assertRaises(ValueError):reverse_delta(b+b'\n',inverse)

    def test_complete_independent_count_result(self):
        r=m.check(self.before,self.after,self.primary,self.qualification);self.assertEqual((146,1639,1083,1630,2713),(r['integrations'],r['reviewed_uses'],r['pending_uses'],r['unchanged_prior_reviews'],r['unchanged_unrelated_observations']));self.assertEqual(1,r['new_scoped_integration_credit']);self.assertFalse(r['whole_goal_complete'])

    def test_duplicate_transition_is_refused(self):
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualification)

    def test_changed_old_review_is_refused(self):
        changed=copy.deepcopy(self.after);records=json.loads(changed[m.META[5]]);records['reviews'][0]['use_summary']+=' changed';changed[m.META[5]]=p.encoded(records)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)

    def test_unrelated_observation_change_is_refused(self):
        changed=copy.deepcopy(self.after);papers=json.loads(changed[m.META[0]]);papers[0]['tools'][0]['use_summary']+=' changed';changed[m.META[0]]=p.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)

    def test_unrelated_inventory_change_is_refused(self):
        changed=copy.deepcopy(self.after);rows=json.loads(changed[m.META[1]]);next(r for r in rows if r['id']!='qtltools')['implementation_status']='missing';changed[m.META[1]]=p.encoded(rows)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.primary,self.qualification)

    def test_forged_prior_scientific_credit_is_refused(self):
        q=copy.deepcopy(self.qualification);q['new_catalog_credit']=1
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_forged_model_qualification_is_refused(self):
        q=copy.deepcopy(self.qualification);q['complete_direct_original_files']=323
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_invented_p190_edition_is_refused(self):
        source=copy.deepcopy(self.primary);role=next(r for r in source['roles'] if r['paper_id']=='P190');role['reported_edition']=dict(literal='1.3.1')
        with self.assertRaises(ValueError):m.transition(self.before,source,self.qualification)

    def test_borrowed_quan_edition_is_refused(self):
        source=copy.deepcopy(self.primary);role=next(r for r in source['roles'] if r['paper_id']=='P219');role['operations'][0]['edition']='1.2'
        with self.assertRaises(ValueError):m.transition(self.before,source,self.qualification)

    def test_changed_source_anchor_is_refused(self):
        source=copy.deepcopy(self.primary);source['roles'][0]['primary_evidence']['paragraph_sha256']='0'*64
        with self.assertRaises(ValueError):m.transition(self.before,source,self.qualification)

    def test_invented_paper_execution_is_refused(self):
        source=copy.deepcopy(self.primary);source['roles'][0]['author_pipeline_executed']=True
        with self.assertRaises(ValueError):m.transition(self.before,source,self.qualification)

    def test_parser_does_not_truncate_genuine_three_component_edition(self):
        block=dict(paragraph_index=1,text='QTLtools MBV v.1.2.27')
        self.assertEqual('1.2.27',p.versions(block)[0]['literal'])

if __name__=='__main__':unittest.main()
