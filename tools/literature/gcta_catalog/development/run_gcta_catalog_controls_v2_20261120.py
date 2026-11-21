from pathlib import Path
import ast,json
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';B=T/'build/gcta-catalog-1';old=B/'controls-1'
(old/'REVIEW.json').write_text(json.dumps(dict(status='RETAINED_LINUX_WINDOWS_WORKTREE_GIT_PATH_FAILURE',windows_tests=23,windows_exit=0,linux_tests=14,linux_exit=1,cause='Linux Git cannot resolve the Windows absolute gitdir in this linked worktree; model test setup failed before its nine model cases',remedy='Supply one portable eight-file fixture,created with Windows Git at exact base and independently checked against retained admitted proof identities',catalog_git_checks_weakened=False,old_attempts_and_source_preserved=True),indent=2)+'\n',encoding='utf-8',newline='\n')
source=(R/'build/run_gcta_catalog_controls_20261120.py').read_text(encoding='utf-8');source=source.replace("O=B/'controls-1'","O=B/'controls-2'")
anchor="def task(host):";insert="""fixture=O/'exact-before.zip'
with zipfile.ZipFile(fixture,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for name in e.m.META:archive.writestr(name,e.git(T,'show',e.BASE+':'+name))
""";assert source.count(anchor)==1;source=source.replace(anchor,insert+anchor)
old="GCTA_PRIMARY_CACHE=str(R/'build/literature-review/xml'))";new="GCTA_PRIMARY_CACHE=str(R/'build/literature-review/xml'),GCTA_EXACT_BEFORE=str(fixture))";assert old in source;source=source.replace(old,new)
old="'GCTA_PRIMARY_CACHE='+linux(R/'build/literature-review/xml'),'PYTHONDONTWRITEBYTECODE=1'";new="'GCTA_PRIMARY_CACHE='+linux(R/'build/literature-review/xml'),'GCTA_EXACT_BEFORE='+linux(fixture),'PYTHONDONTWRITEBYTECODE=1'";assert old in source;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
