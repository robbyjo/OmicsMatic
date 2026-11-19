# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject false component success even after deliberately resealing saved metadata."""
import json
from pathlib import Path
import shutil
import sys
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_components as subject


class ComponentEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parent = subject.ROOT / 'build/plink-catalog-admission-1'
        i = 1
        while (parent / ('component-tests-' + str(i))).exists():
            i += 1
        cls.home = parent / ('component-tests-' + str(i))
        cls.home.mkdir(parents=True)

    def setUp(self):
        self.packet = self.home / self._testMethodName
        self.packet.mkdir()
        for n in ['QUALIFICATION.json', 'FIXTURES.json', 'fixtures.zip', 'evidence.zip']:
            shutil.copyfile(subject.PACKET / n, self.packet / n)
        self.proof = json.loads((self.packet / 'QUALIFICATION.json').read_bytes())

    def save(self):
        (self.packet / 'QUALIFICATION.json').write_text(json.dumps(self.proof), encoding='utf-8')

    def reseal(self, name, change):
        path = self.packet / 'evidence.zip'
        with zipfile.ZipFile(path) as source:
            files = {n: source.read(n) for n in source.namelist()}
        data = json.loads(files[name])
        change(data)
        files[name] = (json.dumps(data, indent=2) + '\n').encode()
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as target:
            for n, raw in sorted(files.items()):
                target.writestr(n, raw)
        self.proof['members'][name] = subject.fingerprint(files[name])
        self.proof['archive'] = subject.fingerprint(path.read_bytes())
        self.save()

    def test_original_passes_without_executing_original_software(self):
        result = subject.verify(self.packet)
        self.assertTrue(result['status'].startswith('PASS_'))
        self.assertFalse(result['original_programs_reexecuted_now'])
        self.assertFalse(result['full_catalog_admitted'])

    def test_false_family_credit_is_refused(self):
        self.proof['new_family_credit'] = 1
        self.save()
        with self.assertRaisesRegex(ValueError, 'False component'):
            subject.verify(self.packet)

    def test_changed_scientific_source_pin_is_refused(self):
        name = 'src/main/java/org/jlinalg/genetics/KingRobustKinship.java'
        self.proof['source_pins'][name]['sha256'] = '0' * 64
        self.save()
        with self.assertRaisesRegex(ValueError, 'Changed qualified source'):
            subject.verify(self.packet)

    def test_resealed_initial_failure_cannot_be_called_success(self):
        def change(data):
            next(r for r in data['records'] if r['component'] == 'glm')['exit_code'] = 0
        self.reseal('records/component-runs-1/COMPLETED.json', change)
        with self.assertRaisesRegex(ValueError, 'initial component outcomes'):
            subject.verify(self.packet)

    def test_resealed_failed_current_glm_is_refused(self):
        self.reseal('records/frozen-fixture-runs-1/glm/verification.json',
                    lambda data: data.update(status='failed-retained'))
        with self.assertRaisesRegex(ValueError, 'Incomplete qualified component: glm'):
            subject.verify(self.packet)

    def test_unrelated_candidate_cannot_inherit_qualification(self):
        self.proof['candidate']['sha256'] = '0' * 64
        self.save()
        with self.assertRaisesRegex(ValueError, 'Current tested JAR differs'):
            subject.verify(self.packet)


if __name__ == '__main__':
    unittest.main(verbosity=2)
