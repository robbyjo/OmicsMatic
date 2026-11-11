import io,unittest,zipfile
import fastp_workflow_validation as f

class FastpWorkflowEvidenceTest(unittest.TestCase):
    def test_complete_frozen_public_original_workflows_and_large_populations(self):
        result=f.verify(emit=False);self.assertEqual(result['public_original_workflows'],58);self.assertEqual(result['input_reads'],301560);self.assertEqual(result['represented_reads'],301362)
    def test_independent_literal_gc_and_phred64_quality_summary(self):
        counts=f.fastq(io.BytesIO(b'@a\nacgTN\n+\nhhhhh\n'),64);self.assertEqual(counts,dict(total_reads=1,total_bases=5,q20_bases=5,q30_bases=5,gc_bases=2))
        with self.assertRaises(AssertionError):f.fastq(io.BytesIO(b'@a\nACGT\n+\n'),33)
    def test_six_significant_digit_comparison_rejects_changed_report(self):
        f.rounded(.571429,4/7)
        with self.assertRaises(AssertionError):f.rounded(.57143,4/7)
    def test_extra_archive_member_is_not_an_admitted_complete_census(self):
        raw=io.BytesIO()
        with zipfile.ZipFile(raw,'w') as z:z.writestr('MANIFEST.json','{}');z.writestr('extra','unlisted')
        with zipfile.ZipFile(io.BytesIO(raw.getvalue())) as z:
            with self.assertRaises(AssertionError):f.census(z,{'MANIFEST.json'})

if __name__=='__main__':unittest.main()
