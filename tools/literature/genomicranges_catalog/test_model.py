"""Credit, source provenance and preservation regressions using the real baseline."""
from pathlib import Path
import copy
import json
import subprocess
import unittest
from genomicranges_catalog import model as m, primary as p

BASE = '3c9cc8a71b8adc8eb9d1ff22f915e7bb1dfa999f'


class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = {n: subprocess.check_output(['git', 'show', BASE + ':' + n], cwd=p.ROOT) for n in m.META}
        cls.primary = p.frozen()
        cls.qualified = json.loads((p.ROOT / 'docs/literature/genomicranges-qualification/QUALIFICATION.json').read_bytes())

    def test_actual_transition_gives_one_provider_and_exactly_five_new_reviews(self):
        after = m.transition(self.before, self.primary, self.qualified)
        result = m.check(self.before, after, self.primary, self.qualified)
        self.assertEqual((149, 27, 122, 1666, 1057), tuple(result[k] for k in ['scoped_families', 'native', 'upstream', 'reviewed_uses', 'pending_uses']))
        self.assertEqual(5, result['new_reviews'])
        self.assertEqual(0, result['new_native_family_credit'])
        self.assertEqual(2, result['existing_scope_notes_updated_without_review_credit'])

    def test_every_missing_measured_gate_is_refused(self):
        for key in ['normal_tests', 'matrix_cases', 'full_RDS_comparisons', 'original_error_comparisons',
                    'replays', 'public_resealed_controls', 'runtime_controls', 'intake_replay_controls',
                    'fresh_primary_sources', 'named_source_roles', 'proposed_new_reviews',
                    'source_functions_per_profile', 'runtime_capture_count']:
            with self.subTest(gate=key):
                q = copy.deepcopy(self.qualified)
                q[key] -= 1
                with self.assertRaises(ValueError):
                    m.transition(self.before, self.primary, q)

    def test_old_unsafe_or_substituted_producer_cannot_receive_credit(self):
        q = copy.deepcopy(self.qualified)
        q['producer']['sha256'] = 'fbd4c907737e2bb5b734be605bc6ba67afe495b5a713f62137b4caba232a7819'
        with self.assertRaises(ValueError):
            m.transition(self.before, self.primary, q)

    def test_linux_or_whole_paper_claim_cannot_replace_the_measured_scope(self):
        for key in ['whole_tool_qualified', 'whole_paper_reproduced', 'whole_goal_complete']:
            q = copy.deepcopy(self.qualified)
            q[key] = True
            with self.subTest(claim=key), self.assertRaises(ValueError):
                m.transition(self.before, self.primary, q)
        q = copy.deepcopy(self.qualified)
        q['profiles'][0] = 'unmeasured-linux-profile'
        with self.assertRaises(ValueError):
            m.transition(self.before, self.primary, q)

    def test_unnamed_function_is_not_attributed_to_common_peak_paper(self):
        primary = copy.deepcopy(self.primary)
        next(r for r in primary['roles'] if r['paper_id'] == 'P051')['functions'] = ['reduce']
        with self.assertRaises(ValueError):
            m.transition(self.before, primary, self.qualified)

    def test_unattributed_mention_cannot_become_an_eighth_provider_role(self):
        primary = copy.deepcopy(self.primary)
        role = copy.deepcopy(primary['roles'][0])
        role['paper_id'] = 'P039'
        primary['roles'].append(role)
        with self.assertRaises(ValueError):
            m.transition(self.before, primary, self.qualified)

    def test_changed_paragraph_or_existing_reviewed_summary_is_refused(self):
        for field in ['evidence_paragraph_sha256', 'use_summary']:
            before = dict(self.before)
            papers = json.loads(before[m.META[0]])
            use = next(u for paper in papers if paper['paper_id'] == 'P094' for u in paper['tools'] if u['tool_id'] == m.TARGET)
            use[field] = 'changed'
            before[m.META[0]] = p.encoded(papers)
            with self.subTest(field=field), self.assertRaises(ValueError):
                m.transition(before, self.primary, self.qualified)

    def test_unrelated_paper_or_previous_review_mutation_is_rejected(self):
        for file, mutate in [(m.META[0], lambda j: j[0].update(doi='changed')),
                             (m.META[5], lambda j: j['reviews'][0].update(use_summary='changed'))]:
            after = m.transition(self.before, self.primary, self.qualified)
            data = json.loads(after[file])
            mutate(data)
            after[file] = p.encoded(data)
            with self.subTest(file=file), self.assertRaises(ValueError):
                m.check(self.before, after, self.primary, self.qualified)

    def test_old_extracted_version_and_both_reported_editions_remain_distinct(self):
        after = m.transition(self.before, self.primary, self.qualified)
        papers = json.loads(after[m.META[0]])
        use = next(u for paper in papers if paper['paper_id'] == 'P094' for u in paper['tools'] if u['tool_id'] == m.TARGET)
        self.assertEqual('1.58.0', use['version_reported_near_name'])
        self.assertEqual(['1.58.0', '1.60.0'], use['source_role_qualification']['reported_editions'])
        self.assertEqual('2.40.0', use['source_role_qualification']['linked_author_code']['recorded_session']['IRanges'])


if __name__ == '__main__':
    unittest.main()
