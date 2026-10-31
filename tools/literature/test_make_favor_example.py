#!/usr/bin/env python3
"""Synthetic request generation keeps explicit pins,scope and existing outputs."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

SPEC=importlib.util.spec_from_file_location('make_favor_example',Path(__file__).with_name('make_favor_example.py'));MODULE=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(MODULE)

class FavorExampleTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.root=Path(self.temporary.name).resolve();policy=MODULE.read_policy();self.inventory=self.root/'inventory.json';self.inventory.write_text(json.dumps(dict(schema_version=1,status='inventory-only-not-admitted',profile=policy['profile'],build_lock_sha256=policy['build_lock_sha256'],GenomeInfoDbData_archive_sha256=policy['GenomeInfoDbData_archive_sha256'])));self.pin=MODULE.sha(self.inventory)
    def tearDown(self):self.temporary.cleanup()
    def generate(self,**kwargs):return MODULE.generate(kwargs.get('inventory',self.inventory),kwargs.get('pin',self.pin),kwargs.get('request',self.root/'request.json'),kwargs.get('output',self.root/'analysis'))
    def test_request_binds_complete_synthetic_inputs_and_original_sources(self):
        result=self.generate();request=json.loads((self.root/'request.json').read_text());self.assertEqual(result['request_sha256'],MODULE.sha(self.root/'request.json'));self.assertEqual(request['build'],'synthetic-no-biological-reference');self.assertEqual(request['runtime']['inventory']['sha256'],self.pin);self.assertEqual(request['mode'],'DIPLOID_GT');self.assertEqual(len(request['reference']['shards']),2);self.assertFalse((self.root/'analysis').exists())
        for row in [request['vcf'],request['reference']['split'],*request['reference']['shards']]:self.assertEqual(row['sha256'],MODULE.sha(Path(row['path'])))
    def test_wrong_and_malformed_inventory_pins_do_not_write_requests(self):
        for value in ['0'*64,'A'*64,'invalid']:
            with self.assertRaises(ValueError):self.generate(pin=value)
        self.assertFalse((self.root/'request.json').exists())
    def test_existing_request_is_retained(self):
        request=self.root/'request.json';request.write_text('retain')
        with self.assertRaises(ValueError):self.generate()
        self.assertEqual(request.read_text(),'retain')
    def test_existing_analysis_is_retained(self):
        output=self.root/'analysis';output.mkdir();(output/'marker').write_text('retain')
        with self.assertRaises(ValueError):self.generate()
        self.assertEqual((output/'marker').read_text(),'retain');self.assertFalse((self.root/'request.json').exists())
    def test_whitespace_and_missing_output_parents_are_refused(self):
        for output in [self.root/'space here',self.root/'missing/analysis']:
            with self.assertRaises(ValueError):self.generate(output=output)
    def test_incomplete_inventory_and_wrong_policy_are_refused(self):
        data=json.loads(self.inventory.read_text());data['status']='started';self.inventory.write_text(json.dumps(data))
        with self.assertRaises(ValueError):self.generate(pin=MODULE.sha(self.inventory))
        data['status']='inventory-only-not-admitted';data['build_lock_sha256']='0'*64;self.inventory.write_text(json.dumps(data))
        with self.assertRaises(ValueError):self.generate(pin=MODULE.sha(self.inventory))
    def test_changed_fixture_fails_without_request_or_analysis(self):
        with mock.patch.object(MODULE,'FIXTURE',self.root/'missing-fixture'):
            with self.assertRaises(OSError):self.generate()
        self.assertFalse((self.root/'request.json').exists());self.assertFalse((self.root/'analysis').exists())
    def test_windows_command_skips_with_required_action(self):
        error=io.StringIO()
        with mock.patch.object(MODULE.sys,'platform','win32'),contextlib.redirect_stderr(error):code=MODULE.main(['--inventory',str(self.inventory),'--inventory-sha256',self.pin,'--out',str(self.root/'request.json'),'--analysis-out',str(self.root/'analysis')])
        self.assertEqual(code,3);self.assertIn('Required action',error.getvalue());self.assertFalse((self.root/'request.json').exists())

if __name__=='__main__':unittest.main()
