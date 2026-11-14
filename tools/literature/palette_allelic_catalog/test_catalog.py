# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise version substitution, source-support loss and scientific-overlay refusals."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from palette_allelic_catalog.model import ROOT,META,PAPERS,REVIEWS,EVIDENCE,CURATION,NOTICE,encoded,transition,check_transition,reviews
from palette_allelic_catalog.bridge import load_before,metadata_view
from palette_allelic_catalog.source import validate
from palette_allelic_catalog.qualification import qualification

class PaletteAllelicCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT);cls.after=transition(ROOT,cls.before)
        cls.papers=json.loads(cls.before[PAPERS]);cls.census=json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes())
    def reject(self,path,change):
        after=dict(self.after);value=json.loads(after[path]);change(value);after[path]=encoded(value)
        with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
    def test_all430_previous_family_records_preserved(self):
        self.assertEqual(json.loads(self.before[META[0]]),json.loads(self.after[META[0]])[:430])
    def test_all250_paper_identities_and2669_observations_preserved(self):
        added=[]
        for old,new in zip(self.papers,json.loads(self.after[PAPERS]),strict=True):
            self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'})
            self.assertEqual(old['tools'],new['tools'][:len(old['tools'])])
            added.extend((old['paper_id'],t['tool_id']) for t in new['tools'][len(old['tools']):])
        self.assertEqual([('P054','wesanderson'),('P204','allelicseries')],added)
    def test_all1130_previous_reviews_preserved(self):
        old=json.loads(self.before[REVIEWS]);new=json.loads(self.after[REVIEWS]);self.assertEqual(old['reviews'],new['reviews'][:1130]);self.assertEqual(1132,len(new['reviews']))
    def test_one_palette_credit_with_no_allelic_candidate_credit(self):
        rows=json.loads(self.after[META[0]]);wes,allelic=rows[-2:]
        self.assertEqual(('wesanderson',1,'native-subset-validated'),(wes['id'],wes['new_scoped_integration_credit'],wes['implementation_status']))
        self.assertEqual(('allelicseries',0,'missing'),(allelic['id'],allelic['new_scoped_integration_credit'],allelic['implementation_status']))
        counts=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((20,86,267),(counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing']))
    def test_candidate_is_source_entrypoint_with_provider_not_a_jar(self):
        row=json.loads(self.after[META[0]])[-1];self.assertTrue(row['development_candidate'].endswith('/batch.py'));self.assertIn('requires-qualified-provider',row['development_candidate_kind']);self.assertFalse(row['project_adapter_available']);self.assertIn('not a standalone JAR',row['implementation_scope'])
    def test_current_paper_role_keeps_trait_and_version_limits_visible(self):
        row=json.loads(self.after[META[0]])[-1];text=row['paper_role_coverage']['P204']
        for required in ['1.1.2','0.1.1.2','unresolved','quantitative-trait','binary/discrete','COAST/SKAT-O remain upstream R']:
            self.assertIn(required,text)
    def test_literal_reported_edition_cannot_be_normalized(self):
        self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P204' for t in p['tools'] if t['tool_id']=='allelicseries').update(version_reported_near_name='0.1.1.2'))
    def test_candidate_cannot_gain_reported_edition_or_full_tool_credit(self):
        self.reject(META[0],lambda rs:rs[-1].update(implementation_status='upstream-adapter-validated',paper_build_equivalence_proven=True,new_scoped_integration_credit=1))
    def test_no_inferred_paper_palette_or_ontology_mapping(self):
        self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P054' for t in p['tools'] if t['tool_id']=='wesanderson').update(use_summary='The authors used GrandBudapest1 with4 colors and the listed MFOD1 mapping'))
    def test_viridis_and_R_graphics_not_covered_by_palette_credit(self):
        self.reject(META[0],lambda rs:rs[-2].update(uncovered_roles=[],implementation_scope='All R graphics and viridisLite palettes are native',whole_tool_qualified=True))
    def test_native_burden_cannot_be_claimed_as_full_COAST_or_SKATO(self):
        self.reject(META[0],lambda rs:rs[-1].update(implementation_scope='Complete native COAST and SKAT-O for all phenotypes'))
    def test_quantitative_scope_does_not_admit_binary_traits_or_paper_benchmark(self):
        self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P204' for t in p['tools'] if t['tool_id']=='allelicseries').update(implementation_scope_note='Binary traits calibrated;20K genes complete in38 minutes'))
    def test_all27_named_paragraphs_support_exactly_two_family_uses(self):
        result=validate(self.census,self.papers,reviews(ROOT));self.assertEqual((27,2),(result['named_primary_paragraphs'],result['new_paper_family_reviews']))
    def test_omitted_duplicate_and_stale_supports_rejected(self):
        for mutation in ['omit','duplicate','stale']:
            rows=copy.deepcopy(reviews(ROOT));supports=rows[-1]['supporting_evidence']
            if mutation=='omit':supports.pop()
            elif mutation=='duplicate':supports[-1]=copy.deepcopy(supports[0])
            else:supports[0]['paragraph_sha256']='0'*64
            with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
    def test_primary_association_anchor_can_differ_from_first_overview_anchor(self):
        row=reviews(ROOT)[-1];self.assertEqual((36,3),(row['evidence_paragraph_index'],row['supporting_evidence'][0]['paragraph_index']))
    def test_foreign_anchor_identity_and_duplicate_census_rejected(self):
        for mutation in ['identity','duplicate']:
            census=copy.deepcopy(self.census)
            if mutation=='identity':census['matches'][0]['pmcid']='PMC00000000'
            else:census['matches'][-1]=copy.deepcopy(census['matches'][0])
            with self.assertRaises(ValueError):validate(census,self.papers,reviews(ROOT))
    def test_scientific_and_other_metadata_overlays_forbidden(self):
        for name in ['src/main/java/org/jlinalg/plot/WesAnderson037.java','tools/literature/allelicseries0112/batch.py','tools/literature/wesanderson037/verify.py',CURATION,'other.json']:
            before=dict(self.before);before[name]=b'forged\n'
            with self.assertRaises(ValueError):metadata_view(ROOT,before)
    def test_scientific_paths_stay_live_through_metadata_view(self):
        base=ROOT/'build/literature-review/monocle-runtime'
        with tempfile.TemporaryDirectory(dir=base) as directory:
            root=Path(directory).resolve();self.assertTrue(root.is_relative_to(base.resolve()));source=root/'science.java';source.write_bytes(b'original\n');view=metadata_view(root,self.before)
            self.assertEqual(b'original\n',(view/'science.java').read_bytes());source.write_bytes(b'changed\n');self.assertEqual(b'changed\n',(view/'science.java').read_bytes())
    def test_changed_qualified_native_source_is_rejected(self):
        base=ROOT/'build/literature-review/monocle-runtime';relative='docs/literature/wesanderson037-workflow-development/VALIDATION.json';proof=json.loads((ROOT/relative).read_bytes())
        with tempfile.TemporaryDirectory(dir=base) as directory:
            root=Path(directory).resolve();self.assertTrue(root.is_relative_to(base.resolve()));path=root/relative;path.parent.mkdir(parents=True);path.write_bytes(encoded(proof))
            for name in proof['sources']:
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/name).read_bytes())
            (root/'src/main/java/org/jlinalg/plot/WesAnderson037.java').write_bytes(b'Deliberately corrupted scientific source\n')
            with self.assertRaisesRegex(ValueError,'Qualified scientific source differs'):qualification(root)
    def test_current_notice_is_prominent_and_counts_are_consistent(self):
        page=self.after[META[4]].decode('utf8');self.assertEqual(1,page.count(NOTICE));self.assertLess(page.index('id="palette-allelic-required-actions"'),page.index('id="overview"'))
        self.assertIn('267 named families remain missing',page);self.assertIn('The reported edition remains missing and earns zero credit',page)
        after=dict(self.after);after[META[5]]+=b'Unsupported change\n'
        with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)

if __name__=='__main__':unittest.main()
