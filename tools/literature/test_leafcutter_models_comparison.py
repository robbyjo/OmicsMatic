# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject dropped/reordered model families, repaired missing states and the observed near-null precision regression."""
import math,unittest
from validate_leafcutter_models import compare


class ModelComparison(unittest.TestCase):
    def errors(self,expected,actual):
        errors=[];counts=dict(numeric_values=0,exact_values=0,maximum_absolute_error=0.0);compare(expected,actual,'case',errors,counts);return errors
    def test_complete_family(self):self.assertTrue(self.errors(dict(cases=[1,2]),dict(cases=[1])))
    def test_extra_family(self):self.assertTrue(self.errors(dict(cases=[1,2]),dict(cases=[1,2,2])))
    def test_identity_and_order(self):self.assertTrue(self.errors(dict(ids=['Z','A']),dict(ids=['A','Z'])))
    def test_missing_is_not_zero(self):self.assertTrue(self.errors(dict(p='NA'),dict(p=0)))
    def test_missing_is_not_null(self):self.assertTrue(self.errors(dict(p='NA'),dict(p=None)))
    def test_nonfinite_never_passes(self):self.assertTrue(self.errors(dict(objective=-200.0),dict(objective=math.nan)))
    def test_degrees_of_freedom_exact_integer(self):self.assertTrue(self.errors(dict(df=1),dict(df=1.0)))
    def test_observed_near_null_tail_precision_regression(self):self.assertTrue(self.errors(dict(p=.9994102103573),dict(p=.9994102106027275)))
    def test_effect_sign_change(self):self.assertTrue(self.errors(dict(delta_psi=[.3,-.3]),dict(delta_psi=[-.3,.3])))
    def test_numeric_boolean_is_not_a_coefficient(self):self.assertTrue(self.errors(dict(objective=1.0),dict(objective=True)))
    def test_explicit_objective_tolerance(self):self.assertFalse(self.errors(dict(objective=-210.0),dict(objective=-210.0+1e-10)));self.assertTrue(self.errors(dict(objective=-210.0),dict(objective=-210.0+1e-5)))


if __name__=='__main__':unittest.main()
