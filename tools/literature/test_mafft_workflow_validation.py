# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Meaningful rejection cases for the retained-evidence reader and narrow message bridge."""
import copy
import io
import json
import lzma
from pathlib import Path
import stat
import struct
import tempfile
import unittest
import zipfile
from mafft_workflow_validation import (Evidence, NEW_MESSAGE, OLD_MESSAGE, RUNTIME, json_object,
                                      open_evidence, patch_setup_class, safe_path, sha,
                                      verify, verify_current_case, verify_matrix, verify_setup_source)


class SetupMessageProofTest(unittest.TestCase):
    def source(self):
        before = b'int scientificValue=42;\nString help="' + OLD_MESSAGE + b'";\n'
        after = before.replace(OLD_MESSAGE, NEW_MESSAGE)
        proof = dict(status='passed-generated-setup-instruction-literal-only-source-delta', path=RUNTIME,
                     before_sha256=sha(before), after_sha256=sha(after), before_literal=OLD_MESSAGE.decode(),
                     after_literal=NEW_MESSAGE.decode())
        return before, after, proof

    def test_exact_message_update_preserves_the_entire_other_source(self):
        verify_setup_source(*self.source())

    def test_rehashed_scientific_edit_cannot_use_a_help_message_exception(self):
        before, after, proof = self.source(); after = after.replace(b'=42', b'=43'); proof['after_sha256'] = sha(after)
        with self.assertRaises(AssertionError): verify_setup_source(before, after, proof)

    def test_exception_cannot_be_relabelled_for_a_different_scientific_file(self):
        before, after, proof = self.source(); proof['path'] = 'src/main/java/org/jlinalg/cli/MafftAnalysis.java'
        with self.assertRaises(AssertionError): verify_setup_source(before, after, proof)

    def test_unreviewed_message_words_cannot_be_substituted(self):
        before, after, proof = self.source(); after += b'// extra source\n'; proof['after_sha256'] = sha(after)
        with self.assertRaises(AssertionError): verify_setup_source(before, after, proof)

    def class_file(self):
        body = b'prefix ' + OLD_MESSAGE + b' suffix'
        return b'\xca\xfe\xba\xbe\x00\x00\x00\x3d\x00\x03' + b'\x01' + struct.pack('>H', len(body)) + body + b'\x08\x00\x01' + b'unchanged-method-instructions'

    def test_only_the_selected_utf8_constant_changes(self):
        before = self.class_file(); after = patch_setup_class(before)
        self.assertIn(NEW_MESSAGE, after); self.assertNotIn(OLD_MESSAGE, after)
        self.assertTrue(after.endswith(b'unchanged-method-instructions'))
        self.assertEqual(len(after)-len(before), len(NEW_MESSAGE)-len(OLD_MESSAGE))

    def test_an_extra_instruction_change_does_not_equal_the_proven_class_delta(self):
        before = self.class_file(); after = patch_setup_class(before)
        self.assertNotEqual(after, after[:-1] + b'X')

    def test_a_missing_message_is_not_a_class_compatibility_proof(self):
        with self.assertRaises(AssertionError): patch_setup_class(self.class_file().replace(OLD_MESSAGE, b'x'*len(OLD_MESSAGE)))

    def test_foreign_or_truncated_class_is_rejected(self):
        for value in [b'', b'#!/bin/sh\n', self.class_file()[:12]]:
            with self.subTest(value=value):
                with self.assertRaises((AssertionError, IndexError, struct.error)): patch_setup_class(value)


class ArchiveReaderTest(unittest.TestCase):
    def archive(self, content=b'original data', bad_digest=False, extra=False, link=False, unsafe=False):
        key = sha(content) if not bad_digest else '0'*64
        record = dict(objects={key:len(content)})
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:
            z.writestr('record.json', json.dumps(record)); z.writestr('README.txt', 'scope')
            if link:
                item = zipfile.ZipInfo('objects/'+key); item.create_system=3; item.external_attr=(stat.S_IFLNK|0o777)<<16; z.writestr(item, content)
            else: z.writestr('../escape' if unsafe else 'objects/'+key, content)
            if extra: z.writestr('unindexed', b'cannot hide this')
        return out.getvalue()

    def test_complete_object_content_is_checked(self):
        with zipfile.ZipFile(io.BytesIO(self.archive())) as z:
            e=Evidence(z); self.assertEqual(e.raw(dict(sha256=sha(b'original data'),bytes=13)), b'original data'); e.content.cache_clear()

    def test_rehashed_container_cannot_hide_changed_object_bytes(self):
        with zipfile.ZipFile(io.BytesIO(self.archive(bad_digest=True))) as z:
            with self.assertRaises(AssertionError): Evidence(z)

    def test_extra_unindexed_members_are_rejected(self):
        with zipfile.ZipFile(io.BytesIO(self.archive(extra=True))) as z:
            with self.assertRaises(AssertionError): Evidence(z)

    def test_archive_link_member_is_not_admitted_as_a_regular_file(self):
        with zipfile.ZipFile(io.BytesIO(self.archive(link=True))) as z:
            with self.assertRaises(AssertionError): Evidence(z)

    def test_parent_traversal_is_rejected_without_extraction(self):
        with zipfile.ZipFile(io.BytesIO(self.archive(unsafe=True))) as z:
            with self.assertRaises(AssertionError): Evidence(z)

    def test_noncanonical_and_foreign_logical_paths_are_rejected(self):
        for name in ['../escape','/absolute','C:/absolute','a\\b','a/../b','a/./b','a//b','','.']:
            with self.subTest(name=name):
                with self.assertRaises(AssertionError): safe_path(name)

    def test_duplicate_json_keys_are_not_silently_overwritten(self):
        with self.assertRaises(AssertionError): json_object(b'{"status":0,"status":1}')

    def test_compressed_reader_verifies_both_outer_and_expanded_sizes(self):
        inner=self.archive(); compressed=lzma.compress(inner)
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'controls.zip.xz';p.write_bytes(compressed)
            pin=dict(bytes=len(compressed),sha256=sha(compressed),inner_bytes=len(inner))
            with open_evidence(p,pin) as e:self.assertEqual(len(e.objects),1)
            wrong=dict(pin,inner_bytes=len(inner)+1)
            with self.assertRaises(AssertionError):
                with open_evidence(p,wrong):pass

    def test_wrong_compressed_digest_is_rejected(self):
        compressed=lzma.compress(self.archive())
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'controls.zip.xz';p.write_bytes(compressed)
            with self.assertRaises(AssertionError):
                with open_evidence(p,dict(bytes=len(compressed),sha256='0'*64,inner_bytes=0)):pass

    def test_empty_protocol_list_cannot_be_relabelled_complete(self):
        class Incomplete:
            record=dict(schema_version=1,format='complete-file-families-content-addressed-v1',status='passed-complete-synthetic-workflow-controls',bytes_are_original_and_losslessly_referenced=True,upstream_executables_redistributed=False,biological_accuracy_certified=False,whole_paper_reproduced=False,protocols=[])
        with self.assertRaises(AssertionError):verify_matrix(Incomplete())

    def test_an_additional_case_without_completion_is_not_a_valid_alignment(self):
        buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w') as z:z.writestr('partial.txt','not a result')
        with self.assertRaises(KeyError):verify_current_case(buffer.getvalue())

    def test_unsafe_additional_case_cannot_escape_its_logical_root(self):
        buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w') as z:z.writestr('../escape','not a result')
        with self.assertRaises(AssertionError):verify_current_case(buffer.getvalue())


class CompleteEvidenceTest(unittest.TestCase):
    def test_current_complete_corpus_and_scientific_source_are_verified(self):
        result=verify(emit=False)
        self.assertEqual(result['protocols'],57);self.assertEqual(result['completed_alignments'],35)
        self.assertEqual(result['expected_skips'],13);self.assertEqual(result['expected_failures'],9)
        self.assertEqual(result['original_residue_cells'],1678);self.assertEqual(result['deliberate_deletions'],37)


if __name__ == '__main__':unittest.main()
