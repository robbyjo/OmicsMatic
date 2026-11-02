#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record reviewed paper roles without changing historical drafts or the catalog."""
import gzip, hashlib, json, sys, xml.etree.ElementTree as ET
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from collect_omics_review import paragraphs
from mashr_original_runtime import pin, write
ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'build/literature-review/mashr-runtime/composite-development-1'
DEST = ROOT / 'docs/literature/mashr-composite'
SELECT = {'P019':[51], 'P021':[94,95,97,98,99,101], 'P024':[84,119], 'P073':[42],
          'P092':[55,56,57], 'P104':[12,47,48,49,50,67], 'P201':[15,27], 'P213':[12]}
REVIEW = {
 'P019': dict(role='Class-level S-snTWAS shrinkage uses Z proxies with SE1, missing Z0/SE1e10, data-driven covariances, and excludes EUR Endo/mural. Specificity counts LFSR<=0.05 conditions and retains counts from one through five among six conditions.',
             implemented=['explicit supplied/canonical or original-source data-driven covariance preparation', 'native posterior fit/prediction', 'explicit inclusive significance and counts'],
             unresolved=['Actual input matrices, excluded-population membership, random seed and unreported covariance/optimizer settings must be supplied. Z proxies are not original effect magnitudes.']),
 'P021': dict(role='mashr0.2.79 with adapted dreamlet run_mash shrinks age-associated effects across nine EN, seven IN and four glial subclasses. Group scores multiply 1-LFSR, require nominal P<0.05 in at least two group members, and select scores>0.9. Tau expression specificity and enrichment are separate downstream analyses.',
             implemented=['native posterior fit/prediction', 'explicit named group marginal sign products', 'separate nominal-P gate and strict score cutoff'],
             unresolved=['Do not infer nominal P from posterior LFSR. Actual group/age matrices, fit choices and the adapted run_mash edition must be supplied. Products do not certify equal directions or joint nonzero probability.']),
 'P024': dict(role='MASH integrates gene/cell-type effects across disease contrasts. Paragraph84 cites P104 via reference168 for composite nonzero-effect testing; it reports all eight disease contrasts and shared scores>=0.05. The inspected downstream author script separately removes shared genes at scores>=0.01 before correlations.',
             implemented=['native fit/prediction', 'explicit independent-marginal composite ALL mode and inclusive cutoff'],
             unresolved=['Exact disease-contrast construction, composite-producing CSV and generating script are not established by the inspected public caller. Do not reconcile the separate >=0.05 article and >=0.01 downstream exclusion stages or change them to 0.95.', 'Current dreamlet source is a pinned reference, not a proven paper binary edition.']),
 'P073': dict(role='mashr0.2.79 learns empirical correlation from 20,000 random cis-sQTL tests and data-driven covariances from strong minimum-P tests per gene, then predicts posterior means and LFSR for the strong targets.',
             implemented=['separate representative fit/control, strong covariance and posterior target populations', 'original-source PCA/ED and native posterior model'],
             unresolved=['The article describes nominal-P inputs without a complete signed-effect/SE conversion. Require signed effects and valid SE; never invent signs from unsigned P. Random subset, mixture fitting and other unreported settings remain explicit inputs.']),
 'P092': dict(role='The reviewed Supplementary Note specifies mashr0.2.79, effects Pi-0.5 and SE=abs(beta/sqrt(Chi-square)). Complete finite cell-type tests define fitting; strong rows have FDR<10% in any cell type and two million remaining pairs form random controls. Queries use the minimum-q SNP per peak. Missing query effects/SE are explicitly 0/1e-6. Downstream RASQUAL effect correlations and motif/Spearman/BH steps are separate.',
             implemented=['separate fit/control, strong covariance and target populations', 'explicit supplied missing effect/SE policy', 'native posterior means and LFSR'],
             unresolved=['Map beta to an explicit input field and admit finite positive SE; do not guess from unsigned P.', 'Preserve reported SE1e-6 rather than changing it to 1e6 or 1e10. Unreported PCA/ED, optimizer and seed choices require explicit settings.']),
 'P104': dict(role='mashr0.2.79 learns a prior from genes expressed in every cell type and empirical correlation from 600,000 random variant-trait pairs. Minimum-P targets use coefficient/SE, with missing coefficient0/SE1e6. The composite method sets p=1-LFSR and assumes independent marginal proxies: ALL=prod(p[include])*prod(1-p[exclude]); ANY=(1-prod(1-p[include]))*prod(1-p[exclude]). The article explicitly uses score>0.5 and reports a stricter 0.8 cutoff.',
             implemented=['original-source covariance preparation and native posterior fit/prediction', 'native ALL/AT_LEAST_ONE included conditions and excluded complements', 'source-rounded decisions with separate stable-log diagnostics'],
             unresolved=['Exact boundary at the reported 0.8 cutoff is unestablished by the inspected methods/caller; select a comparator explicitly.', 'The inspected caller uses exact name membership. Regex-looking subclass strings do not establish automatic regex expansion or full matrix lineage.', 'Paper dreamlet binary edition, full data and unreported fit choices remain unresolved. The marginal proxy is not joint posterior integration or a certified bound under arbitrary dependence.']),
 'P201': dict(role='Population sQTL sharing uses QTLTools effect/SE, a random40% set for null correlation and prior fitting, and a strong minimum-P SNP/intron-cluster set for covariance learning and prediction. Sharing requires the same sign and magnitudes within factor0.5; significance is LFSR<0.05. European MAF exclusions leave some variants untested.',
             implemented=['explicit training/strong/target populations', 'posterior-mean pairwise sharing, strict factor bounds and LFSR union selection'],
             unresolved=['Exact selected rows, seed, unreported covariance choices and denominator populations remain explicit inputs. Variants excluded by MAF are untested, not biological replication failures.']),
 'P213': dict(role='Cell-type meta-analysis uses Z scores derived from log2FC, canonical covariances and residual measurement-correlation EM. Untested log2FC is set to0. LFSR<0.05 and original cell-type |log2FC|>0.1 are separate gates; Seurat/MAST are upstream steps.',
             implemented=['canonical covariances and native conditional residual-correlation EM', 'native posterior and explicit LFSR cutoff'],
             unresolved=['A complete signed Z/SE conversion and untested-value SE are unreported; do not infer them. Keep the original-scale fold-change gate separate from Z/posterior scales. Native EM scope and optimizer departures remain as documented.'])
}


def author_file(path, url, lines, finding):
    return dict(url=url, **pin(path), reviewed_lines=lines, finding=finding,
                full_script_redistributed=False)


def main():
    assert __debug__, 'Do not disable evidence assertions'
    draft_path=ROOT/'docs/literature/MASHR_PAPER_ROLE_DRAFT.json'
    draft=json.loads(draft_path.read_text(encoding='utf-8'))
    sharing_path=ROOT/'docs/literature/mashr-sharing/method-audit.json'
    sharing=json.loads(sharing_path.read_text(encoding='utf-8'))
    roles=[]
    for old in draft['roles']:
        pid=old['paper_id']; path=ROOT/'build/literature-review/xml'/(old['pmcid']+'.xml.gz')
        assert pin(path)==old['source_xml_pin'], pid
        raw=gzip.decompress(path.read_bytes()); root=ET.fromstring(raw)
        ps={p['paragraph_index']:p for p in paragraphs(root)}
        for prior in old['evidence']:
            assert hashlib.sha256(ps[prior['paragraph_index']]['text'].encode()).hexdigest()==prior['text_sha256'], pid
        evidence=[dict(paragraph_index=n,section=ps[n]['section'],text_sha256=hashlib.sha256(ps[n]['text'].encode()).hexdigest()) for n in SELECT[pid]]
        role=dict(paper_id=pid,doi=old['doi'],pmcid=old['pmcid'],article_url='https://pmc.ncbi.nlm.nih.gov/articles/'+old['pmcid']+'/',
                  title=' '.join(''.join(root.find('.//article-title').itertext()).split()),
                  source_gzip=pin(path),source_xml=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()),
                  evidence=evidence,reported_mashr_version='0.2.79' if pid=='P092' else old['reported_version'],
                  status='methods-reviewed-scoped-implementation-with-explicit-unreported-inputs',
                  **REVIEW[pid],paper_reproduced=False,catalog_promoted=False)
        if pid=='P024':
            ref=root.find('.//ref[@id="CR168"]'); assert ref is not None
            text=' '.join(''.join(ref.itertext()).split()); assert '10.1038/s41588-026-02733-5' in text
            assert any(x.get('rid')=='CR168' for x in root.iter('xref'))
            role['companion_reference']=dict(reference_id='CR168',paper_id='P104',doi='10.1038/s41588-026-02733-5',text_sha256=hashlib.sha256(text.encode()).hexdigest())
        if pid=='P092':role['supplement']=sharing['P092_supplement']
        roles.append(role)
    nps=RUN/'source-intake-1'; psych=RUN/'P024-source-intake-1'
    nps_commit='71323f6bb1354888f4998bbd72b515d8253bd5f8'; psych_commit='168c2435f9ed026b2fd38ad34681bca41853d884'
    authors=[author_file(nps/'analysis/freeze2/eQTL/mashr_class.R','https://github.com/DiseaseNeuroGenomics/nps_ad/blob/'+nps_commit+'/analysis/freeze2/eQTL/mashr_class.R',[108,113,140,156,164],'The caller supplies 1-LFSR, explicit include and setdiff exclusion to the internal dreamlet ALL helper; >0.5 is literal.'),
             author_file(nps/'analysis/freeze2/eQTL/mashr_subclass.R','https://github.com/DiseaseNeuroGenomics/nps_ad/blob/'+nps_commit+'/analysis/freeze2/eQTL/mashr_subclass.R',[103,115],'Some included strings resemble regexes; the pinned helper requires exact column names. No automatic expansion is inferred.'),
             author_file(psych/'cross-disorder-dreamlet.R','https://github.com/DiseaseNeuroGenomics/PsychADxD/blob/'+psych_commit+'/5_cross-disorder_expression/PsychAD_cross-disorder_dreamlet.r',[60,62],'A precomputed PsychAD_mashr_shared.csv is read and scores>=0.01 select genes removed before downstream cross-disorder correlations. This is not the generating composite calculation.')]
    reference=json.loads((nps/'dreamlet-receipt.json').read_text())
    assert reference['commit']=='6ddcb83fb060eae989e541b6b82b46b3eaebd7bb'
    assert pin(nps/'dreamlet/R/compositePosteriorTest.R')=={k:reference['files']['R/compositePosteriorTest.R'][k] for k in ['bytes','sha256']}
    audit=dict(schema_version=1,status='eight-paper-mashr-methods-reviewed-composite-formula-resolved-scoped-development',
        role_count=len(roles),roles=roles,author_code=dict(nps_ad_commit=nps_commit,PsychADxD_commit=psych_commit,files=authors),
        original_composite_reference=dict(**reference,version='1.9.3',license='Artistic-2.0',source_function_executed_unchanged=True,
            full_dreamlet_package_installed_or_validated=False,paper_binary_edition_identified=False,
            semantics='Exact column membership; repeated/overlapping selectors retained; NA/NaN probabilities become0; empty ALL/ANY inclusion factors1/0; unlisted columns ignored.',
            native_domain_restriction='Native API admits only probabilities in [0,1] or explicitly permitted NaN; original utility itself does not range-validate.'),
        historical_inputs={str(p.relative_to(ROOT).as_posix()):pin(p) for p in [draft_path,sharing_path]},
        claims=dict(method_formula_resolved=True,all_eight_method_roles_reviewed=True,paper_reproduction_complete=False,
                    paper_binary_editions_identified=False,study_route_available=False,catalog_promoted=False),
        required_action='Supply the identified paper-specific matrices, selectors, scales, nominal P and unreported fit choices explicitly. Inspect source/log numerical diagnostics. Do not infer signs, biological absence, joint posterior probability, missing binary editions or whole-paper reproduction from a scoped tool result.')
    assert len(roles)==8
    DEST.mkdir(exist_ok=True)
    attrs=DEST/'.gitattributes'; assert not attrs.exists();attrs.write_text('* -text\n',encoding='utf-8',newline='\n')
    assert not (DEST/'role-audit.json').exists();write(DEST/'role-audit.json',audit)
    print(json.dumps(dict(status=audit['status'],papers=len(roles),catalog_promoted=False)))


if __name__=='__main__':
    try:main()
    except Exception as failure:
        print('FAIL2_MASHR_ROLE_AUDIT: '+str(failure)+'\nRequired action: retain historical records, inspect source/paragraph mismatch and rerun to a new audit version.',file=sys.stderr);sys.exit(2)
