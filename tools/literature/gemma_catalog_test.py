"""Frozen-cohort catalog integration and intentional semantic-regression checks."""
from pathlib import Path
import copy
import json
import re
import unittest
import zipfile
import gemma_catalog_model as model
import gemma_catalog as catalog
from gemma_primary import encoded

ROOT = Path(__file__).resolve().parents[2]

class GemmaCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(ROOT / 'docs/literature/gemma-catalog-admission/metadata-before.zip') as archive:
            cls.before = {name: archive.read(name) for name in archive.namelist()}
        cls.roles = json.loads((ROOT / 'docs/literature/gemma-source-audit/ROLES.json').read_bytes())
        cls.qualification = json.loads((ROOT / 'docs/literature/gemma-workflow-checkpoint/QUALIFICATION.json').read_bytes())
        cls.after = model.transition(cls.before, cls.roles, cls.qualification)

    def test_complete_frozen_transition_preserves_unrelated_data_and_exposes_unresolved_version(self):
        result = model.check(self.before, self.after, self.roles, self.qualification)
        self.assertEqual((163, 30, 133, 1776, 948), tuple(result[k] for k in
            ('scoped_families', 'native', 'upstream', 'reviewed_uses', 'pending_uses')))
        papers = json.loads(self.after[model.META[0]])
        p176 = next(p for p in papers if p['paper_id'] == 'P176')
        use = next(u for u in p176['tools'] if u['tool_id'] == 'gemma')
        self.assertEqual('0.983', use['version_reported_near_name'])
        self.assertIsNone(use['source_role_qualification']['selected_qualified_runtime_edition'])
        page = self.after[model.META[6]].decode()
        payload = re.search(r'<script id="review-data" type="application/json">(.*?)</script>', page, re.S)
        self.assertIsNotNone(payload)
        self.assertEqual(papers, json.loads(payload[1])['papers'])
        self.assertIn('Exact release unresolved; no substitution', page)
        self.assertEqual(1, page.count('id="gemma-catalog-notice"'))
        self.assertIn('Before GEMMA admission (historical)', page)

    def test_unrelated_observation_mutation_is_refused(self):
        altered = dict(self.after)
        papers = json.loads(altered[model.META[0]])
        unrelated = next(u for p in papers for u in p['tools'] if u['tool_id'] != 'gemma')
        unrelated['use_summary'] += ' controlled unrelated mutation'
        altered[model.META[0]] = encoded(papers)
        with self.assertRaises(ValueError):
            model.check(self.before, altered, self.roles, self.qualification)

    def test_raw_P176_version_cannot_be_normalized_to_another_release(self):
        altered = dict(self.before)
        papers = json.loads(altered[model.META[0]])
        use = next(u for p in papers if p['paper_id'] == 'P176' for u in p['tools'] if u['tool_id'] == 'gemma')
        use['version_reported_near_name'] = '0.98.3'
        altered[model.META[0]] = encoded(papers)
        with self.assertRaises(ValueError):
            model.transition(altered, self.roles, self.qualification)
        roles = copy.deepcopy(self.roles)
        next(r for r in roles['roles'] if r['paper_id'] == 'P176')['reported_edition'] = '0.98.3'
        with self.assertRaises(ValueError):
            model.transition(self.before, roles, self.qualification)

    def test_incomplete_or_expanded_workflow_claims_cannot_grant_catalog_credit(self):
        for key, value in (('admitted_captures', 59), ('optimizer_convergence_certified', True),
                           ('whole_paper_reproduced', True), ('P176_literal_0_983_resolved', True)):
            q = copy.deepcopy(self.qualification)
            q[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                model.transition(self.before, self.roles, q)

    def test_effective_registry_changes_only_GEMMA_and_native_component_has_no_duplicate_credit(self):
        old = json.loads((ROOT / 'docs/literature/gemma-catalog-admission/PREVIOUS-REGISTRY.json').read_bytes())
        new = model.registry()
        self.assertEqual(485, len(new))
        self.assertEqual([r for r in old if r['id'] != 'gemma'], [r for r in new if r['id'] != 'gemma'])
        inventory = json.loads(self.after[model.META[1]])
        target = next(r for r in inventory if r['id'] == 'gemma')
        self.assertEqual(0, target['native_component_family_credit'])
        self.assertEqual(0, target['new_native_family_credit'])
        self.assertEqual(1, target['new_upstream_family_credit'])
        self.assertEqual('src/main/java/org/jlinalg/gwas/RemlAssociationScanner.java', target['related_native_method_source'])

    def test_committed_legacy_checksum_paths_are_preserved_and_unlisted_escapes_are_refused(self):
        rows = catalog.checksum_rows((ROOT / 'docs/literature/gemma-catalog-admission/PREVIOUS-SHA256SUMS').read_bytes())
        self.assertEqual(877, len(rows))
        self.assertEqual(set(catalog.LEGACY_CHECKSUM_MEMBERS), {n for n in rows if n.startswith('../')})
        self.assertTrue(all((ROOT / model.DATA / n).resolve().is_relative_to(ROOT) for n in rows))
        for name in ('../../../../outside', '../unlisted.txt', '/etc/passwd', 'C:/outside'):
            with self.subTest(member=name), self.assertRaises(ValueError):
                catalog.checksum_rows(('0' * 64 + '  ' + name + '\n').encode())

if __name__ == '__main__':
    unittest.main()
