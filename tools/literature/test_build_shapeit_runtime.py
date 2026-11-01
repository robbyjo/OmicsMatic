# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import signal
from types import SimpleNamespace
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from unittest.mock import patch

import build_shapeit_runtime as setup


def identity(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


class Response(io.BytesIO):
    def geturl(self): return 'https://example.invalid/pinned'


class SourceSetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def args(self, **changes):
        values = dict(work=self.root/'work', home=self.root/'home', cache=self.root/'cache', report=self.root/'report.json',
                      allow_download=True, allow_source_build=True, allow_install=True)
        values.update(changes); return SimpleNamespace(**values)

    def archive(self, name, entries, compressed=False):
        path = self.root/name
        with tarfile.open(path, 'w:gz' if compressed else 'w') as target:
            for name, raw, link in entries:
                item = tarfile.TarInfo(name)
                if link is not None: item.type=tarfile.SYMTYPE; item.linkname=link; target.addfile(item)
                else: item.size=len(raw); target.addfile(item, io.BytesIO(raw))
        return path

    def test_every_permission_is_required_before_host_reads_or_install_mutation(self):
        for missing in ['allow_download', 'allow_source_build', 'allow_install']:
            with self.subTest(missing=missing), patch.object(setup, 'host_capabilities') as host:
                with self.assertRaisesRegex(setup.Unavailable, 'Explicit'):
                    setup.build(self.args(**{missing:False}))
                host.assert_not_called()
                self.assertEqual(list(self.root.iterdir()), [])

    def test_unavailable_host_is_a_persistent_skip_with_recovery_and_no_analysis(self):
        args = ['build_shapeit_runtime.py', '--work', str(self.root/'work'), '--home', str(self.root/'home'),
                '--cache', str(self.root/'cache'), '--report', str(self.root/'report.json'),
                '--allow-download', '--allow-source-build', '--allow-install']
        with patch.object(setup.sys, 'argv', args), patch.object(setup.platform, 'system', return_value='Darwin'), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(setup.main(), 3)
        report = json.loads((self.root/'report.json').read_text())
        self.assertEqual(report['status'], 'SKIP3-source-runtime-unavailable')
        self.assertFalse(report['scientific_analysis_admitted'])
        for text in ['Skip dependent', '--allow-source-build', 'fresh work/home/report', 'dpkg-deb']:
            self.assertIn(text, report['required_action'])
        self.assertFalse((self.root/'work').exists()); self.assertFalse((self.root/'home').exists())

    def test_existing_report_is_preserved_before_any_build_is_attempted(self):
        path=self.root/'report.json'; path.write_text('retain')
        args=['builder', '--work', str(self.root/'work'), '--home', str(self.root/'home'), '--cache', str(self.root/'cache'), '--report', str(path)]
        with patch.object(setup.sys, 'argv', args), patch.object(setup, 'build') as build, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit): setup.main()
        build.assert_not_called(); self.assertEqual(path.read_text(), 'retain')

    def test_metadata_duplicates_and_oversize_are_rejected_completely(self):
        path=self.root/'meta.json'; path.write_text('{"files":1,"files":2}')
        with self.assertRaisesRegex(ValueError, 'Duplicate'): setup.read_json(path)
        path.write_bytes(b' '*1048577)
        with self.assertRaisesRegex(ValueError, 'capacity'): setup.read_json(path)

    def test_current_complete_kit_and_existing_adopter_are_pinned(self):
        config=setup.load_config()
        self.assertEqual(config['source_commit'], 'c34d4db3e99a2f7e23deb727671ae260901a5886')
        self.assertEqual(len(config['common_object_order']), 34)
        self.assertEqual(len(config['join_object_order']), 33)
        self.assertIn('runtime-library-sources.json', config['files'])

    def test_cache_hit_is_fully_verified_without_a_network_request(self):
        raw=b'whole pinned archive'; path=self.root/'asset'; path.write_bytes(raw)
        expected=dict(url='https://example.invalid/asset', **identity(raw))
        with patch.object(setup.urllib.request, 'urlopen') as network:
            result=setup.download(self.root, 'asset', expected)
        network.assert_not_called(); self.assertEqual(result['source'], 'verified-cache')
        path.write_bytes(raw+b'corrupt')
        with self.assertRaisesRegex(ValueError, 'differs'): setup.download(self.root, 'asset', expected)
        self.assertEqual(path.read_bytes(), raw+b'corrupt')

    def test_download_checks_complete_hash_and_retains_incomplete_inputs(self):
        raw=b'pinned bytes'; expected=dict(url='https://example.invalid/asset', **identity(raw))
        with patch.object(setup.urllib.request, 'urlopen', return_value=Response(raw[:-1])):
            with self.assertRaisesRegex(ValueError, 'differs'): setup.download(self.root, 'asset', expected)
        self.assertEqual((self.root/'asset.partial').read_bytes(), raw[:-1]); self.assertFalse((self.root/'asset').exists())
        with self.assertRaisesRegex(ValueError, 'partial'): setup.download(self.root, 'asset', expected)

    def test_download_refuses_oversized_responses_and_never_replaces_target(self):
        expected=dict(url='https://example.invalid/asset', **identity(b'a'))
        with patch.object(setup.urllib.request, 'urlopen', return_value=Response(b'ab')):
            with self.assertRaisesRegex(ValueError, 'capacity'): setup.download(self.root, 'asset', expected)
        self.assertFalse((self.root/'asset').exists())

    def test_bootstrap_requires_the_single_exact_executable(self):
        archive=self.archive('bootstrap.tgz', [('pixi', b'pinned-bootstrap', None)], True)
        setup.unpack_pixi(archive, self.root/'pixi', identity(b'pinned-bootstrap'))
        self.assertEqual((self.root/'pixi').read_bytes(), b'pinned-bootstrap')
        bad=self.archive('extra.tgz', [('pixi', b'pinned-bootstrap', None), ('extra', b'bad', None)], True)
        with self.assertRaisesRegex(ValueError, 'layout'): setup.unpack_pixi(bad, self.root/'bad-pixi', identity(b'pinned-bootstrap'))
        self.assertFalse((self.root/'bad-pixi').exists())

    def source_manifest(self, link='../../common/src/utils'):
        return dict(files=[dict(path='phase_common/src/main.cpp', mode='100644', **identity(b'original source\n')),
                           dict(path='common/src/utils/header.h', mode='100644', **identity(b'original header\n')),
                           dict(path='phase_common/src/utils', mode='120000', link=link, **identity(link.encode()))])

    def source_archive(self, name='source.tgz', link='../../common/src/utils', extra=None):
        entries=[('shapeit-commit/phase_common/src/main.cpp', b'original source\n', None),
                 ('shapeit-commit/common/src/utils/header.h', b'original header\n', None),
                 ('shapeit-commit/phase_common/src/utils', b'', link)]
        if extra: entries.extend(extra)
        return self.archive(name, entries, True)

    @unittest.skipUnless(sys.platform.startswith('linux'), 'Linux permits the original source symlink layout')
    def test_source_archive_keeps_exact_bytes_and_original_internal_links(self):
        source=self.root/'source'; manifest=self.source_manifest()
        setup.unpack_source(self.source_archive(), source, manifest, 'commit')
        self.assertEqual((source/'phase_common/src/utils/header.h').read_bytes(), b'original header\n')
        self.assertEqual((source/'phase_common/src/utils').readlink().as_posix(), '../../common/src/utils')
        (source/'phase_common/src/main.cpp').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'differs'): setup.verify_source(source, manifest)

    def test_source_archive_refuses_missing_and_duplicate_selected_members(self):
        archive=self.archive('missing.tgz', [('shapeit-commit/phase_common/src/main.cpp', b'original source\n', None)], True)
        with self.assertRaisesRegex(ValueError, 'Incomplete'): setup.unpack_source(archive, self.root/'missing', self.source_manifest(), 'commit')
        archive=self.source_archive('duplicate.tgz', extra=[('shapeit-commit/phase_common/src/main.cpp', b'original source\n', None)])
        with self.assertRaisesRegex(ValueError, 'Duplicate'): setup.unpack_source(archive, self.root/'duplicate', self.source_manifest(), 'commit')

    def test_archive_traversal_is_rejected_before_any_outside_write(self):
        for name in ['../outside', '/outside', 'shapeit-commit/../../outside', 'shapeit-commit/back\\slash']:
            with self.subTest(name=name):
                archive=self.archive('unsafe.tgz', [(name, b'bad', None)], True)
                source=self.root/('source-'+str(len(list(self.root.iterdir()))))
                with self.assertRaisesRegex(ValueError, 'Unsafe'): setup.unpack_source(archive, source, self.source_manifest(), 'commit')
                self.assertFalse((self.root.parent/'outside').exists())

    def test_source_link_cannot_escape_even_when_manifest_names_it(self):
        link='../../../../outside'; archive=self.source_archive(link=link)
        with self.assertRaisesRegex(ValueError, 'escapes'): setup.unpack_source(archive, self.root/'source', self.source_manifest(link), 'commit')
        self.assertFalse((self.root/'source/phase_common/src/utils').exists())

    def test_canonical_prefix_relocation_preserves_addresses_and_all_nonpath_bytes(self):
        old='/original-package-placeholder'; new='/fixed'
        raw=b'\x7fELF\0'+old.encode()+b'/plugins:'+old.encode()+b'/lib\0\0MARKER\xff'
        expected=b'\x7fELF\0'+new.encode()+b'/plugins:'+new.encode()+b'/lib'+b'\0'*(2*(len(old)-len(new)))+b'\0\0MARKER\xff'
        actual=setup.canonical_binary_prefix(raw, old, new)
        self.assertEqual(actual, expected); self.assertEqual(actual.index(b'MARKER'), raw.index(b'MARKER'))
        self.assertEqual(len(actual), len(raw))
        for invalid in ['', 'bad\0prefix']:
            with self.assertRaisesRegex(ValueError, 'placeholder'): setup.canonical_binary_prefix(raw, invalid, new)
        with self.assertRaisesRegex(ValueError, 'placeholder'): setup.canonical_binary_prefix(raw, '/short', '/longer-prefix')

    def test_package_library_reconstruction_requires_both_raw_and_final_hashes(self):
        cache=self.root/'packages'; (cache/'pkg/lib').mkdir(parents=True)
        raw=b'\x7fELF\0/long-placeholder/lib\0TAIL'; (cache/'pkg/lib/real.so').write_bytes(raw)
        expected=setup.canonical_binary_prefix(raw, '/long-placeholder', '/fixed')
        sources={'lib/libexample.so':dict(package='pkg', path='lib/real.so', raw_bytes=len(raw), raw_sha256=identity(raw)['sha256'], prefix_placeholder='/long-placeholder')}
        runtime=dict(files={'lib/libexample.so':identity(expected)})
        records=setup.reconstruct_libraries(cache, self.root/'libs', sources, runtime, '/fixed')
        self.assertTrue(records[0]['prefix_normalized']); self.assertEqual((self.root/'libs/libexample.so').read_bytes(), expected)
        self.assertEqual((cache/'pkg/lib/real.so').read_bytes(), raw)
        bad=dict(files={'lib/libexample.so':identity(expected+b'bad')})
        with self.assertRaisesRegex(ValueError, 'differs'): setup.reconstruct_libraries(cache, self.root/'bad-libs', sources, bad, '/fixed')

    def test_libc_extraction_selects_only_exact_pinned_regular_files(self):
        raw=b'ELF fixture'; archive=self.archive('libc.tar', [('./usr/lib/x86_64-linux-gnu/libc.so.6', raw, None), ('../ignored', b'not extracted', None)])
        members={'lib/libc.so.6':'./usr/lib/x86_64-linux-gnu/libc.so.6'}; runtime=dict(files={'lib/libc.so.6':identity(raw)})
        setup.unpack_libc(archive, self.root/'libc', members, runtime)
        self.assertEqual([p.name for p in (self.root/'libc').iterdir()], ['libc.so.6'])
        bad=self.archive('linked-libc.tar', [('./usr/lib/x86_64-linux-gnu/libc.so.6', b'', '/outside')])
        with self.assertRaisesRegex(ValueError, 'type'): setup.unpack_libc(bad, self.root/'bad-libc', members, runtime)

    def test_build_home_and_cache_trees_must_be_disjoint(self):
        for work, home, cache in [(self.root/'w', self.root/'w/home', self.root/'cache'), (self.root/'w', self.root/'home', self.root/'w/cache'), (self.root/'w', self.root/'home', self.root/'home/cache')]:
            with self.assertRaisesRegex(ValueError, 'separate'): setup.distinct_paths(work, home, cache)

    def test_make_shell_metacharacters_are_refused_before_path_creation(self):
        for value in ['a;echo', 'a$()b', 'a`id`b', 'a%name', 'a\"name', 'a&b', 'has space', 'unicode-\u00e9']:
            with self.subTest(path=value):
                with self.assertRaisesRegex(ValueError, 'shell-safe ASCII'): setup.canonical(self.root/value, True)
                self.assertFalse((self.root/value).exists())


@unittest.skipUnless(sys.platform.startswith('linux'), 'Owned setup process controls require Linux')
class SourceSetupProcessTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.root=Path(self.temp.name)
        self.env=dict(PATH='/usr/bin:/bin', LANG='C', LC_ALL='C', HOME=str(self.root))

    def test_success_retains_complete_stdout_stderr_and_terminal_group(self):
        record=setup.run_process(self.root, 'success', [sys.executable, '-c', "import sys; print('complete'); print('diagnostic',file=sys.stderr)"], self.env, self.root)
        self.assertEqual(record['exit_code'], 0); self.assertEqual(record['owned_live_after_cleanup'], [])
        self.assertEqual((self.root/'success.stdout').read_bytes(), b'complete\n')
        self.assertEqual(record['stderr_sha256'], identity(b'diagnostic\n')['sha256'])

    def test_nonzero_exit_preserves_error_and_cleanup_record(self):
        with self.assertRaisesRegex(ValueError, 'exit 7'):
            setup.run_process(self.root, 'fail', [sys.executable, '-c', "import sys; print('failure',file=sys.stderr); sys.exit(7)"], self.env, self.root)
        record=json.loads((self.root/'fail.process.json').read_text())
        self.assertEqual(record['exit_code'], 7); self.assertEqual(record['owned_live_after_cleanup'], [])
        self.assertEqual((self.root/'fail.stderr').read_text(), 'failure\n')

    def test_orphan_worker_is_refused_and_cleaned_without_touching_unrelated_process(self):
        sentinel=subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])
        try:
            command=[sys.executable, '-c', "import subprocess,sys; p=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)']);print(p.pid,flush=True)"]
            with self.assertRaisesRegex(ValueError, 'live workers'): setup.run_process(self.root, 'orphan', command, self.env, self.root)
            record=json.loads((self.root/'orphan.process.json').read_text())
            self.assertEqual(record['owned_live_after_cleanup'], []); self.assertIsNone(sentinel.poll())
        finally: sentinel.terminate(); sentinel.wait(timeout=5)

    def test_wall_deadline_terminates_the_owned_group(self):
        with self.assertRaisesRegex(ValueError, 'capacity'):
            setup.run_process(self.root, 'wall', [sys.executable, '-c', 'import time;time.sleep(30)'], self.env, self.root, timeout=.2)
        record=json.loads((self.root/'wall.process.json').read_text()); self.assertEqual(record['owned_live_after_cleanup'], [])

    def test_fast_exit_still_enforces_complete_log_budget(self):
        with self.assertRaisesRegex(ValueError, 'capacity'):
            setup.run_process(self.root, 'log', [sys.executable, '-c', "print('x'*2048)"], self.env, self.root, output_limit=1024)

    def test_cli_sigterm_retains_failure_and_cleans_its_worker_only(self):
        driver=self.root/'driver.py'
        driver.write_text("import sys\nsys.path.insert(0,"+repr(str(setup.HERE))+")\nimport build_shapeit_runtime as setup\n"
            "def controlled(args):\n args.work.mkdir()\n setup.run_process(args.work,'cancel',[sys.executable,'-c','import time;time.sleep(60)'],{'PATH':'/usr/bin:/bin','HOME':str(args.work),'LC_ALL':'C'},args.work)\n"
            "setup.build=controlled\nraise SystemExit(setup.main())\n")
        sentinel=subprocess.Popen([sys.executable, '-c', 'import time;time.sleep(60)'])
        command=[sys.executable,str(driver),'--work',str(self.root/'work'),'--home',str(self.root/'home'),
                 '--cache',str(self.root/'cache'),'--report',str(self.root/'report.json'),'--allow-download','--allow-source-build','--allow-install']
        child=None
        try:
            with (self.root/'cli.stdout').open('wb') as stdout,(self.root/'cli.stderr').open('wb') as stderr:
                child=subprocess.Popen(command,stdout=stdout,stderr=stderr)
                deadline=time.monotonic()+5;running=None
                while time.monotonic()<deadline:
                    try: running=json.loads((self.root/'work/cancel.running.json').read_text());break
                    except (FileNotFoundError,json.JSONDecodeError): time.sleep(.02)
                self.assertIsNotNone(running);self.assertIsNone(child.poll())
                self.assertTrue(setup.live_group(running['pid']))
                child.send_signal(signal.SIGTERM);self.assertEqual(child.wait(timeout=10),2)
            report=json.loads((self.root/'report.json').read_text())
            self.assertEqual(report['status'],'FAIL2-retained-no-analysis');self.assertFalse(report['scientific_analysis_admitted'])
            self.assertIn('signal',report['error']);self.assertIn('WARNING:',(self.root/'cli.stderr').read_text())
            ledger=json.loads((self.root/'work/cancel.process.json').read_text());self.assertEqual(ledger['owned_live_after_cleanup'],[])
            self.assertFalse((self.root/'home').exists());self.assertIsNone(sentinel.poll())
        finally:
            if child is not None and child.poll() is None:child.terminate();child.wait(timeout=5)
            sentinel.terminate();sentinel.wait(timeout=5)


if __name__ == '__main__':
    unittest.main()
