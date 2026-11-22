"""Independent invariants and false-admission controls for the nine-role catalog change."""
from pathlib import Path
import copy,json,unittest,zipfile
from monocle_catalog import model as m,primary as p,evidence as e
from catalog_source_integrity import reverse_delta
from wire_signac_catalog import delta

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        folder=p.ROOT/e.CONTROLS;fixture=json.loads((folder/'FIXTURE.json').read_bytes())
        with zipfile.ZipFile(folder/'metadata-before.zip') as archive:
            cls.before={name:archive.read(name) for name in archive.namelist()}
        assert set(cls.before)==set(m.META)
        assert fixture['before']=={name:p.pin(raw) for name,raw in cls.before.items()}
        cls.primary=p.frozen();cls.qualification=json.loads((folder/'QUALIFICATION-BASE.json').read_bytes())['result']
        cls.after=m.transition(cls.before,cls.primary,cls.qualification)

    def test_counts_credit_only_one_upstream_family(self):
        summary=json.loads(self.after[m.META[2]]);self.assertEqual(dict(scoped=145,native=26,upstream=119,including_existing_adapters=148,validated_alias_labels_excluded_from_extra_credit=2),summary['integration_family_counts'])
        self.assertEqual(119,summary['implementation_status_counts']['upstream-adapter-validated']);self.assertEqual(258,summary['implementation_status_counts']['missing']);self.assertEqual(29,summary['implementation_status_counts']['method-overlap'])

    def test_nine_existing_scopes_change_without_new_reviews_or_summaries(self):
        old=json.loads(self.before[m.META[5]])['reviews'];new=json.loads(self.after[m.META[5]])['reviews'];self.assertEqual(1630,len(new));self.assertEqual([(row['pmcid'],row['tool_id']) for row in old],[(row['pmcid'],row['tool_id']) for row in new]);changed=[]
        for index,(previous,current) in enumerate(zip(old,new,strict=True)):
            self.assertEqual(previous['use_summary'],current['use_summary'])
            if previous!=current:changed.append(index);self.assertIn(previous['tool_id'],['monocle','py-monocle']);self.assertEqual({key:value for key,value in previous.items() if key!='implementation_scope_note'},{key:value for key,value in current.items() if key!='implementation_scope_note'})
        self.assertEqual(10,len(changed));self.assertEqual(9,sum(old[index]['tool_id']=='monocle' for index in changed));self.assertEqual(1,sum(old[index]['tool_id']=='py-monocle' for index in changed))

    def test_other445_inventory_rows_and_separate_providers_are_exact(self):
        old=json.loads(self.before[m.META[1]]);new=json.loads(self.after[m.META[1]]);self.assertEqual([row for row in old if row['id']!='monocle'],[row for row in new if row['id']!='monocle'])
        for tool in ['batchelor','cicero','py-monocle']:
            self.assertEqual(next(row for row in old if row['id']==tool),next(row for row in new if row['id']==tool))

    def test_provider_registry_preserves_all_other484_definitions(self):
        from twosamplemr_catalog.model import registry as previous
        old=previous();new=m.registry();self.assertEqual(485,len(new));self.assertEqual([row for row in old if row['id']!='monocle'],[row for row in new if row['id']!='monocle']);row=next(row for row in new if row['id']=='monocle');self.assertEqual('upstream-adapter-validated',row['current_coverage']);self.assertEqual(m.PROGRAM,row['existing_source'])

    def test_paper_order_source_identity_membership_and_2722_observations_remain_exact(self):
        old=json.loads(self.before[m.META[0]]);new=json.loads(self.after[m.META[0]]);self.assertEqual(250,len(new));self.assertEqual(2722,sum(len(row['tools']) for row in new))
        for a,b in zip(old,new,strict=True):
            self.assertEqual({key:value for key,value in a.items() if key!='tools'},{key:value for key,value in b.items() if key!='tools'});self.assertEqual([row['tool_id'] for row in a['tools']],[row['tool_id'] for row in b['tools']])

    def test_context_and_unknown_editions_never_gain_paper_parity(self):
        roles={row['paper_id']:row for row in self.primary['roles']}
        self.assertEqual([],roles['P024']['qualified_component_commands']);self.assertEqual([],roles['P043']['qualified_component_commands']);self.assertIn('separate',roles['P024']['binding']);self.assertIn('platform',roles['P043']['binding'])
        for pid in ['P024','P043','P099','P134','P233']:self.assertIsNone(roles[pid]['reported_edition']);self.assertFalse(roles[pid]['reported_edition_component_controls_available'])
        for role in roles.values():self.assertFalse(role['whole_tool_qualified']);self.assertFalse(role['whole_paper_reproduced']);self.assertFalse(role['author_pipeline_executed'])

    def test_gene_models_are_no_longer_labelled_pseudotime(self):
        paper=next(row for row in json.loads(self.after[m.META[0]]) if row['paper_id']=='P085');use=next(row for row in paper['tools'] if row['tool_id']=='monocle');self.assertIn('GLMs',use['analysis_role']);self.assertNotIn('trajectory',use['analysis_role'].lower());self.assertIn('historical',use['implementation_scope_note']);self.assertIn('grouped-Holm',use['implementation_scope_note'])

    def test_current_embedded_report_is_identical_to_canonical_after_state(self):
        import re
        source=self.after[m.META[6]].decode();match=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',source,re.S);self.assertIsNotNone(match);self.assertEqual(dict(papers=json.loads(self.after[m.META[0]]),tools=json.loads(self.after[m.META[1]]),summary=json.loads(self.after[m.META[2]])),json.loads(match[1]));self.assertEqual(1,source.count('id="monocle-catalog-notice"'));self.assertIn('Previous TwoSampleMR checkpoint (historical)',source)

    def test_review_inverse_reconstructs_every_prior_byte_and_rejects_tampering(self):
        before=self.before[m.META[5]];after=self.after[m.META[5]];record=delta(m.META[5],before,after);self.assertEqual(before,reverse_delta(after,record))
        with self.assertRaises(ValueError):reverse_delta(after+b'\n',record)

    def test_all_1630_old_review_identities_are_still_unique(self):
        rows=json.loads(self.after[m.META[5]])['reviews'];self.assertEqual(1630,len({(row['pmcid'],row['tool_id']) for row in rows}));result=m.check(self.before,self.after,self.primary,self.qualification);self.assertEqual(0,result['new_review_records']);self.assertEqual(1620,result['unchanged_unrelated_reviews']);self.assertEqual(1,result['related_policy_scope_records_updated'])

def qualification_refusal(field,value):
    def test(self):
        changed=copy.deepcopy(self.qualification);changed[field]=value
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,changed)
    return test

for field,value in [('packaged_tests_per_JVM',888),('packaged_JVMs',2),('current_exact_edition_original_runs',3),('current_scientific_files_byte_identical',28),('native_response_cases',5),('native_response_values',983),('admitted_original_gene_tests',66),('retained_degenerate_family_refusals',0),('source_papers_per_host',249),('source_roles',8),('source_hosts',1),('global_command_registered',False),('new_catalog_credit',1),('whole_goal_complete',True)]:
    setattr(ModelTests,'test_qualification_refuses_'+field,qualification_refusal(field,value))

def source_refusal(field,value):
    def test(self):
        changed=copy.deepcopy(self.primary);changed['roles'][0][field]=value
        with self.assertRaises(ValueError):m.transition(self.before,changed,self.qualification)
    return test

for field,value in [('whole_paper_reproduced',True),('whole_tool_qualified',True),('author_pipeline_executed',True),('paper_executed_commit_confirmed',True),('reported_edition',None),('qualified_component_commands',['RUN']),('binding','complete-paper-parity')]:
    setattr(ModelTests,'test_source_refuses_'+field,source_refusal(field,value))

def before_refusal(change):
    def test(self):
        before=dict(self.before)
        if change=='duplicate-review':
            rows=json.loads(before[m.META[5]]);rows['reviews'].append(copy.deepcopy(rows['reviews'][0]));before[m.META[5]]=p.encoded(rows)
        elif change=='missing-role':
            rows=json.loads(before[m.META[0]]);paper=next(row for row in rows if row['paper_id']=='P024');paper['tools']=[row for row in paper['tools'] if row['tool_id']!='monocle'];before[m.META[0]]=p.encoded(rows)
        elif change=='prior-edition':
            rows=json.loads(before[m.META[0]]);paper=next(row for row in rows if row['paper_id']=='P085');next(row for row in paper['tools'] if row['tool_id']=='monocle')['version_reported_near_name']='1.4.27';before[m.META[0]]=p.encoded(rows)
        elif change=='separate-provider':
            rows=json.loads(before[m.META[1]]);next(row for row in rows if row['id']=='py-monocle')['implementation_status']='upstream-adapter-validated';before[m.META[1]]=p.encoded(rows)
        else:raise AssertionError(change)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.qualification)
    return test

for change in ['duplicate-review','missing-role','prior-edition','separate-provider']:setattr(ModelTests,'test_before_refuses_'+change.replace('-','_'),before_refusal(change))

if __name__=='__main__':unittest.main()
