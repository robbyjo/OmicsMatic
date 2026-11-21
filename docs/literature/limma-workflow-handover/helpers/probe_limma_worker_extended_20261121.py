from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');source=(R/'build/probe_limma_worker_20261121.py').read_text(encoding='utf-8').replace("O=B/'worker-probe-1'","O=B/'worker-probe-extended-1'")
source=source.replace("def run(op):\n    root=O/op;", "def run(label):\n    op='EDGER_VOOM_LMFIT' if label=='EDGER_BLOCK' else 'VOOM' if label.startswith('VOOM_') else 'LM'\n    root=O/label;")
point="    (worker/'parameters.json').write_text"
extra="""    if label in ['LM_BLOCK','VOOM_BLOCK1','VOOM_BLOCK2','EDGER_BLOCK']:
        p['has_block']=True;p['block_levels']=['b'+str(i+1) for i in range(N//2)]
        tsv(worker/'block.tsv',['sample_id','block'],[(samples[j],'b'+str(j//2+1)) for j in range(N)])
        if op!='EDGER_VOOM_LMFIT':p['correlation']=dict(mode='estimate',trim=.15,**({'voom_passes':1 if label.endswith('1') else 2} if op=='VOOM' else {}))
    if label in ['LM_MODERN_ROBUST','LM_LEGACY_ROBUST','LM_TREND','LM_TREAT']:
        weights=[[0.0 if i%10==0 and j==0 else 1.0 for j in range(N)] for i in range(G)]
        p['has_weights']=True;tsv(worker/'weights.tsv',['feature_id',*samples],[(features[i],*weights[i]) for i in range(G)])
        p['moderation']['legacy']='true' if label=='LM_LEGACY_ROBUST' else 'false' if label=='LM_MODERN_ROBUST' else 'auto'
        if label.endswith('ROBUST'):p['moderation'].update(robust=True,winsor_tail_p=[.05,.1])
        if label=='LM_TREND':p['moderation']['trend']='Amean'
        if label=='LM_TREAT':p['moderation']=dict(method='TREAT',trend='Amean',legacy='auto',robust=False,lfc=.2630344058337938)
"""
assert point in source;source=source.replace(point,extra+point)
source=source.replace("print(op,proc.returncode,report.get('error'),report.get('object_snapshots'),flush=True);return op,", "print(label,proc.returncode,report.get('error'),report.get('object_snapshots'),report.get('iterative_gamma_fits'),flush=True);return label,")
source=source.replace("['LM','VOOM','EDGER_VOOM_LMFIT','BATCH','CAMERAPR','NORMALIZE']", "['LM_BLOCK','VOOM_BLOCK1','VOOM_BLOCK2','EDGER_BLOCK','LM_MODERN_ROBUST','LM_LEGACY_ROBUST','LM_TREND','LM_TREAT']")
ast.parse(source);exec(compile(source,__file__,'exec'))
