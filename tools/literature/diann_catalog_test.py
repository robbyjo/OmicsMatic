"""Catalog transition tests use a synthetic qualification fixture, never analysis evidence."""
import copy
import json
import re
import unittest
from pathlib import Path
import diann_catalog as catalog
import diann_catalog_model as model
from gemma_primary import encoded

ROOT = Path(__file__).resolve().parents[2]


class DiannCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = catalog.before(ROOT)
        cls.roles = catalog.source_roles(ROOT)
        cls.q = dict(fixture_only=True, status='PASS_SCOPED_DIA_NN_ORIGINAL_LIBRARY_FREE_CAPTURE',
            Windows_focused_JUnit=74, Linux_focused_JUnit=74, JUnit_skips=0,
            edition_profiles=['1.8', '1.8.1', '2.0.2'], typed_full_Linux_captures=3,
            complete_distinct_raw_runs_each_capture=2, original_fresh_MBR_controls=3,
            portable_positive_controls_each_OS=3, portable_refusal_controls_each_OS=18,
            private_probes_packaged=False, only_prior_packaged_change='org/jlinalg/cli/JLinAlgCli.class',
            new_upstream_family_credit=1, new_native_family_credit=0,
            whole_package_qualified=False, whole_paper_reproduced=False,
            full_author_roles_implemented=False, PTM_localisation_qualified=False, whole_goal_complete=False)
        cls.after = model.transition(cls.before, cls.roles, cls.q)

    def test_one_family_and_no_new_papers_observations_or_reviews(self):
        result = model.check(self.before, self.after, self.roles, self.q)
        self.assertEqual((250, 451, 490, 2731, 1802, 929, 166, 32, 134, 255),
            tuple(result[key] for key in ('papers', 'labels', 'provider_definitions', 'observations',
                'reviewed_uses', 'pending_uses', 'scoped_families', 'native', 'upstream', 'missing')))
        self.assertEqual((0, 0, 0), tuple(result[key] for key in
            ('new_source_role_reviews', 'new_named_providers', 'added_positive_observations')))

    def test_partial_or_expanded_qualification_refused(self):
        cases = [('Windows_focused_JUnit', 73), ('Linux_focused_JUnit', 73), ('JUnit_skips', 1),
            ('typed_full_Linux_captures', 2), ('complete_distinct_raw_runs_each_capture', 1),
            ('original_fresh_MBR_controls', 2), ('portable_positive_controls_each_OS', 2),
            ('portable_refusal_controls_each_OS', 17), ('private_probes_packaged', True),
            ('new_upstream_family_credit', 2), ('new_native_family_credit', 1)]
        cases += [(key, True) for key in ('whole_package_qualified', 'whole_paper_reproduced',
            'full_author_roles_implemented', 'PTM_localisation_qualified', 'whole_goal_complete')]
        for key, value in cases:
            q = copy.deepcopy(self.q); q[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                model.transition(self.before, self.roles, q)

    def test_literal_editions_intensity_and_normalised_readout_preserved(self):
        for role in self.roles['roles']:
            changed = copy.deepcopy(self.roles)
            next(r for r in changed['roles'] if r['paper_id'] == role['paper_id'])['literal_reported_version'] = 'latest'
            with self.subTest(paper=role['paper_id']), self.assertRaises(ValueError):
                model.transition(self.before, changed, self.q)
        for paper, key, value in [('P029', 'reported_intensity_name_literal', 'MaxLFQ'),
                                   ('P097', 'readout', 'PG.MaxLFQ')]:
            changed = copy.deepcopy(self.roles)
            next(r for r in changed['roles'] if r['paper_id'] == paper)['reported_settings'][key] = value
            with self.subTest(paper=paper), self.assertRaises(ValueError):
                model.transition(self.before, changed, self.q)

    def test_unrelated_observations_reviews_and_classifications_preserved(self):
        changed = dict(self.after); rows = json.loads(changed[model.META[1]])
        next(row for row in rows if row['id'] == 'iq')['implementation_status'] = 'missing'
        changed[model.META[1]] = encoded(rows)
        with self.assertRaises(ValueError): model.check(self.before, changed, self.roles, self.q)
        changed = dict(self.after); ledger = json.loads(changed[model.META[5]])
        ledger['reviews'][0]['use_summary'] = 'unrelated edit'
        changed[model.META[5]] = encoded(ledger)
        with self.assertRaises(ValueError): model.check(self.before, changed, self.roles, self.q)

    def test_scoped_availability_does_not_complete_author_roles_or_other_tools(self):
        inventory = json.loads(self.after[model.META[1]])
        row = next(r for r in inventory if r['id'] == 'diann')
        self.assertEqual(1, row['family_credit'])
        for role in row['paper_role_qualification'].values():
            self.assertTrue(role['implementation_available'])
            self.assertFalse(role['full_source_role_implemented'])
            self.assertFalse(role['whole_paper_reproduced'])
            self.assertFalse(role['paper_edition_reproduced'])
            self.assertEqual(0, role['new_family_credit'])
        self.assertEqual('SKIP3_USER_REQUESTED_KEEP_TOOL_PENDING',
            next(r for r in inventory if r['id'] == 'maxquant')['runtime_analysis_status'])
        for name in ('andromeda', 'maxdia'):
            self.assertEqual('missing', next(r for r in inventory if r['id'] == name)['implementation_status'])

    def test_report_embedded_json_and_role_renderer(self):
        page = self.after[model.META[6]].decode()
        data = json.loads(re.search(r'<script id="review-data" type="application/json">(.*?)</script>', page, re.S)[1])
        self.assertEqual(dict(papers=json.loads(self.after[model.META[0]]), tools=json.loads(self.after[model.META[1]]),
                              summary=json.loads(self.after[model.META[2]])), data)
        self.assertEqual(1, page.count('id="diann-catalog-notice"'))
        self.assertIn('${diannVersion(t)}', page)
        for literal in ('full author role pending', 'SKIP3', 'FAIL2', 'Before DIA-NN admission (historical)'):
            self.assertIn(literal, page)

    def test_only_target_registry_row_changes(self):
        old = json.loads((ROOT / model.ADMISSION / 'PREVIOUS-REGISTRY.json').read_bytes())
        new = model.registry(old)
        self.assertEqual(490, len(new))
        self.assertEqual([row for row in old if row['id'] != 'diann'],
                         [row for row in new if row['id'] != 'diann'])
        self.assertEqual(1, sum(left != right for left, right in zip(old, new, strict=True)))


if __name__ == '__main__':
    unittest.main()
