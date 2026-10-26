"""Regression checks for materially misleading literature extraction cases."""
import json
import hashlib
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, tool_matches, publication_record, validate_supporting_evidence

REGISTRY = json.loads(Path(__file__).with_name("tool-registry.json").read_text(encoding="utf-8"))


class EvidenceExtractionTest(unittest.TestCase):
    def extract(self, text, section="Methods / Analysis"):
        return {t["tool_id"] for t in tool_matches([{"section": section, "text": text, "paragraph_index": 1}], REGISTRY)}

    def test_trim_galore_primary_source_underscore_alias_is_bounded(self):
        for name in ['Trim_Galore', 'TrimGalore', 'Trim Galore']:
            self.assertIn('trimgalore',self.extract('Adapter sequences were removed with '+name+' (v0.6.7).'))
        self.assertNotIn('trimgalore',self.extract('Results were stored in prefixTrim_GaloreSuffix.'))

    def test_dose_named_enrichment_package_is_not_treatment_dose(self):
        self.assertIn('dose',self.extract('GSEA used the DOSE v3.24.2 and clusterProfiler packages.'))
        self.assertNotIn('dose',self.extract('Cells received a high dose before pathway analysis.'))
        self.assertNotIn('dose',self.extract('The DOSE of treatment was recorded.'))

    def test_viper_package_is_distinct_from_ensemble_and_species(self):
        self.assertIn('viper',self.extract('The protein-activity signature used the VIPER package.'))
        self.assertNotIn('viper',self.extract('Protein activity was inferred with metaVIPER and msVIPER.'))
        self.assertNotIn('viper',self.extract('We sequenced the viper genome.'))
        self.assertNotIn('viper',self.extract('The VIPER species was sampled.'))

    def test_named_area_method_is_not_ordinary_geometric_area(self):
        self.assertIn('area',self.extract('Signature conservation used analytic rank-based enrichment analysis (aREA).'))
        self.assertNotIn('area',self.extract('The area under the enrichment curve was calculated.'))
        self.assertNotIn('area',self.extract('The AREA of a tissue was measured.'))

    def test_compatible_quantification_algorithm_example_is_not_program_execution(self):
        names=self.extract('We model EM point estimates from a pseudo-mapping quantification algorithm, like Salmon or kallisto.')
        self.assertNotIn('salmon',names);self.assertNotIn('kallisto',names)
        self.assertIn('kallisto',self.extract('We quantified RNA reads using kallisto with sequence-specific bias correction.'))

    def test_named_transcript_import_support_packages_are_not_generic_database_or_read_terms(self):
        self.assertTrue({'biomart','shortread'}.issubset(self.extract('Gene mapping used biomaRt and sequencing quality was assessed with ShortRead.')))
        self.assertNotIn('biomart',self.extract('We downloaded a table from the BioMart database.'))
        self.assertNotIn('shortread',self.extract('The short-read sequencing data and shortread alignments were analyzed.'))
        self.assertTrue({'metaviper','aracne','annotationhub','biocfilecache','biocgenerics'}.issubset(self.extract('Networks used ARACNe and metaVIPER; annotation used AnnotationHub, BiocFileCache and BiocGenerics.')))

    def test_bwa_mem2_is_not_backtracked_into_legacy_bwa_family(self):
        for name in ['BWA-MEM2', 'bwa-mem2', 'BWA MEM2', 'BWA-MEM 2']:
            found = self.extract('Short reads were mapped to the reference using ' + name + ' v2.2.1.')
            self.assertIn('bwa-mem2', found)
            self.assertNotIn('bwa', found)
        self.assertIn('bwa', self.extract('DNA reads were mapped using BWA-MEM v0.7.17.'))

    def test_hmmratac_is_distinct_from_ordinary_macs_peak_calling(self):
        hidden_markov = self.extract('ATAC peaks were called using MACS3 v3.0.3 hmmratac.')
        self.assertTrue({'macs', 'hmmratac'}.issubset(hidden_markov))
        ordinary = self.extract('ChIP peaks were called using MACS3 v3.0.3 callpeak.')
        self.assertIn('macs', ordinary)
        self.assertNotIn('hmmratac', ordinary)

    def test_contact_maps_software_is_not_an_ordinary_word_match(self):
        self.assertIn('maps-contact', self.extract('PLAC-seq chromatin interactions were called using MAPS.'))
        self.assertNotIn('maps-contact', self.extract('We generated contact maps and regional association maps.'))

    def test_source_reported_homer_package_and_genome_viewer_names_are_retained(self):
        self.assertIn('homer', self.extract('Motif analysis and peak annotation used the Homer package v4.11.1.'))
        self.assertNotIn('homer', self.extract('We quantified Homer1 protein abundance and Homer gene expression.'))
        self.assertIn('igv', self.extract('Bigwig tracks were visualized in IGV_2.4.15.'))
        self.assertIn('igv', self.extract('Tracks were visualized with Integrative Genomics Viewer.'))
        self.assertNotIn('igv', self.extract('Immunoglobulin IGV domain abundance was measured.'))
        self.assertIn('nrsa', self.extract('The NRSA pipeline determined PRO-seq gene body changes.'))
        self.assertNotIn('nrsa', self.extract('An NRSA award supported the investigators.', 'Acknowledgments'))

    def test_available_method_lists_do_not_become_claims_of_execution(self):
        listed = 'Many highly optimized tools are available for this task, including PLINK, GCTA, BOLT-LMM, SAIGE and REGENIE.'
        self.assertFalse(self.extract(listed, 'Results / Genome-wide analyses') & {'plink','gcta','bolt-lmm','saige','regenie'})
        actual = self.extract(listed + ' We ran REGENIE for association testing and used Hail to calculate LD.', 'Methods / Genome-wide analyses')
        self.assertIn('regenie', actual)
        self.assertIn('hail', actual)
        self.assertNotIn('gcta', actual)

    def test_liftover_wrappers_and_chain_downloads_are_not_direct_ucsc_execution(self):
        for text, actual in [
            ('GWAS coordinates were lifted to GRCh37 using liftover within MungeSumstats.', 'mungesumstats'),
            ('LiftOver (rtracklayer) ensured coordinate datasets matched hg38.', 'rtracklayer'),
            ('BLUEPRINT coordinates were converted with the R package liftOver v1.26.0.', 'liftover-r-workflow'),
            ('The chain file for the CrossMap liftover tool is available at https://hgdownload.soe.ucsc.edu/goldenPath/hg38/liftOver/.', 'crossmap')]:
            found=self.extract(text);self.assertIn(actual,found);self.assertNotIn('ucsc-liftover',found)
        self.assertIn('ucsc-liftover',self.extract('We used CrossMap and UCSC liftOver as separate coordinate conversion programs.'))

    def test_prelifted_annotations_prior_eligibility_and_custom_mapping_do_not_become_ucsc_runs(self):
        for text in [
            'RNA reads were quantified against GENCODE v36 release liftover to hg19 by RSEM.',
            'Array variants were selected previously for ability to liftover to GRCh38 before imputation.',
            'We attempted a two-step liftover using local and global pairwise alignments.']:
            self.assertNotIn('ucsc-liftover',self.extract(text))
        self.assertIn('ucsc-liftover',self.extract('We used UCSC liftOver for a two-step liftover between the genome builds.'))
        self.assertIn('ucsc-liftover',self.extract('We used GENCODE v36 release liftover to hg19. We ran UCSC liftOver on the peak coordinates.'))

    def test_epistasis_and_prediction_programs_do_not_match_chemical_or_build_homonyms(self):
        self.assertTrue({'fame','sme'}.issubset(self.extract('We compared SME and FAME for marginal epistasis using stochastic trace estimates.')))
        self.assertNotIn('fame',self.extract('Fatty acid methyl esters (FAME) were analyzed using gas chromatography.'))
        self.assertNotIn('pumice',self.extract('PUMICE stones were used to polish the sample.'))
        self.assertIn('pumice',self.extract('We ran PUMICE transcriptome prediction with functional annotations.'))
        self.assertNotIn('re2c',self.extract('The build used RE2C for lexical analysis.'))
        self.assertIn('re2c',self.extract('For overlapping GWAS cohorts we used RE2C meta-analysis.'))

    def test_harmoniser_spellings_and_missing_reviewed_extraction_are_guarded(self):
        for name in ['gwas-sumstats-harmoniser','gwas-sumstats-harmonizer']:
            self.assertIn('gwas-sumstats-harmoniser',self.extract('We followed the '+name+' procedure for reference-strand consensus.'))
        paper={'pmcid':'PMC_TEST_ABSENT','doi':'10.0/test','tools':[]}
        review={'pmcid':'PMC_TEST_ABSENT','doi':'10.0/test','tool_id':'regenie'}
        with self.assertRaisesRegex(ValueError,'Reviewed tools are absent.*regenie'):
            publication_record(paper,[review])

    def test_method_parameters_need_a_checked_named_anchor_and_shared_specific_method(self):
        source = [{"paragraph_index":1,"section":"Methods","text":"SBAT combines burden scores into a gene test."},
                  {"paragraph_index":2,"section":"Methods","text":"We implemented SBAT in REGENIE."}]
        def pointer(index):
            p=source[index-1];return {"paragraph_index":index,"section":p['section'],"paragraph_sha256":hashlib.sha256(p['text'].encode()).hexdigest()}
        extra={**pointer(1),"evidence_role":"method-details","named_anchor":pointer(2),"shared_terms":["SBAT"],"relation_note":"SBAT parameters continue the method explicitly implemented in REGENIE in the named anchor."}
        review={"tool_id":"regenie","supporting_evidence":[extra]};registry={t['id']:t for t in REGISTRY}
        validate_supporting_evidence(source,[review],registry)
        for changed in [{**extra,"named_anchor":{**pointer(2),"paragraph_sha256":"stale"}},
                        {**extra,"shared_terms":["SKAT"]},{**extra,"shared_terms":["test"]},
                        {**extra,"evidence_role":"unreviewed"},{**extra,"named_anchor":pointer(1)}]:
            with self.assertRaises(ValueError):validate_supporting_evidence(source,[{"tool_id":"regenie","supporting_evidence":[changed]}],registry)
        with self.assertRaisesRegex(ValueError,"does not name"):
            validate_supporting_evidence(source,[{"tool_id":"regenie","supporting_evidence":[pointer(1)]}],registry)

    def test_wet_lab_and_ordinary_word_homonyms_are_excluded(self):
        text = ("Cells were cultured in Dulbecco's modified Eagle medium and isolated on a FACSAria Fusion. "
                "A Hamilton Microlab STAR prepared samples, and Tree Star FlowJo analysed the measurements. "
                "We used MACS magnetic beads, a great improvement described in peer-reviewed publications. "
                "The In-Fusion cloning kit was used to produce fusion proteins. Heavy metal levels were measured.")
        self.assertFalse(self.extract(text) & {"eagle", "fusion", "star", "macs", "great", "peer", "metal"})

    def test_software_usage_is_retained_with_biological_context(self):
        text = ("Genotypes were phased with Eagle v2.4.1. RNA-seq reads were aligned using STAR v2.7.11b. "
                "Chromatin peaks were called using MACS2. We ran FUSION for transcriptome-wide association. "
                "PEER factors adjusted expression, GREAT annotated peaks and METAL combined GWAS results.")
        self.assertTrue({"eagle", "star", "macs", "fusion", "peer", "great", "metal"}.issubset(self.extract(text)))

    def test_reference_list_and_intro_mentions_do_not_become_methods_evidence(self):
        root = ET.fromstring("<article><body><sec><title>Methods</title><p>We aligned RNA reads using STAR and quantified genes using featureCounts.</p></sec></body><back><ref-list><ref><p>PLINK is a tool cited only in the bibliography of this article.</p></ref></ref-list></back></article>")
        matches = {t["tool_id"] for t in tool_matches(paragraphs(root), REGISTRY)}
        self.assertIn("star", matches); self.assertNotIn("plink", matches)
        self.assertFalse(self.extract("Many studies used PLINK for genotype analysis in previous research.", "Introduction"))

    def test_plink_release_family_is_not_double_counted(self):
        self.assertEqual({"plink2"}, self.extract("We used PLINK 2.0 to perform genotype quality control and association testing."))

    def test_jats_citations_do_not_hide_software_or_change_primary_evidence(self):
        root = ET.fromstring('<article><body><sec><title>Methods</title><p>RNA reads were aligned with STAR<xref ref-type="bibr">17</xref> version 2.7.11b and offsets estimated with riboWaltz<xref ref-type="bibr">77</xref>.</p></sec></body></article>')
        ps = paragraphs(root)
        self.assertIn("STAR17", ps[0]["text"])
        self.assertIn("STAR version 2.7.11b", ps[0]["match_text"])
        matches = {t["tool_id"]: t for t in tool_matches(ps, REGISTRY)}
        self.assertTrue({"star", "ribowaltz"}.issubset(matches))
        self.assertEqual("2.7.11b", matches["star"]["version_reported_near_name"])
        self.assertEqual(ps[0]["text"], matches["star"]["evidence_text"])

    def test_bowtie_release_one_is_not_conflated_with_bowtie_two(self):
        self.assertEqual({"bowtie1"}, self.extract("Ribosomal RNA reads were removed using Bowtie v.1.3.1."))
        self.assertEqual({"bowtie2"}, self.extract("We aligned reads to the genome using Bowtie 2 v2.5.1."))

    def test_star_gene_quantification_is_not_lost_after_citation_removal(self):
        self.assertIn("star", self.extract("Preprocessing and gene-level quantification using STAR (v.2.7.10) was performed with the data generators.", "Methods / SMART-seq integration"))

    def test_starsolo_is_a_distinct_barcode_umi_workflow_from_bulk_star(self):
        self.assertEqual({'starsolo'},self.extract('Single-cell RNA reads were aligned and UMIs counted with STARsolo v2.7.10a.'))
        self.assertEqual({'star'},self.extract('Bulk RNA reads were aligned using STAR v2.7.11b.'))
        self.assertTrue({'starsolo','wasp'}.issubset(self.extract('STARsolo used --waspOutputMode for WASP filtering with the sample VCF.')))

    def test_later_valid_software_use_is_not_hidden_by_an_earlier_homonym(self):
        text = ("Cells were grown in Eagle medium. Genotypes were phased using Eagle v2.4.1. "
                "A Tree Star cytometer recorded cells. RNA reads were aligned using STAR v2.7.11b. "
                "A FACSAria Fusion sorted cells. We ran FUSION for transcriptome-wide association analysis.")
        found = {t["tool_id"]: t for t in tool_matches([{"section": "Methods / Analysis", "text": text, "paragraph_index": 1}], REGISTRY)}
        self.assertTrue({"eagle", "star", "fusion"}.issubset(found))
        self.assertEqual("2.4.1", found["eagle"]["version_reported_near_name"])
        self.assertIn("aligned", found["star"]["evidence_sentence"])

    def test_individually_reviewed_paragraph_is_not_silently_repointed(self):
        first = {"section": "Methods", "text": "We aligned RNA reads using STAR to the supplied genome reference.", "paragraph_index": 1}
        reviewed = {"section": "Methods", "text": "We aligned RNA reads using STAR with the reviewed transcript reference.", "paragraph_index": 2}
        preferred = {"star": hashlib.sha256(reviewed["text"].encode()).hexdigest()}
        found = tool_matches([first, reviewed], REGISTRY, preferred)
        star = next(t for t in found if t["tool_id"] == "star")
        self.assertEqual(2, star["evidence_paragraph_index"])
        self.assertEqual("individually-reviewed-paragraph", star["evidence_selection"])

    def test_empty_bibliographic_parentheses_do_not_hide_reported_release(self):
        root = ET.fromstring('<article><body><sec><title>Methods</title><p>We aligned reads with bowtie2 (ref. <xref ref-type="bibr">42</xref>), v.2.3.</p></sec></body></article>')
        match = next(t for t in tool_matches(paragraphs(root), REGISTRY) if t["tool_id"] == "bowtie2")
        self.assertEqual("2.3", match["version_reported_near_name"])

    def test_scenic_plus_is_a_distinct_tool_family(self):
        self.assertEqual({"scenicplus"}, self.extract("We used the SCENIC+ motif database for regulatory analysis."))
        self.assertEqual({"scenic"}, self.extract("We ran pySCENIC for regulatory network inference."))

    def test_reviewed_usage_requires_matching_primary_source_evidence(self):
        text = "Synthetic evidence fixture for source identity validation."
        paper = {"pmcid": "PMC-test", "doi": "10.0/test", "fulltext_url": "https://example.org/test", "license": [],
                 "tools": [{"tool_id": "tsne", "analysis_role": "Embedding", "evidence_text": text,
                            "evidence_sentence": text, "evidence_section": "Methods"}]}
        review = {"pmcid": "PMC-test", "doi": "10.0/test", "tool_id": "tsne",
                  "evidence_paragraph_sha256": hashlib.sha256(text.encode()).hexdigest(),
                  "use_summary": "Reviewed synthetic use.", "implementation_scope_note": "Prepared inputs required."}
        published = publication_record(paper, [review])["tools"][0]
        self.assertEqual("Reviewed synthetic use.", published["use_summary"])
        self.assertIn("usage-summary-reviewed", published["review_status"])
        self.assertNotIn("evidence_text", published)
        for key in ("doi", "evidence_paragraph_sha256"):
            with self.assertRaisesRegex(ValueError, "stale/mismatched"):
                publication_record(paper, [{**review, key: "mismatch"}])

    def test_duplicate_usage_reviews_are_rejected(self):
        review = {"pmcid": "PMC-test", "tool_id": "tsne"}
        with self.assertRaisesRegex(ValueError, "Duplicate reviewed"):
            publication_record({"tools": []}, [review, review])

    def test_rsubread_spelling_and_standalone_subread_are_distinct(self):
        found=self.extract('We quantified gene expression using the featureCounts function of the R subRead package.')
        self.assertTrue({'rsubread','featurecounts'}.issubset(found));self.assertNotIn('subread-suite',found)
        self.assertIn('subread-suite',self.extract('We aligned RNA reads using Subread 2.0.1.'))
        self.assertNotIn('subread-suite',self.extract('Each PacBio subread was retained after filtering.'))

    def test_guppy_basecaller_is_not_the_fish_name(self):
        self.assertNotIn('guppy',self.extract('The guppy genome was sequenced using paired reads.'))
        self.assertIn('guppy',self.extract('Basecalling was performed with Guppy version 5.0.1.'))

    def test_gene_set_mixer_and_magma_wrapper_are_distinct_named_methods(self):
        found=self.extract('We used GSA-MiXeR v2.1.1 for gene-set heritability enrichment.')
        self.assertIn('gsa-mixer',found);self.assertNotIn('mixer',found)
        self.assertIn('mixer',self.extract('Univariate and bivariate MiXeR analyses estimated polygenicity and overlap.'))
        self.assertIn('magma-celltyping',self.extract('Gene-set enrichment used the MAGMA.Celltyping R package.'))

    def test_flash_merging_is_distinct_from_flashpca(self):
        found=self.extract('We merged paired reads using FLASH with minimum overlap10 and maximum mismatch density0.25.')
        self.assertIn('flash-merge',found);self.assertNotIn('flashpca',found)
        found=self.extract('We calculated principal components using FlashPCA.')
        self.assertIn('flashpca',found);self.assertNotIn('flash-merge',found)
        self.assertNotIn('flash-merge',self.extract('Fluorescence was monitored with a Varioskan FLASH plate reader and SkanIt Software.'))

    def test_deeptools_generation_two_and_library_api_have_distinct_names(self):
        self.assertIn('deeptools',self.extract('We used DeepTools2 computeMatrix reference-point with10bp bins.'))
        found=self.extract('We used the stats function of the deeptools/pyBigWig package for mean accessibility.')
        self.assertIn('pybigwig',found)
        self.assertIn('ucsc-bigwigmerge',self.extract('UCSC bigWigMerge merged normalized coverage tracks.'))
        self.assertIn('qnorm',self.extract('We used qnorm for quantile normalization across cell subclasses.'))
        self.assertNotIn('qnorm',self.extract('We applied inverse rank normalization with qnorm((rank(gene)-0.5)/n).'))


if __name__ == "__main__":
    unittest.main()
