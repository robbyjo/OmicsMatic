# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Ensure the scientific comparator rejects tiny numeric changes, missing families and state/identity substitutions."""
import copy,math,unittest
from validate_cellphonedb_statistics import compare


class CompleteComparison(unittest.TestCase):
    original=dict(means=[[1.,'NaN','+Inf']],pvalues=[[0.,.05,1.]],pairs=[['A','B'],['B','A']],packed_greater=[[128,0]],permuted_means=[[[2.,3.,4.]],[[5.,6.,7.]]])
    def errors(self,actual):
        errors=[];compare(self.original,actual,'case',errors,dict(numeric_values=0,exact_values=0));return errors
    def test_exact_roundtrip(self):self.assertEqual([],self.errors(copy.deepcopy(self.original)))
    def test_one_ulp_is_rejected(self):
        x=copy.deepcopy(self.original);x['means'][0][0]=math.nextafter(1.,2.);self.assertTrue(self.errors(x))
    def test_missing_permutation_is_rejected(self):
        x=copy.deepcopy(self.original);x['permuted_means'].pop();self.assertTrue(self.errors(x))
    def test_missing_row_is_rejected(self):
        x=copy.deepcopy(self.original);x['means'].pop();self.assertTrue(self.errors(x))
    def test_changed_pair_order_is_rejected(self):
        x=copy.deepcopy(self.original);x['pairs'].reverse();self.assertTrue(self.errors(x))
    def test_nonfinite_state_substitution_is_rejected(self):
        x=copy.deepcopy(self.original);x['means'][0][1]=None;self.assertTrue(self.errors(x))
    def test_float_for_nonfinite_state_is_rejected(self):
        x=copy.deepcopy(self.original);x['means'][0][2]=float('inf');self.assertTrue(self.errors(x))
    def test_bit_substitution_is_rejected(self):
        x=copy.deepcopy(self.original);x['packed_greater'][0][0]=129;self.assertTrue(self.errors(x))
    def test_bool_for_number_is_rejected(self):
        x=copy.deepcopy(self.original);x['pvalues'][0][0]=False;self.assertTrue(self.errors(x))
    def test_added_field_is_rejected(self):
        x=copy.deepcopy(self.original);x['extra']='unbound';self.assertTrue(self.errors(x))


if __name__=='__main__':unittest.main()
