from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/start_gcta_catalog_20261120.py').read_text(encoding='utf-8')
for old,new in {
"P=R/'build/git-publish/20261120-gcta-global-73';T=R/'build/gc-8';O=R/'build/gcta-catalog-start-20261120-1';BASE='aed274a43aacaec95c59ef5f66dc03e791ea39be'":"P=R/'build/git-publish/20261120-gcta-catalog-74';T=R/'build/lm-1';O=R/'build/limma-start-20261120-1';BASE='9b2e159ab06ee1682cc268863173d3d8a4459723'",
"assert len(previous)==29":"assert len(previous)==30",
"codex/gcta-catalog-20261120":"codex/limma-high-impact-20261120",
"prior_worktrees_preserved=29,current_worktrees=30":"prior_worktrees_preserved=30,current_worktrees=31",
"PASS_NEW_GCTA_CATALOG_WORKTREE":"PASS_NEW_LIMMA_WORKTREE",
"progress: global GCTA route,27 tests per JVM,246 capture checks and expanded all250 source audit finding P169 and P090 role;verified main publication":"progress: one GCTA family and COJO alias admitted,27 source reviews,141 integrations,1575 reviewed,1140 pending; verified main and successful hosted workflow",
"29 predecessors unchanged;30 worktrees":"30 predecessors unchanged;31 worktrees"
}.items():assert old in source,old;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
