from pathlib import Path
import hashlib,json,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'saved-fixture-1';O.mkdir()
sources={p.name:p/'analysis' for folder in ['java-probe-2','java-probe-extended-1','java-variants-1'] for p in (B/folder).iterdir() if p.is_dir() and json.loads((p/'analysis/analysis-status.json').read_bytes())['results_admitted']};sources['PROVIDED_LIBRARIES_FACTORS']=B/'java-variants-recovery-1/analysis';assert len(sources)==45
blobs={};cases={}
for name,root in sorted(sources.items()):
    files={}
    for path in sorted(root.rglob('*')):
        if not path.is_file():continue
        raw=path.read_bytes();sha=hashlib.sha256(raw).hexdigest();assert sha not in blobs or blobs[sha]==raw;blobs[sha]=raw;files[path.relative_to(root).as_posix()]=dict(bytes=len(raw),sha256=sha)
    cases[name]=dict(files=files,operation=json.loads((root/'request.json').read_bytes())['operation'])
fixture=dict(schema_version=1,status='SYNTHETIC_ORIGINAL_WORKFLOW_CAPTURES_FOR_OFFLINE_VERIFICATION',case_count=45,cases=cases,qualifies_only_saved_fixture_replay_and_arithmetic=True,original_runtime_analysis_tested_separately=True,whole_tool_or_paper_qualification=False)
target=T/'src/test/resources/org/jlinalg/cli';target.mkdir(parents=True,exist_ok=True);archive=target/'limma-workflow-originals.zip'
with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED) as z:
    z.writestr('FIXTURE.json',json.dumps(fixture,indent=2)+'\n')
    for sha,raw in sorted(blobs.items()):z.writestr('blobs/'+sha,raw)
(O/'COMPLETED.json').write_text(json.dumps(dict(status='SAVED_45_SYNTHETIC_ORIGINAL_WORKFLOW_CAPTURES',cases=45,unique_blobs=len(blobs),logical_files=sum(len(c['files']) for c in cases.values()),archive=dict(bytes=archive.stat().st_size,sha256=hashlib.sha256(archive.read_bytes()).hexdigest())),indent=2)+'\n',encoding='utf-8',newline='\n');print((O/'COMPLETED.json').read_text(),flush=True)
