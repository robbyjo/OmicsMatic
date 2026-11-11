# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial checks for the narrowly scoped original unordered-field comparison."""
import copy,unittest
from compare_loftee_original import compare_tables

class ComparisonTests(unittest.TestCase):
    def setUp(self):self.original=dict(CSQ_header='##INFO=<ID=CSQ,Description="Format: Allele|LoF|LoF_info">',column_header='#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tSYN',records=['SYN1\t104\tID\tC\tT\t.\tPASS\tCSQ=T|HC|X:NA&Y:-4&X:NA\tGT\t0/1'])
    def changed(self,before,after):other=copy.deepcopy(self.original);other['records'][0]=other['records'][0].replace(before,after);return other
    def test_exact_records_remain_exact(self):self.assertEqual(compare_tables(self.original,self.original)['byte_exact_records'],1)
    def test_only_extended_info_token_order_can_differ(self):self.assertEqual(compare_tables(self.original,self.changed('X:NA&Y:-4&X:NA','Y:-4&X:NA&X:NA'),True)['records_with_only_LoF_info_token_order_difference'],1)
    def test_unordered_permission_is_never_inferred(self):
        with self.assertRaises(AssertionError):compare_tables(self.original,self.changed('X:NA&Y:-4&X:NA','Y:-4&X:NA&X:NA'))
    def test_duplicate_token_loss_is_not_hidden(self):
        with self.assertRaises(AssertionError):compare_tables(self.original,self.changed('X:NA&Y:-4&X:NA','Y:-4&X:NA'),True)
    def test_numeric_changes_are_not_rounded_away(self):
        with self.assertRaises(AssertionError):compare_tables(self.original,self.changed('Y:-4','Y:-4.0'),True)
    def test_na_and_nan_are_distinct_literal_tokens(self):
        with self.assertRaises(AssertionError):compare_tables(self.original,self.changed('X:NA','X:NaN'),True)
    def test_confidence_change_is_refused(self):
        with self.assertRaises(AssertionError):compare_tables(self.original,self.changed('|HC|','|LC|'),True)
    def test_genotype_change_is_refused(self):
        with self.assertRaises(AssertionError):compare_tables(self.original,self.changed('0/1','1/0'),True)

if __name__=='__main__':unittest.main()
