"""Comparison regressions for real result roles versus temporary environment directories."""
from pathlib import Path
import tempfile
import unittest
import verify_homer_atac_workflow as verifier


class AtacReferenceComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup);self.root=Path(self.temporary.name);self.actual=self.root/'actual';self.reference=self.root/'reference';self.actual.mkdir();self.reference.mkdir();self.session=dict(original_tag_populations={'R0001':{},'MERGED':{}})
        for parent in [self.actual,self.reference]:
            for role in self.session['original_tag_populations']:
                directory=parent/('tags-'+role);directory.mkdir();(directory/'tagInfo.txt').write_text('name\tUnique Positions\tTotal Tags\ngenome\t1\t1.0\ncmd='+str(parent)+'\t\t\n');(directory/'chr1.tags.tsv').write_text('\tchr1\t100\t0\t1.0\t100\n')

    def test_temporary_process_homes_are_not_mistaken_for_scientific_roles(self):
        for name in ['tags-r0001-home','tags-r0001-tmp']:(self.actual/name).mkdir()
        verifier.compare_tag_families(self.actual,self.reference,self.session)

    def test_actual_scientific_record_difference_is_rejected(self):
        (self.reference/'tags-MERGED/chr1.tags.tsv').write_text('\tchr1\t100\t0\t0.5\t100\n')
        with self.assertRaisesRegex(ValueError,'Every original tag/QC/metadata'):verifier.compare_tag_families(self.actual,self.reference,self.session)

    def test_missing_expected_role_is_an_artifact_failure_not_a_missing_dependency(self):
        (self.reference/'tags-R0001/chr1.tags.tsv').unlink();(self.reference/'tags-R0001/tagInfo.txt').unlink();(self.reference/'tags-R0001').rmdir()
        with self.assertRaisesRegex(ValueError,'Expected original tag-result directory missing'):verifier.compare_tag_families(self.actual,self.reference,self.session)

    def test_path_metadata_is_ignored_but_scientific_mass_is_not(self):
        verifier.compare_tag_families(self.actual,self.reference,self.session);path=self.reference/'tags-MERGED/tagInfo.txt';path.write_text(path.read_text().replace('genome\t1\t1.0','genome\t1\t2.0'))
        with self.assertRaises(ValueError):verifier.compare_tag_families(self.actual,self.reference,self.session)


if __name__=='__main__':unittest.main()
