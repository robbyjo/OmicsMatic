# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact source facts and one-family accounting; reject invented scientific coverage."""
from pathlib import Path
import copy,json,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from hicat109_catalog.model import ROOT,META,PAPERS,REVIEWS,PROOF,transition,check_transition
from hicat109_catalog.source import TOOL,original_reviews,reviews,scan
from hicat109_catalog.bridge import load_before,metadata_view
from hicat109_catalog.catalog import compatibility
from catalog_source_integrity import reverse_delta

class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META}
        cls.after=transition(ROOT,cls.before)

    def test_one_existing_family_one_note_and_no_duplicate_native_credit(self):
        result=check_transition(ROOT,self.before,self.after)
        self.assertEqual((result['papers'],result['named_tools'],result['observations'],result['reviewed_uses'],result['unreviewed_uses']),(250,438,2679,1180,1499))
        self.assertEqual((result['new_scoped_integrations'],result['missing'],result['unchanged_previous_families'],result['unchanged_previous_observations'],result['unchanged_previous_reviews']),(117,262,437,2678,1179))
        self.assertEqual((result['native_family_credit_added'],result['added_reviews'],result['added_observations'],result['new_registry_definitions']),(0,0,0,0))
        counts=json.loads(self.after[META[1]])['implementation_status_counts']
        self.assertEqual((counts['native-subset-validated'],counts['upstream-adapter-validated']),(20,97))

    def test_primary_edition_strict_cutoff_and_all_context_classifications_unchanged(self):
        old,new=original_reviews(ROOT)[0],reviews(ROOT)[0]
        self.assertEqual({k:v for k,v in old.items() if k!='implementation_scope_note'},{k:v for k,v in new.items() if k!='implementation_scope_note'})
        self.assertEqual(new['version_reported_near_name'],'1.0.9')
        self.assertEqual(new['reported_settings'],dict(doublet_score_threshold=.3,removal_comparator='>'))
        self.assertEqual([a['paragraph_index'] for a in new['supporting_evidence']],[85,87,88,91])
        self.assertEqual([a['kind'] for a in new['named_anchor_classification']],['primary-methods']+['historical-predecessor-context']*3)

    def test_selected_source_is_not_claimed_as_the_author_commit_function_or_panel(self):
        row=next(r for r in json.loads(self.after[META[0]]) if r['id']==TOOL)
        self.assertEqual(row['selected_upstream_versions'],['1.0.9'])
        self.assertEqual(row['selected_source_commits'],['24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4'])
        self.assertTrue(row['project_adapter_available'] and row['paper_62_housekeeping_QC_is_separate'])
        for key in ['canonical_adapter_available','root_dispatch_available','author_source_commits_established','author_exact_function_established','author_doublet_gene_panel_established','whole_tool_qualified','whole_paper_reproduced','upstream_license_labels_resolved','upstream_source_or_runtime_redistributed']:
            self.assertFalse(row[key],key)
        self.assertIn('no HICAT score calculation',row['native_component_scope'])

    def test_exact_review_inverse_and_metadata_only_views(self):
        inverse=compatibility(self.before,self.after)
        self.assertEqual(len(inverse['paths']),1)
        self.assertEqual(reverse_delta(self.after[REVIEWS],inverse['paths'][0]),self.before[REVIEWS])
        for name in ['tools/literature/scrattchhicat109/Worker.R','src/main/java/org/jlinalg/singlecell/DoubletScoreThreshold.java','docs/literature/scrattchhicat109-workflow-validation/QUALIFICATION.json']:
            with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,name:b'changed'})

    def test_invented_primary_settings_and_borrowed_editions_rejected(self):
        for key,value in [('version_reported_near_name','0.0.5'),('reported_settings',dict(doublet_score_threshold=.3,removal_comparator='>=')),('reported_settings',dict(doublet_score_threshold=.3,removal_comparator='>',doublet_genes=62))]:
            papers=json.loads(self.after[PAPERS]);row=next(r for p in papers if p['paper_id']=='P059' for r in p['tools'] if r['tool_id']==TOOL)
            row[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,PAPERS:json.dumps(papers).encode()})

    def test_extra_native_credit_other_family_and_whole_tool_claims_rejected(self):
        for family,key,value in [(TOOL,'native_family_credit_added',1),(TOOL,'whole_tool_qualified',True),('pyarrow','implementation_status','upstream-adapter-validated')]:
            rows=json.loads(self.after[META[0]]);next(r for r in rows if r['id']==family)[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:json.dumps(rows).encode()})

    def test_all250_primary_sources_preserve_one_method_and_three_historical_anchors(self):
        census=scan(ROOT,json.loads(self.before[PAPERS]));self.assertEqual(len(census),250)
        anchors={r['paper_id']:[a['paragraph_index'] for a in r['anchors'][TOOL]] for r in census if r['anchors'][TOOL]}
        self.assertEqual(anchors,{'P059':[85,87,88,91]})

    def test_missing_predecessor_metadata_rejected(self):
        incomplete=dict(self.before);del incomplete[PAPERS]
        with self.assertRaises(ValueError):transition(ROOT,incomplete)

if __name__=='__main__':unittest.main()
