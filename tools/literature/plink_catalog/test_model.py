# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Frozen source-transition proposal tests; do not write canonical metadata."""
from pathlib import Path
import copy
import json
import sys
import unittest

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plink_catalog import model, primary


class ProposalTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = primary.before()
        cls.census = json.loads((primary.ROOT / 'docs/literature/plink-source-review/CENSUS.json').read_bytes())
        cls.after = model.transition(cls.before, cls.census)

    def changed_census_rejected(self, edit):
        census = copy.deepcopy(self.census); edit(census)
        with self.assertRaises(ValueError): model.transition(self.before, census)

    def changed_after_rejected(self, edit):
        parsed = {n: json.loads(b) for n, b in self.after.items()}; edit(parsed)
        after = {n: primary.encoded(d) for n, d in parsed.items()}
        with self.assertRaises(ValueError): model.check(self.before, after, self.census)

    def test_exact_counts_and_pure_inputs(self):
        before = dict(self.before); census = copy.deepcopy(self.census)
        result = model.check(before, self.after, census)
        self.assertEqual(result['observations_after'], 2700)
        self.assertEqual(result['reviews_after'], 1413)
        self.assertEqual(result['new_family_credit'], 0)
        self.assertFalse(result['metadata_written'])
        self.assertEqual(before, self.before); self.assertEqual(census, self.census)

    def test_complete_existing_review_prefix_is_preserved(self):
        old = json.loads(self.before[model.CORE[3]])['reviews']
        new = json.loads(self.after[model.CORE[3]])['reviews']
        self.assertEqual(new[:1339], old)
        self.assertEqual(len({(r['pmcid'], r['tool_id']) for r in new}), len(new))

    def test_prior_observation_retains_old_text_with_separate_fuller_scope(self):
        def use(raw):
            paper = next(p for p in json.loads(raw) if p['paper_id'] == 'P084')
            return next(t for t in paper['tools'] if t['tool_id'] == 'plink2')
        old, new = use(self.before[model.CORE[0]]), use(self.after[model.CORE[0]])
        self.assertEqual({k: v for k, v in new.items() if k != 'source_role_qualification'}, old)
        self.assertIn('dosage', new['source_role_qualification']['use_summary'])

    def test_hosted_estimator_cannot_become_standalone_execution(self):
        self.changed_census_rejected(lambda c: next(r for r in c['roles'] if r['paper_id'] == 'P084' and r['tool_id'] == 'king')
                                     .update(package_execution_established=True))

    def test_source_review_cannot_claim_author_workflow_reproduction(self):
        self.changed_census_rejected(lambda c: c['roles'][0].update(whole_paper_reproduced=True))

    def test_p114_requires_the_exact_author_evidence_binding(self):
        self.changed_census_rejected(lambda c: next(r for r in c['roles'] if r['paper_id'] == 'P114' and r['tool_id'] == 'plink')
                                     ['author_code_evidence'].update(archive_sha256='0' * 64))

    def test_missing_role_is_not_a_complete_census(self):
        self.changed_census_rejected(lambda c: c['roles'].pop())

    def test_source_additions_are_closed(self):
        self.changed_census_rejected(lambda c: c['observations_added'].append(dict(paper_id='P001', tool_id='king')))

    def test_proper_names_cannot_be_promoted(self):
        self.changed_census_rejected(lambda c: c['non_tool_name_contexts'][0].update(positive_use=True))

    def test_unrelated_observation_changes_are_rejected(self):
        self.changed_after_rejected(lambda d: d[model.CORE[0]][0]['tools'][0].update(use_summary='changed'))

    def test_old_review_rewrites_are_rejected(self):
        self.changed_after_rejected(lambda d: d[model.CORE[3]]['reviews'][0].update(use_summary='changed'))

    def test_source_only_model_cannot_promote_family_implementation_status(self):
        self.changed_after_rejected(lambda d: next(r for r in d[model.CORE[1]] if r['id'] == 'king')
                                   .update(implementation_status='upstream-adapter-validated'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
