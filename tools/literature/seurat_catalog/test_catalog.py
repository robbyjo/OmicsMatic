# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Frozen handover/dry-transition checks; these do not admit a catalog change."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import unittest

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from seurat_catalog.model import META, check, transition, validate_census
from seurat_catalog.primary import BASE, encoded


class FrozenSeuratHandoverTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = {name: subprocess.check_output(
            ['git', '--no-replace-objects', 'show', BASE + ':' + name], cwd=ROOT)
            for name in META}
        cls.census = json.loads((ROOT / 'docs/literature/seurat-catalog-handover/PRIMARY-CENSUS.json').read_bytes())
        cls.after = transition(cls.before, cls.census)

    def reject_census(self, edit):
        census = copy.deepcopy(self.census)
        edit(census)
        with self.assertRaises((ValueError, KeyError)):
            transition(self.before, census)

    def reject_after(self, name, edit):
        after = dict(self.after)
        value = json.loads(after[name])
        edit(value)
        after[name] = encoded(value)
        with self.assertRaises(ValueError):
            check(self.before, after, self.census)

    def test_01_dry_transition_counts(self):
        result = check(self.before, self.after, self.census)
        self.assertEqual((result['observations'], result['reviewed_uses'], result['unreviewed_uses']), (2696, 1339, 1357))
        self.assertEqual((result['new_scoped_integrations'], result['native_integrations'], result['upstream_integrations']), (131, 24, 107))
        self.assertFalse(result['whole_goal_complete'])

    def test_02_live_catalog_stays_at_handover_predecessor(self):
        self.assertEqual(self.before, {name: (ROOT / name).read_bytes() for name in META})

    def test_03_only_p160_added_and_other_uses_retained(self):
        old, new = json.loads(self.before[META[0]]), json.loads(self.after[META[0]])
        added = []
        for a, b in zip(old, new, strict=True):
            self.assertEqual([u for u in a['tools'] if u['tool_id'] != 'seurat'],
                             [u for u in b['tools'] if u['tool_id'] != 'seurat'])
            if len(a['tools']) != len(b['tools']):
                added.append(b['paper_id'])
        self.assertEqual(added, ['P160'])

    def test_04_p061_and_review_prefix_remain_exact(self):
        old, new = json.loads(self.before[META[0]]), json.loads(self.after[META[0]])
        a = next(p for p in old if p['paper_id'] == 'P061')
        b = next(p for p in new if p['paper_id'] == 'P061')
        self.assertEqual(a, b)
        old_reviews = json.loads(self.before['tools/literature/reviewed-uses.json'])['reviews']
        new_reviews = json.loads(self.after['tools/literature/reviewed-uses.json'])['reviews']
        self.assertEqual(new_reviews[:1290], old_reviews)
        self.assertEqual(len(new_reviews[1290:]), 49)

    def test_05_contexts_and_no_author_parity(self):
        rows = {r['paper_id']: r for r in self.census['roles']}
        self.assertEqual({k for k, r in rows.items() if not r['package_execution_established']}, {'P047', 'P061'})
        self.assertEqual(rows['P093']['role'], 'comparative-benchmark')
        self.assertEqual(rows['P200']['role'], 'direct-package-visualization')
        self.assertEqual(rows['P200']['qualified_component_overlap'], [])
        self.assertEqual([r['paper_id'] for r in self.census['excluded_contexts']], ['P083'])
        for row in rows.values():
            for key in ['author_runtime_executed', 'exact_author_settings_established',
                        'complete_author_workflow_qualified', 'whole_paper_reproduced']:
                self.assertFalse(row[key])

    def test_06_source_identity_mutation_refused(self):
        self.reject_census(lambda c: c['primary_files'][0].update(xml_sha256='0' * 64))

    def test_07_reported_edition_mutation_refused(self):
        def edit(c):
            row = next(r for r in c['roles'] if r['paper_id'] == 'P051')
            row['reported_editions'][0]['literal'] += '-invented'
        self.reject_census(edit)

    def test_08_pdf_hash_mutation_refused(self):
        self.reject_census(lambda c: c['source_pdf_binding']['pdf'].update(sha256='0' * 64))

    def test_09_p160_without_supplement_refused(self):
        def edit(c):
            del next(r for r in c['roles'] if r['paper_id'] == 'P160')['supplementary_primary_evidence']
        self.reject_census(edit)

    def test_10_anchor_mutation_refused(self):
        self.reject_census(lambda c: c['roles'][0]['anchors'][0].update(paragraph_sha256='0' * 64))

    def test_11_false_author_runtime_claim_refused(self):
        self.reject_census(lambda c: c['roles'][0].update(author_runtime_executed=True))

    def test_12_wrong_new_observation_count_refused(self):
        self.reject_census(lambda c: c.update(observations_added=[]))

    def test_13_rewriting_old_review_refused(self):
        self.reject_after('tools/literature/reviewed-uses.json',
                          lambda d: d['reviews'][0].update(use_summary='rewritten'))

    def test_14_unrelated_observation_mutation_refused(self):
        def edit(papers):
            next(u for p in papers for u in p['tools'] if u['tool_id'] != 'seurat')['use_summary'] = 'rewritten'
        self.reject_after(META[0], edit)

    def test_15_false_native_credit_refused(self):
        self.reject_after(META[2], lambda d: d['implementation_status_counts'].update({'native-subset-validated': 25}))

    def test_16_readmission_refused(self):
        with self.assertRaises(ValueError):
            transition(self.after, self.census)


if __name__ == '__main__':
    unittest.main(verbosity=2)
