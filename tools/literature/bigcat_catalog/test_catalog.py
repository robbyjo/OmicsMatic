# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Primary facts, exact family accounting and rejection of unsupported coverage."""
from pathlib import Path
import copy,json,sys,unittest,uuid
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from bigcat_catalog.model import ROOT,META,PAPERS,REVIEWS,PROOF,transition,check_transition
from bigcat_catalog.bridge import load_before,metadata_view
from bigcat_catalog.source import original_reviews,reviews,scan
from bigcat_catalog.catalog import compatibility
from catalog_source_integrity import reverse_delta
from bigcat_catalog.dispatcher import PATH,INSERTION,DECLARATION,COMPATIBILITY as HELP_COMPATIBILITY,declaration,review,private_help_fixture

class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META}
        cls.after=transition(ROOT,cls.before)

    def test_one_existing_family_and_two_coverage_notes_only(self):
        result=check_transition(ROOT,self.before,self.after)
        self.assertEqual((result['papers'],result['named_tools'],result['observations'],result['reviewed_uses'],result['unreviewed_uses']),(250,438,2679,1180,1499))
        self.assertEqual((result['new_scoped_integrations'],result['missing'],result['unchanged_previous_families'],result['unchanged_previous_observations'],result['unchanged_previous_reviews']),(116,263,437,2677,1178))
        self.assertEqual((result['native_family_credit_added'],result['added_reviews'],result['added_observations'],result['new_registry_definitions']),(0,0,0,0))
        inventory=json.loads(self.after[META[0]]);row=next(r for r in inventory if r['id']=='scrattch-bigcat')
        self.assertEqual(row['selected_source_profiles'],['prepublication-2023','pre-regression-2022'])
        self.assertTrue(row['project_adapter_available'])
        self.assertFalse(row['canonical_adapter_available'] or row['root_dispatch_available'] or row['whole_tool_qualified'] or row['whole_paper_reproduced'] or row['author_source_commits_established'] or row['P058_edition_established'])
        counts=json.loads(self.after[META[1]])['implementation_status_counts']
        self.assertEqual((counts['native-subset-validated'],counts['upstream-adapter-validated']),(20,96))
        for name in ['scrattch-hicat','pyarrow']:self.assertEqual(next(r for r in inventory if r['id']==name)['implementation_status'],'missing')
        for name in ['arrow-r','scrattch-mapping']:self.assertEqual(next(r for r in inventory if r['id']==name)['implementation_status'],'upstream-adapter-validated')

    def test_primary_editions_settings_and_uncertainties_are_unchanged(self):
        rows=reviews(ROOT);self.assertEqual([(r['paper_id'],r['version_reported_near_name']) for r in rows],[('P058',None),('P059','0.0.5')])
        for before,after in zip(original_reviews(ROOT),rows):
            self.assertEqual({k:v for k,v in before.items() if k!='implementation_scope_note'},{k:v for k,v in after.items() if k!='implementation_scope_note'})
        p58,p59=rows
        self.assertEqual((p58['reported_settings']['tree_marker_genes'],p58['reported_settings']['constellation_neighbors'],p58['reported_settings']['outside_neighbor_fraction_threshold'],p58['reported_settings']['minimum_SPN_nuclei_in_selected_clusters']),(1871,15,.05,5))
        self.assertTrue(any('space in Allen Institute' in s for s in p58['uncertainties']))
        self.assertEqual(p59['reported_settings']['matrix_bins'],dict(cells=50000,genes=500))
        self.assertEqual(p59['reported_settings']['mapping_cross_validation'],dict(folds=5,all_markers=8460,TF_markers=534,random_marker_sets=20,random_set_size=534,centroid_index='Annoy; default cosine distance'))
        self.assertIn('follows integration',p58['implementation_scope_note'])
        self.assertIn('all training cells per centroid',p59['implementation_scope_note'])

    def test_exact_metadata_inverse_forbids_scientific_overlays(self):
        inverse=compatibility(self.before,self.after)
        self.assertEqual(reverse_delta(self.after[REVIEWS],inverse['paths'][0]),self.before[REVIEWS])
        for name in ['tools/literature/scrattchbigcat005_workflows/Worker.R','docs/literature/scrattchbigcat005-workflow-validation/QUALIFICATION.json']:
            with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,name:b'changed'})
        incomplete=dict(self.before);del incomplete[PAPERS]
        with self.assertRaises(ValueError):transition(ROOT,incomplete)

    def test_borrowed_version_invented_settings_and_other_family_credit_rejected(self):
        papers=json.loads(self.after[PAPERS])
        for key,value in [('version_reported_near_name','0.0.5'),('reported_settings',dict(min_cells=5))]:
            changed=copy.deepcopy(papers)
            row=next(r for p in changed if p['paper_id']=='P058' for r in p['tools'] if r['tool_id']=='scrattch-bigcat')
            row[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,PAPERS:json.dumps(changed).encode()})
        for name,key,value in [('scrattch-hicat','implementation_status','upstream-adapter-validated'),('scrattch-bigcat','native_family_credit_added',1),('scrattch-bigcat','whole_paper_reproduced',True)]:
            inventory=json.loads(self.after[META[0]]);next(r for r in inventory if r['id']==name)[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:json.dumps(inventory).encode()})

    def test_all250_primary_sources_and_both_complete_anchor_populations(self):
        census=scan(ROOT,json.loads(self.before[PAPERS]));self.assertEqual(len(census),250)
        anchors={r['paper_id']:[a['paragraph_index'] for a in r['anchors']['scrattch-bigcat']] for r in census if r['anchors']['scrattch-bigcat']}
        self.assertEqual(anchors,{'P058':[50,52,58,77,85],'P059':[6,87,88,93,96,102,107,109,114,155,162]})

    def test_exact_six_help_lines_only_and_executable_changes_rejected(self):
        raw=(ROOT/PATH).read_bytes();document=declaration(ROOT);restored=review(raw,document)
        self.assertEqual(raw.replace(INSERTION,b'',1),restored)
        for changed in [raw.replace(b'return ',b'throw ',1),raw.replace(b'--adjust-map',b'--different-map',1),raw+b'\n']:
            with self.assertRaises(ValueError):review(changed,document)
        for key,value in [('path','src/main/java/org/jlinalg/cli/AnalysisRunner.java'),('before_bytes',0),('after_sha256','0'*64)]:
            changed=copy.deepcopy(document);changed['paths'][0][key]=value
            with self.assertRaises(ValueError):review(raw,changed)

    def test_private_help_fixture_restores_complete_source_on_failure(self):
        # Synthetic linked-worktree marker tests restoration, not Git identity.
        # Complete fixtures remain in this owned build directory.
        out=ROOT/'build/literature-review/monocle-runtime'/('bigcat-help-restoration-unit-'+uuid.uuid4().hex)
        out.mkdir(parents=True);(out/'.git').write_text('gitdir: synthetic-test-marker\n')
        raw=(ROOT/PATH).read_bytes();descriptor=declaration(ROOT)
        for name,content in [(PATH,raw),(DECLARATION,json.dumps(descriptor).encode()),(HELP_COMPATIBILITY,json.dumps(descriptor).encode())]:
            target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
        with self.assertRaisesRegex(RuntimeError,'retained controlled failure'):
            with private_help_fixture(out):
                self.assertEqual((out/PATH).read_bytes(),review(raw,descriptor))
                raise RuntimeError('retained controlled failure')
        self.assertEqual((out/PATH).read_bytes(),raw)
        receipts=list((out/'build/literature-review/monocle-runtime').glob('bigcat-private-help-fixture-*/RESTORED.json'))
        self.assertEqual(len(receipts),1)

    def test_shared_checkout_help_is_never_changed(self):
        out=ROOT/'build/literature-review/monocle-runtime'/('bigcat-help-shared-refusal-unit-'+uuid.uuid4().hex)
        out.mkdir(parents=True);(out/'.git').mkdir()
        with self.assertRaisesRegex(OSError,'isolated linked Git worktree'):
            with private_help_fixture(out):self.fail('Shared checkout was admitted')

if __name__=='__main__':unittest.main()
