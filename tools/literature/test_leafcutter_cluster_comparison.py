# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Scientific comparison must detect integer precision loss, changed families, states and source order."""
import copy,unittest
from validate_leafcutter_clusters import compare

class ExactClusterComparison(unittest.TestCase):
    def reject(self,expected,actual):
        errors=[];compare(expected,actual,'case',errors);self.assertTrue(errors)
    def test_integer_above_double_precision(self):self.reject(dict(reads=9007199254740993),dict(reads=9007199254740992))
    def test_float_replacement_of_exact_integer(self):self.reject(dict(reads=3),dict(reads=3.0))
    def test_boolean_is_not_integer_count(self):self.reject(dict(reads=1),dict(reads=True))
    def test_zero_denominator_is_not_imputed(self):self.reject(dict(numerators=[0,5],denominators=[0,10]),dict(numerators=[5,5],denominators=[10,10]))
    def test_intron_order_is_not_normalized(self):self.reject([dict(start=100),dict(start=20)],[dict(start=20),dict(start=100)])
    def test_sample_order_is_not_sorted(self):self.reject(dict(samples=['S3','S1']),dict(samples=['S1','S3']))
    def test_duplicate_rows_are_not_collapsed(self):self.reject([dict(start=100),dict(start=100)],[dict(start=100)])
    def test_missing_required_field(self):self.reject(dict(start=100,end=200),dict(start=100))
    def test_extra_output_field(self):self.reject(dict(start=100),dict(start=100,repaired=True))
    def test_complete_identity_and_json_object_key_order(self):
        value=dict(rows=[dict(start=100,counts=[9007199254740993,0])],samples=['S3','S1']);other=copy.deepcopy(value);other=dict(reversed(list(other.items())));errors=[];compare(value,other,'case',errors);self.assertEqual(errors,[])

if __name__=='__main__':unittest.main()
