# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Protect source identity, old observations, literal editions and scientific qualification boundaries."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from qupath_catalog.model import ROOT,META,PAPERS,REVIEWS,EVIDENCE,CURATION,NOTICE,encoded,transition,check_transition,reviews
from qupath_catalog.bridge import load_before,metadata_view
from qupath_catalog.source import EXPECTED,validate
from qupath_catalog.qualification import COMPONENT,qualification

class QuPathCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT);cls.after=transition(ROOT,cls.before)
        cls.papers=json.loads(cls.before[PAPERS]);cls.census=json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes())
    def reject(self,path,change):
        after=dict(self.after);value=json.loads(after[path]);change(value);after[path]=encoded(value)
        with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
    @staticmethod
    def qupath(rows):return next(t for t in rows if t['id']=='qupath')
    def test_431_other_family_records_unchanged(self):
        self.assertEqual([t for t in json.loads(self.before[META[0]]) if t['id']!='qupath'],[t for t in json.loads(self.after[META[0]]) if t['id']!='qupath'])
    def test_all_paper_identities_2660_other_observations_and_exact_old_records_retained(self):
        enriched=[];unchanged=0
        for old,new in zip(self.papers,json.loads(self.after[PAPERS]),strict=True):
            self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'})
            for a,b in zip(old['tools'],new['tools'],strict=True):
                if old['paper_id'] in EXPECTED and old['paper_id']!='P040' and a['tool_id']=='qupath':
                    self.assertEqual(a,b['source_extraction_before_review']);enriched.append(old['paper_id'])
                else:self.assertEqual(a,b);unchanged+=1
        self.assertEqual([p for p in EXPECTED if p!='P040'],enriched);self.assertEqual(2660,unchanged)
    def test_1132_old_reviews_preserved_and_exactly_11_appended(self):
        old=json.loads(self.before[REVIEWS]);new=json.loads(self.after[REVIEWS]);self.assertEqual(old['reviews'],new['reviews'][:1132]);self.assertEqual(1143,len(new['reviews']))
        self.assertEqual(1143,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
    def test_existing_P040_observation_and_review_unchanged(self):
        for path,find in [(PAPERS,lambda d:next(t for p in d if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='qupath')),
                          (REVIEWS,lambda d:next(r for r in d['reviews'] if r['pmcid']=='PMC13645638' and r['tool_id']=='qupath'))]:
            self.assertEqual(find(json.loads(self.before[path])),find(json.loads(self.after[path])))
    def test_one_upstream_credit_and_no_new_family_or_native_credit(self):
        row=self.qupath(json.loads(self.after[META[0]]));self.assertEqual(('upstream-adapter-validated',1,12),(row['implementation_status'],row['new_scoped_integration_credit'],row['source_roles_reviewed']))
        counts=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((20,87,266),(counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing']))
        self.assertEqual(432,len(json.loads(self.after[META[0]])));self.assertEqual(2671,sum(len(p['tools']) for p in json.loads(self.after[PAPERS])))
    def test_no_whole_tool_or_twelve_paper_reproduction_credit(self):
        self.reject(META[0],lambda ts:self.qupath(ts).update(whole_tool_qualified=True,whole_paper_reproduced=True,new_scoped_integration_credit=12))
    def test_no_native_segmentation_substitution(self):
        self.reject(META[0],lambda ts:self.qupath(ts).update(implementation_status='native-subset-validated',implementation_scope='All QuPath algorithms are native Java'))
    def test_literal_editions_and_unreported_identities(self):
        ts={p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='qupath') for p in json.loads(self.after[PAPERS]) if p['paper_id'] in EXPECTED}
        for pid,version in {'P009':'0.5.1','P020':'0.6.0','P040':'0.5.1','P049':'0.4.3','P058':'0.4.1','P158':'v.0.2.3 to v.0.4.3'}.items():self.assertEqual(version,ts[pid]['version_reported_near_name'])
        self.assertEqual(6,sum(t['version_reported_near_name']=='unreported' for t in ts.values()))
        self.assertEqual('0.2.3',ts['P158']['source_extraction_before_review']['version_reported_near_name'])
    def test_reported_range_cannot_be_replaced_by_selected_edition(self):
        self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P158' for t in p['tools'] if t['tool_id']=='qupath').update(version_reported_near_name='0.5.1'))
    def test_current_role_visibility_includes_each_known_gap(self):
        coverage=self.qupath(json.loads(self.after[META[0]]))['paper_role_coverage']
        required={'P009':['0.5.1','DAPI','H-DAB','50-micrometer','20-micrometer'], 'P020':['0.6.0','0.5.1','classifiers'],
            'P021':['Edition unreported','H-DAB','78','Fiji'], 'P024':['Edition unreported','H-DAB','CLR'],
            'P040':['Numeric stain vectors','area-unit','0.08/0.16/0.24','unchanged'], 'P049':['0.4.3','H-DAB','LIF'],
            'P056':['without specifying an algorithm','no missing settings'], 'P058':['0.4.1','73','Wilcoxon'],
            'P098':['blinded','no author pipeline'], 'P158':['v.0.2.3 to v.0.4.3','StarDist','skip the dependent analysis'],
            'P161':['registration','unknown','CytoSignal'], 'P226':['2-micrometer','Nikon','H-DAB']}
        for pid,words in required.items():
            for word in words:self.assertIn(word,coverage[pid],pid)
    def test_custom_model_cannot_be_implied_by_stock_provider(self):
        self.reject(META[0],lambda ts:self.qupath(ts)['paper_role_coverage'].update(P158='Stock0.5.1 supplies the author StarDist model'))
    def test_unknown_stains_ROIs_and_area_units_cannot_be_inferred(self):
        self.reject(META[0],lambda ts:self.qupath(ts)['paper_role_coverage'].update(P040='Exact author stain vectors and ROIs are supplied; area units repaired'))
    def test_16_anchors_cover_12_uses_and_only14_analysis_anchors(self):
        result=validate(self.census,self.papers,reviews(ROOT));self.assertEqual((12,16,14,1,1),tuple(result[k] for k in ['reviewed_paper_family_uses','named_primary_paragraphs','image_analysis_anchors','acknowledgement_anchors','downstream_output_table_anchors']))
    def test_advice_and_output_reuse_not_promoted_to_execution(self):
        for pid,index in [('P021','78'),('P058','73')]:
            rows=copy.deepcopy(reviews(ROOT));next(r for r in rows if r['paper_id']==pid)['named_anchor_classification'][index]='image-analysis-role'
            with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
    def test_missing_duplicate_and_stale_supports_rejected(self):
        for mutation in ['omit','duplicate','stale']:
            rows=copy.deepcopy(reviews(ROOT));supports=rows[0]['supporting_evidence']
            if mutation=='omit':supports.pop()
            elif mutation=='duplicate':supports[-1]=copy.deepcopy(supports[0])
            else:supports[0]['paragraph_sha256']='0'*64
            with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
    def test_foreign_anchor_and_duplicate_census_rejected(self):
        for mutation in ['identity','duplicate']:
            census=copy.deepcopy(self.census)
            if mutation=='identity':census['matches'][0]['doi']='10.0000/wrong'
            else:census['matches'][-1]=copy.deepcopy(census['matches'][0])
            with self.assertRaises(ValueError):validate(census,self.papers,reviews(ROOT))
    def test_scientific_and_additional_metadata_overlays_forbidden(self):
        for name in ['src/main/java/org/jlinalg/cli/QuPath051WorkflowCli.java','tools/literature/qupath051/provider.py','tools/literature/qupath051/verify.py',CURATION,'other.json']:
            before=dict(self.before);before[name]=b'forged\n'
            with self.assertRaises(ValueError):metadata_view(ROOT,before)
    def test_scientific_paths_remain_live(self):
        base=ROOT/'build/literature-review/monocle-runtime'
        with tempfile.TemporaryDirectory(dir=base) as directory:
            root=Path(directory).resolve();self.assertTrue(root.is_relative_to(base.resolve()));source=root/'science.java';source.write_bytes(b'original\n');view=metadata_view(root,self.before)
            self.assertEqual(b'original\n',(view/'science.java').read_bytes());source.write_bytes(b'changed\n');self.assertEqual(b'changed\n',(view/'science.java').read_bytes())
    def test_changed_qualified_source_refused_before_import(self):
        base=ROOT/'build/literature-review/monocle-runtime';proof=json.loads((ROOT/COMPONENT/'VALIDATION.json').read_bytes())
        with tempfile.TemporaryDirectory(dir=base) as directory:
            root=Path(directory).resolve();path=root/COMPONENT/'VALIDATION.json';path.parent.mkdir(parents=True);path.write_bytes(encoded(proof))
            for name in proof['sources']:
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/name).read_bytes())
            (root/'src/main/java/org/jlinalg/cli/QuPath051WorkflowCli.java').write_bytes(b'Deliberately changed scientific controller\n')
            with self.assertRaisesRegex(ValueError,'Qualified scientific source differs'):qualification(root)
    def test_current_warnings_prominent_and_changed_warning_refused(self):
        page=self.after[META[4]].decode('utf8');self.assertEqual(1,page.count(NOTICE));self.assertLess(page.index('id="qupath-required-actions"'),page.index('id="overview"'))
        for text in ['1,143','1,528','266 named families remain missing','SKIP3','FAIL2','Cell Ranger ARC','zero credit','retry NEW paths']:self.assertIn(text,NOTICE)
        after=dict(self.after);after[META[5]]+=b'Unsupported change\n'
        with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)

if __name__=='__main__':unittest.main()
