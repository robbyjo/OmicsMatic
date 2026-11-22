from pathlib import Path
import hashlib,json,shutil,subprocess
from pypdf import PdfReader
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'plot-review-1';B.mkdir();images=B/'tmp/pdfs';images.mkdir(parents=True);poppler=Path('C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1119-907-0_26.1119.907.0_x64__3k8sg7r9htsxt/dependencies/native/poppler/Library/bin');source=F/'workflow-packaged-3';records=[]
for case in ['post-plot','post-correlation-plot','post-eqtl-plot','post-scatter-plot']:
 for file in sorted((source/case/'worker/out').glob('*.pdf')):
  reader=PdfReader(file);name=case+'-'+file.stem;prefix=images/name;args=[str(poppler/'pdftoppm.exe'),'-r','110','-png',str(file),str(prefix)];result=subprocess.run(args,cwd=T,capture_output=True);(B/(name+'.stderr')).write_bytes(result.stderr);assert result.returncode==0
  pngs=sorted(images.glob(name+'-*.png'));assert len(pngs)==len(reader.pages)
  record=dict(case=case,source_pdf=str(file),source_sha256=hashlib.sha256(file.read_bytes()).hexdigest(),pages=len(reader.pages),pngs=[str(p) for p in pngs],texts=[page.extract_text() for page in reader.pages]);records.append(record);print(name,len(reader.pages),flush=True)
(B/'RENDERED.json').write_text(json.dumps(dict(status='ORIGINAL_FUSION_PLOTS_RENDERED_VISUAL_REVIEW_PENDING',records=records,original_PDFs_modified=False),indent=2)+'\n')