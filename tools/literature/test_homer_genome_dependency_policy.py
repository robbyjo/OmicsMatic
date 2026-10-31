#!/usr/bin/env python3
"""Offline permission, content-pin and installation-path policies for genomic HOMER."""
import base64
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import install_homer_genome as genome


class GenomicDependencyPolicy(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def options(self, **extra):
        paths = dict(home=self.root / 'home', cache=self.root / 'cache', report=self.root / 'report')
        paths.update(extra)
        return ['--profile', 'linux-x86_64-genome-5.1', *sum((["--" + k, str(v)] for k, v in paths.items()), [])]

    def invoke(self, arguments):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return genome.main(arguments)

    def test_installation_permission_is_required_and_no_home_is_created(self):
        with patch.object(genome.core, 'host', return_value=dict(os='Linux', architecture='x86_64', kernel='test')):
            # Keep the policy portable while exercising the same Linux-safe home check.
            with patch.object(genome.re, 'fullmatch', return_value=True):
                self.assertEqual(3, self.invoke(self.options()))
        self.assertFalse((self.root / 'home').exists())
        record = json.loads((self.root / 'report/installation.json').read_text())
        self.assertIn('--allow-install absent', record['cause'])
        self.assertIn('Required action:', (self.root / 'report/WARNINGS.md').read_text())

    def test_unsupported_host_skips_before_creating_runtime_or_cache(self):
        with patch.object(genome.core, 'host', side_effect=genome.core.Unavailable('Unsupported test host')):
            self.assertEqual(3, self.invoke(self.options() + ['--allow-install']))
        self.assertFalse((self.root / 'home').exists()); self.assertFalse((self.root / 'cache').exists())

    def test_missing_compiler_skips_without_home_and_names_recovery(self):
        with patch.object(genome.core, 'host', return_value=dict(os='Linux', architecture='x86_64', kernel='test')):
            with patch.object(genome.re, 'fullmatch', return_value=True):
                self.assertEqual(3, self.invoke(self.options(compiler=self.root / 'missing-cxx') + ['--allow-install']))
        self.assertFalse((self.root / 'home').exists())
        self.assertIn('compiler/make unavailable', json.loads((self.root / 'report/installation.json').read_text())['cause'])

    def test_existing_report_is_preserved(self):
        report = self.root / 'report'; report.mkdir(); (report / 'sentinel').write_bytes(b'preserve')
        self.assertEqual(2, self.invoke(self.options()))
        self.assertEqual([report / 'sentinel'], list(report.iterdir()))
        self.assertEqual(b'preserve', (report / 'sentinel').read_bytes())

    def test_nested_home_cache_and_report_fail_without_making_home(self):
        self.assertEqual(2, self.invoke(self.options(home=self.root / 'cache/home')))
        self.assertFalse((self.root / 'cache').exists())

    def test_offline_missing_archive_skips_without_downloading(self):
        cache = self.root / 'cache'; cache.mkdir()
        with patch.object(genome.core.urllib.request, 'urlopen', side_effect=AssertionError('Network forbidden by test')):
            with self.assertRaises(genome.core.Unavailable):
                genome.core.obtain('homer.v5.1.zip', cache, [], False)
        self.assertEqual([], list(cache.iterdir()))

    def test_corrupt_existing_archive_is_retained(self):
        archive = self.root / 'homer.v5.1.zip'; archive.write_bytes(b'corrupt-retain')
        with self.assertRaises(ValueError):
            genome.core.obtain(archive.name, self.root, [], False)
        self.assertEqual(b'corrupt-retain', archive.read_bytes())

    def test_duplicate_zip_member_is_rejected(self):
        path = self.root / 'input.zip'; raw = b'original'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('bin/helper', raw)
            with __import__('warnings').catch_warnings():
                __import__('warnings').simplefilter('ignore', UserWarning)
                archive.writestr('bin/helper', raw)
        pin = dict(member='bin/helper', bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
        with zipfile.ZipFile(path) as archive, self.assertRaises(ValueError):
            genome.member_bytes(archive, pin)

    def test_zip_content_digest_is_required(self):
        path = self.root / 'input.zip'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('bin/helper', b'changed!')
        pin = dict(member='bin/helper', bytes=8, sha256=hashlib.sha256(b'original').hexdigest())
        with zipfile.ZipFile(path) as archive, self.assertRaises(ValueError):
            genome.member_bytes(archive, pin)

    def test_script_relocation_accepts_only_exact_original_body(self):
        home = self.root / 'home'; (home / 'bin').mkdir(parents=True)
        old = b'#!/usr/bin/env perl\nuse warnings;\nuse lib "OLD/bin";\nmy $homeDir = "OLD/";\n'
        body = b'print "original science";\n'; raw = old + body
        pin = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), relocate=True,
                   original_prefix_base64=base64.b64encode(old).decode())
        script = home / 'bin/helper'; script.write_bytes(genome.prefix(home) + body); script.chmod(0o755)
        self.assertEqual(genome.core.sha(script), genome.installed(home, 'bin/helper', pin))
        script.write_bytes(genome.prefix(home) + body.replace(b'science', b'changed'))
        with self.assertRaises(genome.core.Unavailable):
            genome.installed(home, 'bin/helper', pin)

    def test_runtime_marker_missing_is_unavailable_without_install(self):
        home = self.root / 'home'; home.mkdir()
        with patch.object(genome.re, 'fullmatch', return_value=True), self.assertRaises(genome.core.Unavailable):
            genome.verify_home(home, 'linux-x86_64-genome-5.1', {})
        self.assertEqual([], list(home.iterdir()))

    def test_lock_copies_and_shared_archive_pins_are_identical(self):
        self.assertEqual(genome.RAW, (genome.HERE.parents[1] / 'src/main/resources/org/jlinalg/omics/homer-genome-lock.json').read_bytes())
        self.assertEqual(genome.LOCK['archives'], genome.core.LOCK['archives'])
        self.assertEqual({'linux-x86_64-genome-' + v for v in ['5.1', '4.11', '4.11.1']}, set(genome.LOCK['profiles']))


if __name__ == '__main__':
    unittest.main()
