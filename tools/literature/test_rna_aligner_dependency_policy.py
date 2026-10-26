"""Installer policy/extraction tests; no scientific binary or network is invoked."""
import argparse
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import install_rna_aligners as installer
from install_aligner_dependencies import mark


class RnaAlignerPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.args=argparse.Namespace(profile='minimap2',environment=self.root/'env',report=self.root/'report',cache=self.root/'cache',
            cygwin_root=self.root/'cygwin',allow_download=False,allow_source_build=False,build_threads=1,build_timeout=60)
    def tearDown(self): self.temp.cleanup()
    def install(self):
        with patch.object(installer,'download',side_effect=AssertionError('No network in policy tests')),patch.object(installer,'probe'):
            code=installer.install(self.args)
        return json.loads((self.args.report/'dependency-status.json').read_text(encoding='utf-8')),code
    def test_denied_download_skips_with_required_action(self):
        d,c=self.install();self.assertEqual(3,c);self.assertEqual('skipped-install-not-permitted',d['status']);self.assertFalse(self.args.environment.exists())
        self.assertIn('Required action',(self.args.report/'WARNINGS.md').read_text());self.assertIn('Skip dependent analyses',d['required_action'])
    def test_denied_source_build_is_separate_from_download_authorization(self):
        self.args.allow_download=True
        with patch.object(installer.sys,'platform','win32'),patch.object(installer.platform,'machine',return_value='AMD64'):
            d,c=self.install()
        self.assertEqual(3,c);self.assertEqual('skipped-source-build-not-permitted',d['status'])
    def test_existing_unmanaged_or_corrupt_runtime_is_preserved(self):
        self.args.environment.mkdir();sentinel=self.args.environment/'keep';sentinel.write_text('user file');self.args.allow_download=True
        d,c=self.install();self.assertEqual(3,c);self.assertEqual('user file',sentinel.read_text());self.assertIn('preserved',d['error'])
    def test_verified_runtime_reuse_does_not_require_install_permissions_and_keeps_warnings(self):
        binary=self.args.environment/'bin/minimap2.exe';binary.parent.mkdir(parents=True);binary.write_bytes(b'policy fixture; never executed')
        mark(self.args.environment,installer.MARKER,installer.identity('minimap2'))
        p=self.args.environment/installer.MARKER;d=json.loads(p.read_text());d['provenance']={'compiler_warning_count':2,'compiler_warnings_first_100':['retained upstream warning']};p.write_text(json.dumps(d))
        d,c=self.install();self.assertEqual(0,c);self.assertTrue(d['runtime_reused']);self.assertEqual(2,d['compiler_warning_count']);self.assertIn('COMPILER WARNINGS',(self.args.report/'WARNINGS.md').read_text())
    def test_failed_version_probe_skips_and_preserves_validated_files(self):
        binary=self.args.environment/'bin/minimap2.exe';binary.parent.mkdir(parents=True);binary.write_bytes(b'never executed');mark(self.args.environment,installer.MARKER,installer.identity('minimap2'))
        with patch.object(installer,'probe',side_effect=RuntimeError('missing required runtime library')):
            code=installer.install(self.args)
        self.assertEqual(3,code);self.assertEqual(b'never executed',binary.read_bytes())
    def test_unsupported_host_skips_without_network(self):
        self.args.allow_download=True
        with patch.object(installer.sys,'platform','linux'):
            d,c=self.install()
        self.assertEqual(3,c);self.assertEqual('skipped-platform-unavailable',d['status'])
    def test_zip_path_escape_is_rejected(self):
        archive=self.root/'unsafe.zip';staging=self.root/'staging';staging.mkdir()
        with zipfile.ZipFile(archive,'w') as z:z.writestr('../outside','bad')
        with self.assertRaisesRegex(ValueError,'Unsafe archive path'):installer.extract_zip(archive,staging)
        self.assertFalse((self.root/'outside').exists())
    def test_exact_dangling_research_links_are_omitted_without_omitting_build_sources(self):
        archive=self.root/'source.tar.gz';staging=self.root/'staging';staging.mkdir()
        with tarfile.open(archive,'w:gz') as t:
            for name,dest in installer.HISAT_TEST_LINKS.items():
                e=tarfile.TarInfo(name);e.type=tarfile.SYMTYPE;e.linkname=dest;t.addfile(e)
            e=tarfile.TarInfo('hisat2-2.2.3/Makefile');e.size=4;t.addfile(e,io.BytesIO(b'test'))
        omitted=installer.extract_source(archive,staging,'hisat2');self.assertEqual(20,len(omitted));self.assertEqual('test',(staging/'hisat2-2.2.3/Makefile').read_text())
    def test_changed_link_is_rejected(self):
        archive=self.root/'source.tar.gz';staging=self.root/'staging';staging.mkdir()
        with tarfile.open(archive,'w:gz') as t:
            e=tarfile.TarInfo(next(iter(installer.HISAT_TEST_LINKS)));e.type=tarfile.SYMTYPE;e.linkname='../../../../outside';t.addfile(e)
        with self.assertRaisesRegex(ValueError,'Unexpected HISAT2'):installer.extract_source(archive,staging,'hisat2')
    def test_prior_reports_are_retained_as_history_and_attempt_logs_are_distinct(self):
        self.install();first=list((self.args.report/'attempts').iterdir());d,c=self.install();self.assertEqual(3,c);self.assertEqual(1,len(d['history']));self.assertEqual(2,len(list((self.args.report/'attempts').iterdir())));self.assertTrue(first[0].exists())
    def test_identical_clock_ticks_never_reuse_or_replace_attempt_directories(self):
        instant=datetime(2026,10,25,12,0,0,tzinfo=timezone.utc)
        with patch.object(installer.dt,'datetime') as clock:
            clock.now.return_value=instant
            first,code=self.install();self.assertEqual(3,code)
            retained=Path(first['attempt_directory']);marker=retained/'original.log';marker.write_text('preserve original diagnostics')
            second,code=self.install();self.assertEqual(3,code)
        self.assertEqual(first['recorded_utc'],second['recorded_utc'])
        self.assertNotEqual(first['attempt_directory'],second['attempt_directory'])
        self.assertEqual('preserve original diagnostics',marker.read_text())
        self.assertEqual(first['attempt_directory'],second['history'][0]['attempt_directory'])
        self.assertEqual(2,len(list((self.args.report/'attempts').iterdir())))


if __name__=='__main__': unittest.main()
