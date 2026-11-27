# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create original synthetic post-fit selection cases at a NEW local path."""
import argparse
from pathlib import Path

HEADER=['ID','GeneID','geneSymbol','chr','strand','exonStart_0base','exonEnd','upstreamES','upstreamEE','downstreamES','downstreamEE','ID','IJC_SAMPLE_1','SJC_SAMPLE_1','IJC_SAMPLE_2','SJC_SAMPLE_2','IncFormLen','SkipFormLen','PValue','FDR','IncLevel1','IncLevel2','IncLevelDifference']
CASES=[('0.01','0.05','0.9'),('0.05','0.9','0.001'),('0.049999999999999999999999999999999','-0.05','0.9'),('0.01','0.049999999999999999999999999999999','0.001'),('NA','0.9','0.001'),('0.9','NA','0.001'),('0.01','0.2','NA'),('0.5','0.2','0.001')]
def create(output):
    with Path(output).open('x',encoding='utf-8',newline='\n')as stream:
        stream.write('\t'.join(HEADER)+'\n')
        for i,(p,psi,fdr)in enumerate(CASES,1):
            cells=[f'event-{i}',f'"gene-{i}"',f'gene{i}','chrSynthetic','+','10','20','0','5','30','40',f'count-row-{i}','1,2','3,4','1,2','3,4','100','50',p,fdr,'NA,0.5','0.25,0.25',psi];stream.write('\t'.join(cells)+'\n')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);create(p.parse_args().output)
