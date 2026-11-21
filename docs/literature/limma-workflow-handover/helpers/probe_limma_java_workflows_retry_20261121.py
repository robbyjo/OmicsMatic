from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');source=(R/'build/probe_limma_java_workflows_20261121.py').read_text(encoding='utf-8').replace("O=B/'java-probe-1'","O=B/'java-probe-2'")
ast.parse(source);exec(compile(source,__file__,'exec'))
