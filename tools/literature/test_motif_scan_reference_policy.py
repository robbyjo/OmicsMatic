"""Scientific comparison boundaries for actual original FIMO text references."""
import unittest
from verify_motif_scan import compare

def site(p='0.913',score='1.23457'):
    return dict(motif_id='M',motif_alt_id='',sequence_name='chr1',start='1',stop='4',strand='+',matched_sequence='ACGT',score=score,**{'p-value':p,'q-value':''})

class MotifScanReferencePolicyTest(unittest.TestCase):
    def test_reported_halfway_rounding_interval_allows_floating_point_neighbor(self):
        compare([site()],[site(p='0.9124999999999999',score='1.23456789')])
    def test_rounding_interval_cannot_hide_a_material_p_or_score_error(self):
        for actual in [site(p='.91'),site(score='1.2346')]:
            with self.assertRaises(AssertionError):compare([site()],[actual])
    def test_rare_probabilities_have_no_absolute_tolerance_floor(self):
        with self.assertRaises(AssertionError):compare([site(p='1.02e-100')],[site(p='1.0e-100')])
        with self.assertRaises(AssertionError):compare([site(p='1.02e-100')],[site(p='0')])
    def test_identity_orientation_and_multiplicity_are_checked(self):
        wrong=site();wrong['strand']='-'
        with self.assertRaises(AssertionError):compare([site()],[wrong])
        with self.assertRaises(AssertionError):compare([site(),site()],[site()])
    def test_no_q_value_is_substituted_for_native_text_scanning(self):
        wrong=site();wrong['q-value']='.1'
        with self.assertRaises(AssertionError):compare([site()],[wrong])
    def test_nonfinite_and_out_of_probability_range_values_fail(self):
        for p in ['NaN','Infinity','-1','1.1']:
            with self.assertRaises(AssertionError):compare([site(p='1')],[site(p=p)])

if __name__=='__main__':unittest.main()
