from pathlib import Path
import hashlib,json,shutil
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';Q=T/'docs/literature/gcta-catalog-checkpoint';O=T/'build/gcta-catalog-1/checkpoint-binding-1';O.mkdir()
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
path=Q/'CHECKS.json';before=path.read_bytes();(O/'CHECKS.before.json').write_bytes(before);record=json.loads(before);global_profile=json.loads((T/'docs/literature/gcta-global-development/PROFILE.json').read_bytes())
assert 'src/main/java/org/jlinalg/cli/JLinAlgCli.java' not in record['source_inputs']
for name,w in global_profile['source_inputs'].items():assert pin(T/name)==w,name
record['source_inputs'].update(global_profile['source_inputs'])
for name in ['PROFILE.json','INVENTORY.json','evidence.zip','SOURCE-ROLES.json','SOURCE-CENSUS.json']:
    relative='docs/literature/gcta-global-development/'+name;record['canonical_after'][relative]=pin(T/relative)
source=R/'build/finalize_gcta_catalog_checkpoint_20261120.py';target=T/'tools/literature/gcta_catalog/development'/source.name;shutil.copyfile(source,target);record['source_inputs'][target.relative_to(T).as_posix()]=pin(target)
record['qualified_global_sources_and_proof_bytes_also_bound']=True;record['initial_unpublished_metadata_preserved']=True
path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n');(O/'RESULT.json').write_text(json.dumps(dict(status='PASS_ADDED_INHERITED_QUALIFIED_GLOBAL_SOURCE_BINDINGS',before=pin(O/'CHECKS.before.json'),after=pin(path),scientific_sources_or_results_changed=False,old_metadata_preserved=True),indent=2)+'\n',encoding='utf-8',newline='\n');print('Bound current catalog snapshot to all unchanged qualified global sources and proofs',flush=True)
