# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-bound SuSiE census, including result reuse, missed uses and proper-name exclusions."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
BASE='6c70f8dc990652855875a56e713cff00d23d6507'
DATA='docs/literature/omics-2023-2026'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:SuSiE(?:[-_ ]?(?:RSS|R))?|susieR|susie_rss|susie_suff_stat|runsusie)(?![A-Za-z0-9_])',re.I)
# Anchors are the frozen collector's one-based body paragraphs. Code-availability
# duplicates are context, not additional executions or observations.
DECISIONS={
 'P073':([21],'fine-mapping-result-description','direct-unspecified',[],
  'Used SuSiE fine-mapping to identify the most likely causal variants for HNRNPLL cis-eQTL/PTPRC trans-sQTL signals in naive CD4+ and CD4+ TEM cells.',
  'This results paragraph supports an analysis use missed by extraction. Edition, input interface, LD, priors and settings are not specified; colocalization H4 is a separate quantity.'),
 'P090':([26],'functionally-informed-summary-fine-mapping','PolyFun',[(26,'0.11.92','SuSiE v.0.11.92')],
  'Used PolyFun 2020-11-14 priors with SuSiE 0.11.92, up to five causal variants and 95% credible sets, separately for DCM GWAS and MTAG loci; LD came from 10,000 UKB European participants.',
  'PolyFun prior estimation, reference construction, GWAS/MTAG locus populations, cross-analysis aggregation and annotation remain distinct from a supplied-prior standalone fit.'),
 'P092':([59],'harmonized-summary-fine-mapping','direct',[(59,'0.12.35','susieR (v0.12.35)')],
  'Fine-mapped Crohn disease summary statistics with susieR 0.12.35, a uniform prior and up to ten causal variants; compared UKBB and 1000 Genomes European LD references after mapgen harmonization and LDetect block selection.',
  'Mismatched variants and the chromosome-6 25–35 Mb region were excluded. Reference choice, harmonization and locus selection are author preparation, not inferred or repeated by the fit adapter.'),
 'P095':([19],'received-fine-mapped-eqtl-results','result-reuse',[],
  'Reused GTEx v8 SuSiE-fine-mapped eQTLs from Avsec et al. to benchmark Enformer effect direction; selected EBV-transformed lymphocyte variants with PIP greater than 0.9 and removed conflicting cis-gene directions.',
  'No new SuSiE fit is established here. The supplied results, strict PIP selection, direction exclusions, Enformer track and bootstrap evaluation are separate from model fitting.'),
 'P105':([50,54,11],'summary-fine-mapping-and-signal-colocalization','direct-and-coloc',[(50,'0.11.92','SuSiE (v.0.11.92')],
  'Used SuSiE 0.11.92 with default configurations and ten putative causal variants per locus, using LDstore dosage LD from 171,085 unrelated BBJ participants; used SuSiE signals in multi-signal coloc comparisons.',
  'Do not silently alter the reported KING threshold, LD preparation, locus boundaries or defaults. Signal colocalization, annotation and HP-stratified analyses require their own declared settings.'),
 'P108':([36,46],'univariate-predictive-regression','FUSION-and-isoTWAS',[],
  'Trained per-isoform and gene-level SuSiE predictive models alongside elastic-net and BLUP alternatives, selecting predictive models by cross-validated adjusted R-squared within the isoTWAS/FUSION framework.',
  'This is predictive regression, not only a summary fine-mapping task. Model selection, five-fold cross-validation, gene windows, isoform aggregation and the CV R-squared >=0.01 rule are not provided by one standalone fit.'),
 'P127':([26,27],'fine-mapped-variant-reuse-for-conditioning','result-reuse',[],
  'Used the highest conditional PIP variant from each SuSiE credible set as conditioning SNPs in REMETA/REGENIE comparisons, and generated REGENIE conditional summary statistics for a REMETA analysis.',
  'The named results paragraphs do not establish a standalone SuSiE runtime, edition, input interface or fitting settings. REMETA/REGENIE conditioning and buffer/LD-threshold comparisons remain separate.'),
 'P151':([55,17,22],'summary-fine-mapping-with-explicit-fallback','multiCOGS',[],
  'Applied susieR to summary-imputed GWAS blocks with minimum P <1e-6; passed SNP probabilities for detected 95% credible-set signals to multiCOGS. Other blocks or runs with no qualifying signals used a separate single-causal-variable approach.',
  'Preserve the reported fallback as a distinct author algorithm; no automatic substitute is implemented. Per-signal probabilities must not be silently replaced by marginal PIPs. Imputation and multiCOGS scoring are separate.'),
 'P153':([61,62],'received-fine-mapped-benchmark-results','result-reuse',[],
  'Reused eQTL Catalogue V7 and Finucane-laboratory UKB SuSiE results to benchmark enhancer/gene prediction; applied distinct >50% eQTL, >10% noncoding GWAS and >50% coding-variant PIP criteria.',
  'No new SuSiE fit is reported. Retain the different input resources, phenotype/trait populations, strict PIP cuts and 500-kb credible-set/gene linking rule.'),
 'P157':([43,58,61,63,17,71],'multi-tool-consensus-fine-mapping-and-colocalization','direct-and-coloc',[(43,'0.11.43','susieR v.0.11.43')],
  'Used susieR 0.11.43 with summary statistics and in-sample covariate-adjusted LD, five effects, and no minimum-P screening of genes. Reported the minimum FINEMAP/SuSiE PIP and used pure credible-set combinations for SuSiE-coloc comparisons.',
  'The exact 0.11.43 source is not established by adjacent CRAN editions. FINEMAP minimum-PIP consensus, purity filtering, GTEx result reuse, CLPP enrichment/permutations and conditional sample restrictions remain separate.'),
 'P168':([50],'sequential-component-count-fine-mapping','direct',[(50,'0.14.2','susieR (v.0.14.2)')],
  'Used susieR 0.14.2 on integrated GWAS/ExWAS regions, sequentially considering one through ten credible sets and retaining the largest approximately independent set of lead variants with maximum r-squared <0.25.',
  'Exonic/imputed variant selection, 690-participant LD construction, iterative choice of component count and corrected lead-significance filtering are explicit author preparation/post-processing, not one fixed-L fit.'),
 'P169':([39,6],'functionally-informed-summary-fine-mapping','PolyFun',[(39,'0.11.92','susieR27 v0.11.92')],
  'Fine-mapped CRC loci with PolyFun 1.4.1 and susieR 0.11.92 using annotation-based variant priors and Genomics England LD. Chose L from the number of independent variants, with L=2 for a single independent signal.',
  'Preserve the results/methods difference between cumulative PIP >0.95 and reaching 0.95, and the individual PIP >0.001 rule. These do not define a replacement for source credible sets; PolyFun and locus/reference preparation remain separate.'),
 'P181':([20,21],'hosted-individual-level-fine-mapping-and-pip-colocalization','tensorQTL-cis_susie',[],
  'Ran SuSiE via tensorQTL cis_susie for the strongest RNA phenotype per gene in each GTEx tissue, then used 95% credible-set PIPs for functional enrichment and eCAVIAR-style xQTL/FinnGen CLPP.',
  'A standalone susieR fit does not qualify tensorQTL hosting. Preserve chromosome/position/REF/ALT matching, shared-variant PIP products and CLPP >0.01; this CLPP is not coloc H4.'),
 'P186':([10],'gwas-locus-fine-mapping','direct',[(10,'0.12.35','SUSIE v.0.12.35')],
  'Fine-mapped genome-wide significant FinnGen female-cohort regions using SuSiE 0.12.35 and used good-quality credible sets to define independent loci after additive and recessive REGENIE analyses.',
  'The paragraph does not recover the good-quality rule, L, LD source or exact fit arguments. REGENIE models, case/control composition and recessive eligibility are not SuSiE defaults.'),
 'P200':([16,24],'ancestry-weighted-reference-summary-fine-mapping','direct',[],
  'Used SuSiE with L=10 and default prior variance for multi-biobank meta-analysis regions, ancestry/effective-sample-size-weighted reference LD and LD–Z concordance assessment; described 95% minimal credible sets.',
  'No edition is supplied. Preserve cross-biobank variant intersection, window definitions and the reported LD–Z concordance metric; do not substitute unweighted LD or infer exact s-diagnostic arguments.'),
 'P203':([31],'population-specific-eqtl-fine-mapping-result-description','direct-unspecified',[],
  'Applied SuSiE separately to European, East Asian and African American cis-eQTL results to identify credible SNP sets and candidate causal variants.',
  'This results paragraph supports a missed analysis use. The 5% empirical gene-level FDR belongs to preceding eQTL mapping; no SuSiE edition, fit interface, prior, L or credible-set coverage is specified.'),
 'P219':([33,58,77],'multi-signal-colocalization','coloc',[],
  'Used SuSiE-coloc for skin cis-eQTL/meQTL pairs after explicit allele/beta flipping, allowing multiple causal signals; reported p12=1.93e-3 and posterior shared-causal probability >0.8.',
  'Keep the 83,529 methods versus 83,530 results pair counts unresolved. SuSiE fitting, coloc H4, allele harmonization and later mediation are separate; no SuSiE package edition or fit arguments are provided.'),
 'P221':([13],'fine-mapping-informed-prediction-penalties','direct-and-glmnet',[],
  'Applied SuSiE to MESA multi-ancestry gene windows using in-sample LD, L=10 and minimum absolute credible-set correlation 0.5; used 1-PIP as elastic-net variant penalties for EN-FM prediction.',
  'The precise fit interface/edition and upstream phenotype/genotype preparation are not stated. The 1-PIP penalty, glmnet alpha=0.5 and predictive training are not performed by the SuSiE fit itself.'),
 'P234':([16,17,18,20,31,37],'individual-level-pcqtl-eqtl-and-null-fine-mapping','direct-and-coloc',[(16,'0.12.35','susieR v.0.12.35')],
  'Ran susieR 0.12.35 on individual-level genotype and PC/expression phenotypes with uniform priors, L=10 and minimum absolute correlation 0.5, without prefiltering phenotype significance; repeated the workflow after genotype shuffling and used coloc.SuSiE for colocalization.',
  'Preserve the PC versus single-gene phenotype populations and genotype/phenotype matching. Calibration shuffles, phenotype-level FDR, coloc v5 hosting and GWAS/LD preparation remain separate.'),
 'P246':([10,32],'casr-fine-mapping-and-colocalization','direct-and-coloc',[],
  'Fine-mapped common CASR signals with SuSiE, reporting ten independent causal sets, and tested colocalization with 55 phenotypes using Coloc-SuSiE.',
  'Ten reported sets do not establish the supplied L argument. Full computational details are deferred to supplemental methods; no edition, exact inputs/LD, priors or fit/colocalization settings are established by these body paragraphs.'),
}
NONEXEC={'P212':([2],'Introductory comparison with GCTA-COJO/APEX; no execution of SuSiE is reported in this paragraph.')}
PROPER_NAMES={'P235':([5,16],'Susie is the name of a Sumatran orangutan whose assemblies/haplotypes are described, not the SuSiE statistical package.')}
EXTRA_CONTEXTS={'P157':[79],'P234':[50,58]}


class MissingSource(RuntimeError):pass


def require(value,message):
    if not value:raise ValueError(message)


def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def digest(raw):return hashlib.sha256(raw).hexdigest()


def source_view():
    result={}
    for name in [DATA+'/papers.json','tools/literature/reviewed-uses.json']:
        try:result[name]=subprocess.check_output(['git','--no-replace-objects','show',BASE+':'+name],cwd=ROOT)
        except (OSError,subprocess.CalledProcessError) as error:raise MissingSource('Restore exact Git baseline '+BASE+':'+name) from error
    return result


def audit(cache):
    from collect_omics_review import paragraphs
    from catalog_fixed_cohort import verify_fixed_cohort
    before=source_view();papers=json.loads(before[DATA+'/papers.json']);reviews=json.loads(before['tools/literature/reviewed-uses.json'])['reviews']
    verify_fixed_cohort(ROOT,ROOT/DATA,papers)
    require(len(papers)==250 and len(reviews)==1483,'Different frozen baseline population')
    existing={p['paper_id'] for p in papers if any(t['tool_id']=='susie' for t in p['tools'])}
    require(existing==(set(DECISIONS)-{'P073','P203'})|{'P235'},'Different original SuSiE membership')
    require(not any(r['tool_id']=='susie' for r in reviews),'Unexpected prior SuSiE reviews')
    identities=[];named=[];roles=[];nonexec=[];proper=[]
    for p in papers:
        path=Path(cache)/(p['pmcid']+'.xml.gz')
        if not path.is_file():raise MissingSource('Restore exact permitted primary XML '+str(path))
        with gzip.open(path,'rb') as f:raw=f.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and digest(raw)==p['xml_sha256'],'Changed primary XML: '+p['paper_id'])
        root=ET.fromstring(raw);dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==p['doi'].lower(),'Primary DOI differs')
        identities.append({**{k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256']},'xml_bytes':len(raw)})
        blocks=paragraphs(root);by_index={b['paragraph_index']:b for b in blocks}
        hits=[b for b in blocks if PATTERN.search(b.get('match_text',b['text']))]
        if hits:named.append(dict(paper_id=p['paper_id'],paragraphs=[dict(paragraph_index=b['paragraph_index'],section=b['section'],paragraph_sha256=digest(b['text'].encode())) for b in hits]))
        def anchors(indices):
            require(all(i in by_index and PATTERN.search(by_index[i].get('match_text',by_index[i]['text'])) for i in indices),'Named anchor differs: '+p['paper_id'])
            return [dict(paragraph_index=i,section=by_index[i]['section'],paragraph_sha256=digest(by_index[i]['text'].encode())) for i in indices]
        pid=p['paper_id']
        if pid in NONEXEC or pid in PROPER_NAMES:
            indices,reason=(NONEXEC if pid in NONEXEC else PROPER_NAMES)[pid]
            row=dict(paper_id=pid,pmcid=p['pmcid'],doi=p['doi'],reason=reason,anchors=anchors(indices),
                author_execution_established=False,existing_observation=(pid in existing))
            if pid in PROPER_NAMES:
                prior=[t for t in p['tools'] if t['tool_id']=='susie'];require(len(prior)==1,'Proper-name observation differs')
                row.update(existing_observation_object=prior[0],existing_observation_object_sha256=digest(encoded(prior[0])),
                    proposed_disposition='remove-unreviewed-proper-name-false-positive-with-exact-before-preservation')
            (nonexec if pid in NONEXEC else proper).append(row)
        if pid not in DECISIONS:continue
        indices,role,host,editions,summary,caution=DECISIONS[pid];reported=[]
        for index,literal,context in editions:
            require(literal in context and context in by_index[index]['text'],'Reported edition context differs: '+pid)
            reported.append(dict(paragraph_index=index,literal=literal,source_context=context,refers_to='susieR'))
        roles.append(dict(paper_id=pid,pmcid=p['pmcid'],doi=p['doi'],fulltext_url=p['fulltext_url'],role=role,
            declared_host=host,reported_editions=reported,use_summary=summary,required_caution=caution,anchors=anchors(indices),
            newly_found_observation=(pid in ['P073','P203']),existing_review_preserved=False,
            reported_fitting_or_hosted_analysis=(host!='result-reuse'),standalone_susieR_function_named=False,author_runtime_executed=False,
            historical_package_parity=False,exact_author_settings_established=False,whole_paper_reproduced=False))
    require({p['paper_id'] for p in named}==set(DECISIONS)|set(NONEXEC)|set(PROPER_NAMES),'Different named paper population')
    require(len(roles)==20 and len(nonexec)==1 and len(proper)==1,'Different source role population')
    return dict(schema_version=1,status='PASS250_IDENTITIES_20_SUSIE_ROLES_TWO_MISSED_USES_ONE_PROPER_NAME',base_commit=BASE,
        primary_files=identities,named_body_hits=named,roles=roles,nonexecution_contexts=nonexec,proper_name_exclusions=proper,
        positive_roles=20,named_papers=len(named),named_paragraphs=sum(len(p['paragraphs']) for p in named),
        existing_review_records=0,potential_new_review_records=20,new_observations=['P073','P203'],
        false_positive_observations_to_remove=['P235'],extra_contexts=EXTRA_CONTEXTS,
        canonical_metadata_written=False,scientific_programs_executed=False,implementation_qualified=False,
        new_family_credit=0,whole_goal_complete=False)


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Require Python3.11+ without -O')
        args.out.mkdir(parents=True,exist_ok=False);owned=True;result=audit(args.primary_cache)
        (args.out/'CENSUS.json').write_bytes(encoded(result));print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(MissingSource,FileNotFoundError,PermissionError)) else 2
        failure=dict(status='SKIP3_DEPENDENT_CENSUS_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_CENSUS',error=repr(error),
            required_action='Preserve original evidence. Restore the named exact permitted source, or correct the source decision and use NEW output. No canonical metadata or old hashes are changed.')
        if owned:(args.out/'FAILURE.json').write_bytes(encoded(failure))
        print(failure['status']+': '+str(error),file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
