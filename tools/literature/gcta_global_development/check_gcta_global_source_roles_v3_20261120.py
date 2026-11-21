from pathlib import Path
import ast
source=Path('D:/projects/JLinAlg/build/check_gcta_global_source_roles_20261120.py').read_text(encoding='utf-8')
for old,new in {
"O=B/'source-role-review-1'":"O=B/'source-role-review-3'",
"B/'source-intake-1/CANDIDATES.json'":"B/'source-intake-expanded-1/CANDIDATES.json'",
"len(m.ROLES)==18":"len(m.ROLES)==19",
"(pid!='P112')":"(pid not in ['P112','P169'])",
"len(records)==26":"len(records)==27",
"for r in records)==18":"for r in records)==19",
"PASS_REVIEWED_GCTA_COJO_26_POSITIVE_LABEL_ROLES_18_PAPERS_AND_FOUR_EXCLUSIONS_NOT_ADMITTED":"PASS_REVIEWED_GCTA_COJO_27_POSITIVE_LABEL_ROLES_19_PAPERS_AND_FOUR_EXCLUSIONS_NOT_ADMITTED",
"proposed_addition=m.PROPOSED_ADDITION":"proposed_additions=m.PROPOSED_ADDITIONS",
"expected_observation_delta=0":"expected_observation_delta=1",
"PASS26 source label roles on18 executed papers":"PASS27 source label roles on19 executed papers"
}.items():assert old in source,old;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
