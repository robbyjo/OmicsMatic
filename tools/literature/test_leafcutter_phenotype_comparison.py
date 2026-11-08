# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject altered source states/axes/order/precision; preserve explicitly undefined PCA diagnostics."""
import copy,unittest
from validate_leafcutter_phenotypes import compare
from audit_leafcutter_pca import audit

class PhenotypeComparison(unittest.TestCase):
    def compare(self,expected,actual):
        errors=[];counts=dict(floating_values=0,exact_values=0,maximum_absolute_error=0.0);compare(expected,actual,'case',errors,counts);return errors
    def test_exact_large_original_count(self):self.assertTrue(self.compare(dict(counts=[9007199254740993,0]),dict(counts=[9007199254740992,0])))
    def test_missing_is_not_zero(self):self.assertTrue(self.compare(dict(ratios=[None,.5]),dict(ratios=[0.0,.5])))
    def test_missing_is_not_NaN(self):self.assertTrue(self.compare(dict(ratios=[None,.5]),dict(ratios=[float('nan'),.5])))
    def test_filter_decision_has_no_float_tolerance(self):self.assertTrue(self.compare(dict(sd=.005,disposition='KEPT'),dict(sd=.005-1e-16,disposition='SOURCE_LOW_VARIATION_FILTER')))
    def test_row_and_sample_axes(self):self.assertTrue(self.compare(dict(matrix=[[.1,.2,.3],[.4,.5,.6]]),dict(matrix=[[.1,.4],[.2,.5],[.3,.6]])))
    def test_original_coordinate_text(self):self.assertTrue(self.compare(dict(id='chr1:020:120:clu_1_+'),dict(id='chr1:20:120:clu_1_+')))
    def test_sample_order(self):self.assertTrue(self.compare(dict(samples=['S3','S1']),dict(samples=['S1','S3'])))
    def test_floating_tolerance_is_explicit(self):self.assertFalse(self.compare(dict(score=.5),dict(score=.5+1e-15)));self.assertTrue(self.compare(dict(score=.5),dict(score=.5+1e-8)))
    def test_nonfinite_values_never_pass_as_close(self):self.assertTrue(self.compare(dict(score=.5),dict(score=float('nan'))))
    def test_extra_missing_or_duplicated_family(self):self.assertTrue(self.compare(dict(rows=[1,2]),dict(rows=[1,2,2])));self.assertTrue(self.compare(dict(rows=[1,2]),dict(rows=[1])))
    def test_undefined_variance_must_remain_explicit(self):
        source=dict(samples=['S1','S2'],kept=[dict(inverse_normal_scores=[0.0,0.0]),dict(inverse_normal_scores=[0.0,0.0])]);pca=dict(observations=2,features=2,mean=[0.0,0.0],components=[[1.0,0.0]],scores=[[0.0],[0.0]],singular_values=[0.0],explained_variance=[0.0],explained_variance_ratio=[dict(state='NaN')],solver='full');self.assertEqual(audit(source,pca)['source_undefined_variance_ratios'],1);changed=copy.deepcopy(pca);changed['explained_variance_ratio']=[0.0]
        with self.assertRaises(AssertionError):audit(source,changed)

if __name__=='__main__':unittest.main()
