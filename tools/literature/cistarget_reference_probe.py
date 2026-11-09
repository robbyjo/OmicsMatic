# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Call unchanged installed ctxcore public recovery, enrichment and leading-edge functions."""
import csv,json,sys,warnings
from pathlib import Path
import numpy as np
import pandas as pd
from ctxcore.genesig import GeneSignature
from ctxcore.rnkdb import RankingDatabase
from ctxcore.recovery import recovery,aucs,leading_edge,enrichment4features,derive_rank_cutoff


class PreparedRankingDatabase(RankingDatabase):
    """Synthetic selected-column provider only; not an original Feather-file run."""
    def __init__(self,frame,total):super().__init__('complete-prepared-control');self.frame=frame;self.total=total
    @property
    def total_genes(self):return self.total
    @property
    def genes(self):return frozenset('feature_'+str(i) for i in range(self.total))
    def load_full(self):raise NotImplementedError('Only the exact selected-rank control population is supplied')
    def load(self,signature):return self.frame.loc[:,[g for g in self.frame.columns if g in signature.genes]].copy()


def main():
    cases=json.loads(Path(sys.argv[1]).read_bytes());out=Path(sys.argv[2]);out.mkdir();reports=[]
    for case in cases:
        if case['profile']!='CTXCORE_0_2_0':continue
        home=out/case['id'];home.mkdir();ranks=np.asarray(case['rankings'],dtype=np.int64);weights=np.asarray(case['weights'],dtype=np.float64)
        genes=np.array(['feature_'+str(i) for i in range(case['selected_features'])],dtype=object);motifs=['motif_'+str(i) for i in range(case['motifs'])]
        frame=pd.DataFrame(ranks,index=motifs,columns=genes);before=ranks.copy();old_weights=weights.copy()
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter('always');curves,area=recovery(frame,case['total_features'],weights,case['recovery_bound'],case['auc_parameter'])
            direct=aucs(frame,case['total_features'],weights,case['auc_parameter'])
            signature=GeneSignature('test-signature',dict(zip(genes,weights,strict=True)))
            complete=enrichment4features(PreparedRankingDatabase(frame,case['total_features']),signature,case['recovery_bound'],case['auc_parameter'])
            nes=complete[('Enrichment','NES')].to_numpy();assert np.array_equal(area,complete[('Enrichment','AUC')].to_numpy(),equal_nan=True)
            mean=curves.mean(axis=0);sd=curves.std(axis=0);boundary=mean+2*sd
        np.savetxt(home/'curves.tsv',curves,delimiter='\t',fmt='%.17g');np.savetxt(home/'background.tsv',np.stack([np.arange(case['recovery_bound']),mean,sd,boundary],axis=1),delimiter='\t',fmt='%.17g',header='rank\tmean\tsd\tmean_plus_two_sd',comments='')
        metrics=[];targets=[]
        for row in range(case['motifs']):
            hits,rank=leading_edge(curves[row],boundary,ranks[row],genes,weights)
            metrics.append([row,area[row],nes[row],rank,curves[row,rank],curves[row,rank]-boundary[rank],direct[row]])
            for order,(gene,weight) in enumerate(hits):
                index=int(gene.split('_')[-1]);targets.append([row,order,index,int(ranks[row,index]),float(weight)])
        np.savetxt(home/'metrics.tsv',np.asarray(metrics),delimiter='\t',fmt='%.17g',header='motif\tauc\tnes\tleading_rank\tleading_recovery\tleading_excess\tdirect_auc',comments='')
        with (home/'targets.tsv').open('w',encoding='utf8',newline='') as stream:
            writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(['motif','source_order','feature','rank','weight']);writer.writerows(targets)
        rounded=round(case['auc_parameter']*case['total_features']);cut=derive_rank_cutoff(case['auc_parameter'],case['total_features'],case['recovery_bound'])
        np.savetxt(home/'parameters.tsv',[[rounded,cut,area.mean(),area.std()]],delimiter='\t',fmt='%.17g',header='rounded_auc_count\tsource_auc_cutoff\tmean_auc\tsd_auc',comments='')
        (home/'warnings.json').write_text(json.dumps([str(w.message) for w in caught],indent=2)+'\n',encoding='utf8')
        assert np.array_equal(ranks,before) and np.array_equal(weights,old_weights)
        reports.append(dict(id=case['id'],complete_motifs=case['motifs'],complete_recovery_points=case['recovery_bound'],original_input_arrays_unchanged=True));print('PASS original ctxcore '+case['id'],flush=True)
    (out/'COMPLETED.json').write_text(json.dumps(dict(status='PASS_32_ORIGINAL_CTXCORE_PREPARED_RECOVERY_ENRICHMENT_AND_LEADING_EDGE_FAMILIES_ONLY',cases=reports,whole_file_workflow_executed=False),indent=2)+'\n',encoding='utf8')


if __name__=='__main__':main()
