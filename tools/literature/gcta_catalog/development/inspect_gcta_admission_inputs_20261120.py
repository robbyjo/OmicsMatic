from pathlib import Path
import json,sys
T=Path('D:/projects/JLinAlg/build/gc-7');sys.path.insert(0,str(T/'tools/literature'));from gsea_catalog.model import registry,META
rows=json.loads((T/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes());r=registry()
for tool in ['gcta','cojo']:
    print('INVENTORY',tool,json.dumps(next(x for x in rows if x['id']==tool)),flush=True);print('REGISTRY',tool,json.dumps(next(x for x in r if x['id']==tool)),flush=True)
print('META',META,flush=True);summary=json.loads((T/'docs/literature/omics-2023-2026/summary.json').read_bytes());print('SUMMARY',json.dumps(summary),flush=True)
