from pathlib import Path
from datetime import datetime,timezone
import json,shutil,sys

stage=Path(__file__).resolve().parent
project=Path('D:/Research/topmed/splicing-bmi/new')
now=datetime.now().astimezone();name=now.strftime('%Y-%m-%dT%H%M%S%z')+'-persuasive-typeset-manuscript'
b=project/'outputs'/name;b.mkdir(exist_ok=False)
for part in ['artifacts/figures','scripts','metadata/source-results','logs','previews']:(b/part).mkdir(parents=True,exist_ok=True)
for p in stage.iterdir():
 if p.suffix in ['.py','.java','.cjs','.css']:shutil.copy2(p,b/'scripts'/p.name)
shutil.copy2('build/cli/omicsmatic-0.3.7.jar',b/'scripts/omicsmatic-0.3.7.jar')
shutil.copy2('src/main/resources/org/jlinalg/study/manuscript-writing-v1.txt',b/'scripts/manuscript-writing-v1.txt')
shutil.copy2(project/'outputs/_index/scripts/update_index_scoped_v3.py',b/'scripts/update_index_scoped_v3.py')
parameters={'started':now.isoformat(),'project':str(project),'previous_manuscript':str(project/'outputs/2026-11-14-current-outline-manuscript'),'scientific_source':str(project/'outputs/2026-10-20T144000-0400-newer-qtl-expanded-mr-manuscript'),'template':'2026-11-14.5','primary_cohorts':['FHS','CARDIA','WHI'],'JHS_scope':'PBMC supplementary sensitivity','scientific_analysis':'unchanged; no association or MR fit','authoring':'assistant-authored application of current template, actual local native math/manuscript renderer and review, browser-to-PDF','no_external_provider_calls':True,'participant_records_read':False,'seed':'not applicable; no stochastic analysis','pdf':'Letter, 11 pt body, 1.42 line height, offline native MathML','previous_manuscript_preserved':True}
(b/'metadata/parameters.json').write_text(json.dumps(parameters,indent=2)+'\n',encoding='utf-8')
(stage/'bundle-path.txt').write_text(str(b),encoding='utf-8')
print(b)
