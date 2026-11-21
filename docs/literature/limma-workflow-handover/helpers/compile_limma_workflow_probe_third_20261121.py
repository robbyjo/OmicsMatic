from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');source=(R/'build/compile_limma_workflow_probe_20261121.py').read_text(encoding='utf-8').replace("O=B/'compile-probe-1'","O=B/'compile-probe-3'")
ast.parse(source);exec(compile(source,__file__,'exec'))
