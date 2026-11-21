import copy,os,unittest
from pathlib import Path
from gcta_catalog import primary as p

class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.primary=p.frozen()
    def test_fresh_all250_xml_hash_doi_and_role_audit_matches(self):
        self.assertEqual(self.primary,p.audit(Path(os.environ['GCTA_PRIMARY_CACHE'])))
    def test_population_includes_expanded_name_and_four_nonexecutions(self):
        self.assertEqual(250,len(self.primary['source_identities']));self.assertEqual(23,len(self.primary['candidates']));self.assertEqual(27,len(self.primary['roles']));self.assertEqual(19,len({r['paper_id'] for r in self.primary['roles']}));self.assertEqual({'P127','P177','P199','P204'},{r['paper_id'] for r in self.primary['exclusions']})
    def test_literal_source_name_case_and_paragraph_are_retained(self):
        role=next(r for r in self.primary['roles'] if r['paper_id']=='P169');self.assertEqual('genome-wide complex trait analysis',role['source_name_witness']['literal']);self.assertEqual(24,role['source_name_witness']['paragraph_index'])
    def test_reported_editions_and_p248_operation_labels_remain_distinct(self):
        a=next(r for r in self.primary['roles'] if r['paper_id']=='P090');b=next(r for r in self.primary['roles'] if r['paper_id']=='P248');self.assertEqual('1.92.4',a['reported_edition']);self.assertFalse(a['matching_runtime_edition_only']);self.assertEqual([('PCA','1.94.1'),('REML','1.94')],[(r['operation'],r['literal']) for r in b['reported_version_witnesses']])
    def test_wrong_edition_witness_is_refused(self):
        changed=copy.deepcopy(self.primary);changed['roles'][0]['reported_version_witnesses'][0]['literal']='1.94.1'
        with self.assertRaises(ValueError):p.validate(changed)
    def test_unknown_source_name_and_negative_offset_are_refused(self):
        for key,value in [('literal','renamed-tool'),('character_offset',-1)]:
            changed=copy.deepcopy(self.primary);changed['roles'][0]['source_name_witness'][key]=value
            with self.assertRaises(ValueError):p.validate(changed)
    def test_candidate_identity_or_hash_change_is_refused(self):
        for key,value in [('doi','10.invalid/example'),('existing_tool_ids',[])]:
            changed=copy.deepcopy(self.primary);changed['candidates'][0][key]=value
            with self.assertRaises(ValueError):p.validate(changed)
    def test_false_author_completion_or_exclusion_change_is_refused(self):
        changed=copy.deepcopy(self.primary);changed['roles'][0]['author_runtime_executed']=True
        with self.assertRaises(ValueError):p.validate(changed)
        changed=copy.deepcopy(self.primary);changed['exclusions']=changed['exclusions'][1:]
        with self.assertRaises(ValueError):p.validate(changed)
if __name__=='__main__':unittest.main()
