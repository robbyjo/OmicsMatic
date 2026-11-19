# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admission, source-integrity, exact-inverse and owned-rollback controls."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from seurat_catalog.catalog import install, META, PROOF, STATUS
from seurat_catalog.evidence import baseline, packet_write, packet_read
from seurat_catalog.model import check, transition
from seurat_catalog.primary import supplement, encoded
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta


class SourceAdmissionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = baseline()
        cls.census = json.loads((ROOT / 'docs/literature/seurat-catalog-handover/PRIMARY-CENSUS.json').read_bytes())
        cls.after = transition(cls.before, cls.census)

    def test_01_live_stage_is_exact_before_or_qualified_after(self):
        current = {n: (ROOT / n).read_bytes() for n in META}
        if (ROOT / PROOF).exists():
            proof = json.loads((ROOT / PROOF).read_bytes())
            self.assertEqual(proof['status'], STATUS)
            self.assertEqual(current, self.after)
        else:
            self.assertEqual(current, self.before)

    def test_02_all_old_reviews_and_only_new_p160(self):
        result = check(self.before, self.after, self.census)
        self.assertEqual(result['observations_added'], [dict(paper_id='P160', tool_id='seurat', evidence_type='author-supplementary-pdf')])
        self.assertEqual((result['observations'], result['reviewed_uses'], result['unreviewed_uses']), (2696, 1339, 1357))
        self.assertEqual(result['existing_reviews_retained'], 1290)

    def test_03_review_inverse_retains_actual_bytes(self):
        name = 'tools/literature/reviewed-uses.json'
        change = delta(name, self.before[name], self.after[name])
        self.assertEqual(reverse_delta(self.after[name], change), self.before[name])

    def test_04_tampered_review_inverse_refused(self):
        name = 'tools/literature/reviewed-uses.json'
        change = delta(name, self.before[name], self.after[name])
        with self.assertRaises((ValueError, AssertionError, RuntimeError)):
            reverse_delta(self.after[name] + b' ', change)

    def test_05_source_identity_mutation_refused(self):
        census = copy.deepcopy(self.census)
        census['primary_files'][0]['doi'] = 'invented'
        with self.assertRaises(ValueError):
            transition(self.before, census)

    def test_06_adapted_method_cannot_become_direct_execution(self):
        census = copy.deepcopy(self.census)
        next(r for r in census['roles'] if r['paper_id'] == 'P047')['package_execution_established'] = True
        with self.assertRaises(ValueError):
            transition(self.before, census)

    def test_07_author_parity_claim_refused(self):
        census = copy.deepcopy(self.census)
        census['roles'][0]['complete_author_workflow_qualified'] = True
        with self.assertRaises(ValueError):
            transition(self.before, census)

    def test_08_pdf_page_identity_mutation_refused(self):
        census = copy.deepcopy(self.census)
        census['source_pdf_binding']['selected_pages'][0]['sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            transition(self.before, census)

    def test_09_missing_author_pdf_is_a_prerequisite_failure(self):
        paper = dict(paper_id='P160', doi='10.1038/s41588-025-02352-6')
        xml = ET.fromstring('<article xmlns:xlink="http://www.w3.org/1999/xlink"><supplementary-material id="MOESM1"><media xlink:href="41588_2025_2352_MOESM1_ESM.pdf"/></supplementary-material></article>')
        with self.assertRaises(FileNotFoundError):
            supplement(None, paper, xml)

    def test_10_wrong_pdf_is_an_integrity_failure(self):
        paper = dict(paper_id='P160', doi='10.1038/s41588-025-02352-6')
        xml = ET.fromstring('<article xmlns:xlink="http://www.w3.org/1999/xlink"><supplementary-material id="MOESM1"><media xlink:href="41588_2025_2352_MOESM1_ESM.pdf"/></supplementary-material></article>')
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'wrong.pdf'
            path.write_bytes(b'not the author supplement')
            with self.assertRaises(ValueError):
                supplement(path, paper, xml)

    def test_11_qualification_packet_tamper_refused(self):
        with tempfile.TemporaryDirectory() as d:
            folder = Path(d)
            expected = packet_write(folder, {'receipt.json': encoded(dict(results_admitted=True))})
            self.assertEqual(packet_read(folder / 'qualification-evidence.zip', expected), 1)
            with (folder / 'qualification-evidence.zip').open('ab') as stream:
                stream.write(b'corruption')
            with self.assertRaises(ValueError):
                packet_read(folder / 'qualification-evidence.zip', expected)

    def test_12_assertions_cannot_be_disabled(self):
        process = subprocess.run([sys.executable, '-O', '-B', str(ROOT / 'tools/literature/seurat_catalog/catalog.py'), '--help'], capture_output=True)
        self.assertEqual(process.returncode, 2)
        self.assertIn(b'rerun without Python -O', process.stderr)


class OwnedMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.out = self.root / 'out'
        self.out.mkdir()
        (self.root / 'old.json').write_bytes(b'original')
        self.expected = {'old.json': b'original', 'new.json': None}
        self.updates = {'old.json': b'changed', 'new.json': b'new'}

    def tearDown(self):
        self.temporary.cleanup()

    def test_13_successful_owned_transition(self):
        result = install(self.root, self.out, self.expected, self.updates, lambda: 'verified')
        self.assertEqual(result, 'verified')
        self.assertEqual((self.root / 'old.json').read_bytes(), b'changed')
        self.assertEqual((self.root / 'new.json').read_bytes(), b'new')

    def test_14_failed_validation_restores_old_and_retains_new(self):
        def fail():
            raise ValueError('failed validation')
        with self.assertRaises(ValueError):
            install(self.root, self.out, self.expected, self.updates, fail)
        self.assertEqual((self.root / 'old.json').read_bytes(), b'original')
        self.assertFalse((self.root / 'new.json').exists())
        self.assertEqual((self.out / 'retained-failed-metadata/new.json').read_bytes(), b'new')

    def test_15_concurrent_edits_survive_rollback(self):
        def edit_then_fail():
            (self.root / 'old.json').write_bytes(b'user concurrent edit')
            raise ValueError('validation fails after user edit')
        with self.assertRaises(ValueError):
            install(self.root, self.out, self.expected, self.updates, edit_then_fail)
        self.assertEqual((self.root / 'old.json').read_bytes(), b'user concurrent edit')
        report = json.loads((self.out / 'ROLLBACK.json').read_bytes())
        self.assertEqual(report['concurrent_changes_preserved'], ['old.json'])

    def test_16_preexisting_new_destination_refused(self):
        (self.root / 'new.json').write_bytes(b'user file')
        with self.assertRaises(ValueError):
            install(self.root, self.out, self.expected, self.updates, lambda: None)
        self.assertEqual((self.root / 'old.json').read_bytes(), b'original')
        self.assertEqual((self.root / 'new.json').read_bytes(), b'user file')

    def test_17_changed_predecessor_refused(self):
        (self.root / 'old.json').write_bytes(b'user edit')
        with self.assertRaises(ValueError):
            install(self.root, self.out, self.expected, self.updates, lambda: None)
        self.assertFalse((self.root / 'new.json').exists())

    def test_18_unowned_write_refused(self):
        with self.assertRaises(ValueError):
            install(self.root, self.out, {'old.json': b'original'}, self.updates, lambda: None)

    def test_19_workspace_escape_refused(self):
        with self.assertRaises(ValueError):
            install(self.root, self.out, {'../escape': None}, {'../escape': b'bad'}, lambda: None)

    def test_20_repeated_admission_refused(self):
        install(self.root, self.out, self.expected, self.updates, lambda: None)
        with self.assertRaises(ValueError):
            install(self.root, self.out, self.expected, self.updates, lambda: None)


if __name__ == '__main__':
    unittest.main(verbosity=2)
