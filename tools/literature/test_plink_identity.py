"""Release identity must follow actual named spans, not a shared sentence."""
from pathlib import Path
import hashlib,json,unittest,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,tool_matches

REGISTRY=[t for t in json.loads(Path(__file__).with_name('tool-registry.json').read_text(encoding='utf-8')) if t['id'] in {'plink','plink2'}]

class PlinkIdentityTest(unittest.TestCase):
    def extract(self,text,section='Methods / Genotype quality control'):
        return {t['tool_id']:t for t in tool_matches([dict(section=section,text=text,paragraph_index=1)],REGISTRY)}

    def test_parenthesized_version_is_release_two_only(self):
        for spelling in ['PLINK (v.2.0)','PLINK (v2.0)','PLINK (version 2.0)','PLINK (2.0)']:
            with self.subTest(spelling=spelling):
                found=self.extract('We used '+spelling+' to compute genetic principal components.')
                self.assertEqual({'plink2'},set(found));self.assertEqual('2.0',found['plink2']['version_reported_near_name'])

    def test_period_version_prefix_and_bare_family_are_release_two(self):
        for spelling,version in [('PLINK v.2.0','2.0'),('PLINK v.2','2'),('PLINK version 2','2'),('PLINK2','2'),('PLINK 2.00','2.00')]:
            with self.subTest(spelling=spelling):
                found=self.extract('Genotypes were filtered using '+spelling+'.')
                self.assertEqual({'plink2'},set(found));self.assertEqual(version,found['plink2']['version_reported_near_name'])

    def test_alpha_build_suffix_and_source_spelling_are_preserved(self):
        for spelling,version in [('Plink v.2.0.0','2.0.0'),('PLINK v.2.00a3LM','2.00a3LM'),('PLINK (v.2.00a3.1LM)','2.00a3.1LM')]:
            with self.subTest(spelling=spelling):
                found=self.extract('We performed genotype quality control using '+spelling+'.')
                self.assertEqual({'plink2'},set(found));self.assertEqual(version,found['plink2']['version_reported_near_name'])

    def test_family_name_followed_by_full_version_keeps_full_version(self):
        found=self.extract('Variants were filtered using PLINK2 (v.2.00a5LM) with --geno 0.1 and --maf 0.05.')
        self.assertEqual({'plink2'},set(found));self.assertEqual('2.00a5LM',found['plink2']['version_reported_near_name'])

    def test_release_one_is_not_reclassified(self):
        for spelling,version in [('PLINK1.90b5.3','1.90b5.3'),('PLINK v.1.9','1.9'),('PLINK (version 1.90b6.26)','1.90b6.26')]:
            with self.subTest(spelling=spelling):
                found=self.extract('We clumped GWAS variants using '+spelling+'.')
                self.assertEqual({'plink'},set(found));self.assertEqual(version,found['plink']['version_reported_near_name'])

    def test_unspecified_release_remains_unspecified(self):
        found=self.extract('We performed genotype quality control with PLINK.')
        self.assertEqual({'plink'},set(found));self.assertIsNone(found['plink']['version_reported_near_name'])

    def test_both_releases_in_one_sentence_are_distinct(self):
        found=self.extract('We used PLINK 1.9 to clump variants and PLINK 2.0 to compute polygenic scores.')
        self.assertEqual({'plink','plink2'},set(found));self.assertEqual('1.9',found['plink']['version_reported_near_name']);self.assertEqual('2.0',found['plink2']['version_reported_near_name'])

    def test_both_releases_in_different_paragraphs_are_distinct(self):
        ps=[dict(section='Methods',text='We used PLINK 2.0 to compute genetic principal components.',paragraph_index=1),dict(section='Methods',text='We used PLINK 1.9 to clump GWAS variants.',paragraph_index=2)]
        found={t['tool_id']:t for t in tool_matches(ps,REGISTRY)}
        self.assertEqual({'plink','plink2'},set(found));self.assertEqual(2,found['plink']['evidence_paragraph_index']);self.assertEqual(1,found['plink2']['evidence_paragraph_index'])

    def test_shared_sentence_does_not_overwrite_release_specific_evidence(self):
        text='We computed scores with PLINK v.2.0 and used PLINK v.1.90b5.3 for LD clumping.'
        found=self.extract(text)
        self.assertEqual({'plink','plink2'},set(found));self.assertEqual('1.90b5.3',found['plink']['version_reported_near_name']);self.assertEqual('2.0',found['plink2']['version_reported_near_name'])

    def test_wrappers_and_longer_tokens_are_not_the_executable(self):
        for token in ['plink2R','myPLINK2','PLINK20','PLINK2Python','PLINK2.0R']:
            with self.subTest(token=token):self.assertFalse(self.extract('We performed genotype analyses using '+token+'.'))

    def test_file_format_conversion_does_not_create_generic_execution(self):
        found=self.extract('Genotypes were converted to the plink format and filtered using PLINK2 (v.2.00a5LM) with --geno 0.1.')
        self.assertEqual({'plink2'},set(found));self.assertEqual('2.00a5LM',found['plink2']['version_reported_near_name'])
        self.assertFalse(self.extract('We received genotype data in PLINK binary format for further analysis.'))

    def test_urls_do_not_create_generic_or_release_specific_execution(self):
        found=self.extract('Genetic PCs were calculated with plink2 (https://www.cog-genomics.org/plink/2.0/) on LD-pruned SNPs.')
        self.assertEqual({'plink2'},set(found))
        self.assertFalse(self.extract('We obtained genotype documentation from https://example.org/PLINK2/ and https://www.cog-genomics.org/plink/2.0/.'))

    def test_format_or_url_before_real_name_does_not_hide_execution(self):
        found=self.extract('The PLINK format was exported and https://example.org/PLINK/ documented it; we ran PLINK v.1.9 for genotype QC.')
        self.assertEqual({'plink'},set(found));self.assertEqual('1.9',found['plink']['version_reported_near_name'])

    def test_availability_list_and_prior_study_are_not_execution(self):
        self.assertFalse(self.extract('Several tools are available including PLINK 1.9 and PLINK 2.0 for GWAS analysis.'))
        self.assertFalse(self.extract('PLINK v.2.0: https://www.cog-genomics.org/plink/2.0/.','Code availability'))
        self.assertFalse(self.extract('Previous studies have used PLINK v.2.0 for association analysis.'))

    def test_appendix_gwas_methods_supply_execution_evidence(self):
        found=self.extract('Analyses were performed separately using PLINK v.2.00a3LM in each sex stratum.','Appendix A / Sex-specific sleep GWASs in MVP')
        self.assertEqual({'plink2'},set(found));self.assertEqual('2.00a3LM',found['plink2']['version_reported_near_name'])

    def test_gwas_heading_does_not_make_available_or_prior_tools_execution(self):
        self.assertFalse(self.extract('Several tools are available including PLINK 1.9 and PLINK 2.0 for association studies.','Appendix A / GWAS'))
        self.assertFalse(self.extract('Previous studies have used PLINK v.2.0 for association analysis.','Appendix A / GWAS'))

    def test_jats_reference_label_is_not_part_of_the_build(self):
        root=ET.fromstring('<article><body><sec><title>Methods</title><p>Analyses were performed using PLINK v.2.00a3LM<xref ref-type="bibr">56</xref> separately in each sex stratum.</p></sec></body></article>')
        ps=paragraphs(root);self.assertIn('2.00a3LM56',ps[0]['text']);self.assertNotIn('LM56',ps[0]['match_text'])
        found={t['tool_id']:t for t in tool_matches(ps,REGISTRY)}
        self.assertEqual({'plink2'},set(found));self.assertEqual('2.00a3LM',found['plink2']['version_reported_near_name'])
        self.assertEqual(ps[0]['text'],found['plink2']['evidence_text']);self.assertEqual(hashlib.sha256(ps[0]['text'].encode()).hexdigest(),hashlib.sha256(found['plink2']['evidence_text'].encode()).hexdigest())

    def test_preferred_release_one_anchor_survives_release_two_selection(self):
        ps=[dict(section='Methods',text='We used PLINK 1.9 and PLINK 2.0 for LD and score analysis.',paragraph_index=1),dict(section='Methods',text='We performed additional genotype quality control with PLINK v.1.90b5.3.',paragraph_index=2)]
        preferred={'plink':hashlib.sha256(ps[1]['text'].encode()).hexdigest()}
        found={t['tool_id']:t for t in tool_matches(ps,REGISTRY,preferred)}
        self.assertEqual({'plink','plink2'},set(found));self.assertEqual(2,found['plink']['evidence_paragraph_index']);self.assertEqual(1,found['plink2']['evidence_paragraph_index'])

if __name__=='__main__':unittest.main()
