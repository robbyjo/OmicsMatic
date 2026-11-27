# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Unmodified Popcorn class execution, complete row accounting and source-result capture."""
import contextlib,hashlib,json,math,os,sys,traceback,types
from pathlib import Path
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')
def safe(value):
    import numpy as np
    if isinstance(value,dict):return{str(k):safe(v)for k,v in value.items()}
    if isinstance(value,(tuple,list,np.ndarray)):return[safe(v)for v in value]
    if isinstance(value,np.integer):return int(value)
    if isinstance(value,(float,np.floating))and not math.isfinite(value):return 'NaN'if math.isnan(value)else('Infinity'if value>0 else'-Infinity')
    return value
def scan(path,capacity):
    rows=0
    with path.open('r',encoding='utf-8',errors='strict',newline='')as stream:
        while True:
            line=stream.readline(capacity['line_characters']+1)
            if not line:break
            assert len(line)<=capacity['line_characters'],'Complete text line capacity exceeded: '+str(path)
            assert '\x00'not in line,'NUL in scientific input'
            rows+=1;assert rows<=capacity['rows'],'Complete input row capacity exceeded: '+str(path)
    return rows
def main():
    out=Path(sys.argv[1]);source=Path(sys.argv[2]);request=json.loads((out/'request.json').read_bytes());effective=json.loads((out/'engine/effective.json').read_bytes());options=request['options'];capacity=request['capacity'];inputs=out/'engine/inputs';outputs=out/'engine/outputs';outputs.mkdir()
    sys.path.insert(0,str(source));import numpy as np,pandas as pd
    from popcorn import compute,fit,sumstats
    warnings=['Exact author version, phenotype/sample preparation and reference/LD representativeness are not inferred.','Original score AF is computed from count_A1=False (A2 dosage); retain original allele/AF provenance.','Original correlation Z tests H0:rho=1; a paper-specific rho=0 test must be separately labelled.','Source jackknife pseudovalues use nb*delete-(nb-1)*full; no bootstrap or conventional-sign substitution.']
    audit=dict(input_rows={},reference_panels=[],score_provenance=request['score_provenance'],preparation=request['preparation'])
    for path in sorted(inputs.iterdir()):
        if path.suffix!='.bed':audit['input_rows'][path.name]=scan(path,capacity)
    args=types.SimpleNamespace(maf=options['maf'],gen_effect=options['correlation']=='genetic-effect',h2weight=options['h2weight'],from_bp=options['from_bp'],to_bp=options['to_bp'],no_align=False,window_size=options['window_size'],window_type=options['window_type'],SNPs_to_read=options['read_snps'],SNPs_to_store=options['store_snps'],use_bias=options['use_bias'],extract=str(inputs/'extract.txt')if effective['extract']else None,no_intercept=not options['intercept'],no_jackknife=not options['jackknife'],use_mle=options['estimator']=='original-mle',plot_likelihood=False,K1=None,P1=None,K2=None,P2=None,Ns=None,regions=None,M=None,tol=1e-5,v=options['verbosity'])
    score_path=outputs/'scores.tsv'
    if effective['compute']:
        from pysnptools.snpreader import Bed
        panels=[]
        for index in [1,2]:
            prefix=inputs/('population'+str(index));bim=pd.read_table(str(prefix)+'.bim',header=None,names=['chr','id','cm','bp','a1','a2']);fam=pd.read_table(str(prefix)+'.fam',header=None,sep=r'\s+')
            assert len(bim)>1 and len(bim)<=capacity['variants'],'Complete reference variant capacity/QC failed'
            assert len(fam)>2 and len(fam)<=capacity['samples'],'Complete reference sample capacity/QC failed'
            assert len(bim)*len(fam)<=capacity['genotype_cells'],'Complete reference genotype cell capacity exceeded'
            assert bim.shape[1]==6 and not bim.isna().any().any(),'Require original tab-delimited six-column BIM metadata'
            assert fam.shape[1]==6 and not fam.iloc[:,:2].duplicated().any(),'Require six-column FAM and unique FID/IID pairs'
            assert bim.chr.nunique()==1 and np.all(np.diff(bim.bp.to_numpy())>0),'Original window code does not separate chromosomes; supply one chromosome with strictly ordered distinct BP, preserving original inputs'
            assert not bim.id.duplicated().any(),'Duplicate reference identifiers require explicit upstream preparation; no silent first-row repair'
            bed=Path(str(prefix)+'.bed');assert bed.stat().st_size==3+len(bim)*((len(fam)+3)//4),'Complete SNP-major BED byte population differs'
            with bed.open('rb')as stream:assert stream.read(3)==b'\x6c\x1b\x01','Original SNP-major BED required'
            panels.append(bim);audit['reference_panels'].append(dict(variants=len(bim),samples=len(fam),chromosome=str(bim.chr.iloc[0]),count_A1=False))
        common=set(panels[0].id)&set(panels[1].id);a=panels[0].loc[panels[0].id.isin(common)];b=panels[1].loc[panels[1].id.isin(common)]
        assert list(a.id)==list(b.id)and list(a.bp)==list(b.bp)and list(a.chr)==list(b.chr),'Original compute independently sorts panel indices; mismatched shared SNP order/coordinates would silently mispair rows. Prepare explicit aligned panels; adapter does not reorder'
        args.bfile1=str(inputs/'population1');args.bfile2=str(inputs/'population2');args.out=str(score_path)
        with(outputs/'original-compute.stdout').open('x')as stdout,(outputs/'original-compute.stderr').open('x')as stderr,contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
            scores_object=compute.covariance_scores_2_pop(args);scores_object.write(args)
        audit['computed_score_M']=int(scores_object.M)
        assert 'Error when setting scores'not in(outputs/'original-compute.stdout').read_text(),'Original 1.0 buffer failure retained; no complete score admission'
    else:score_path=inputs/'scores.tsv'
    scan(score_path,capacity)
    columns=['chr','pos','id','a1','a2','af1','af2','score1','score2','scoreX']
    scores=pd.read_table(score_path,header=None,comment='#',names=columns)
    assert len(scores)>1 and len(scores)<=capacity['variants']and not scores.isna().any().any(),'Complete ten-column two-population score file required'
    assert not scores.id.duplicated().any(),'Duplicate score identifiers cannot be admitted'
    assert scores[['a1','a2']].isin(['A','C','G','T','a','c','g','t']).all().all(),'Resolved original SNP alleles required'
    assert (scores[['a1']].values!=scores[['a2']].values).all(),'Identical score alleles fail QC'
    assert np.isfinite(scores[['chr','pos','af1','af2','score1','score2','scoreX']].to_numpy(dtype=float)).all(),'Nonfinite score values retained without complete admission'
    assert ((scores[['af1','af2']]>0)&(scores[['af1','af2']]<1)).all().all(),'Score allele frequencies must be within (0,1)'
    M=len(scores);audit['score_M_before_summary_merge_and_MAF']=M
    if effective['compute']:assert M==audit['computed_score_M'],'Original score writer omitted rows'
    canonical=dict(correlation=options['correlation'],estimator=options['estimator'],score_columns=columns,scores=safe(scores.values.tolist()),source_M=M)
    if effective['fit']:
        args.sfile1=str(inputs/'summary1.tsv');args.sfile2=str(inputs/'summary2.tsv');scores.index=scores.id
        with(outputs/'original-fit.stdout').open('x')as stdout,(outputs/'original-fit.stderr').open('x')as stderr,contextlib.redirect_stdout(stdout),contextlib.redirect_stderr(stderr):
            data=sumstats.sumstats_2_trait(scores,args);data.data.to_csv(outputs/'aligned.tsv',sep='\t',index=False,na_rep='NaN')
            assert not data.overlap,'Two-population workflow does not model sample overlap'
            assert len(data.data)>1 and len(data.data)<=capacity['rows'],'No adequate complete aligned summary population'
            audit['aligned_rows']=len(data.data);audit['aligned_columns']=list(data.data.columns)
            required=['N1','N2','Z1','Z2','score1','score2','scoreX']
            assert np.isfinite(data.data[required].to_numpy(dtype=float)).all(),'Nonfinite complete aligned regression rows'
            assert (data.data[['N1','N2']]>0).all().all(),'Positive actual sample sizes required'
            data.data[required].to_csv(outputs/'native-regression-rows.tsv',sep='\t',index=False)
            result=fit.fit_pg(data.data,args,M=M);args.out=str(outputs/'fit.tsv');result.write(args.out)
        values=result.res.to_numpy(dtype=float);canonical.update(aligned_columns=list(data.data.columns),aligned_rows=safe(data.data.values.tolist()),fit_index=list(result.res.index),fit_columns=list(result.res.columns),fit=safe(values))
        if result.jackknife is not None:
            canonical.update(blocks=safe(result.jackknife.blocks),delete_values=safe(result.jackknife.delete_values),source_pseudo_values=safe(result.jackknife.psuedovalues),covariance=safe(result.jackknife.cov.filled(np.nan)))
        save(outputs/'original-fit-details.json',safe(canonical))
        assert np.isfinite(result.res['Val (obs)'].to_numpy()).all()and (result.res['Val (obs)'].iloc[:2]>0).all(),'Original nonfinite/nonpositive heritability result retained; no complete scientific result'
        if options['jackknife']:assert np.isfinite(values).all()and(result.res.SE>0).all(),'Original undefined/zero uncertainty or nonfinite test retained; no complete scientific result'
        if abs(result.res['Val (obs)'].iloc[2])>1:warnings.append('Unconstrained original regression correlation is outside [-1,1]; it is retained without biological clipping. Investigate model/LD fit before interpretation.')
        if not options['jackknife']:warnings.append('Jackknife explicitly disabled. Original SE/Z/P are NaN; only point estimates are admitted, without inferential claims.')
    save(out/'INPUT-AUDIT.json',safe(audit));save(out/'canonical.json',safe(canonical));save(out/'OUTPUT-AUDIT.json',dict(status='COMPLETE_ORIGINAL_POPCORN_ROWS',warnings=warnings,source_M=M,score_rows=len(scores),fit_performed=effective['fit'],score_computed=effective['compute'],author_recipe_admitted=False))
if __name__=='__main__':
    try:main()
    except BaseException as error:
        save(Path(sys.argv[1])/'WORKER-FAILURE.json',dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),analysis_admitted=False));raise
