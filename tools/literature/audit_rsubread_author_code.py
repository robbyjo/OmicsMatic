# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit one paper-linked immutable author-code snapshot without executing or redistributing its scripts."""
import argparse,gzip,json,re
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    intake=json.loads((a.intake/'INTAKE.json').read_bytes());commit='3aeb503348e52b1f0b88d47839427380250c0ea5'
    assert intake['commit']==commit and not intake['executed'] and not intake['redistribution_license_identified']
    paper=next(q for q in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes()) if q['paper_id']=='P133')
    xml=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
    assert __import__('hashlib').sha256(xml).hexdigest()==paper['xml_sha256'] and b'github.com/haolongli87/ptges3_resources' in xml
    records={q['path']:q for q in intake['files']}
    for name,value in records.items():assert pin(a.intake/name)=={k:value[k] for k in ['bytes','sha256']}
    atac='atacseq_sgPTGES3/scripts/02_prepare_atacseq_feature_counts.R';rna='rnaseq_sgPTGES3/scripts/reproduce_RNAseq_figures_and_NG_revision.R'
    lines=(a.intake/atac).read_text(encoding='utf8').splitlines();start=next(i for i,line in enumerate(lines) if 'fcResults <- Rsubread::featureCounts(' in line)
    body='\n'.join(lines[start:start+9]);assert body.count('Rsubread::featureCounts(')==1 and body.endswith('verbose=FALSE)')
    expected={'files':'files.bam','annot.ext':'regionsToCount','isPairedEnd':'TRUE','countMultiMappingReads':'FALSE','maxFragLength':'100','autosort':'TRUE','nthreads':'nthreads','verbose':'FALSE'}
    actual=dict(re.findall(r'^\s*(\w+(?:\.\w+)*)\s*=\s*([\w.]+)\s*[,)]',body,re.M));assert actual==expected
    assert not re.search(r'checkFragLength|countReadPairs',body)
    rename=next(i for i,line in enumerate(lines) if 'colnames(counts) <- sampleids' in line)
    rna_text=(a.intake/rna).read_text(encoding='utf8');assert not re.search(r'Rsubread|featureCounts',rna_text,re.I)
    output=dict(status='passed-read-only-P133-linked-author-code-audit',paper_id='P133',pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],repository=intake['repository'],commit=commit,
                audited_files=list(records.values()),atac=dict(path=atac,url=f'https://github.com/haolongli87/ptges3_resources/blob/{commit}/{atac}#L{start+1}',call_lines=[start+1,start+9],API='Rsubread::featureCounts',explicit_arguments=actual,
                annotation_argument_kind='Explicitly constructed SAF-style data.frame from consensus peak regions',checkFragLength_reported=False,countReadPairs_reported=False,package_edition_identified=False,
                later_sample_rename_line=rename+1,sample_rename_is_separate=True),
                rna=dict(path=rna,Rsubread_or_featureCounts_call_found=False,scope='Only this linked RNA figure/revision script was scanned; absence here does not imply absence in all author materials'),
                required_actions='Retain the article spelling featurecount as reported and the actual ATAC API as a separate code observation. Declare an edition, checkFragLength and countReadPairs explicitly for new analysis. Do not infer a fragment-length filter from maxFragLength=100 or apply the later sample rename to the original returned object.',
                scripts_executed=False,raw_author_scripts_redistributed=False,license_identified=False,paper_binary_identified=False,catalog_changed=False,implementation_credit=False,whole_goal_complete=False)
    save(out/'AUTHOR-CODE-AUDIT.json',output);save(out/'COMPLETED.json',dict(status=output['status'],audit=pin(out/'AUTHOR-CODE-AUDIT.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},catalog_changed=False,implementation_credit=False,whole_goal_complete=False));print(output['status'])


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable immutable author-source and primary-paper guards')
    main()
