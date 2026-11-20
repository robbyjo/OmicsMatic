"""Owned metadata transaction and source-inverse refusal controls."""
from pathlib import Path
import contextlib
import hashlib
import io
import json
import os
import unittest
from unittest import mock
import uuid
import zipfile
from edger_catalog import catalog as c
from edger_catalog import model as m
from edger_catalog import evidence as e
from edger_dispatch_transition import BEFORE, DISPATCH, dispatcher_before
from seurat_catalog.catalog import install
from catalog_source_integrity import verify_source
from wire_signac_catalog import delta

ROOT = Path(__file__).resolve().parents[3]


class EdgeROwnedAdmissionControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        requested = os.environ.get('OMICSMATIC_EDGER_ADMISSION_TEST_ROOT')
        cls.folder = Path(requested) if requested else ROOT / 'build' / ('edger-admission-tests-' + uuid.uuid4().hex)
        cls.folder = cls.folder.resolve()
        if not cls.folder.is_relative_to((ROOT / 'build').resolve()):
            raise ValueError('Keep retained test artifacts inside this workspace build directory')
        cls.folder.mkdir(parents=True, exist_ok=False)

    def setUp(self):
        self.case = self.folder / self.id().rsplit('.', 1)[-1]
        self.root = self.case / 'root'
        self.out = self.case / 'receipt'
        self.root.mkdir(parents=True)
        self.out.mkdir()
        (self.root / 'sub').mkdir()
        (self.root / 'a.txt').write_bytes(b'original')

    def transaction(self, post):
        return install(self.root, self.out, {'a.txt': b'original', 'sub/b.txt': None},
            {'a.txt': b'replacement', 'sub/b.txt': b'new retained bytes'}, post)

    def test_exact_owned_writes(self):
        self.assertEqual('complete', self.transaction(lambda: 'complete'))
        self.assertEqual(b'replacement', (self.root / 'a.txt').read_bytes())
        self.assertEqual(b'new retained bytes', (self.root / 'sub/b.txt').read_bytes())

    def test_stale_before_is_refused_without_overwrite(self):
        (self.root / 'a.txt').write_bytes(b'concurrent')
        with self.assertRaises(ValueError):
            self.transaction(lambda: None)
        self.assertEqual(b'concurrent', (self.root / 'a.txt').read_bytes())
        self.assertFalse((self.root / 'sub/b.txt').exists())

    def test_failure_restores_original_and_retains_new_bytes(self):
        def fail():
            raise ValueError('controlled post-write validation failure')
        with self.assertRaises(ValueError):
            self.transaction(fail)
        self.assertEqual(b'original', (self.root / 'a.txt').read_bytes())
        self.assertFalse((self.root / 'sub/b.txt').exists())
        self.assertEqual(b'new retained bytes', (self.out / 'retained-failed-metadata/sub/b.txt').read_bytes())

    def test_failure_preserves_a_concurrent_user_change(self):
        def fail():
            (self.root / 'a.txt').write_bytes(b'foreign change')
            raise ValueError('controlled concurrent post-write change')
        with self.assertRaises(ValueError):
            self.transaction(fail)
        self.assertEqual(b'foreign change', (self.root / 'a.txt').read_bytes())
        record = json.loads((self.out / 'ROLLBACK.json').read_bytes())
        self.assertEqual(['a.txt'], record['concurrent_changes_preserved'])

    def test_escape_target_is_refused(self):
        outside = self.case / 'outside.txt'
        outside.write_bytes(b'outside original')
        with self.assertRaises(ValueError):
            install(self.root, self.out, {'../outside.txt': b'outside original'},
                {'../outside.txt': b'altered'}, lambda: None)
        self.assertEqual(b'outside original', outside.read_bytes())

    def test_unexpected_existing_new_target_is_refused(self):
        (self.root / 'sub/b.txt').write_bytes(b'user bytes')
        with self.assertRaises(ValueError):
            self.transaction(lambda: None)
        self.assertEqual(b'original', (self.root / 'a.txt').read_bytes())
        self.assertEqual(b'user bytes', (self.root / 'sub/b.txt').read_bytes())

    def test_incomplete_before_archive_is_refused(self):
        path = self.case / 'incomplete.zip'
        with zipfile.ZipFile(path, 'x') as z:
            z.writestr(m.META[0], b'[]')
        with self.assertRaises(ValueError):
            c.read_before(path)

    def test_extra_before_archive_member_is_refused(self):
        path = self.case / 'extra.zip'
        with zipfile.ZipFile(path, 'x') as z:
            for name in m.META:
                z.writestr(name, b'{}')
            z.writestr('unreviewed.txt', b'not a canonical input')
        with self.assertRaises(ValueError):
            c.read_before(path)

    def test_scientific_source_cannot_use_metadata_inverse(self):
        relative = 'src/main/java/org/jlinalg/cli/EdgeRWorkflowRuntime.java'
        target = self.root / relative
        target.parent.mkdir(parents=True)
        target.write_bytes(b'new scientific source\n')
        data = self.root / 'data'
        data.mkdir()
        change = delta(relative, b'old scientific source\n', target.read_bytes())
        (data / 'fake-shared-source-compatibility.json').write_bytes(m.encoded(dict(schema_version=1,
            status='passed-dispatch-catalog-only-source-deltas', paths=[change])))
        with self.assertRaisesRegex(ValueError, 'Changed scientific/unreviewed source'):
            verify_source(self.root, data, relative, hashlib.sha256(b'old scientific source\n').hexdigest())

    def test_dispatcher_proposal_accepts_only_exact_original(self):
        original = (ROOT / DISPATCH).read_bytes()
        if e.pin(original) != BEFORE:
            original = dispatcher_before(original)
        after = c.proposed_dispatcher(original)
        self.assertEqual(original, dispatcher_before(after))
        with self.assertRaises(ValueError):
            c.proposed_dispatcher(original + b'\n')

    def test_wrong_plan_status_is_refused(self):
        (self.root / 'PLAN.json').write_bytes(m.encoded(dict(schema_version=1, status='unreviewed')))
        with self.assertRaises(ValueError):
            c.inspect(self.root)

    def test_missing_qualified_owner_sources_in_plan_are_refused(self):
        (self.root / 'PLAN.json').write_bytes(m.encoded(dict(schema_version=1,
            status='PLANNED_EDGER_CUMULATIVE_ADMISSION', source_code={})))
        with self.assertRaises(ValueError):
            c.inspect(self.root)

    def test_existing_cli_output_is_preserved(self):
        (self.out / 'sentinel.txt').write_bytes(b'preserve existing output')
        argv = ['close_edger_catalog', '--verify', '--out', str(self.out)]
        with mock.patch('sys.argv', argv), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, c.main())
        self.assertEqual(b'preserve existing output', (self.out / 'sentinel.txt').read_bytes())

    def test_cli_output_outside_build_is_refused(self):
        target = ROOT / 'docs' / ('forbidden-edger-output-' + uuid.uuid4().hex)
        argv = ['close_edger_catalog', '--verify', '--out', str(target)]
        with mock.patch('sys.argv', argv), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, c.main())
        self.assertFalse(target.exists())

    def test_registry_keeps_definition_population(self):
        rows = m.registry()
        self.assertEqual(485, len(rows))
        self.assertEqual('upstream-adapter-validated', next(r for r in rows if r['id'] == 'edger')['current_coverage'])


if __name__ == '__main__':
    unittest.main()
