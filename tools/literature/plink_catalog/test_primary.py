# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-bound refusal cases; no research inputs or scientific runtime needed."""
from pathlib import Path
import copy
import hashlib
import json
import sys
import tempfile
import unittest
import zipfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plink_catalog import primary


class SourceTests(unittest.TestCase):
    def decision(self, literal, indices=None):
        return dict(paragraph_indices=[1], reported_editions=[dict(
            literal=literal, paragraph_indices=[1] if indices is None else indices,
            context='selected software stage')])

    def test_version_prefix_and_sentence_punctuation_are_not_build_digits(self):
        for text in ['PLINK v.2.0 was used.', 'PLINK v2.0.', 'PLINK2.0 (reference).']:
            primary.validate_editions(self.decision('2.0'), {1: dict(text=text, match_text=text)})

    def test_longer_and_nested_editions_are_not_shorter_builds(self):
        for text in ['PLINK v.2.00a5LM', 'PLINK v.1.2.0', 'PLINK2.0.1']:
            with self.assertRaises(ValueError):
                primary.validate_editions(self.decision('2.0'), {1: dict(text=text, match_text=text)})

    def test_bibliography_labels_cannot_become_an_edition(self):
        paragraph = dict(text='PLINK v.1.934 was used.', match_text='PLINK v.1.9 was used.')
        primary.validate_editions(self.decision('1.9'), {1: paragraph})
        with self.assertRaises(ValueError):
            primary.validate_editions(self.decision('1.934'), {1: paragraph})

    def test_edition_cannot_migrate_to_another_cohort_paragraph(self):
        paragraphs = {1: dict(text='PLINK used without edition.'), 2: dict(text='PLINK v.2.0.')}
        with self.assertRaises(ValueError):
            primary.validate_editions(self.decision('2.0', [2]), paragraphs)
        with self.assertRaises(ValueError):
            primary.validate_editions(self.decision('2.0'), paragraphs)

    def archive(self, root):
        path = root / 'author.zip'
        text = b'plink --cnv-write\n'
        with zipfile.ZipFile(path, 'x') as archive:
            archive.writestr('author/workflow.R', text)
        raw = path.read_bytes()
        binding = dict(archive_url='https://example.invalid/frozen-author.zip',
                       archive_bytes=len(raw), archive_sha256=hashlib.sha256(raw).hexdigest(),
                       archive_published_md5='md5:' + hashlib.md5(raw).hexdigest(),
                       members=[dict(member='author/workflow.R', bytes=len(text),
                                     sha256=hashlib.sha256(text).hexdigest(), line_count=1,
                                     selected_line_sha256={'1': hashlib.sha256(text.rstrip(b'\n')).hexdigest()})],
                       scientific_code_executed=False, script_execution_observed=False,
                       complete_author_workflow_qualified=False)
        return path, binding

    def test_exact_author_archive_is_read_without_extracting_or_executing(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); path, binding = self.archive(root)
            before = path.read_bytes()
            self.assertEqual(primary.verify_author_archive(path, binding), binding)
            self.assertEqual(path.read_bytes(), before)
            self.assertEqual(list(root.iterdir()), [path])

    def test_missing_and_changed_archive_are_distinct_refusals(self):
        with self.assertRaises(primary.MissingSource):
            primary.verify_author_archive(None, dict(archive_url='https://example.invalid/a', archive_sha256='0' * 64))
        with tempfile.TemporaryDirectory() as folder:
            path, binding = self.archive(Path(folder))
            raw = bytearray(path.read_bytes()); raw[-1] ^= 1; path.write_bytes(raw)
            with self.assertRaises(ValueError):
                primary.verify_author_archive(path, binding)

    def test_member_line_and_execution_claim_changes_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path, binding = self.archive(Path(folder))
            for edit in ['member', 'line', 'execution']:
                changed = copy.deepcopy(binding)
                if edit == 'member': changed['members'][0]['sha256'] = '0' * 64
                if edit == 'line': changed['members'][0]['selected_line_sha256']['1'] = '0' * 64
                if edit == 'execution': changed['scientific_code_executed'] = True
                with self.subTest(edit=edit), self.assertRaises(ValueError):
                    primary.verify_author_archive(path, changed)

    def test_no_duplicate_or_conflicting_manual_decisions(self):
        primary.check_decision_keys()
        self.assertEqual(len(primary.REVIEWS), 96)
        self.assertEqual(len(primary.CONTEXTS), 7)
        self.assertEqual(len(primary.RAW_CONTEXTS), 8)
        self.assertEqual(sum(r['preserve_prior_review'] for r in primary.REVIEWS.values()), 22)


if __name__ == '__main__':
    unittest.main(verbosity=2)
