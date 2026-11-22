"""Real baseline conservation, exact source roles and unearned-credit refusals."""
import copy
import json
import subprocess
import unittest
from gprofiler_catalog import model as m, primary as p

BASE = '744d366e3d2246bfe9831a3eec30a931fc864c35'


class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = {n: subprocess.check_output(['git', 'show', BASE + ':' + n], cwd=p.ROOT) for n in m.META}
        cls.primary = m.frozen_primary()
        cls.q = json.loads((p.ROOT / 'docs/literature/gprofiler-workflow-qualification/QUALIFICATION.json').read_bytes())

    def test_one_upstream_provider_seven_reviews_and_all_prior_records_conserved(self):
        after = m.transition(self.before, self.primary, self.q)
        result = m.check(self.before, after, self.primary, self.q)
        self.assertEqual((150, 27, 123, 1673, 1050), tuple(result[k] for k in ['scoped_families', 'native', 'upstream', 'reviewed_uses', 'pending_uses']))
        self.assertEqual(2716, result['unchanged_unrelated_observations'])
        self.assertEqual(1666, result['unchanged_prior_reviews'])
        self.assertEqual(0, result['new_native_family_credit'])

    def test_each_missing_gate_or_substituted_producer_is_refused(self):
        for key in ['normal_tests', 'enrichment_RUN_cases', 'metadata_RUN_cases', 'Windows_offline_REPLAY_cases',
                    'Linux_offline_REPLAY_cases', 'saved_protocol_refusal_controls',
                    'runtime_resource_replay_refusal_controls', 'exact_build_preflight_SKIP3_cases']:
            q = copy.deepcopy(self.q); q[key] -= 1
            with self.subTest(key=key), self.assertRaises(ValueError):
                m.transition(self.before, self.primary, q)
        q = copy.deepcopy(self.q); q['producer']['sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            m.transition(self.before, self.primary, q)

    def test_overbroad_completion_and_unredacted_export_claims_refused(self):
        for key in ['whole_tool_qualified', 'whole_paper_reproduced', 'whole_goal_complete', 'public_cookie_values_redacted']:
            q = copy.deepcopy(self.q); q[key] = not q[key]
            with self.subTest(key=key), self.assertRaises(ValueError):
                m.transition(self.before, self.primary, q)

    def test_renamed_omitted_or_reinterpreted_primary_role_refused(self):
        for key, value in [('paper_id', 'P001'), ('reported_client', 'invented'), ('summary', 'generic enrichment')]:
            primary = copy.deepcopy(self.primary); primary['roles'][0][key] = value
            with self.subTest(field=key), self.assertRaises(ValueError):
                m.transition(self.before, primary, self.q)
        primary = copy.deepcopy(self.primary); primary['roles'].pop()
        with self.assertRaises(ValueError):
            m.transition(self.before, primary, self.q)

    def test_changed_full_paragraph_source_anchor_refused(self):
        before = dict(self.before); papers = json.loads(before[m.META[0]])
        next(u for paper in papers if paper['paper_id'] == 'P052' for u in paper['tools'] if u['tool_id'] == m.TARGET)['evidence_paragraph_sha256'] = '0' * 64
        before[m.META[0]] = m.encoded(papers)
        with self.assertRaises(ValueError):
            m.transition(before, self.primary, self.q)

    def test_existing_review_identity_cannot_be_counted_twice(self):
        before = dict(self.before); ledger = json.loads(before[m.META[5]])
        ledger['reviews'][0].update(pmcid=self.primary['roles'][0]['pmcid'], tool_id=m.TARGET)
        before[m.META[5]] = m.encoded(ledger)
        with self.assertRaises(ValueError):
            m.transition(before, self.primary, self.q)

    def test_unrelated_observation_or_prior_ledger_mutation_refused(self):
        for filename, mutate in [(m.META[0], lambda j: j[0].update(doi='changed')),
                                 (m.META[5], lambda j: j['reviews'][0].update(use_summary='changed'))]:
            after = m.transition(self.before, self.primary, self.q)
            value = json.loads(after[filename]); mutate(value); after[filename] = m.encoded(value)
            with self.subTest(file=filename), self.assertRaises(ValueError):
                m.check(self.before, after, self.primary, self.q)

    def test_client_versions_builds_and_unknowns_stay_distinct(self):
        after = m.transition(self.before, self.primary, self.q)
        papers = json.loads(after[m.META[0]])
        use = lambda id: next(u for paper in papers if paper['paper_id'] == id for u in paper['tools'] if u['tool_id'] == m.TARGET)
        self.assertIsNone(use('P227')['version_reported_near_name'])
        self.assertEqual('0.2.1', use('P227')['source_role_qualification']['reported_client'])
        self.assertEqual('0.2.3', use('P090')['source_role_qualification']['reported_client'])
        self.assertEqual(['e111_eg58_p18_30541362'], use('P052')['source_role_qualification']['reported_builds'])
        self.assertFalse(use('P052')['source_role_qualification']['runtime_qualified'])


if __name__ == '__main__':
    unittest.main()
