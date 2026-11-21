from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/check_gcta_catalog_preview_ui_20261120.py').read_text(encoding='utf-8')
for old,new in {"O=B/'preview-ui-1'":"O=B/'installed-ui-1'","B/'plan-1/COMPLETED.json'":"B/'apply-1/COMPLETED.json'","PASS_GCTA_PLAN_WITHOUT_CANONICAL_MUTATION":"PASS_GCTA_CUMULATIVE_ADMISSION_ONE_FAMILY_ONE_ALIAS_27_SOURCE_ROLES","directory=str(B/'plan-1/preview/site')":"directory=str(T/'site')","preview_reports_with_unchanged_local_site_assets=True":"installed_reports_with_unchanged_local_site_assets=True"}.items():assert old in source,old;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
