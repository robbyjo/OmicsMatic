"""Check append-only reviews, source-specific versions and unrelated-use retention."""
from pathlib import Path
import copy,json,os,unittest,zipfile
from gsea_catalog import model as m,primary as p,evidence as e

class GseaModelControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture=os.environ.get('OMICSMATIC_GSEA_MODEL_BEFORE')
        if fixture:
            with zipfile.ZipFile(Path(fixture)) as z:
                if len(z.namelist())!=8 or set(z.namelist())!=set(m.META):raise ValueError('Different portable exact-before fixture population')
                cls.before={n:z.read(n) for n in m.META}
            expected=json.loads((e.ROOT/e.OLD_PROOF).read_bytes())['after']
            if {n:e.pin(raw) for n,raw in cls.before.items()}!=expected:raise ValueError('Portable exact-before fixture differs from admitted predecessor')
        else:cls.before={n:e.git(e.ROOT,'show',e.BASE+':'+n) for n in m.META}
        cls.primary=p.frozen();cls.qualification=e.native_qualification()['result'];cls.after=m.transition(cls.before,cls.primary,cls.qualification)

    def test_all_1520_old_reviews_are_exact_and_new_records_unique(self):
        old=json.loads(self.before[m.META[5]])['reviews'];new=json.loads(self.after[m.META[5]])['reviews'];self.assertEqual(old,new[:1520]);self.assertEqual(1548,len(new));self.assertEqual(1548,len({(r['pmcid'],r['tool_id']) for r in new}))

    def test_every_unrelated_tool_use_and_paper_identity_is_preserved(self):
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):
            self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'})
            self.assertEqual([t for t in a['tools'] if t['tool_id']!='gsea'],[t for t in b['tools'] if t['tool_id']!='gsea'])

    def test_other_445_inventory_rows_are_exact(self):
        self.assertEqual([r for r in json.loads(self.before[m.META[1]]) if r['id']!='gsea'],[r for r in json.loads(self.after[m.META[1]]) if r['id']!='gsea'])

    def test_alternate_provider_versions_do_not_become_Broad_versions(self):
        uses={x['paper_id']:next((t for t in x['tools'] if t['tool_id']=='gsea'),None) for x in json.loads(self.after[m.META[0]])}
        self.assertIsNone(uses['P040']['version_reported_near_name']);self.assertIsNone(uses['P096']['version_reported_near_name'])
        self.assertEqual('4.3.2',uses['P077']['version_reported_near_name']);self.assertEqual('4.3.3',uses['P220']['version_reported_near_name'])
        self.assertEqual('fgsea',uses['P182']['source_role_qualification']['reported_provider'])

    def test_tampered_old_review_is_refused(self):
        after=dict(self.after);reviews=json.loads(after[m.META[5]]);reviews['reviews'][0]['use_summary']+=' altered';after[m.META[5]]=m.encoded(reviews)
        with self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.qualification)

    def test_unreviewed_tool_addition_is_refused(self):
        after=dict(self.after);papers=json.loads(after[m.META[0]]);papers[0]['tools'].append(dict(tool_id='gsea',tool='GSEA'));after[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.qualification)

    def test_bad_qualification_cannot_admit_family(self):
        qualification=copy.deepcopy(self.qualification);qualification['previous_captures_VERIFY_REPLAY']=False
        with self.assertRaisesRegex(ValueError,'qualification required'):m.transition(self.before,self.primary,qualification)

    def test_double_transition_is_refused(self):
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualification)

if __name__=='__main__':unittest.main()
