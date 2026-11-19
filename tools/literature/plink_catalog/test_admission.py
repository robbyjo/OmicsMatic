# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source scope, family credit, review preservation and real transaction controls."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import unittest
import argparse
import zipfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from plink_catalog import admission_model as model
from plink_catalog import catalog
from plink_catalog.evidence import baseline, qualification
from seurat_catalog.catalog import install

options, remaining = argparse.ArgumentParser(add_help=False).parse_known_args()
FROZEN = None
if '--frozen-before' in sys.argv:
    index = sys.argv.index('--frozen-before')
    FROZEN = Path(sys.argv[index + 1])
    del sys.argv[index:index + 2]


class AdmissionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if FROZEN is None:
            cls.before = baseline()
            cls.q = qualification()['component_audit']
        else:
            from plink_catalog.evidence import rawpin
            from plink_catalog.verify_components import verify as audit
            with zipfile.ZipFile(FROZEN) as archive:
                assert len(archive.namelist()) == 8 and set(archive.namelist()) == set(model.META)
                cls.before = {n:archive.read(n) for n in model.META}
            prior = json.loads((ROOT / 'docs/literature/omics-2023-2026/seurat-catalog-validation.json').read_bytes())
            assert prior['after'] == {n:rawpin(raw) for n,raw in cls.before.items()}
            cls.q = audit()
            print('FROZEN BEFORE: no fresh Git or primary-source audit is claimed', flush=True)
        cls.census = json.loads((ROOT / 'docs/literature/plink-source-review/CENSUS.json').read_bytes())
        cls.after = model.transition(cls.before, cls.census, cls.q)
        parent = ROOT / 'build/plink-catalog-admission-1'
        i = 1
        while (parent / ('admission-tests-' + str(i))).exists():
            i += 1
        cls.home = parent / ('admission-tests-' + str(i))
        cls.home.mkdir(parents=True)

    def setUp(self):
        self.out = self.home / self._testMethodName
        self.out.mkdir()

    def test_credits_three_adapters_without_double_counting_native_helpers(self):
        result = model.check(self.before, self.after, self.census, self.q)
        self.assertEqual((result['new_scoped_integrations'], result['native_integrations'],
                          result['upstream_integrations']), (134, 24, 110))
        self.assertEqual(result['new_scoped_integration_credit'], 3)
        self.assertFalse(result['whole_goal_complete'])

    def test_entire_review_prefix_and_paper_identities_are_preserved(self):
        n = 'tools/literature/reviewed-uses.json'
        old, new = json.loads(self.before[n]), json.loads(self.after[n])
        self.assertEqual(new['reviews'][:1339], old['reviews'])
        self.assertEqual(len(new['reviews']), 1413)
        old, new = json.loads(self.before[model.META[0]]), json.loads(self.after[model.META[0]])
        self.assertEqual([{k:v for k,v in p.items() if k!='tools'} for p in old],
                         [{k:v for k,v in p.items() if k!='tools'} for p in new])

    def test_hosted_and_legacy_roles_are_not_called_author_reproductions(self):
        inventory = {r['id']:r for r in json.loads(self.after[model.META[1]])}
        for tool in ['plink','plink2','king']:
            self.assertFalse(inventory[tool]['whole_tool_qualified'])
            for role in inventory[tool]['paper_role_qualification'].values():
                self.assertFalse(role['author_runtime_executed'])
                self.assertFalse(role['whole_paper_reproduced'])
        self.assertFalse(inventory['king']['paper_role_qualification']['P191']['package_execution_established'])
        self.assertIn('1.4', str(inventory['king']['paper_role_qualification']['P212']['reported_editions']))

    def test_forged_source_edition_is_refused(self):
        census = copy.deepcopy(self.census)
        next(r for r in census['roles'] if r['paper_id']=='P212' and r['tool_id']=='king')['reported_editions'] = []
        with self.assertRaises(Exception):
            model.transition(self.before, census, self.q)

    def test_duplicate_review_role_is_refused(self):
        census = copy.deepcopy(self.census)
        census['roles'].append(census['roles'][0])
        with self.assertRaises(Exception):
            model.transition(self.before, census, self.q)

    def test_unqualified_components_cannot_promote_families(self):
        q = dict(self.q, new_family_credit=3)
        with self.assertRaises(Exception):
            model.transition(self.before, self.census, q)

    def test_changed_old_review_cannot_pass_final_check(self):
        after = dict(self.after)
        name = 'tools/literature/reviewed-uses.json'
        obj = json.loads(after[name]);obj['reviews'][0]['use_summary']='invented'
        after[name] = json.dumps(obj).encode()
        with self.assertRaises(Exception):
            model.check(self.before, after, self.census, self.q)

    def fixture(self):
        root, out = self.out/'workspace', self.out/'attempt'
        root.mkdir();out.mkdir();(root/'a').write_bytes(b'old')
        return root, out

    def test_transaction_success(self):
        root,out = self.fixture()
        value = install(root,out,{'a':b'old','b':None},{'a':b'new','b':b'added'},lambda: 'checked')
        self.assertEqual(value,'checked');self.assertEqual((root/'a').read_bytes(),b'new')
        self.assertEqual((root/'b').read_bytes(),b'added')

    def test_failed_postcheck_restores_old_and_retains_failed_new(self):
        root,out = self.fixture()
        def fail():raise ValueError('deliberate postcheck failure')
        with self.assertRaises(ValueError):
            install(root,out,{'a':b'old','b':None},{'a':b'new','b':b'added'},fail)
        self.assertEqual((root/'a').read_bytes(),b'old');self.assertFalse((root/'b').exists())
        self.assertEqual((out/'retained-failed-metadata/b').read_bytes(),b'added')

    def test_concurrent_edit_after_write_is_preserved(self):
        root,out = self.fixture()
        def fail():
            (root/'a').write_bytes(b'user edit')
            raise ValueError('concurrent edit')
        with self.assertRaises(ValueError):
            install(root,out,{'a':b'old','b':None},{'a':b'new','b':b'added'},fail)
        self.assertEqual((root/'a').read_bytes(),b'user edit')
        self.assertEqual(json.loads((out/'ROLLBACK.json').read_bytes())['concurrent_changes_preserved'],['a'])

    def test_concurrent_change_between_writes_restores_only_owned_bytes(self):
        root,out = self.fixture();(root/'b').write_bytes(b'old b')
        import os
        original = os.replace
        def replace(src,dest):
            original(src,dest)
            if Path(dest).name=='a':(root/'b').write_bytes(b'user edit b')
        with patch('seurat_catalog.catalog.os.replace',side_effect=replace), self.assertRaises(Exception):
            install(root,out,{'a':b'old','b':b'old b'},{'a':b'new','b':b'new b'},lambda:None)
        self.assertEqual((root/'a').read_bytes(),b'old')
        self.assertEqual((root/'b').read_bytes(),b'user edit b')

    def test_stale_input_is_refused_without_writing(self):
        root,out = self.fixture()
        with self.assertRaises(Exception):
            install(root,out,{'a':b'different'},{'a':b'new'},lambda:None)
        self.assertEqual((root/'a').read_bytes(),b'old');self.assertEqual(list(out.iterdir()),[])

    def test_escaped_target_is_refused(self):
        root,out = self.fixture()
        with self.assertRaises(Exception):
            install(root,out,{'../outside':None},{'../outside':b'bad'},lambda:None)
        self.assertFalse((self.out/'outside').exists())

    def test_existing_cli_output_is_untouched(self):
        dest=self.out/'existing';dest.mkdir();keep=dest/'keep';keep.write_bytes(b'keep')
        r=subprocess.run([sys.executable,'-B',str(Path(catalog.__file__)),'--inspect',
            '--plan-dir',str(self.out/'absent'),'--out',str(dest)],capture_output=True)
        self.assertEqual(r.returncode,2);self.assertEqual(list(dest.iterdir()),[keep])

    def test_missing_source_and_history_are_skips_not_scientific_failures(self):
        from plink_catalog.primary import MissingSource
        self.assertEqual(catalog.failure_code(MissingSource('exact author archive absent')),3)
        self.assertEqual(catalog.failure_code(subprocess.CalledProcessError(128,['git','show','absent:path'])),3)
        self.assertEqual(catalog.failure_code(ValueError('changed source identity')),2)
        self.assertEqual(catalog.failure_code(subprocess.CalledProcessError(8,['plink2','--king-cutoff'])),2)


if __name__=='__main__':
    unittest.main(verbosity=2)
