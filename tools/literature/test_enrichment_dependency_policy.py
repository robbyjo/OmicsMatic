"""Run original R installers against policy fixtures and real sealed libraries.

No download or installation is performed. Minimal package metadata in policy
fixtures tests rejection before an engine loads, not scientific equivalence.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PINS = dict(DOSE='4.6.0', clusterProfiler='4.20.3', fgsea='1.38.0',
            enrichit='0.2.6', jsonlite='2.0.0')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class EnrichmentDependencyPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rscript = os.environ.get('JLINALG_ENRICHMENT_RSCRIPT')
        cls.library = Path(os.environ.get('JLINALG_ENRICHMENT_R_LIBRARY', 'absent'))
        cls.legacy = Path(os.environ.get('JLINALG_DOSE_LEGACY_R_LIBRARY', 'absent'))
        if not cls.rscript or not cls.library.is_dir() or not cls.legacy.is_dir():
            raise unittest.SkipTest('Supply all three permitted pinned enrichment runtime environment variables')

    def call(self, lib, report, policy='forbid', legacy=False, deps=None):
        command = [self.rscript, '--vanilla', str(ROOT / 'tools/literature' /
                   ('install_dose_legacy.R' if legacy else 'install_enrichment_dependencies.R')), str(lib)]
        if legacy:
            command.append(str(self.library if deps is None else deps))
        command.extend([str(report), policy])
        env = dict(os.environ, R_LIBS_SITE='', R_LIBS_USER=str(self.library))
        env.pop('R_HOME', None)
        return subprocess.run(command, capture_output=True, encoding='utf-8', errors='replace', env=env, timeout=180)

    def rejected(self, result, report, text):
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        warning = (report / 'WARNINGS.md').read_text(encoding='utf-8')
        self.assertIn('SKIPPED', warning)
        self.assertIn('Required action:', warning)
        self.assertIn(text, warning)
        self.assertFalse(any(report.rglob('downloads')))

    def main_fixture(self, home):
        home.mkdir()
        # Actual integrity readers only. Stub engine DESCRIPTION files let the
        # installer reach the seal check; no engine API is invoked by a fixture.
        for package in ['jsonlite', 'digest']:
            shutil.copytree(self.library / package, home / package)
        for package, version in PINS.items():
            if package == 'jsonlite':
                continue
            folder = home / package
            folder.mkdir()
            (folder / 'DESCRIPTION').write_text(f'Package: {package}\nVersion: {version}\n', encoding='utf-8')
        (home / '.jlinalg-enrichment-library').write_text('Policy test fixture; no engine is loaded\n')
        (home / 'policy-payload').write_bytes(b'original fixture')
        marker = home / '.jlinalg-enrichment-runtime.json'
        metadata = dict(schema='jlinalg-enrichment-runtime-v1', pins=PINS,
                        files={p.relative_to(home).as_posix(): sha(p) for p in home.rglob('*') if p.is_file()})
        marker.write_text(json.dumps(metadata), encoding='utf-8')
        return marker

    def legacy_fixture(self, home, dependency):
        home.mkdir()
        (home / '.jlinalg-dose-legacy-library').write_text('Policy test fixture; no engine is loaded\n')
        (home / 'payload').write_bytes(b'original fixture')
        marker = home / '.jlinalg-dose-legacy-runtime.json'
        marker.write_text(json.dumps(dict(schema='jlinalg-dose-legacy-v1', version='4.5.0',
            dependency_marker_sha256=sha(dependency), files={p.name: sha(p) for p in home.iterdir()})), encoding='utf-8')
        return marker

    def test_main_forbidden_absent_skips_without_creating_library(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); lib = folder / 'absent'; report = folder / 'report'
            self.rejected(self.call(lib, report), report, 'installation is forbidden')
            self.assertFalse(lib.exists())

    def test_legacy_forbidden_absent_skips_without_dependency_probe(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); lib = folder / 'absent'; report = folder / 'report'
            self.rejected(self.call(lib, report, legacy=True, deps=folder / 'absent-deps'), report, 'installation is forbidden')
            self.assertFalse(lib.exists())

    def test_main_unmanaged_library_preserved_before_network_access(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); lib = folder / 'unmanaged'; lib.mkdir(); keep = lib / 'keep'; keep.write_bytes(b'preserve')
            report = folder / 'report'
            self.rejected(self.call(lib, report, 'allow'), report, 'unmanaged library is preserved')
            self.assertEqual(b'preserve', keep.read_bytes()); self.assertEqual([keep], list(lib.iterdir()))

    def test_main_incomplete_library_preserved_before_network_access(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); lib = folder / 'incomplete'; lib.mkdir(); (lib / '.jlinalg-enrichment-library').touch()
            report = folder / 'report'
            self.rejected(self.call(lib, report, 'allow'), report, 'incomplete library is preserved')
            self.assertEqual(1, len(list(lib.iterdir())))

    def test_main_extra_unsealed_file_rejected(self):
        self.main_mutation('extra')

    def test_main_missing_sealed_file_rejected(self):
        self.main_mutation('missing')

    def test_main_changed_sealed_file_rejected(self):
        self.main_mutation('changed')

    def test_main_escaping_marker_path_rejected(self):
        self.main_mutation('escape')

    def main_mutation(self, mutation):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); lib = folder / 'fixture'; marker = self.main_fixture(lib)
            if mutation == 'extra': (lib / 'unsealed-file').write_text('new')
            elif mutation == 'missing': (lib / 'policy-payload').unlink()
            elif mutation == 'changed': (lib / '.jlinalg-enrichment-library').write_text('changed')
            else:
                data = json.loads(marker.read_text()); data['files']['../outside'] = 'a' * 64; marker.write_text(json.dumps(data))
            before = {p.relative_to(lib).as_posix(): sha(p) for p in lib.rglob('*') if p.is_file()}
            report = folder / 'report'
            message = 'invalid relative paths' if mutation == 'escape' else ('file changed' if mutation == 'changed' else 'unsealed or missing files')
            self.rejected(self.call(lib, report), report, message)
            self.assertEqual(before, {p.relative_to(lib).as_posix(): sha(p) for p in lib.rglob('*') if p.is_file()})

    def test_legacy_unmanaged_library_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); deps = folder / 'main'; self.main_fixture(deps)
            lib = folder / 'legacy'; lib.mkdir(); (lib / 'keep').write_bytes(b'preserve'); report = folder / 'report'
            self.rejected(self.call(lib, report, 'allow', True, deps), report, 'unmanaged or incomplete legacy library is preserved')
            self.assertEqual(b'preserve', (lib / 'keep').read_bytes())

    def test_legacy_extra_unsealed_file_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); deps = folder / 'main'; dependency = self.main_fixture(deps)
            lib = folder / 'legacy'; self.legacy_fixture(lib, dependency); (lib / 'unsealed').write_text('preserve'); report = folder / 'report'
            self.rejected(self.call(lib, report, legacy=True, deps=deps), report, 'unsealed or missing files')
            self.assertEqual('preserve', (lib / 'unsealed').read_text())

    def test_legacy_changed_dependency_marker_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp); deps = folder / 'main'; dependency = self.main_fixture(deps)
            lib = folder / 'legacy'; self.legacy_fixture(lib, dependency)
            dependency.write_text(dependency.read_text() + '\n'); report = folder / 'report'
            self.rejected(self.call(lib, report, legacy=True, deps=deps), report, 'profile/dependencies changed')

    def test_invalid_modes_do_not_create_library_or_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            for legacy in [False, True]:
                lib = folder / str(legacy); report = folder / (str(legacy) + '-report')
                result = self.call(lib, report, 'maybe', legacy)
                self.assertNotEqual(0, result.returncode); self.assertIn('Usage:', result.stderr)
                self.assertFalse(lib.exists()); self.assertFalse(report.exists())

    def test_real_main_sealed_reuse_with_installation_forbidden(self):
        self.actual_reuse(False)

    def test_real_legacy_sealed_reuse_with_installation_forbidden(self):
        self.actual_reuse(True)

    def actual_reuse(self, legacy):
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / 'report'; home = self.legacy if legacy else self.library
            marker = home / ('.jlinalg-dose-legacy-runtime.json' if legacy else '.jlinalg-enrichment-runtime.json')
            before = sha(marker); result = self.call(home, report, legacy=legacy)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('available-not-yet-analysis-validated', (report / 'dependency-status.yaml').read_text())
            self.assertEqual(before, sha(marker)); self.assertFalse(any(report.rglob('downloads')))


if __name__ == '__main__':
    unittest.main()
