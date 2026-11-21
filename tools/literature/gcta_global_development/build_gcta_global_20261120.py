from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/run_mtcojo_gradle_20261120.py').read_text(encoding='utf-8')
changes={
"T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1'":"T=R/'build/gc-7';B=T/'build/gcta-global-1'",
"'org.jlinalg.cli.GctaMtcojoContractsTest']":"'org.jlinalg.cli.GctaMtcojoContractsTest','org.jlinalg.cli.GctaMtcojoWorkflowTest','org.jlinalg.cli.GctaGlobalDispatchTest']",
"'src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip','build.gradle']":"'src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip','src/test/resources/org/jlinalg/cli/mtcojo1941-workflow-captures.zip','src/test/java/org/jlinalg/cli/GctaMtcojoWorkflowTest.java','src/test/java/org/jlinalg/cli/GctaGlobalDispatchTest.java','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/gcta_dispatch_transition.py','build.gradle']"
}
for old,new in changes.items():assert old in source,old;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
