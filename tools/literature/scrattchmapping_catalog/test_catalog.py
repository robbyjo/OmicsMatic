# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Scope, primary uncertainty, exact predecessor replay and mutation rejection."""
import json, sys, unittest
from pathlib import Path
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scrattchmapping_catalog.model import ROOT, META, PAPERS, REVIEWS, PROOF, transition, check_transition
from scrattchmapping_catalog.bridge import load_before, metadata_view
from scrattchmapping_catalog.source import reviews, scan
from scrattchmapping_catalog.catalog import compatibility
from catalog_source_integrity import reverse_delta


class CatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = load_before(ROOT) if (ROOT / PROOF).exists() else {n: (ROOT / n).read_bytes() for n in META}
        cls.after = transition(ROOT, cls.before)

    def test_single_credit_and_all_unrelated_records_preserved(self):
        result = check_transition(ROOT, self.before, self.after)
        self.assertEqual((result['papers'], result['observations'], result['reviewed_uses'], result['new_scoped_integrations'], result['missing']), (250, 2679, 1180, 114, 265))
        self.assertEqual((result['unchanged_previous_families'], result['unchanged_previous_observations'], result['unchanged_previous_reviews']), (437, 2677, 1178))
        inventory = json.loads(self.after[META[0]])
        row = next(r for r in inventory if r['id'] == 'scrattch-mapping')
        self.assertEqual(row['implementation_status'], 'upstream-adapter-validated')
        self.assertTrue(row['project_adapter_available'])
        self.assertFalse(row['canonical_adapter_available'] or row['root_dispatch_available'] or row['whole_tool_qualified'])
        for identifier in ['scrattch-hicat', 'scrattch-bigcat', 'arrow-r', 'pyarrow']:
            self.assertEqual(next(r for r in inventory if r['id'] == identifier)['implementation_status'], 'missing')
        self.assertEqual((result['new_scoped_integration_credit'], result['native_family_credit_added'], result['added_reviews']), (1, 0, 0))

    def test_primary_editions_and_roles_not_inferred_from_runtime(self):
        rows = reviews(ROOT)
        self.assertIsNone(rows[0]['version_reported_near_name'])
        self.assertEqual(rows[1]['version_reported_near_name'], '0.2')
        self.assertEqual(rows[0]['reported_settings']['correspondence_levels'], ['cluster', 'supertype', 'subclass'])
        self.assertEqual(rows[1]['reported_settings']['hierarchy'], ['root', 'class', 'subclass', 'cluster'])
        for old, new in zip(json.loads(self.before[REVIEWS])['reviews'], json.loads(self.after[REVIEWS])['reviews']):
            if old['tool_id'] == 'scrattch-mapping':
                self.assertEqual({k: v for k, v in old.items() if k != 'implementation_scope_note'}, {k: v for k, v in new.items() if k != 'implementation_scope_note'})

    def test_complete_metadata_inverse_and_no_scientific_overlays(self):
        inverse = compatibility(self.before, self.after)
        self.assertEqual(reverse_delta(self.after[REVIEWS], inverse['paths'][0]), self.before[REVIEWS])
        with self.assertRaises(ValueError):
            metadata_view(ROOT, {**self.before, 'tools/literature/scrattchmapping02/Worker.R': b'changed'})
        partial = dict(self.before)
        del partial[PAPERS]
        with self.assertRaises(ValueError):
            transition(ROOT, partial)

    def test_primary_unknown_edition_and_unrelated_mutations_rejected(self):
        bad = dict(self.after)
        papers = json.loads(bad[PAPERS])
        next(r for p in papers if p['paper_id'] == 'P058' for r in p['tools'] if r['tool_id'] == 'scrattch-mapping')['version_reported_near_name'] = '0.2'
        bad[PAPERS] = json.dumps(papers).encode()
        with self.assertRaises(ValueError):
            check_transition(ROOT, self.before, bad)
        bad = dict(self.after)
        inventory = json.loads(bad[META[0]])
        next(r for r in inventory if r['id'] == 'scrattch-hicat')['implementation_status'] = 'upstream-adapter-validated'
        bad[META[0]] = json.dumps(inventory).encode()
        with self.assertRaises(ValueError):
            check_transition(ROOT, self.before, bad)

    def test_all250_primary_source_identities_and_dot_hyphen_anchors(self):
        census = scan(ROOT, json.loads(self.before[PAPERS]))
        self.assertEqual(len(census), 250)
        selected = {r['paper_id']: [a['paragraph_index'] for a in r['anchors']['scrattch-mapping']]
                    for r in census if r['anchors']['scrattch-mapping']}
        self.assertEqual(selected, {'P058': [57, 77, 85], 'P059': [17, 111, 155, 162]})


if __name__ == '__main__':
    unittest.main()
