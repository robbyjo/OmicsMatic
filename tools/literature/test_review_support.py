"""Multi-paragraph curation must retain exact source locators and hashes."""
import hashlib
import unittest
from collect_omics_review import validate_supporting_evidence, tool_sentence_context


class SupportingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.paragraphs = [{"paragraph_index":2,"section":"Methods / Assembly","text":"Native sequence extraction and repeat scoring were performed with the named tool."}]
        self.pointer = {"paragraph_index":2,"section":"Methods / Assembly",
                        "paragraph_sha256":hashlib.sha256(self.paragraphs[0]['text'].encode()).hexdigest()}

    def test_valid_additional_source_is_accepted(self):
        validate_supporting_evidence(self.paragraphs,[{'supporting_evidence':[self.pointer]}])

    def test_changed_hash_or_section_is_rejected(self):
        for key,value in [('paragraph_sha256','0'*64),('section','Different methods')]:
            with self.assertRaisesRegex(ValueError,'stale'):
                validate_supporting_evidence(self.paragraphs,[{'supporting_evidence':[{**self.pointer,key:value}]}])

    def test_missing_or_duplicate_pointer_is_rejected(self):
        for pointers in [[{**self.pointer,'paragraph_index':3}],[self.pointer,self.pointer]]:
            with self.assertRaisesRegex(ValueError,'Duplicate or missing'):
                validate_supporting_evidence(self.paragraphs,[{'supporting_evidence':pointers}])

    def test_flash_read_reconstruction_and_merge_roles_are_accepted(self):
        for text in ['Before full read reconstruction using FLASH (v1.2.11).',
                     'The trimmed forward read and the reverse read were merged using FLASH with a minimum overlap of 10.']:
            self.assertTrue(tool_sentence_context('flash-merge', text))

    def test_flash_frozen_hardware_and_factorization_roles_are_not_read_merging(self):
        for text in ['Samples were flash-frozen before RNA sequencing.',
                     'Luminescence was measured using the FLASH plate reader.',
                     'FLASH camera images were merged into the panel.',
                     'Read-derived matrices were merged using flash matrix factorization.']:
            self.assertFalse(tool_sentence_context('flash-merge', text))


if __name__ == '__main__':unittest.main()
