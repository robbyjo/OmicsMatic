from pathlib import Path
import hashlib,json
R=Path('D:/projects/JLinAlg');T=R/'build/mr-4';B=T/'build/twosamplemr-clump-adapter-1';O=B/'runtime-preservation-1';O.mkdir();source=R/'build/mr-2/build/twosamplemr-workflow-1/runtime-census-1';root=R/'build/twosamplemr-runtime-intake-20261121-1'
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
files=json.loads((source/'FILES.json').read_bytes());assert len(files)==5765
for name,wanted in files.items():assert pin(root/name)==wanted,name
plink=R/'build/omics-plink-official/plink1/plink.exe';assert pin(plink)['sha256']=='efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b'
(O/'RESULT.json').write_text(json.dumps(dict(status='PASS_5765_PRIVATE_RUNTIME_FILES_UNCHANGED_AND_EXACT_PLINK',runtime_files=len(files),runtime_bytes=sum(p['bytes'] for p in files.values()),prior_census=pin(source/'FILES.json'),plink=pin(plink),runtime_binaries_distributed=False,new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n');print('PASS 5765 runtime files and exact PLINK preserved')
