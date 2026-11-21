from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/run_mtcojo_gradle_20261120.py').read_text(encoding='utf-8')
old="'org.jlinalg.cli.GctaMtcojoContractsTest']"
assert source.count(old)==1
source=source.replace(old,"'org.jlinalg.cli.GctaMtcojoContractsTest','org.jlinalg.cli.GctaMtcojoWorkflowTest']")
old="'src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip','build.gradle']"
assert source.count(old)==1
source=source.replace(old,"'src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip','src/test/java/org/jlinalg/cli/GctaMtcojoWorkflowTest.java','src/test/resources/org/jlinalg/cli/mtcojo1941-workflow-captures.zip','build.gradle']")
ast.parse(source)
exec(compile(source,__file__,'exec'))
