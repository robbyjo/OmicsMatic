#!/usr/bin/env python3
"""Dependency permission/identity unit controls; actual original annotation has separate scientific proofs."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import install_homer_annotation as subject

class Policy(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.root=Path(self.temporary.name)
    def tearDown(self):self.temporary.cleanup()
    def invoke(self,*flags,report=None):
        args=['--profile','linux-x86_64-annotation-5.1','--home',str(self.root/'home'),'--cache',str(self.root/'cache'),'--report',str(report or self.root/'report'),*flags]
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):return subject.main(args)
    def member(self,relocate=True):
        home=self.root/'home';(home/'bin').mkdir(parents=True)
        original=b'#!/usr/bin/env perl\nuse warnings;\nuse lib "/old/bin";\nmy $homeDir = "/old/";\nprint "science unchanged\\n";\n'
        import base64
        prefix=b''.join(original.splitlines(keepends=True)[:4]);name='bin/annotatePeaks.pl';raw=subject.prefix(home)+original[len(prefix):] if relocate else original
        path=home/name;path.write_bytes(raw);path.chmod(0o755)
        pin=dict(relocate=relocate,bytes=len(original),sha256=hashlib.sha256(original).hexdigest(),original_prefix_base64=base64.b64encode(prefix).decode());return home,name,path,pin
    def test_lock_mirrors_and_three_exact_profiles(self):
        root=subject.HERE.parent.parent;self.assertEqual(subject.RAW,(root/'src/main/resources/org/jlinalg/omics/homer-annotation-lock.json').read_bytes());self.assertEqual(3,len(subject.LOCK['profiles']))
        for p in subject.LOCK['profiles'].values():self.assertEqual(11,len(p['members']));self.assertEqual(subject.LOCK['archives'][p['archive']],subject.core.LOCK['archives'][p['archive']])
    def test_relocation_preserves_pinned_scientific_body(self):
        home,name,path,pin=self.member();self.assertEqual(subject.core.sha(path),subject.installed(home,name,pin))
    def test_changed_scientific_body_is_rejected(self):
        home,name,path,pin=self.member();path.write_bytes(path.read_bytes()+b'# changed\n');self.assertRaises(subject.core.Unavailable,subject.installed,home,name,pin)
    def test_changed_installation_prefix_is_rejected(self):
        home,name,path,pin=self.member();path.write_bytes(path.read_bytes().replace(b'use warnings;',b'use strict;'));self.assertRaises(subject.core.Unavailable,subject.installed,home,name,pin)
    def test_unchanged_archive_members_are_content_checked(self):
        home,name,path,pin=self.member(False);self.assertEqual(subject.core.sha(path),subject.installed(home,name,pin));path.write_bytes(b'changed');self.assertRaises(subject.core.Unavailable,subject.installed,home,name,pin)
    def test_linked_member_is_never_admitted(self):
        home,name,path,pin=self.member()
        with patch.object(Path,'is_symlink',return_value=True):self.assertRaises(subject.core.Unavailable,subject.installed,home,name,pin)
    def test_download_permission_is_separate_from_archive_need(self):
        cache=self.root/'cache';cache.mkdir();name='homer.v5.1.zip'
        with patch.object(subject.core.urllib.request,'urlopen',side_effect=AssertionError('Network must not be called')):self.assertRaises(subject.core.Unavailable,subject.core.obtain,name,cache,[],False)
        self.assertEqual([],list(cache.iterdir()))
    def test_corrupt_archive_is_preserved_not_overwritten(self):
        cache=self.root/'cache';cache.mkdir();p=cache/'homer.v5.1.zip';p.write_bytes(b'corrupt');self.assertRaises(ValueError,subject.core.obtain,p.name,cache,[],False);self.assertEqual(b'corrupt',p.read_bytes())
    def test_no_install_permission_creates_no_home_and_warns(self):
        self.assertEqual(3,self.invoke());self.assertFalse((self.root/'home').exists());warning=(self.root/'report/WARNINGS.md').read_text();self.assertIn('ANALYSIS SKIPPED',warning);self.assertIn('Required action:',warning)
    def test_install_permission_does_not_implicitly_allow_download(self):
        with patch.object(subject.core.urllib.request,'urlopen',side_effect=AssertionError('No implicit download')):self.assertEqual(3,self.invoke('--allow-install'))
        self.assertFalse((self.root/'home').exists())
    def test_existing_report_is_preserved(self):
        report=self.root/'report';report.mkdir();p=report/'sentinel';p.write_text('preserve');self.assertEqual(2,self.invoke(report=report));self.assertEqual('preserve',p.read_text());self.assertFalse((report/'installation.json').exists())
    def test_unsupported_host_warns_and_skips_without_install(self):
        with patch.object(subject.core,'host',side_effect=subject.core.Unavailable('Unsupported host;use Linux Java inside WSL')):self.assertEqual(3,self.invoke('--allow-install','--allow-download'))
        self.assertFalse((self.root/'home').exists());self.assertIn('Unsupported host',(self.root/'report/WARNINGS.md').read_text())

if __name__=='__main__':unittest.main()
