from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');source=(R/'build/probe_limma_java_workflows_20261121.py').read_text(encoding='utf-8').replace("O=B/'java-probe-1'","O=B/'java-probe-extended-1'").replace("B/'compile-probe-3/classes'","B/'compile-probe-4/classes'")
source=source.replace("def run(op):\n    folder=O/op;folder.mkdir();src=B/'worker-probe-3'/op/'worker';p=json.loads((src/'parameters.json').read_bytes());out=folder/'analysis'", "def run(label):\n    folder=O/label;folder.mkdir();src=B/'worker-probe-extended-1'/label/'worker';p=json.loads((src/'parameters.json').read_bytes());op=p['operation'];out=folder/'analysis'")
point="    request=folder/'request.json';"
extra="""    if p.get('has_weights'):r['weights']=str(src/'weights.tsv')
    if p.get('has_block'):r.update(block=str(src/'block.tsv'),block_levels=p['block_levels'])
"""
source=source.replace(point,extra+point)
source=source.replace("print(op,mode,p2.returncode,", "print(label,mode,p2.returncode,").replace("return op,rows","return label,rows")
source=source.replace("['LM','VOOM','EDGER_VOOM_LMFIT','BATCH','CAMERAPR','NORMALIZE']","['LM_BLOCK','VOOM_BLOCK1','VOOM_BLOCK2','EDGER_BLOCK','LM_MODERN_ROBUST','LM_LEGACY_ROBUST','LM_TREND','LM_TREAT']")
ast.parse(source);exec(compile(source,__file__,'exec'))
