from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');source=(R/'build/probe_limma_worker_20261121.py').read_text(encoding='utf-8').replace("O=B/'worker-probe-1'","O=B/'worker-probe-3'")
ast.parse(source);exec(compile(source,__file__,'exec'))
