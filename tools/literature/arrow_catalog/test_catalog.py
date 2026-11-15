# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Primary-source identity, one-family accounting and rejection of unsupported scope."""
from pathlib import Path
import copy,json,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from arrow_catalog.model import ROOT,META,PAPERS,REVIEWS,PROOF,transition,check_transition
from arrow_catalog.bridge import load_before,metadata_view
from arrow_catalog.source import reviews,scan
from arrow_catalog.catalog import compatibility
from catalog_source_integrity import reverse_delta

class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META}
        cls.after=transition(ROOT,cls.before)

    def test_exact_single_family_credit_and_unrelated_records_preserved(self):
        result=check_transition(ROOT,self.before,self.after)
        self.assertEqual((result['papers'],result['observations'],result['reviewed_uses'],result['new_scoped_integrations'],result['missing']),(250,2679,1180,115,264))
        self.assertEqual((result['unchanged_previous_families'],result['unchanged_previous_observations'],result['unchanged_previous_reviews']),(437,2678,1179))
        inventory=json.loads(self.after[META[0]])
        row=next(r for r in inventory if r['id']=='arrow-r')
        self.assertEqual(row['selected_CPP_build'],'h32ed672_2_cpu')
        self.assertTrue(row['project_adapter_available'])
        self.assertFalse(row['canonical_adapter_available'] or row['root_dispatch_available'] or row['whole_tool_qualified'] or row['whole_paper_reproduced'])
        for name in ['scrattch-hicat','scrattch-bigcat','pyarrow']:
            self.assertEqual(next(r for r in inventory if r['id']==name)['implementation_status'],'missing')
        self.assertEqual(next(r for r in inventory if r['id']=='scrattch-mapping')['implementation_status'],'upstream-adapter-validated')
        self.assertEqual((result['native_family_credit_added'],result['new_registry_definitions'],result['added_reviews']),(0,0,0))

    def test_primary_edition_dimensions_and_unknowns_preserved(self):
        row=reviews(ROOT)[0]
        self.assertEqual(row['version_reported_near_name'],'12.0.1')
        self.assertEqual(row['reported_settings'],dict(expression_chunk_cells=50000,expression_chunk_genes=500,format='Parquet'))
        self.assertIn('Specific Arrow function calls, memory-map settings and query options are unreported.',row['uncertainties'])
        for old,new in zip(json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews']):
            if old['tool_id']=='arrow-r':
                self.assertEqual({k:v for k,v in old.items() if k!='implementation_scope_note'},
                                 {k:v for k,v in new.items() if k!='implementation_scope_note'})

    def test_exact_metadata_inverse_and_no_scientific_overlays(self):
        inverse=compatibility(self.before,self.after)
        self.assertEqual(reverse_delta(self.after[REVIEWS],inverse['paths'][0]),self.before[REVIEWS])
        for path in ['tools/literature/arrow1201/Worker.R','docs/literature/arrow1201-workflow-validation/INVALIDATION.json']:
            with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,path:b'changed'})
        incomplete=dict(self.before);del incomplete[PAPERS]
        with self.assertRaises(ValueError):transition(ROOT,incomplete)

    def test_borrowed_edition_invented_schema_wrong_build_and_other_family_rejected(self):
        papers=json.loads(self.after[PAPERS])
        for key,value in [('version_reported_near_name','16.1.0'),('reported_settings',dict(format='Parquet',sparse_encoding='invented-CSR'))]:
            changed=copy.deepcopy(papers)
            row=next(r for p in changed if p['paper_id']=='P059' for r in p['tools'] if r['tool_id']=='arrow-r')
            row[key]=value;bad={**self.after,PAPERS:json.dumps(changed).encode()}
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,bad)
        for name,key,value in [('arrow-r','selected_CPP_build','h0de2578_50_cpu'),('pyarrow','implementation_status','upstream-adapter-validated')]:
            inventory=json.loads(self.after[META[0]])
            next(r for r in inventory if r['id']==name)[key]=value
            with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:json.dumps(inventory).encode()})

    def test_all250_sources_and_separate_arrow_candidates(self):
        census=scan(ROOT,json.loads(self.before[PAPERS]))
        self.assertEqual(len(census),250)
        self.assertEqual({r['paper_id']:[a['paragraph_index'] for a in r['anchors']['arrow-r']] for r in census if r['anchors']['arrow-r']},{'P059':[87,91]})
        self.assertEqual({r['paper_id']:[a['paragraph_index'] for a in r['anchors']['pyarrow']] for r in census if r['anchors']['pyarrow']},{'P250':[51]})

if __name__=='__main__':unittest.main()
