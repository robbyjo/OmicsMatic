from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/check_gcta_global_source_roles_20261120.py').read_text(encoding='utf-8');assert source.count("O=B/'source-role-review-1'")==1;source=source.replace("O=B/'source-role-review-1'","O=B/'source-role-review-2'");ast.parse(source);exec(compile(source,__file__,'exec'))
