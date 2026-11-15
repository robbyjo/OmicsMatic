"""Second editorial pass: expand abstract abbreviations; keep scientific body unchanged."""
from pathlib import Path
import shutil
b=Path(__file__).resolve().parents[1]
v1=b/'previews/authoring-v1'
v1.mkdir(exist_ok=False)
for name in ['artifacts/Splicing_BMI_current_outline_20261114.pdf','artifacts/manuscript-draft.txt','metadata/authored-content.json','logs/validation.json','logs/commands.json']:
    shutil.copy2(b/name,v1/Path(name).name)
source=(b/'scripts/build_manuscript.py').read_text(encoding='utf-8-sig')
# Serialize the replacements to avoid quote and escape ambiguity in executable source.
import json
replacement={
'Background':'Relative transcript abundance may reveal adiposity-associated RNA variation obscured by total gene expression. We evaluated its reproducibility across whole-blood cohorts and used genetic evidence to prioritize associated transcripts.',
'Methods':"The Framingham Heart Study supplied discovery, and the Coronary Artery Risk Development in Young Adults study and Women's Health Initiative supplied replication. Gene-expression-adjusted summaries for 224,365 body mass index (BMI) and 223,927 obesity transcripts entered meta-analysis and functional enrichment. All significant transcripts were assessed for Mendelian randomization (MR) using cis genetic instruments for transcript ratios. Jackson Heart Study peripheral blood mononuclear cell (PBMC) results were supplementary. Verified analysis-population totals remain unavailable.",
'Results':'Meta-analysis identified 61,299 BMI and 48,973 obesity associations at false discovery rate <0.05; 30,747 and 20,283 Framingham discoveries replicated in at least one validator. PCBP2 transcript ENST00000553064.6 led both traits. Median I2 among significant transcripts was 66.6% and 59.4%; random-effects analyses retained 29,150 and 22,981 associations. MR was estimable for 21,645 BMI and 18,235 obesity transcripts, with 3,877 and 150 outcome-specific false-discovery-rate hits. Most significant MR findings used a single instrument. The PBMC cohort replicated one available BMI discovery and no obesity discoveries.'}
source=source.replace("TITLE='Whole-blood",'abstract.update('+repr(replacement)+')\nTITLE=\'Whole-blood',1)
assert 'abstract.update(' in source
exec(compile(source,str(b/'scripts/build_manuscript.py'),'exec'))
