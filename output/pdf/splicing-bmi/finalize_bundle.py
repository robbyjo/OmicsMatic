from pathlib import Path
import json,hashlib,subprocess,sys,re,platform
from datetime import datetime,timezone
from pypdf import PdfReader
from PIL import Image

B=Path(__file__).resolve().parents[1];P=B.parents[1];M=B/'metadata';L=B/'logs';A=B/'artifacts'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(p,v):p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for d in iter(lambda:f.read(1048576),b''):h.update(d)
    return h.hexdigest()
params=load(M/'parameters.json');old=Path(params['scientific_predecessor'])
s=load(old/'artifacts/analysis-summary.json');mr=load(old/'artifacts/expanded-mr-summary.json')
assert [s[p]['common_universe'] for p in ['BMI','Obesity']]==[224365,223927]
assert [s[p]['fhs_discoveries'] for p in ['BMI','Obesity']]==[39262,25531]
assert [s[p]['meta_fdr_hits'] for p in ['BMI','Obesity']]==[61299,48973]
assert [s[p]['random_effects_fdr_hits'] for p in ['BMI','Obesity']]==[29150,22981]
for ph,counts in [('bmi',(61299,21645,3877,2766)),('obesity',(48973,18235,150,105))]:
    assert tuple(mr[ph][k] for k in ['selected_significant_isoforms','estimable_tests','fdr_hits','full_selected_family_sensitivity_hits'])==counts
    assert mr[ph]['single_instrument_tests']+mr[ph]['multi_instrument_tests']==mr[ph]['estimable_tests']
    assert sum(mr[ph]['source_statuses'].values())==mr[ph]['selected_significant_isoforms']
for inp in load(M/'input-provenance.json'):assert sha(Path(inp['path']))==inp['sha256'],inp['path']
pdf=A/'Splicing_BMI_current_outline_20261114.pdf';r=PdfReader(pdf)
prior=PdfReader(B/'previews/authoring-v1'/pdf.name)
assert len(r.pages)==len(prior.pages)==19
assert all(r.pages[i].extract_text()==prior.pages[i].extract_text() for i in range(1,19))
pop=Path(r'C:\Program Files\WindowsApps\OpenAI.CodexPrimaryRuntime.v26-1112-611-0_26.1112.611.0_x64__3k8sg7r9htsxt\dependencies\native\poppler\Library\bin\pdftoppm.exe')
ver=subprocess.run([str(pop),'-v'],capture_output=True,text=True)
versions=load(M/'software_versions.json');versions['poppler']=ver.stdout+ver.stderr;save(M/'software_versions.json',versions)
validation=load(L/'validation.json')
validation['visual_review']={'status':'pass','pages':list(range(1,20)),'method':'Rendered every page with Poppler; assistant inspected contact sheets for pages 1-19 and the revised first page at full render size. Pages 2-19 text and pagination are identical to the inspected first pass.','findings':'No clipped text, overlapping elements, unreadable glyphs, detached table captions, or broken figure labels observed.'}
validation['source_cross_checks']={'major_counts_and_MR_accounting':'pass','all_recorded_inputs_unchanged':'pass','pages_2_to_19_unchanged_after_abstract_revision':True}
validation['native_lexical_cues_review']='Three generic-caution cues retained: each addresses a material distinction (isoform-specific evidence, PBMC transfer, or mechanism inference). No automatic caution deletion.'
save(L/'validation.json',validation)
completion=datetime.now(timezone.utc).isoformat();params['completion']=completion;params['final_build']='scripts/build_manuscript_v2.py (executes frozen build_manuscript.py with expanded abstract)';save(M/'parameters.json',params)
readme=f'''# Splicing-BMI manuscript using the current writing outline

Audit status: complete. Scientific status: working manuscript for author review.
Started {params['start']}; completed {completion}.

## Purpose and result

Generate a readable PDF using OmicsMatic writing template 2026-11-14.2 and the latest completed corrected Splicing-BMI evidence. The final PDF has 19 pages, four tables, four unchanged scientific figures, 27 cited references, and Supplementary Methods. Main text: {validation['main_text_words']} words (including headings/availability); abstract: {validation['abstract_words']} words. FHS, CARDIA and WHI whole blood are primary; JHS PBMCs remain supplementary. The latest analysis is the 20 October 2026 corrected expanded-MR bundle, not the historical four-cohort draft used to motivate the template.

## Generation route

The assistant authored the prose by applying the current runtime template. The packaged template exactly matched the current repository resource. The output-local Java bridge called the actual current StudyAssistance manuscript renderer and StudyManuscriptWriting/StudyAbstract checks with native participant-summary objects. No external model provider was called by project software. This is not a full study auto-pipeline execution, a native ledger revision, a live provider-integration test, or a new scientific analysis. ReportLab typeset the PDF from the same authored blocks, with native participant values checked and every displayed estimate drawn from recorded inputs. Writing quality remains a scientific/editorial judgment.

## Inputs and preserved definitions

Scientific source: {old}. Provisional participant summaries: {params['participant_source']}. Exact paths, roles, sizes and SHA-256 hashes are in input-provenance.json. All recorded inputs were rehashed after generation and remained unchanged. Figures are unmodified copies. The participant table preserves original available-observation means/sample SDs and two-decimal presentation; the phenotype-file N is explicitly not an association-population N. The descriptive BMI >30 threshold is distinguished from the inherited >=30 association protocol. No participant records were used or transmitted by this authoring run. Public searches used only general publication topics and identifiers.

## Commands and reproduction

Working directory: {B}. Python executable: {sys.executable}.
1. Run scripts/build_manuscript.py in a fresh copy of the bundle structure containing the frozen scripts/JAR and accessible source inputs.
2. Run scripts/build_manuscript_v2.py; it preserves the first-pass PDF/text/checks and expands abstract abbreviations without changing the body.
3. Run scripts/render_check_v1.py to render every page with the exact Poppler path recorded there. Inspect all rendered pages.
4. Run scripts/finalize_bundle.py only after confirming equivalent visual QA; this script records the inspected state, validates inputs/counts, registers the output, and seals the manifest.

Use a new output directory for reproduction; do not rerun into this sealed bundle. The exact commands, stdout/stderr, statuses and runtimes for Java are in logs/commands.json. Build and render logs accompany both authoring passes. Creation involved no random calculations; prior scientific seeds and thresholds are retained in the manuscript and source bundle.

## Artifacts

- artifacts/Splicing_BMI_current_outline_20261114.pdf: final requested reading manuscript, including scientific supplement.
- artifacts/editorial-review.txt: concise improvements and remaining author inputs.
- artifacts/manuscript-draft.txt and metadata/authored-content.json: exact authored content and formatted table values.
- artifacts/native-render/: current native HTML reading copy, participant-table outputs, template identity, and structural/lexical reviews. These checks do not certify scientific validity.
- artifacts/participant-characteristics-source.csv: full-precision source summary rows, not participant-level data.
- artifacts/figures/: four exact source figure copies.
- previews/: page renders/contact sheets plus the first authoring pass; final PDF previews are page-01.png through page-19.png.

## Validation and limitations

Structural checks passed: one structured abstract, population-table marker, all four tables and figures, key result counts, corrected genetic-source discussion, adverse PBMC evidence, and valid glyphs. Major universe/discovery/meta/MR counts and complete MR accounting were independently checked against the retained summaries. All 19 pages were visually inspected; the final abstract was separately inspected, with pages 2-19 unchanged. The current native population review correctly remains needs_input because analysis membership is unverified. Three caution cues were reviewed and retained for material scientific qualifications.

Author completion is required for population membership, visits, ethics/consent, exact upstream models/scales, annotation, authorship and final scientific appraisal. Network and mediation are unperformed placeholders. The study is not submission-ready solely because the PDF and audit are complete. Reference additions were bounded primary-source checks, not a systematic literature review; inherited references remain attributed to the previous manuscript. See reference-review.json.

## Software

Windows; Python {platform.python_version()}; ReportLab {versions['reportlab']}; pypdf {versions['pypdf']}; Pillow {versions['Pillow']}; python-docx {versions['python-docx']}; Java 25+37-LTS-3491; OmicsMatic 0.3.7 current working build, template 2026-11-14.2; Poppler identity in software_versions.json. Original analysis software remains JLinAlg 0.3.6 a8d5052 with Java 21.0.12.1 and supporting Python versions recorded by the predecessor. Jar/resource hashes and exact imported versions are in software_versions.json. Code and all authoring inputs are frozen in scripts; no application source was edited. The first-pass build is retained for chronology.

The searchable project outputs/index.html registers this bundle. SESSION_HISTORY.md has an appended entry. The manifest covers local scripts, metadata, artifacts, logs and previews, plus referenced inputs; it excludes its own hash to avoid recursion.
'''
(M/'README.md').write_text(readme,encoding='utf-8')
record={'bundle':B.name,'purpose':'Current-outline Splicing-BMI manuscript PDF: clearer rationale and interpretation, structured abstract, provisional Table 1, corrected three-cohort results and scientific supplement.','generated':completion,'status':'complete','status_reason':'Artifact provenance and visual QA complete; scientific author-review draft with unverified participant membership and upstream methods.','tags':['TOPMed','BMI','obesity','FHS','CARDIA','WHI','JHS PBMC sensitivity','manuscript','PDF','template 2026-11-14.2'],'primary_artifacts':['artifacts/Splicing_BMI_current_outline_20261114.pdf','artifacts/editorial-review.txt','artifacts/native-render/manuscript.html']}
save(M/'index_record.json',record)
cmd=[sys.executable,str(B/'scripts/update_index_scoped_v3.py'),'--outputs',str(P/'outputs'),'--timestamp',completion]
rr=subprocess.run(cmd,cwd=P,capture_output=True,text=True,encoding='utf-8',errors='replace');save(L/'index-update.json',{'command':cmd,'cwd':str(P),'stdout':rr.stdout,'stderr':rr.stderr,'exit_code':rr.returncode})
if rr.returncode:raise RuntimeError(rr.stdout+rr.stderr)
with (P/'SESSION_HISTORY.md').open('a',encoding='utf-8') as f:
    f.write(f'\n\n## {completion} - Current-outline Splicing-BMI PDF\n\nRequested a manuscript PDF demonstrating the current outline. Created outputs/{B.name}/ with a 19-page PDF, four tables, four figures, scientific supplement, native HTML/checks, exact authoring scripts/JAR, source hashes, logs, and page previews. Used template 2026-11-14.2 and the corrected 20 October three-whole-blood-cohort evidence; no association/MR refit or source changes. Assistant-authored prose passed through the current local native renderer/checks, followed by ReportLab PDF typesetting. This was not a complete study auto run or provider integration test. All structural/count/input-preservation checks passed and all pages were visually reviewed. Participant membership and upstream methods remain unresolved; network/mediation remain pending. Software and exact commands: outputs/{B.name}/metadata/software_versions.json and logs/. Updated outputs/index.html using the frozen scoped updater.\n')
manifest=[]
for p in sorted(B.rglob('*')):
    if not p.is_file() or p==M/'manifest.json':continue
    st=p.stat();rel=p.relative_to(B).as_posix()
    manifest.append({'path':rel,'role':rel.split('/')[0],'bytes':st.st_size,'modified':datetime.fromtimestamp(st.st_mtime,timezone.utc).isoformat(),'sha256':sha(p)})
save(M/'manifest.json',{'bundle':B.name,'generated':completion,'status':'complete','files':manifest,'external_inputs':load(M/'input-provenance.json'),'self_hash':'omitted to avoid recursion'})
assert all((B/x['path']).exists() for x in manifest)
print(json.dumps({'pdf':str(pdf),'pages':19,'manifest_files':len(manifest),'source_inputs_unchanged':True,'index':rr.stdout.strip(),'pdf_sha256':sha(pdf)},indent=2))
