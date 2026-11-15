"""Register and seal only after actual visual inspection of every rendered page."""
from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,subprocess,sys

assert '--visual-qa-complete' in sys.argv,'Visual inspection must precede sealing.'
B=Path(__file__).resolve().parents[1];M=B/'metadata';L=B/'logs';P=B.parents[1]
def sha(p):
 h=hashlib.sha256()
 with Path(p).open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
def save(p,x):Path(p).write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
now=datetime.now(timezone.utc).isoformat();params=json.loads((M/'parameters.json').read_text());versions=json.loads((M/'software_versions.json').read_text());validation=json.loads((L/'pdf-validation.json').read_text());scientific=json.loads((L/'scientific-validation.json').read_text());inputs=json.loads((M/'input-provenance.json').read_text())
assert all(sha(v['path'])==v['sha256'] for v in inputs)
assert validation['preview_count']==validation['pages']
validation['visual_review']='All PDF page PNGs inspected by the authoring assistant: equations, symbols, table/figure legibility, pagination and references checked.';validation['completion']=now;save(L/'pdf-validation.json',validation)
save(M/'reference-review.json',{'date':now,'new_reference':{'title':'Annotation-free quantification of RNA splicing using LeafCutter','doi':'10.1038/s41588-017-0004-9','url':'https://www.nature.com/articles/s41588-017-0004-9','basis':'Primary publisher abstract and bibliographic record; bounded contrast with transcript-ratio interpretation.'},'rechecked_context':[{'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC12756580/','basis':'Published human adipocyte study; differential splicing and expression are only partly overlapping.'},{'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC6066754/','basis':'Primary abstract: mouse embryonic Pcbp2/Runx1 splicing and erythropoiesis; not validation of the human measured isoform.'},{'url':'https://pmc.ncbi.nlm.nih.gov/articles/PMC3289123/','basis':'Primary abstract and sample-source description: whole blood and PBMC profiles differ.'},{'url':'https://www.nature.com/articles/s41467-025-64336-8','basis':'Indexed publisher and PubMed abstract; whole-blood inherited regulation of isoform ratios.'}],'remaining_references':'Inherited bibliography; no comprehensive systematic review or revalidation of every historical reference claimed. Encoding punctuation repaired, numerical citations renumbered by first appearance.','private_research_data_transmitted':False})
readme=f'''# Splicing-BMI revised manuscript

Requested a new PDF demonstrating the current manuscript outline, persuasive Introduction and Discussion, and mathematical typography.

Started: {params['started']}. Completed: {now}.

## Deliverable and route

`artifacts/Splicing_BMI_revised_manuscript.pdf` is the {validation['pages']}-page author-review manuscript with four tables, four unchanged scientific figures, {scientific['references']} references and Supplementary Methods. Main text is approximately {scientific['main_words_approximate']} words, with an approximately {scientific['abstract_words_approximate']}-word structured abstract. The exact authored content is in `metadata/authored-content.json`; readable HTML and text are in `artifacts/`.

The assistant applied the exact current template to the preserved evidence. The output-local Java bridge used the current OmicsMatic manuscript/math renderer and native writing/abstract reviews from the frozen JAR. Local browser export preserves the resulting MathML in PDF. This is an attributed editorial revision, not a full study auto run, a live external-provider integration test or a new scientific analysis. Native reviews still require substantive scientific appraisal.

The Introduction explains the splicing-BMI question, why ratios are an interpretable proxy against annotated transcripts, the remaining gap and potential impact. The Discussion integrates findings, implications, context dependence, grounded weaknesses and discriminating next steps. The sharp PBMC contrast is highlighted without inventing a prior expectation or declaring an unsupported surprise. All new interpretive statements about opposing transcript signs are grounded in the retained source table.

## Scientific inputs and limits

Scientific predecessor: `{params['scientific_source']}`. Editorial predecessor: `{params['previous_manuscript']}`. Both remain unchanged. FHS/CARDIA/WHI whole blood are primary; JHS PBMC remains supplementary. Corrected derived irQTL inputs and outcome-specific MR families are retained; no new source corrections or fits were performed. Original source results, transformations, thresholds and seeds are described in the manuscript and upstream provenance; exact direct inputs, byte sizes and hashes are in `input-provenance.json`, with key source summaries snapshotted in `metadata/source-results/`.

The participant table describes supplied phenotype-file populations, not verified analysis membership. Its strict BMI >30 definition remains distinct from the inherited â‰¥30 association protocol. Actual analysis masks, visits, ethics/consent, upstream model scales and RNA quantification details still require author completion. No participant-level records were read for this authoring run. Network and mediation remain unperformed. The PDF is a scientific draft, not submission approval.

## Reproduction and commands

Working directory: `{B}`. Exact executable command arrays, runtimes, output streams and statuses are in `logs/generation-commands-v4.json` and `logs/native-commands-v4.json`. Scripts were copied before execution and preserved unchanged. Initial attempts caught a phenotype-label case mismatch, a Java generic-list compilation error and the absence of two tables from the predecessor text-block list. Versioned fixes and logs remain; the first PDF is retained under previews/authoring-v3. The v4 authoring code explicitly inserts all four checked tables and expands BMI at first abstract use. No scientific values changed. To reproduce, create a new output bundle with the same structure and frozen inputs; do not rerun into this sealed bundle.

1. `scripts/prepare_bundle.py` records the new location and copies the authoring scripts, current JAR, prompt and index updater.
2. `scripts/generate_and_check_v4.py RUNTIME_DEPENDENCIES BROWSER_EXECUTABLE` calls `build_manuscript_v4.py`, compiles/runs the Java bridge, exports via `export_pdf.cjs`, adds PDF provenance metadata and renders page PNGs with Poppler.
3. Visually inspect every page and mathematical expression; native and browser checks are insufficient on their own.
4. `scripts/seal_bundle_v5.py --visual-qa-complete` rehashes inputs, records the actual inspection, registers the output and seals this manifest.

The exact revised Introduction/Discussion and notation edits are in `scripts/author_revision_v4.py`. `scripts/manuscript.css` controls layout. `artifacts/native-render/` preserves the unmodified native draft rendering, participant table, writing policy and reviews. The supplemental scientific claims and estimates come from retained inputs; none were inferred from presentation layout.

## Validation

Every displayed meta-analysis and MR coefficient, interval and adjusted P value was checked against original result rows. Discovery, replication, meta-analysis, MR-family counts and complete MR status accounting were reconciled. Figures match source SHA-256 hashes. Original inputs were rehashed after generation and are unchanged. All {validation['equations']} delimited equations rendered without errors, overflow or external requests. PDF text checks confirm the required results, tables and figures and no unresolved formula pseudocode, ASCII comparison operators or replacement glyphs. All {validation['pages']} pages were visually reviewed; page previews and detailed logs are retained. This does not certify the science or the eventual writing quality of a live model.

## Software

{versions['os']}; Python {versions['python'].split()[0]}; Java/Javac: {versions['java'].splitlines()[0]}, {versions['javac']}; OmicsMatic 0.3.7, template 2026-11-14.5; KaTeX 0.19.0; Node {versions['node']}; Playwright {versions['playwright']}; browser {versions['browser']}; pypdf {versions['pypdf']}; Poppler {versions['poppler'].splitlines()[0]}. Runtime bundle 26.1112.11011. Exact executable paths, prompt and JAR hashes are in `software_versions.json`. Original statistical software remains documented by the upstream scientific bundle.

The project `outputs/index.html` links this deliverable. `SESSION_HISTORY.md` records the session. The manifest covers inputs plus all scripts, artifacts, metadata, logs and previews, excluding its own hash to avoid recursion. Artifact audit status is complete; human scientific review remains pending.
'''
(M/'README.md').write_text(readme,encoding='utf-8')
record={'bundle':B.name,'purpose':'Revised Splicing-BMI manuscript: persuasive splicing/isoform-ratio rationale, integrated Discussion and native equation typesetting.','generated':now,'status':'complete','status_reason':'Artifact provenance and visual QA complete; scientific author-review draft with unresolved upstream methods and analysis membership.','tags':['TOPMed','BMI','obesity','FHS','CARDIA','WHI','JHS PBMC sensitivity','manuscript','PDF','template 2026-11-14.5'],'primary_artifacts':['artifacts/Splicing_BMI_revised_manuscript.pdf','artifacts/manuscript.html','artifacts/editorial-review.txt']}
save(M/'index_record.json',record)
cmd=[sys.executable,str(B/'scripts/update_index_scoped_v3.py'),'--outputs',str(P/'outputs'),'--timestamp',now]
r=subprocess.run(cmd,cwd=P,capture_output=True,text=True,encoding='utf-8',errors='replace');save(L/'index-update.json',{'command':cmd,'cwd':str(P),'stdout':r.stdout,'stderr':r.stderr,'exit_code':r.returncode})
if r.returncode:raise RuntimeError(r.stdout+r.stderr)
with (P/'SESSION_HISTORY.md').open('a',encoding='utf-8') as f:
 f.write(f'\n\n## {now} - Persuasive manuscript and mathematical typography\n\nGenerated outputs/{B.name}/ using current template 2026-11-14.5, the frozen OmicsMatic 0.3.7 JAR/native renderer and corrected 20 October three-whole-blood-cohort evidence. Revised Introduction and Discussion, retained four tables/four source figures, added a primary LeafCutter reference and rendered {validation["equations"]} equations correctly in a {validation["pages"]}-page PDF. No statistical refits, participant-record access or project provider calls. Source values/counts and input hashes passed checks; all pages were visually reviewed. Old outputs remain intact. Analysis membership, upstream methods and final scientific appraisal still require author completion. Exact scripts, commands, versions, inputs and QA: outputs/{B.name}/metadata/README.md and logs/. Updated searchable outputs/index.html.\n')
files=[]
for p in sorted(B.rglob('*')):
 if not p.is_file() or p==M/'manifest.json':continue
 s=p.stat();files.append({'path':p.relative_to(B).as_posix(),'role':p.relative_to(B).parts[0],'bytes':s.st_size,'modified':datetime.fromtimestamp(s.st_mtime,timezone.utc).isoformat(),'sha256':sha(p)})
save(M/'manifest.json',{'created':now,'bundle':B.name,'inputs':inputs,'files':files,'self_hash':'omitted to avoid recursion','status':'complete artifact provenance; scientific review pending'})
assert all((B/item['path']).is_file() and sha(B/item['path'])==item['sha256'] for item in files)
print(json.dumps({'pdf':str(B/'artifacts/Splicing_BMI_revised_manuscript.pdf'),'pages':validation['pages'],'equations':validation['equations'],'manifest_files':len(files),'index_update':json.loads(r.stdout),'status':'complete artifact; author review pending'},indent=2))
