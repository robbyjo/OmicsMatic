from pathlib import Path
import ast,json
R=Path('D:/projects/JLinAlg');T=R/'build/gc-6';B=T/'build/mtcojo-qualification-1';O=B/'packaging-first-failure-1';O.mkdir()
# Preserve the initial derived output and helper snapshots; never delete history.
for source,target in [(T/'docs/literature/mtcojo-qualification',O/'public-output'),(T/'tools/literature/gcta_mtcojo/qualification-development',O/'helper-snapshots')]:
    assert source.resolve().is_relative_to(T.resolve()) and target.resolve().is_relative_to(T.resolve()) and not target.exists()
    source.rename(target)
(O/'FAILURE.json').write_text(json.dumps(dict(status='FAILED_PACKAGING_ONLY_NO_ANALYSIS_RERUN',cause='Windows stat on retained Linux alias raised WinError1920 before alias-exclusion check',original_attempt_outputs_preserved=True,remedy='Exclude alias names before stat; preserve alias creation receipt and exact underlying parent files'),indent=2)+'\n',encoding='utf-8')
source=(R/'build/package_mtcojo_qualification_20261120.py').read_text(encoding='utf-8');old="if p.is_file() and 'parent-alias' not in relative.parts";assert source.count(old)==1;source=source.replace(old,"if 'parent-alias' not in relative.parts and p.is_file()")
old="'package_mtcojo_qualification_20261120.py']";assert source.count(old)==1;source=source.replace(old,"'package_mtcojo_qualification_20261120.py','package_mtcojo_qualification_v2_20261120.py']")
ast.parse(source);exec(compile(source,__file__,'exec'))
