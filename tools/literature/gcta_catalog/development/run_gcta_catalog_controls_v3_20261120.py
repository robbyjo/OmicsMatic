from pathlib import Path
import ast
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';source=T/'docs/literature/gcta-catalog-controls';target=T/'build/gcta-catalog-1/controls-2/initial-control-packet'
assert source.resolve().is_relative_to(T.resolve()) and target.resolve().is_relative_to(T.resolve()) and not target.exists();source.rename(target)
text=(R/'build/run_gcta_catalog_controls_v2_20261120.py').read_text(encoding='utf-8');line=next(line for line in text.splitlines() if line.startswith("(old/'REVIEW.json').write_text"));text=text.replace(line,"assert (old/'REVIEW.json').is_file()")
assert text.count("O=B/'controls-2'")==1;text=text.replace("O=B/'controls-2'","O=B/'controls-3'");ast.parse(text);exec(compile(text,__file__,'exec'))
