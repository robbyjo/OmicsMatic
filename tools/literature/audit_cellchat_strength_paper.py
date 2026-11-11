# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the implemented P145 strength role to retained primary-source facts."""
import argparse,gzip,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    previous=json.loads((BASE/'primary-audit-1/COMPLETED.json').read_bytes());xml=ROOT/'build/literature-review/xml/PMC13553309.xml.gz';assert pin(xml)==previous['original_sources'][xml.relative_to(ROOT).as_posix()]
    root=ET.fromstring(gzip.decompress(xml.read_bytes()));paragraphs=[''.join(p.itertext()) for p in root.findall('.//p')];body=next(p for p in paragraphs if 'CellChat::netAnalysis_computeCentrality' in p and 'Wilcoxon rank-sum test' in p)
    normalized=re.sub(r'\s+',' ',body);assert all(t in normalized for t in ['v2.2.0','CellChatDB.human','min.samples = 3','min.cells = 10','>0.25','each sample separately'])
    code=next(e for e in root.iter() if e.tag in ['sec','notes'] and e.find('title') is not None and ''.join(e.find('title').itertext()).lower()=='code availability');assert 'No custom code was created in this study.' in ''.join(code.itertext())
    source=BASE/'source-intake-1/2.2.0-2025-release/source/R/modeling.R';assert re.search(r'computeCommunProb\s*<-\s*function[\s\S]{0,2048}?nboot\s*=\s*100',source.read_text(encoding='utf8'))
    families=['strength-controls-3x4-javac21-2','strength-controls-3x4-javac25-2']+['strength-edge-'+n+'-1' for n in ['rare-zero','missing-group','missing-feature','asymptotic-zero','missing-pooled-feature']];fitCounts={}
    for name in families:
        home=BASE/name/'analysis';d=json.loads((home/'COMPLETED.json').read_bytes());assert d['result_admitted'];fitCounts[name]=len(json.loads((home/'results/worker-report.json').read_bytes())['cases'])
    addendum=dict(status='PASS_PRIMARY_BOUND_P145_STRENGTH_ROLE_DEVELOPMENT_ONLY',paper_id='P145',pmcid='PMC13553309',doi='10.1038/s41588-026-02709-5',primary_xml=pin(xml),primary_text_redistributed=False,custom_study_code_reported=False,
        paper_role=['Two independently requested condition contrasts can represent the two paper models','Condition pooling and original human protein-signaling default-family inference','Pooled min.samples3/min.cells10; original LR/pathway centrality per cell type','Strict strength-difference threshold0.25','Original unpaired sample Wilcoxon tests on selected comparable node strengths'],
        choices_kept_explicit=dict(difference_mode=['absolute','positive','negative'],paper_sign_convention_recovered=False,all_signed_and_absolute_flags_exported=True,example_mode='absolute',exact=[None,True,False],example_exact=None,automatic_method_bound_to_R='4.6.1 Windows',pvalue_cutoff_or_multiplicity_correction_inferred=False,sample_filter_min_samples=1),
        tested_candidate=dict(version='2.2.0',source_label='2.2.0-2025-release',commit='346fb615aaefa3c1a2830ec2fe3a933c5f723c83',historical_commit_established=False,historical_environment_reproduced=False),
        validation=dict(original_admitted_fits=fitCounts,separate_original_default_family_refits=9,independent_default_fit_and_strength_checks=174,original_parameter_specific_export_checks=175,synthetic_fixture_only=True),
        implementation=dict(controller='src/main/java/org/jlinalg/cli/CellChatStrengthCli.java',native='src/main/java/org/jlinalg/omics/CellChatSignalingStrengths.java',independent_native_pvalues=False,CellChat_full_catalog_credit=False,whole_goal_complete=False))
    save(out/'paper-addendum.json',addendum);save(out/'COMPLETED.json',dict(status=addendum['status'],addendum=pin(out/'paper-addendum.json'),primary_xml=pin(xml),source_pin=pin(Path(__file__).resolve()),new_catalog_credit=False));print(addendum['status'])

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary source checks')
    main()
