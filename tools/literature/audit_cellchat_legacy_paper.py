# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify immutable author-code facts for P085 without executing/redistributing it."""
import argparse,gzip,hashlib,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--intake',type=Path,default=BASE/'p085-author-code-intake-1');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    intake=a.intake.resolve();receipt=json.loads((intake/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_IMMUTABLE_P085_AUTHORS_CODE_INTAKE_ONLY' and receipt['commit']=='5775f130506d80917461fb27226f44130e2ba783' and not receipt['executed']
    for row in receipt['files']:
        file=intake/'source'/row['path'];raw=file.read_bytes();assert pin(file)=={k:row[k] for k in ['bytes','sha256']};assert hashlib.sha1(('blob '+str(len(raw))+'\0').encode()+raw).hexdigest()==row['git_blob']
    path='snRNA/cell_cell_communication/spatial_cellchat.Rmd';entry=next(r for r in receipt['files'] if r['path']==path);lines=(intake/'source'/path).read_text(encoding='utf8').splitlines()
    def anchor(fragment):
        matches=[dict(line=i+1,line_sha256=hashlib.sha256(line.encode()).hexdigest()) for i,line in enumerate(lines) if fragment in line];assert matches,fragment;return matches
    anchors={key:anchor(fragment) for key,fragment in dict(human_database='CellChatDB <- CellChatDB.human',projection_species='projectData(cellchat_list[[cond]], PPI.mouse)',raw_use='computeCommunProb(cellchat_list[[cond]], raw.use = TRUE)',condition_group_counts='table(seurat_obj$Diagnosis, seurat_obj$cellchat_anno)',joint_group_exclusion="tmp[tmp$Freq < 30, 'Var2']",remove_from_both='!(seurat_obj$cellchat_anno %in% groups_remove)',parallel_plan='future::plan("multiprocess", workers = 8)',rank_net='rankNet(cellchat, mode = "comparison", stacked = T, do.stat = TRUE, do.flip=FALSE)').items()}
    catalog=ROOT/'docs/literature/omics-2023-2026/papers.json';paper=next(r for r in json.loads(catalog.read_bytes()) if r['paper_id']=='P085');assert paper['pmcid']=='PMC11631771' and paper['doi']=='10.1038/s41588-024-01961-x'
    xml=ROOT/'build/literature-review/xml/PMC11631771.xml.gz';assert hashlib.sha256(gzip.decompress(xml.read_bytes())).hexdigest()==paper['xml_sha256']
    draft=dict(status='verified-author-code-addendum-not-applied-to-catalog',paper_id='P085',pmcid=paper['pmcid'],doi=paper['doi'],tool_id='cellchat',version_reported_in_primary_methods='1.1.3',author_repository='swaruplabUCI/DSAD_Spatial_Miyoshi_Morabito_2024',author_commit=receipt['commit'],file=entry,anchors=anchors,facts=dict(database='CellChatDB.human',projected_resource='PPI.mouse',raw_use=True,projection_consumed_by_computeCommunProb=False,prefilter='Remove a group from both conditions if its count in any condition is below30; exactly30 is retained unless another condition is below30.',rank_net_paired_edge_test=True,independent_biological_replicate_test=False,author_parallel_workers=8,adapter_parallel_workers=1),scope='Primary methods plus immutable posted code. Posted eval=FALSE code is not a historical execution receipt. No author package commit, R environment or biological result is inferred. CellTrek region labels are caller-supplied group annotations, not CellChat distance modeling. LIANA/Pearson is separate. This addendum resolves the earlier projection-choice uncertainty but adds no catalog availability credit.',author_code_executed=False,author_code_redistributed=False,new_catalog_credit=False,whole_goal_complete=False)
    save(out/'role-addendum.json',draft);save(out/'COMPLETED.json',dict(status='PASS_P085_PRIMARY_AND_IMMUTABLE_AUTHOR_CODE_FACTS_ONLY',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},author_code_intake_pin=pin(intake/'COMPLETED.json'),catalog_unchanged=pin(catalog),role_addendum=pin(out/'role-addendum.json'),new_catalog_credit=False,whole_goal_complete=False));print('Verified P085 author-code facts; catalog unchanged; source code not executed or redistributed')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable immutable primary-source checks')
    main()
