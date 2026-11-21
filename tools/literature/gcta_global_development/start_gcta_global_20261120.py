from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/start_mtcojo_qualification_20261120.py').read_text(encoding='utf-8')
changes={
"P=R/'build/git-publish/20261120-mtcojo-handover-71';T=R/'build/gc-6';O=R/'build/mtcojo-qualification-start-20261120-1';BASE='46611ae55e9b0778df6a117fd8be76ae468c27b5'":"P=R/'build/git-publish/20261120-mtcojo-qualification-72';T=R/'build/gc-7';O=R/'build/gcta-global-start-20261120-1';BASE='cfeee186d5068a2af3be33ef9fb69508feefdd17'",
"assert len(previous)==27":"assert len(previous)==28",
"codex/gcta-mtcojo-qualification-20261120":"codex/gcta-global-20261120",
"prior_worktrees_preserved=27,current_worktrees=28":"prior_worktrees_preserved=28,current_worktrees=29",
"progress: native/original mtCOJO development, 24 packaged tests on three JVMs, eight normal/original comparisons, handover and verified GitHub main publication":"progress: 57 actual mtCOJO execution requests,18 replay controls,24 table comparisons,runnable examples and verified main publication",
"PASS_NEW_MTCOJO_QUALIFICATION_WORKTREE":"PASS_NEW_GCTA_GLOBAL_WORKTREE",
"27 predecessors unchanged;28 total":"28 predecessors unchanged;29 total"
}
for old,new in changes.items():assert old in source,old;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
