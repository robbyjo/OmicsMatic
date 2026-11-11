# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check that complete-source comparison rejects lost fields, reordered identifiers and changed numeric states."""
import unittest
from validate_hail_sample_qc import compare

class CompleteQcComparisonTest(unittest.TestCase):
    def check(self,expected,actual,accepted):
        errors=[];counts=dict(numeric_states=0,exact_scalar_values=0);compare(expected,actual,'root',errors,counts);self.assertEqual(not errors,accepted);return counts
    def test_reordered_duplicate_sample_ordinals_rejected(self):
        self.check([dict(sample='S',ordinal=0),dict(sample='S',ordinal=1)],[dict(sample='S',ordinal=1),dict(sample='S',ordinal=0)],False)
    def test_lost_sample_rejected(self):self.check([dict(sample='A'),dict(sample='B')],[dict(sample='A')],False)
    def test_lost_metric_rejected(self):self.check(dict(n_called=2,n_filtered=1),dict(n_called=2),False)
    def test_changed_count_rejected(self):self.check(dict(n_called=2),dict(n_called=3),False)
    def test_floating_extremum_cannot_be_integer(self):self.check(dict(state='value',text='10.0'),10,False)
    def test_missing_is_not_nan(self):self.check(dict(state='missing'),dict(state='NaN',text='NaN'),False)
    def test_infinity_sign_preserved(self):self.check(dict(state='+Inf',text='+Inf'),dict(state='-Inf',text='-Inf'),False)
    def test_nonfinite_cannot_be_finite(self):self.check(dict(state='NaN',text='NaN'),dict(state='value',text='0'),False)
    def test_large_statistical_difference_rejected(self):self.check(dict(state='value',text='1'),dict(state='value',text='1.0001'),False)
    def test_roundoff_tolerance_only(self):self.check(dict(state='value',text='1.0'),dict(state='value',text='1.000000000000001'),True)
    def test_identical_empty_nonfinite_and_missing_preserved(self):
        source=dict(mean=dict(state='NaN',text='NaN'),min=dict(state='missing'));self.assertEqual(self.check(source,source,True)['numeric_states'],2)
    def test_boolean_cannot_replace_count(self):self.check(1,True,False)

if __name__=='__main__':unittest.main()
