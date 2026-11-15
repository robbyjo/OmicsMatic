"""Reproducible authoring and current-native-renderer bridge; preserves scientific source outputs."""
from pathlib import Path
from datetime import datetime,timezone
import csv,hashlib,json,platform,re,shutil,subprocess,sys,zipfile
from author_revision import author

B=Path(__file__).resolve().parents[1];M=B/'metadata';A=B/'artifacts';L=B/'logs'
CONFIG=json.loads((M/'parameters.json').read_text(encoding='utf-8'))
PREV=Path(CONFIG['previous_manuscript']);SCI=Path(CONFIG['scientific_source'])
INPUTS=[];COMMANDS=[]
def sha(p):
 h=hashlib.sha256()
 with Path(p).open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
def save(p,x):Path(p).write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def use(p,role):
 p=Path(p);s=p.stat();INPUTS.append({'path':str(p),'role':role,'bytes':s.st_size,'modified':datetime.fromtimestamp(s.st_mtime,timezone.utc).isoformat(),'sha256':sha(p)});return p
def command(args):
 start=datetime.now(timezone.utc).isoformat();r=subprocess.run(list(map(str,args)),cwd=B,capture_output=True,text=True,encoding='utf-8',errors='replace')
 COMMANDS.append({'command':list(map(str,args)),'cwd':str(B),'start':start,'completed':datetime.now(timezone.utc).isoformat(),'stdout':r.stdout,'stderr':r.stderr,'exit_code':r.returncode});save(L/'native-commands.json',COMMANDS)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip() or r.stderr.strip()

old=json.loads(use(PREV/'metadata/authored-content.json','previous authored scientific manuscript').read_text(encoding='utf-8'))
data=author(old)
summary=json.loads(use(SCI/'artifacts/analysis-summary.json','verified association result summary').read_text())
mr=json.loads(use(SCI/'artifacts/expanded-mr-summary.json','corrected expanded MR result summary').read_text())
selection=list(csv.DictReader(use(SCI/'artifacts/main-manuscript-mr-selection.tsv','source MR table selection').open(encoding='utf-8-sig'),delimiter='\t'))
for name in ['replication-summary.tsv','replication-membership-summary.tsv','jhs-sensitivity-summary.tsv','cohort-fdr-summary.tsv','effect-correlations.tsv','enrichment-summary.tsv','mr-eligible-chromosome-coverage.tsv']:
 p=use(SCI/'artifacts'/name,'retained scientific result table');shutil.copy2(p,M/'source-results'/name)
for name in ['analysis-summary.json','expanded-mr-summary.json','main-manuscript-mr-selection.tsv']:
 shutil.copy2(SCI/'artifacts'/name,M/'source-results'/name)
for name in ['README.md','parameters.json','software_versions.json']:use(SCI/'metadata'/name,'upstream provenance')
pop=use(PREV/'metadata/native-population-tables.json','native available-case participant summaries; no individual records')
shutil.copy2(pop,M/'native-population-tables.json')
shutil.copy2(use(PREV/'artifacts/participant-characteristics-source.csv','full-precision participant summary table'),A/'participant-characteristics-source.csv')

# Verify every displayed meta-analysis and MR coefficient/interval against source rows.
for row in old['tables']['meta']['rows'][1:]:
 trait,feature,estimate,fdr,i2=row;feature=feature.split('\n')[0]
 source=next(v for v in summary[trait]['top10'] if v['feature_id']==feature)
 assert estimate==f"{source['beta']:.2f} ({source['ci_lower']:.2f}, {source['ci_upper']:.2f})"
 assert fdr==f"{source['adjusted_p']:.2e}" and i2==f"{source['i_squared']:.1f}%"
for row in old['tables']['mr']['rows'][1:]:
 trait,feature,method,estimate,fdr=row;feature=feature.split('\n')[0]
 source=next(v for v in selection if v['feature_id']==feature and v['phenotype'].casefold()==trait.casefold())
 assert estimate==f"{float(source['estimate']):.2f} ({float(source['ci_lower']):.2f}, {float(source['ci_upper']):.2f})"
 assert fdr==f"{float(source['derived_bh_fdr']):.2e}"
for label,expected in [('BMI',(224365,39262,61299,29150,30747,16429)),('Obesity',(223927,25531,48973,22981,20283,9775))]:
 assert tuple(summary[label][k] for k in ['common_universe','fhs_discoveries','meta_fdr_hits','random_effects_fdr_hits','any_validator','both_validators'])==expected
for label,expected in [('bmi',(21645,3877,2766)),('obesity',(18235,150,105))]:
 assert tuple(mr[label][k] for k in ['estimable_tests','fdr_hits','full_selected_family_sensitivity_hits'])==expected
 s=mr[label];assert sum(s['source_statuses'].values())==s['features_accounted']==s['selected_significant_isoforms'];assert sum(s['pair_statuses'].values())==s['source_statuses']['prepared']

figures=[
 ('figure1-study-flow.png','Figure 1. Study design and analytical scope','Counts refer to transcripts, not unique participants or genes. FHS, CARDIA and WHI form the primary whole-blood analysis; JHS PBMCs provide supplementary sensitivity. Network and mediation remain planned.'),
 ('figure4-meta-forest-v2.png','Figure 2. Cohort-specific and pooled associations','Cohort-specific and pooled coefficients for the two lowest-P transcripts per outcome. Squares show cohort estimates and normal-theory 95% confidence intervals. Blue diamonds indicate fixed-effect pooling and open diamonds the DerSimonian-Laird sensitivity. Panels retain supplied coefficient scales and have different axes.'),
 ('figure2-effect-comparisons-publication.png','Figure 3. Comparison of cohort coefficients','Cohort coefficient comparisons across the complete primary transcript universe. Top panels show BMI and bottom panels obesity. No significance filter was required for inclusion. Dense central points overlap; coefficients retain their source model scale.'),
 ('figure3-enrichment-publication-v2.png','Figure 4. Functional annotations of associated transcript sets','Leading GO and Reactome annotations. Four lowest-FDR terms were selected within each outcome and collection, with duplicate term identifiers collapsed for display. Color encodes '+r'\(-\log_{10}\mathrm{FDR}\)'+', capped at 300; blue means a lower significance score, not a negative effect. Terms share genes and are not independent discoveries.')]
data['figures']=[]
for name,title,caption in figures:
 source=use(SCI/'artifacts/figures'/name,'unchanged scientific figure');shutil.copy2(source,A/'figures'/name)
 assert sha(source)==sha(A/'figures'/name);data['figures'].append({'file':name,'title':title,'caption':caption})
save(M/'authored-content.json',data)
plain=['Abstract']+[f'{k}: {v}' for k,v in data['abstract'].items()]
for b in data['blocks']:
 if b['kind']=='table':
  if b['id']=='population':plain.append('[TABLE_1]')
  else:plain.append(data['tables'][b['id']]['title'])
 else:plain.append(b['text'])
plain+=['References']+data['references']+['Supplementary Methods']+[b['text'] for b in data['supplement']]
(A/'manuscript-draft.txt').write_text('\n\n'.join(plain)+'\n',encoding='utf-8')
jar=use(B/'scripts/omicsmatic-0.3.7.jar','frozen current executable')
prompt=use(B/'scripts/manuscript-writing-v1.txt','frozen current prompt source')
with zipfile.ZipFile(jar) as z:
 packed=z.read('org/jlinalg/study/manuscript-writing-v1.txt').replace(b'\r\n',b'\n')
 assert packed==prompt.read_bytes().replace(b'\r\n',b'\n')
 assert packed.startswith(b'OmicsMatic manuscript writing template 2026-11-14.5')
command(['javac','-encoding','UTF-8','-cp',jar,'-d',M/'java-classes',B/'scripts/ManuscriptCurrentBridge.java'])
command(['java','-cp',str(M/'java-classes')+';'+str(jar),'org.jlinalg.cli.ManuscriptCurrentBridge',B])
native=json.loads((A/'native-render/review.json').read_text());assert native['abstract_review']['input_abstract_count']==1
assert native['mathematics_review']['equation_count']>0
assert native['motivation_review']['status']=='substantive_review_required'
assert native['discussion_review']['status']=='substantive_review_required'
java=command(['java','-version']);javac=command(['javac','-version'])
save(M/'input-provenance.json',INPUTS)
save(M/'software_versions.json',{'os':platform.platform(),'architecture':platform.machine(),'python':sys.version,'python_executable':sys.executable,'java':java,'javac':javac,'omicsmatic':'0.3.7','template':'2026-11-14.5','jar_sha256':sha(jar),'prompt_sha256':hashlib.sha256(packed).hexdigest(),'math_renderer':'KaTeX 0.19.0 to native MathML','snapshot':'Output-local scripts, executable and authoring inputs; exact browser and PDF tool versions added after export.'})
review="""Editorial revision for review

The Introduction now begins with the splicing-BMI biological question. Paragraph 3 explains transcript ratios as an interpretable proxy and fairly distinguishes them from junction-based measurements. Paragraph 4 states the consequential gap and potential scientific impact; paragraph 5 connects it to the actual design.

The Discussion integrates relative-usage associations, opposing transcript signs, replication with heterogeneity, PBMC non-transfer and genetic prioritization. It explains the contribution relative to prior studies and proposes a staged, discriminating validation program. The strong whole-blood/PBMC contrast is highlighted without manufacturing an undocumented prior expectation or calling it a prespecified surprise. Opposing ratio signs are interpreted with their shared-denominator constraint. No causal effect or method superiority is inferred from statistical significance alone.

Mathematics uses the current native offline renderer: summations, fractions, roots, Greek symbols, powers, scientific notation and inclusive inequality signs. The analysis values, gene/transcript identifiers, fixed-effect versus random-effects distinction, MR multiplicity families and strict descriptive BMI >30 threshold are preserved. References are renumbered by first appearance; inherited encoding damage is repaired; LeafCutter is added as the primary reference for the junction-method comparison.

Author inputs still needed: verified analysis membership and visits; ethics/consent; complete cohort models, transformations, annotation and quantification; confirmation of association obesity coding versus the strict descriptive threshold; authorship and final scientific review. Network and mediation remain unperformed. Statistical analyses were not rerun.

Generation route: assistant-authored revision applying the exact current template; current OmicsMatic native manuscript/math renderer and writing reviews, followed by local browser PDF export. This is not a live provider-integration test or a new full study auto run. The native review remains substantive_review_required; structural tests do not certify scientific interpretation or persuasive quality.
"""
(A/'editorial-review.txt').write_text(review,encoding='utf-8')
save(L/'scientific-validation.json',{'main_words_approximate':sum(len(b.get('text','').split()) for b in data['blocks']),'abstract_words_approximate':sum(len(t.split()) for t in data['abstract'].values()),'references':len(data['references']),'tables':4,'figures':4,'meta_table_values_match_source':True,'mr_table_values_match_source':True,'discovery_meta_and_mr_counts_match_source':True,'mr_status_accounting_complete':True,'figures_unchanged':True,'current_template_verified':True,'new_association_or_mr_runs':False,'input_hashes_unchanged':all(sha(v['path'])==v['sha256'] for v in INPUTS)})
print(json.dumps({'bundle':str(B),'template':'2026-11-14.5','main_words':sum(len(b.get('text','').split()) for b in data['blocks']),'references':len(data['references']),'native_review':'completed; substantive author review required'},indent=2))
