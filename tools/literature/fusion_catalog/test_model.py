"""Scientific-role, source-provenance and cumulative-credit admission regressions."""
import copy
import json
import subprocess
import unittest
from fusion_catalog import model as m

BASE = '25cbe501eb83049df29c16147a38b1dbbd590318'


class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = {n: subprocess.check_output(['git', 'show', BASE + ':' + n], cwd=m.ROOT) for n in m.META}
        cls.primary = m.frozen_primary()
        cls.q = json.loads((m.ROOT / 'docs/literature/fusion-workflow-qualification/STATUS.json').read_bytes())

    def after(self):
        return m.transition(self.before, self.primary, self.q)

    def test_one_family_six_reviews_and_one_removed_homonym(self):
        result = m.check(self.before, self.after(), self.primary, self.q)
        self.assertEqual((151, 27, 124, 1679, 1043, 2722), tuple(result[k] for k in
            ['scoped_families', 'native', 'upstream', 'reviewed_uses', 'pending_uses', 'observations']))
        self.assertEqual((1673, 2716, 445), tuple(result[k] for k in
            ['unchanged_prior_reviews', 'unchanged_unrelated_observations', 'unchanged_unrelated_inventory_rows']))

    def test_cohort_removed_and_exact_extraction_retained_separately(self):
        papers = json.loads(self.after()[m.META[0]])
        self.assertFalse(any(u['tool_id'] == 'fusion' for p in papers if p['paper_id'] == 'P212' for u in p['tools']))
        old = next(u for p in json.loads(self.before[m.META[0]]) if p['paper_id'] == 'P212' for u in p['tools'] if u['tool_id'] == 'fusion')
        self.assertEqual(old, self.primary['excluded_observations'][0]['original_observation'])
        self.assertEqual(0, self.primary['excluded_observations'][0]['positive_tool_use_credit'])

    def test_method_baseline_does_not_create_an_original_execution_claim(self):
        papers = json.loads(self.after()[m.META[0]])
        self.assertFalse(any(u['tool_id'] == 'fusion' for p in papers if p['paper_id'] == 'P108' for u in p['tools']))
        baseline = self.primary['additional_method_baselines'][0]
        self.assertFalse(baseline['original_upstream_execution_established'])
        self.assertEqual(0, baseline['positive_original_FUSION_tool_use_credit'])
        author = baseline['author_review']
        self.assertEqual(4, len(author['inspected_baseline_training_scripts']))
        self.assertEqual([], author['original_FUSION_command_matches'])
        self.assertFalse(author['author_code_executed'])
        self.assertFalse(author['source_or_archive_redistributed'])

    def test_training_colocalization_comparator_and_version_boundaries(self):
        papers = json.loads(self.after()[m.META[0]])
        use = lambda key: next(u for p in papers if p['paper_id'] == key for u in p['tools'] if u['tool_id'] == 'fusion')
        self.assertIn('BSLMM, LASSO, top1 and elastic net', use('P183')['use_summary'])
        self.assertIn('not performed for circRNAs', use('P183')['use_summary'])
        self.assertIn('HRS', use('P183')['implementation_scope_note'])
        self.assertIn('FUSION colocalization', use('P131')['use_summary'])
        self.assertIsNone(use('P131')['source_role_qualification']['reported_FUSION_edition'])
        self.assertIn('separate packages', use('P131')['use_summary'])
        self.assertIn('coloc-package', use('P181')['use_summary'])
        self.assertIn(28, [p['paragraph_index'] for p in use('P238')['supporting_evidence']])
        self.assertEqual(11, use('P238')['evidence_paragraph_index'])

    def test_missing_qualification_gates_and_substituted_producer_refused(self):
        for key in ['normal_tests', 'packaged_cases', 'resealed_offline_controls', 'runtime_controls']:
            q = copy.deepcopy(self.q); q[key] -= 1
            with self.subTest(key=key), self.assertRaises(ValueError):
                m.transition(self.before, self.primary, q)
        q = copy.deepcopy(self.q); q['producer']['sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            m.transition(self.before, self.primary, q)

    def test_broader_completion_claims_refused(self):
        for key in ['whole_tool_qualified', 'whole_paper_reproduced', 'whole_goal_complete']:
            q = copy.deepcopy(self.q); q[key] = True
            with self.subTest(key=key), self.assertRaises(ValueError):
                m.transition(self.before, self.primary, q)

    def test_changed_original_source_or_baseline_membership_refused(self):
        for paper_id in ['P112', 'P212']:
            before = dict(self.before); papers = json.loads(before[m.META[0]])
            next(u for p in papers if p['paper_id'] == paper_id for u in p['tools'] if u['tool_id'] == 'fusion')['evidence_paragraph_sha256'] = '0' * 64
            before[m.META[0]] = m.encoded(papers)
            with self.subTest(paper_id=paper_id), self.assertRaises(ValueError):
                m.transition(before, self.primary, self.q)

    def test_existing_review_cannot_be_counted_again(self):
        before = dict(self.before); ledger = json.loads(before[m.META[5]])
        ledger['reviews'][0].update(tool_id='fusion', pmcid=self.primary['roles'][0]['pmcid'])
        before[m.META[5]] = m.encoded(ledger)
        with self.assertRaises(ValueError):
            m.transition(before, self.primary, self.q)

    def test_altered_prior_review_unrelated_observation_or_source_role_refused(self):
        for filename, mutate in [(m.META[0], lambda x: x[0].update(doi='changed')),
                                  (m.META[5], lambda x: x['reviews'][0].update(use_summary='changed'))]:
            after = self.after(); value = json.loads(after[filename]); mutate(value); after[filename] = m.encoded(value)
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                m.check(self.before, after, self.primary, self.q)
        role = copy.deepcopy(self.primary); role['roles'][0]['summary'] = 'invented use'
        with self.assertRaises(ValueError):
            m.transition(self.before, role, self.q)


if __name__ == '__main__':
    unittest.main()
