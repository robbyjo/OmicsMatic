"""Generate a local PDF and page previews; sealing requires a later, actual visual inspection."""
from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,os,subprocess,sys
from pypdf import PdfReader,PdfWriter
import pypdf

B=Path(__file__).resolve().parents[1];R=Path(sys.argv[1]);BROWSER=Path(sys.argv[2]);logs=[]
def save(p,x):Path(p).write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def run(cmd,env=None):
 start=datetime.now(timezone.utc).isoformat();r=subprocess.run(list(map(str,cmd)),cwd=B,capture_output=True,text=True,encoding='utf-8',errors='replace',env=env)
 logs.append({'command':list(map(str,cmd)),'cwd':str(B),'start':start,'completion':datetime.now(timezone.utc).isoformat(),'exit_code':r.returncode,'stdout':r.stdout,'stderr':r.stderr});save(B/'logs/generation-commands.json',logs)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip() or r.stderr.strip()
run([sys.executable,B/'scripts/build_manuscript.py'])
env=os.environ.copy();env['NODE_PATH']=str(R/'node/node_modules');env['MANUSCRIPT_BROWSER']=str(BROWSER)
run([R/'node/bin/node.exe',B/'scripts/export_pdf.cjs',B],env)
source=B/'previews/manuscript-unsealed.pdf';reader=PdfReader(source)
writer=PdfWriter();writer.clone_document_from_reader(reader)
title=json.loads((B/'metadata/authored-content.json').read_text(encoding='utf-8'))['title']
writer.add_metadata({'/Title':title,'/Author':'Author-review draft; author list pending','/Subject':'Splicing-BMI manuscript. Bundle '+B.name+'; provenance: ../metadata/README.md','/Keywords':'BMI, obesity, isoform ratios, alternative splicing, TOPMed','/Creator':'OmicsMatic 0.3.7; writing template 2026-11-14.5; KaTeX 0.19.0; local browser export; pypdf '+pypdf.__version__})
pdf=B/'artifacts/Splicing_BMI_revised_manuscript.pdf'
with pdf.open('wb') as f:writer.write(f)
reader=PdfReader(pdf);text='\n'.join(p.extract_text() for p in reader.pages)
(B/'logs/pdf-text.txt').write_text(text,encoding='utf-8')
assert len(reader.pages)>5
for token in ['61,299','48,973','21,645','18,235','3,877','2,433,662','ENST00000553064.6','Table 1.','Table 2.','Table 3.','Table 4.','Figure 1.','Figure 2.','Figure 3.','Figure 4.','Supplementary Methods']:
 assert token in text,token
for bad in ['sum(w beta)','sqrt(1/sum(w))','<=','>=','\ufffd','equation could not be typeset','[TABLE_1]']:
 assert bad not in text,bad
assert text.count('Abstract')==1
browser=json.loads((B/'logs/browser-validation.json').read_text(encoding='utf-8'))
assert browser['mathStatus']=='complete' and not browser['errors'] and not browser['remoteRequests']
versions=json.loads((B/'metadata/software_versions.json').read_text(encoding='utf-8'))
versions.update({'node':browser['node'],'playwright':browser['playwright'],'browser':browser['browser'],'browser_executable':str(BROWSER),'pypdf':pypdf.__version__,'poppler':run([R/'native/poppler/Library/bin/pdftoppm.exe','-v']),'runtime_bundle':'26.1112.11011','runtime_path':str(R)})
save(B/'metadata/software_versions.json',versions)
params=json.loads((B/'metadata/parameters.json').read_text(encoding='utf-8'));params['render_environment']={'NODE_PATH':env['NODE_PATH'],'MANUSCRIPT_BROWSER':env['MANUSCRIPT_BROWSER']};save(B/'metadata/parameters.json',params)
run([R/'native/poppler/Library/bin/pdftoppm.exe','-scale-to','1450','-png',pdf,B/'previews/page'])
save(B/'logs/pdf-validation.json',{'pages':len(reader.pages),'equations':browser['equations'],'all_equations_rendered':True,'required_results_tables_figures_present':True,'no_ascii_comparison_or_pseudocode':True,'no_replacement_glyph':True,'browser_external_requests':0,'preview_count':len(list((B/'previews').glob('page-*.png'))),'visual_review':'pending'})
print(json.dumps({'bundle':str(B),'pages':len(reader.pages),'equations':browser['equations'],'pdf':str(pdf),'visual_review':'pending'},indent=2))
