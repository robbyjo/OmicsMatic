# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admission invariants, source distinctions and exact historical reversibility."""
from pathlib import Path
import copy, json, sys, unittest
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from doubletfinder_catalog.model import ROOT, META, PAPERS, REVIEWS, PROOF, transition, check_transition
from doubletfinder_catalog.bridge import load_before, metadata_view
from doubletfinder_catalog.source import reviews, definitions, scan, author_sources
from doubletfinder_catalog.catalog import compatibility
from catalog_source_integrity import reverse_delta


class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = load_before(ROOT) if (ROOT / PROOF).exists() else {n: (ROOT / n).read_bytes() for n in META}
        cls.after = transition(ROOT, cls.before)

    def test_complete_preservation_and_credit(self):
        result = check_transition(ROOT, self.before, self.after)
        self.assertEqual((result['papers'], result['observations'], result['reviewed_uses']), (250, 2679, 1180))
        self.assertEqual((result['unchanged_previous_families'], result['unchanged_previous_observations'], result['unchanged_previous_reviews']), (432, 2666, 1167))
        inventory = json.loads(self.after[META[0]])
        self.assertEqual(next(r for r in inventory if r['id'] == 'doubletfinder')['implementation_status'], 'upstream-adapter-validated')
        self.assertEqual({r['implementation_status'] for r in inventory if r['id'] in {d['id'] for d in definitions(ROOT)}}, {'missing'})
        self.assertEqual((result['new_scoped_integration_credit'], result['native_family_credit_added']), (1, 0))

    def test_reported_versions_and_roles_not_substituted(self):
        rows = reviews(ROOT)
        self.assertIsNone(rows[0]['version_reported_near_name'])
        self.assertEqual(rows[2]['version_reported_near_name'], '3.0')
        self.assertEqual(rows[2]['reported_settings']['combined_removal_rule'], 'DoubletFinder UNION (DoubletDecon INTERSECTION Scrublet)')
        self.assertEqual(rows[5]['version_reported_near_name'], '1.0.9')
        self.assertIn('strictly greater', rows[5]['use_summary'])
        self.assertEqual([r['version_reported_near_name'] for r in rows[6:10]], [None, '0.0.5', None, '0.2'])
        self.assertEqual(rows[8]['evidence_paragraph_index'], 57)
        self.assertEqual(rows[11]['usage_context'], 'declared-development-dependency')
        self.assertIsNone(rows[11]['reported_settings']['analysis_operation'])

    def test_source_correction_and_all_reviews_reversible(self):
        proof = compatibility(self.before, self.after)
        self.assertEqual(reverse_delta(self.after[REVIEWS], proof['paths'][0]), self.before[REVIEWS])
        paper = next(p for p in json.loads(self.after[PAPERS]) if p['paper_id'] == 'P096')
        row = next(r for r in paper['tools'] if r['tool_id'] == 'scrublet')
        self.assertEqual(row['version_reported_near_name'], '0.2')
        self.assertIn('Scrublet 0.2,', row['use_summary'])
        self.assertNotIn('0.0.2', row['use_summary'])

    def test_fresh_all250_sources_and_original_author_error(self):
        census = scan(ROOT, json.loads(self.before[PAPERS]))
        self.assertEqual(len(census), 250)
        self.assertEqual(sum(len(r['anchors']['arrow-candidate']) for r in census), 15)
        self.assertEqual(sum(bool(r['anchors']['arrow-r']) for r in census), 1)
        self.assertEqual(sum(bool(r['anchors']['pyarrow']) for r in census), 1)
        self.assertEqual(author_sources(ROOT)['original_script5_syntax_error_line'], 21)

    def test_partial_collections_scientific_overlays_and_metadata_mutations_refused(self):
        bad = dict(self.before)
        del bad[PAPERS]
        with self.assertRaises(ValueError):
            transition(ROOT, bad)
        with self.assertRaises(ValueError):
            metadata_view(ROOT, {**self.before, 'tools/literature/doubletfinder206/Worker.R': b'changed'})
        after = dict(self.after)
        rows = json.loads(after[REVIEWS])
        rows['reviews'][0]['use_summary'] = 'Unrelated altered source claim'
        after[REVIEWS] = json.dumps(rows).encode()
        with self.assertRaises(ValueError):
            check_transition(ROOT, self.before, after)


if __name__ == '__main__':
    unittest.main()
