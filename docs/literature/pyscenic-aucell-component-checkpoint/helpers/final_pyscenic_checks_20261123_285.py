from pathlib import Path
import concurrent.futures,json,subprocess,sys,shutil
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-1';D=B/'predecessor-checks-1';D.mkdir()
code=(R/'build/qualify_pyscenic_aucell_jar_20261123_281.py').read_text(encoding='utf-8').replace('packaged-check-1','packaged-check-2').replace('normal-build-1','normal-build-2')
(R/'build/qualify_pyscenic_aucell_jar_20261123_285.py').write_text(code,encoding='utf-8',newline='\n')
reader=T/'tools/literature/verify_smartpca_catalog_checkpoint.py';oldjar=T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar'
def linux(p):s=Path(p).as_posix();return '/mnt/'+s[0].lower()+s[2:]
commands=[('packaged',[sys.executable,'-X','utf8','-B',str(R/'build/qualify_pyscenic_aucell_jar_20261123_285.py')]),
 ('windows-catalog',[sys.executable,'-X','utf8','-B',str(reader),'--jar',str(oldjar)]),
 ('linux-catalog',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','env','GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE='+linux(T),'GIT_OPTIONAL_LOCKS=0','python3','-B',linux(reader)])]
def run(row):
 name,args=row
 with (D/(name+'.stdout')).open('xb') as out,(D/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(name+'.STARTED.json'),dict(pid=p.pid,command=args));status=p.wait()
 save(D/(name+'.RESULT.json'),dict(exit_code=status));assert status==0,(name,(D/(name+'.stderr')).read_text(errors='replace')[-1800:]);print(json.dumps(dict(case=name,exit_code=status)),flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(run,commands))
save(D/'COMPLETED.json',dict(status='PASS_EXACT_FINAL_PACKAGED_AUCELL_AND_PREDECESSOR_READERS',catalog_reader_hosts=['Windows','Linux'],old_scientific_sources_and_hashes_refreshed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
first=B/'original-aucell-reference-1';save(first/'RETAINED-LIMITATION.json',dict(status='HISTORICAL_CAPTURE_WITH_WARNING_LIST_OMISSION_ON_EXCEPTIONS',raw_original_values_and_tracebacks_retained=True,archive_unchanged=True,qualified_final_reference=False,
 remedy='Use original-aucell-reference-2, which genuinely repeats the same37 datasets and retains caught warnings on exceptional returns too. Counts are repeated cases, not74 unique data sets.'))
idx=B/'runtime-index-1';save(idx/'FAILURE.json',dict(status='FAILED_METADATA_INDEXER_ONLY_NO_SCIENTIFIC_ANALYSIS',error='Python3.10 lacks hashlib.file_digest. The first metadata indexer stopped before writing an inventory.',corrected_index='runtime-index-2/INVENTORY.json',runtime_scientific_source_changed=False))
save(B/'EARLIER-PRODUCER-LIMITS.json',dict(status='SUPERSEDED_DEVELOPMENT_COMPONENTS_RETAINED',normal_build_1='8658659410bf963c2d443c5709f00c00ade6618df0344416c9d8e358114c7d33',final_normal_build_2='925ef8573b06bd0547a9c1aaa38b339e41720144ee1b4dbc1f9e8089b6c2f585',
 boundary='Earlier source contexts passed their recorded tests but preceded the final combined-work and/or empty-recovery-window admission diagnostics. Their values and results remain historical; use the final qualified source/JAR.',new_family_credit=0,new_reviews=0))
print('Final packaged/source checks terminal; earlier setup/reference/metadata attempts retained.')
