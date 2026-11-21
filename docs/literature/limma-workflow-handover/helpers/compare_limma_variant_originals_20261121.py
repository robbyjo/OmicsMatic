from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');source=(R/'build/compare_limma_direct_originals_20261121.py').read_text(encoding='utf-8').replace("O=B/'direct-originals-1'","O=B/'direct-originals-variants-1'")
start=source.index('sources={');end=source.index('\nenv=',start)
source=source[:start]+"sources={p.name:p/'analysis' for p in (B/'java-variants-1').iterdir() if p.is_dir() and json.loads((p/'analysis/analysis-status.json').read_bytes())['results_admitted']};sources['PROVIDED_LIBRARIES_FACTORS']=B/'java-variants-recovery-1/analysis';assert len(sources)==31"+source[end:]
ast.parse(source);exec(compile(source,__file__,'exec'))
