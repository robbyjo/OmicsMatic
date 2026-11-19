# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-bound coloc role census; no catalog mutation, execution or family credit."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
BASE='2d820447e7e4b4aab414f3185916e83d5ea76a5c'
DATA='docs/literature/omics-2023-2026'
# Indices are the existing collector's one-based body paragraphs. Adjacent
# paragraphs without a tool name are deliberately included when they define
# thresholds, priors, cohorts or post-processing.
DECISIONS={
 'P073':([17,20,23,24,46],'abf','direct',[(46,'5.2.3')],
  'Used coloc.abf with GWAS/sQTL beta estimates, at least ten variants and default priors; used sQTL MAFs to estimate quantitative-trait scale. Applied H4>0.75, including cis/trans and dynamic sQTL comparisons.',
  'Keep each comparison and sample-size/scale declaration explicit. Regional H4 differs from a conditional SNP posterior; LocusCompare visualization and S-LDSC are separate.'),
 'P074':([45,47],'unspecified','direct',[(45,'3.2.1'),(47,'5.1.0')],
  'Used COLOC for aortic-valve eQTL/AS TWAS candidates around gene boundaries and separately for GTEx extravalvular eQTL/GWAS regions around lead variants; both used PP4>0.75.',
  'The valve stage names 3.2.1 and the GTEx stage 5.1.0; code availability repeats 3.2.1. Preserve the stage-specific editions, distinct window centers and unreported function/priors.'),
 'P088':([32,59],'unspecified','direct',[(32,'4.0.4')],
  'Applied coloc after significant MR results for heart/whole-blood eQTLs and UKB protein pQTLs with DCM/MTAG-DCM. Set p1=p2=1e-4 and p12=1e-6; PP4>0.5 was some evidence and >0.7 strong evidence.',
  'MR instrument clumping and Bonferroni screens are preceding steps, not coloc SNP filters. Preserve the custom strict shared prior, two thresholds, source datasets and unreported function/input details.'),
 'P090':([29],'multi-causal-unspecified','direct',[(29,'5.2.3')],
  'Used coloc allowing multiple causal variants to compare DCM GWAS and GTEx eQTLs for genes at DCM loci, choosing tissues with the lowest TWAS P value.',
  'The multi-causal entry point, LD, priors and signal-fitting settings are not named here. Do not infer an exact coloc.susie recipe solely from allowing multiple variants.'),
 'P097':([21,35,41],'unspecified','direct',[],
  'Compared Tarkin/QMDiab shared regional variants within +/-1 Mb for replication, requiring H4 to be the most likely hypothesis plus a joint association threshold and direction concordance. Also formed peptide-detection-weighted coloc-MSPA fractions for H3/H4 against UKB-PPP.',
  'H4 argmax is not a fixed PP4 cutoff. Preserve the separate datasets and joint-P criterion. The weighted coloc-MSPA aggregation is custom post-processing, not coloc prior weighting.'),
 'P102':([49],'unspecified','direct',[(49,'5.1.0')],
  'Compared overlapping SV-eQTL and agronomic SV-GWAS loci with COLOC defaults, using regional H4>0.5 and a shared-variant posterior threshold >0.5.',
  'Keep regional and variant conditional probabilities distinct. Nonhuman structural-variant association inputs, exact function and phenotype-scale choices are not established by a human SNP workflow.'),
 'P104':([46],'unspecified','direct',[],
  'Used coloc on overlapping molecular-QTL/GWAS meta-analysis regions around gene bodies without a P-value filter; normalized trait variance was set to one, other settings default, and PP4>=0.8 denoted colocalization.',
  'Do not introduce significance filtering or infer a version/function. The separate three-signal moloc analysis is outside two-trait coloc.'),
 'P105':([54],'single-and-susie','direct',[(54,'5.1.0')],
  'Applied single-causal-variant coloc at significant EAS loci and applied coloc to SuSiE signals at loci with multiple conditionally independent signals.',
  'Keep the single-causal and SuSiE branches distinct. Exact entry points, priors, LD and conditional/SuSiE settings are not supplied in this paragraph.'),
 'P120':([28],'hosted-unspecified','FUSION',[],
  'Used FUSION TWAS and its internal COLOC method with selected GTEx tissue weights to link hypothyroidism risk to expression; reported regional PP4>0.75 for candidate gene mapping.',
  'This is a FUSION-hosted method, not proof of a standalone coloc invocation. The eQTL/TWAS screen is not an automatic SNP-level coloc filter; version/function/priors remain unspecified.'),
 'P130':([57,58,59],'abf','direct',[(58,'5.2.2')],
  'Used coloc.abf for neuropathology loci and significant GTEx/ROSMAP QTLs, with ordinal case fractions chosen by dichotomization, shared prior 1e-5 and posterior >80%. Also compared selected concordant NPE signals and excluded APOE for that comparison.',
  'Preserve region-selection versus within-region coverage, source screening and case definitions. The source uses PrC for prior and posterior wording; do not conflate the prior 1e-5 with the posterior threshold.'),
 'P131':([16,45],'hosted-unspecified','FUSION',[],
  'Used FUSION colocalization for second-order disease-MDD GWAS factors with the same GTEx tissue weights, then integrated PP4>0.80 hits with T-SEM and normalized tissue gene proportions.',
  'FUSION hosts this analysis; standalone coloc version/function is unreported. T-SEM/QGENE filtering, ACAT and normalized gene proportions are separate operations.'),
 'P132':([27,60],'unspecified','direct',[],
  'Used COLOC in R to compare CH GWAS with expression QTLs; the TCL1A example compared the remaining eQTL signal after conditioning on rs78986913.',
  'The conditional association fit is separate from coloc. Exact regional inputs, software edition, function, priors and a general decision threshold are not specified here.'),
 'P157':([17,58],'abf-and-susie-benchmark','direct',[],
  'Benchmarked single-causal coloc from P-value Bayes factors and SuSiE-coloc over pairs of pure mRNA/protein credible sets; the maximum pairwise H4 represented the gene.',
  'These are comparison methods, not the main CLPP analysis. The authors retained their strict purity filter and used minimum SuSiE/FINEMAP PIPs separately; do not replace that procedure with generic coloc defaults.'),
 'P170':([16,58,59,60],'abf','direct',[],
  'Used coloc.abf defaults for perturbation-dependent reQTL/GWAS comparisons, H4>0.5, conditional variant probabilities and 95% credible sets; reran QTL/coloc analyses within perturbation-score tertiles.',
  'FastQTL eGene selection, pseudobulk/tertile construction, credible-set summarization and Fisher enrichment are separate. Results and Methods differ on the nonimmune-trait count; no count is silently repaired.'),
 'P181':([15],'unspecified','direct',[],
  'Applied tests from the coloc R package to significant FUSION multimodal-TWAS hits across traits, using COLOC.PP4>0.8.',
  'TWAS construction/screens are preceding operations. The coloc function, version, regional variant coverage, priors and scale inputs are not specified here.'),
 'P183':([19,26,27,31,32],'abf','direct',[],
  'Used coloc.abf with p1=p2=1e-4 and p12=1e-5 to integrate eQTL/GWAS data and compare lncRNA/circRNA with neighboring PCG eQTL/sQTL signals in shared regions.',
  'Methods use PP4>0.8 for GWAS integration; results also describe PP3/PP4>=0.8 for RNA pairs. Preserve these distinct decisions. SMR/HEIDI, OPERA, FUSION and eCAVIAR are separate.'),
 'P185':([15,16],'abf','direct',[(15,'5.1.0')],
  'Used coloc.abf separately for OA GWAS phenotypes and cartilage mQTLs within +/-500 kb, retaining all mQTL test results before colocalization and using shared variants. Applied H4>0.8, then retained genotype-present significant mQTL results.',
  'The later mQTL FDR filter is post-processing, not the input P-value cutoff. Preserve GWAS-phenotype/probe pairing and distinguish regional H4 from SNP-level evidence.'),
 'P189':([11],'abf','direct',[],
  'Used coloc.abf for protein pQTL/PCOS GWAS regions within +/-500 kb, with p1=p2=1e-4 and p12=1e-5 and H4>0.8. With multiple pQTLs, reported the strongest colocalization per protein.',
  'Retain per-pQTL tests before the strongest-hit summary. Function priors are explicit but version, scale and complete input construction are not.'),
 'P190':([56],'unspecified','unspecified',[],
  'Reported COLOC comparisons of MAJIQTL- versus LeafCutter-derived sQTLs with AD/PD GWAS, using PPH4>0.5.',
  'This Results paragraph establishes reported COLOC analysis, but not its exact R entry point/host, version or settings. Detailed supplementary Note S7.1 is not reviewed here; no default ABF or SuSiE recipe is invented.'),
 'P193':([11],'unspecified','direct',[],
  'Used coloc with GWAS Catalog associations, locally generated linear-model irQTL summary statistics and GTEx v8 eQTLs, checking robustness using an independent eQTL dataset.',
  'Version, entry point, priors, region, threshold and scale inputs are not stated in this paragraph. The subsequent scipy transcription model is unrelated to coloc inference.'),
 'P201':([20,30],'unspecified','direct',[(20,'5.2.3')],
  'Compared significant sQTL loci with hematological GWAS traits using p1=p2=1e-4 and declared p12=1e-6, with common-causal posterior >0.8 and H4/H3>5.',
  'The source calls 1e-6 a default; retain that explicit value rather than replacing it with the selected current API default. Lead/locus selection and LD visualization are separate; the exact function is not named.'),
 'P203':([26],'unspecified','direct',[(26,'5.1.0')],
  'Selected independent significant GWAS lead variants, then used all variants in surrounding 100-kb regions for coloc with eQTLs, declaring PP4>0.8.',
  'Keep all within-region variants and the separate lead-selection step. The stated 10e-6 prior is attributed to eQTL association without an unambiguous parameter allocation; do not silently relabel it p12 or normalize author wording.'),
 'P212':([18],'unspecified','direct',[(18,'5.1.0.1')],
  'After a separate PLINK LD screen, ran coloc on eQTL/GWAS signal pairs with lead r2>=0.5, using all shared variants within 500 kb of the GWAS lead. Used PPH4>=0.7 and prior-sensitivity comparisons.',
  'Retain the literal edition 5.1.0.1 and separate LD preselection. Exact function and sensitivity grid are unreported; do not substitute a particular historical runtime or infer its prior choices.'),
 'P219':([33,34,77],'susie','direct',[],
  'Used SuSiE-coloc on paired skin meQTL/eQTL signals after allele/beta alignment. Calibrated shared priors using four scenarios and selected p12=0.0019; marginal association quantities are described as p1+p12=0.0026 and p2+p12=0.0122.',
  'Preserve the source marginal-versus-exclusive prior definitions; do not silently infer the exact p1/p2 arguments from rounded quantities. LD/fitting, calibration selection and mediation are separate; reported comparison uses posterior >0.8.'),
 'P234':([31,32,33],'susie','direct',[(31,'v.5')],
  'Used coloc-SuSiE to compare pcQTL, eQTL and GWAS credible sets within tissues; linked hits and constructed connected-component credible-set groups using reported PPH4>=0.75.',
  'The source also says greater than 0.75 in prose; retain the explicit >= expressions and note the wording difference. Edition v.5 is partial. PLINK LD estimation, graph grouping and cross-tissue variant-trait deduplication are separate.'),
 'P246':([10],'susie','direct',[],
  'Fine-mapped the CASR region and tested colocalization with 55 phenotypes using Coloc-SuSiE.',
  'Exact functions, version, LD, priors and thresholds are deferred to supplementary methods not reviewed here. SKAT-O and logistic-direction analyses are separate; this is not a complete author workflow.'),
 'P248':([57],'unspecified','direct',[(57,'5.2.3')],
  'Used COLOC to compare SV-eQTL and GWAS signals in a plant RIL study, using H4>0.50 for shared causal variants.',
  'Retain nonhuman structural-variant context. Exact coloc function and inputs are not stated. EMMAX association/TWAS, PLINK clustering and hotspot permutations are separate steps.'),
}


class MissingSource(Exception): pass

# Literal primary-source contexts retain version prefixes, adjacent references
# and attached prose exactly. Collector text joins XML citation tags, so a
# generic semantic-version boundary would reject e.g. ``v5.2.3.62`` (ref. 62)
# or ``5.2.2R package``. The complete XML identity is checked separately.
EDITION_CONTEXTS={
 ('P073',46):'COLOC R package57 v.5.2.3 (with default prior',
 ('P074',45):'using COLOC (v3.2.1)',
 ('P074',47):'using the COLOC (v5.1.0) package',
 ('P088',32):'R-package coloc (v.4.0.4)',
 ('P090',29):'R coloc v.5.2.3 (ref. 49)',
 ('P102',49):'COLOC (v.5.1.0)18',
 ('P105',54):'coloc package (v.5.1.0;',
 ('P130',58):'coloc 5.2.2R package39',
 ('P185',15):'coloc R package33 (v.5.1.0)',
 ('P201',20):'coloc v5.2.3.62 We tested',
 ('P203',26):'coloc R (v.5.1.0) package.31',
 ('P212',18):'coloc (v.5.1.0.1)9',
 ('P234',31):'coloc.SuSiE (v.5)18,23',
 ('P248',57):'COLOC (v5.2.3)108',
}
def digest(raw):return hashlib.sha256(raw).hexdigest()
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def require(value,message):
    if not value:raise ValueError(message)
def audit(cache):
    from collect_omics_review import paragraphs
    from catalog_fixed_cohort import verify_fixed_cohort
    names=[DATA+'/papers.json','tools/literature/reviewed-uses.json']
    before={}
    for n in names:
        try:before[n]=subprocess.check_output(['git','--no-replace-objects','show',BASE+':'+n],cwd=ROOT,stderr=subprocess.PIPE)
        except subprocess.CalledProcessError as e:raise MissingSource('Restore Git predecessor '+BASE+':'+n) from e
    papers=json.loads(before[names[0]]);reviews=json.loads(before[names[1]])['reviews']
    verify_fixed_cohort(ROOT,ROOT/DATA,papers)
    existing={p['paper_id'] for p in papers if any(t['tool_id']=='coloc' for t in p['tools'])}
    require(len(papers)==250 and len(reviews)==1413 and existing==set(DECISIONS),'Different source cohort/coloc population')
    prior={(r['pmcid'],r['tool_id']):r for r in reviews};identities=[];roles=[];named=[]
    for paper in papers:
        path=Path(cache)/(paper['pmcid']+'.xml.gz')
        if not path.is_file():raise MissingSource('Restore exact primary XML '+str(path))
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and digest(raw)==paper['xml_sha256'],'Changed primary XML '+paper['paper_id'])
        root=ET.fromstring(raw);dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==paper['doi'].lower(),'Primary DOI differs')
        identities.append({**{k:paper[k] for k in ['paper_id','pmcid','doi','xml_sha256']},'xml_bytes':len(raw)})
        ps=paragraphs(root);hits=[p for p in ps if re.search(r'\bcoloc\b',p.get('match_text',p['text']),re.I)]
        if hits:named.append(dict(paper_id=paper['paper_id'],paragraphs=[dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=digest(p['text'].encode())) for p in hits]))
        if paper['paper_id'] not in DECISIONS:continue
        indices,method,host,editions,summary,caution=DECISIONS[paper['paper_id']];by_index={p['paragraph_index']:p for p in ps}
        require(all(i in by_index for i in indices),'Selected paragraph missing')
        for i,literal in editions:
            context=EDITION_CONTEXTS[(paper['paper_id'],i)]
            require(literal in context and context in by_index[i]['text'],'Edition context differs: '+paper['paper_id'])
        old=prior.get((paper['pmcid'],'coloc'))
        roles.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],fulltext_url=paper['fulltext_url'],source_xml_sha256=paper['xml_sha256'],
            method_family=method,host=host,reported_editions=[dict(paragraph_index=i,literal=v,source_context=EDITION_CONTEXTS[(paper['paper_id'],i)]) for i,v in editions],use_summary=summary,required_caution=caution,
            anchors=[dict(paragraph_index=i,section=by_index[i]['section'],paragraph_sha256=digest(by_index[i]['text'].encode())) for i in indices],
            positive_use=True,direct_package_execution_established=host=='direct',comparative_benchmark=paper['paper_id']=='P157',
            existing_review_preserved=old is not None,previous_review_object_sha256=digest(encoded(old)) if old else None,
            author_runtime_executed=False,exact_author_settings_established=False,whole_paper_reproduced=False))
    require({r['paper_id'] for r in named}==set(DECISIONS) and sum(len(r['paragraphs']) for r in named)==51,'Named coloc context population differs')
    return dict(schema_version=1,status='PASS250_IDENTITIES_27_CURATED_COLOC_ROLES_SOURCE_REVIEW_ONLY',base_commit=BASE,
        primary_files=identities,named_body_hits=named,roles=roles,positive_roles=27,direct_package_roles=24,hosted_roles=2,host_unspecified_roles=1,
        existing_reviews_retained=1,potential_new_review_records=26,observations_added=[],canonical_metadata_written=False,
        scientific_programs_executed=False,new_family_credit=0,whole_goal_complete=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',type=Path,required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();owned=False
    try:
        require(__debug__,'Rerun without Python -O');args.out.mkdir(parents=True,exist_ok=False);owned=True;result=audit(args.cache);(args.out/'CENSUS.json').write_bytes(encoded(result));print(result['status'])
    except Exception as e:
        code=3 if isinstance(e,(MissingSource,FileNotFoundError,PermissionError)) else 2
        failure=dict(status='SKIP3_COLOC_SOURCE_AUDIT_SKIPPED' if code==3 else 'FAIL2_NO_SOURCE_CENSUS_ADMITTED',error=repr(e),required_action='Restore exact named permitted XML/Git prerequisites for SKIP3; retain changed evidence/errors for FAIL2, correct the cause and retry NEW output. No source edits, invented settings or historical hash refresh.')
        if owned:(args.out/'FAILURE.json').write_bytes(encoded(failure))
        print(failure,file=sys.stderr);raise SystemExit(code)
