"""Display-only precision revision; exact source values remain in the predecessor and snapshots."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, json, os, platform, re, shutil, subprocess, sys, time, zipfile
import pypdf
from pypdf import PdfReader, PdfWriter
from PIL import Image, ImageChops
import PIL

B = Path(__file__).resolve().parents[1]
M, A, L, V, S = [B / x for x in ['metadata', 'artifacts', 'logs', 'previews', 'scripts']]
P = B.parents[1]
OLD = P / 'outputs/2026-11-14T214405-0500-persuasive-typeset-manuscript'
R = Path('C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1112-611-0_26.1112.611.0_x64__3k8sg7r9htsxt/dependencies')
BROWSER = Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
EDITS = {'was 1.482602218505602 times': 'was approximately 1.48 times',
         'divided by 1.348979500392164 as': 'divided by approximately 1.35 as'}
COMMANDS = []
def now(): return datetime.now(timezone.utc).isoformat()
def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for part in iter(lambda: stream.read(1024*1024), b''): h.update(part)
    return h.hexdigest()
def save(path, value): path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
def read(path): return json.loads(path.read_text(encoding='utf-8'))
def run(args, env=None):
    start = time.monotonic()
    r = subprocess.run([str(x) for x in args], cwd=B, env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    COMMANDS.append({'command': [str(x) for x in args], 'cwd': str(B), 'elapsed_seconds': time.monotonic()-start,
                     'completed': now(), 'exit_code': r.returncode, 'stdout': r.stdout, 'stderr': r.stderr})
    save(L/'commands.json', COMMANDS)
    if r.returncode: raise RuntimeError(r.stdout+r.stderr)
    return r.stdout.strip() or r.stderr.strip()

def generate():
    started = now(); inputs = []
    def record(path, role):
        s = path.stat()
        inputs.append({'path': str(path), 'role': role, 'bytes': s.st_size, 'modified': datetime.fromtimestamp(s.st_mtime, timezone.utc).isoformat(), 'sha256': sha(path)})
    for rel in ['metadata/authored-content.json', 'metadata/native-population-tables.json', 'metadata/manifest.json',
                'metadata/README.md', 'logs/scientific-validation.json', 'artifacts/manuscript-draft.txt',
                'artifacts/participant-characteristics-source.csv', 'artifacts/Splicing_BMI_revised_manuscript.pdf']:
        record(OLD/rel, 'verified predecessor input')
    shutil.copy2(OLD/'metadata/authored-content.json', M/'source-authored-content.json')
    shutil.copy2(OLD/'metadata/native-population-tables.json', M/'native-population-tables.json')
    shutil.copy2(OLD/'metadata/manifest.json', M/'predecessor-manifest.json')
    shutil.copy2(OLD/'logs/scientific-validation.json', M/'predecessor-scientific-validation.json')
    shutil.copy2(OLD/'artifacts/participant-characteristics-source.csv', A/'participant-characteristics-source.csv')
    authored = (OLD/'metadata/authored-content.json').read_text(encoding='utf-8')
    plain = (OLD/'artifacts/manuscript-draft.txt').read_text(encoding='utf-8')
    for before, after in EDITS.items():
        assert authored.count(before)==1 and plain.count(before)==1, before
        authored = authored.replace(before, after); plain = plain.replace(before, after)
    (M/'authored-content.json').write_text(authored, encoding='utf-8')
    (A/'manuscript-draft.txt').write_text(plain, encoding='utf-8')
    original = read(M/'source-authored-content.json'); current = read(M/'authored-content.json')
    for key in original:
        if key != 'blocks': assert original[key] == current[key], key
    assert len(original['blocks']) == len(current['blocks'])
    changes = []
    for i,(before,after) in enumerate(zip(original['blocks'],current['blocks'])):
        if before!=after:
            expected = json.dumps(before, ensure_ascii=False)
            for old,new in EDITS.items(): expected=expected.replace(old,new)
            assert json.loads(expected)==after
            changes.append({'block_index': i, 'before': before, 'after': after})
    assert len(changes)==1
    save(M/'editorial-changes.json', {'scope':'Two explicitly approximate display constants in one Methods paragraph; computation and all evidence unchanged.', 'changes':changes})
    (A/'figures').mkdir()
    for figure in current['figures']:
        source = OLD/'artifacts/figures'/figure['file']; record(source, 'unchanged scientific figure')
        shutil.copy2(source, A/'figures'/figure['file']); assert sha(source)==sha(A/'figures'/figure['file'])
    jar = S/'omicsmatic-0.3.7.jar'; record(jar, 'frozen current executable')
    with zipfile.ZipFile(jar) as z:
        prompt = z.read('org/jlinalg/study/manuscript-writing-v1.txt').replace(b'\r\n',b'\n')
    assert prompt.startswith(b'OmicsMatic manuscript writing template 2026-11-14.6')
    assert prompt==(S/'manuscript-writing-v1.txt').read_bytes().replace(b'\r\n',b'\n')
    run(['javac','-encoding','UTF-8','-cp',jar,'-d',M/'java-classes',S/'ManuscriptCurrentBridge.java'])
    run(['java','-cp',str(M/'java-classes')+';'+str(jar),'org.jlinalg.cli.ManuscriptCurrentBridge',B])
    native=read(A/'native-render/review.json')
    assert 'excessive decimal precision' not in json.dumps(native['mathematics_review'])
    env=os.environ.copy(); env['NODE_PATH']=str(R/'node/node_modules'); env['MANUSCRIPT_BROWSER']=str(BROWSER)
    run([R/'node/bin/node.exe',S/'export_pdf.cjs',B],env)
    browser=read(L/'browser-validation.json')
    assert browser['mathStatus']=='complete' and browser['equations']==55 and not browser['errors']
    writer=PdfWriter(); writer.clone_document_from_reader(PdfReader(V/'manuscript-unsealed.pdf'))
    writer.add_metadata({'/Title':current['title'], '/Author':'Author-review draft; author list pending',
                         '/Subject':'Display precision revision; bundle '+B.name+'; provenance ../metadata/README.md',
                         '/Creator':'OmicsMatic 0.3.7; template 2026-11-14.6; KaTeX 0.19.0; Edge '+browser['browser']+'; pypdf '+pypdf.__version__})
    pdf=A/'Splicing_BMI_revised_manuscript.pdf'
    with pdf.open('wb') as f: writer.write(f)
    pages=PdfReader(pdf).pages; text='\n'.join(p.extract_text() for p in pages)
    (L/'pdf-text.txt').write_text(text,encoding='utf-8')
    for old in ['1.482602218505602','1.348979500392164','<=','>=','\ufffd']: assert old not in text
    assert 'approximately 1.48' in text and 'approximately 1.35' in text
    # Equality after whitespace normalization proves all remaining PDF text is retained.
    oldtext='\n'.join(p.extract_text() for p in PdfReader(OLD/'artifacts/Splicing_BMI_revised_manuscript.pdf').pages)
    for old,new in EDITS.items(): oldtext=oldtext.replace(old,new)
    assert re.sub(r'\s+','',oldtext)==re.sub(r'\s+','',text), 'Unexpected PDF text change'
    run([R/'native/poppler/Library/bin/pdftoppm.exe','-scale-to','1450','-png',pdf,V/'page'])
    identical=[]; changed=[]
    for i in range(1,len(pages)+1):
        previous=OLD/'previews'/f'page-{i:02d}.png'; record(previous,'previously inspected page used for pixel comparison')
        with Image.open(previous) as old, Image.open(V/f'page-{i:02d}.png') as new:
            same=old.size==new.size and ImageChops.difference(old.convert('RGB'),new.convert('RGB')).getbbox() is None
        (identical if same else changed).append(i)
    save(L/'pdf-validation.json',{'pages':len(pages),'equations':browser['equations'],'only_two_intended_text_changes':True,
         'source_tables_figures_and_other_sections_unchanged':True,'native_long_decimal_cues':0,
         'pixel_identical_to_reviewed_predecessor':identical,'pages_requiring_visual_review':changed,'visual_review':'pending'})
    versions={'os':platform.platform(),'architecture':platform.machine(),'python':sys.version,'python_executable':sys.executable,
              'java':run(['java','-version']),'javac':run(['javac','-version']),'node':browser['node'],'playwright':browser['playwright'],
              'browser':browser['browser'],'browser_executable':str(BROWSER),'pypdf':pypdf.__version__,'Pillow':PIL.__version__,
              'poppler':run([R/'native/poppler/Library/bin/pdftoppm.exe','-v']),'runtime_bundle':'26.1112.11011','runtime_path':str(R),
              'omicsmatic':'0.3.7','template':'2026-11-14.6','jar_sha256':sha(jar),'prompt_sha256':hashlib.sha256(prompt).hexdigest(),'math_renderer':'KaTeX 0.19.0'}
    save(M/'software_versions.json',versions)
    save(M/'parameters.json',{'started':started,'predecessor':str(OLD),'edits':EDITS,'scientific_parameters_and_seeds':'unchanged; no fitting or stochastic work',
         'render_environment':{'NODE_PATH':env['NODE_PATH'],'MANUSCRIPT_BROWSER':env['MANUSCRIPT_BROWSER']},'render_scale':1450})
    save(M/'input-provenance.json',inputs)
    assert all(sha(x['path'])==x['sha256'] for x in inputs)
    print(json.dumps(read(L/'pdf-validation.json'),indent=2))

def seal():
    assert '--visual-qa-complete' in sys.argv
    inputs=read(M/'input-provenance.json'); assert all(sha(x['path'])==x['sha256'] for x in inputs)
    validation=read(L/'pdf-validation.json'); validation['visual_review']='Changed pages visually inspected; all other page PNGs pixel-identical to the previously inspected PDF.'
    validation['completed']=now(); save(L/'pdf-validation.json',validation)
    versions=read(M/'software_versions.json'); params=read(M/'parameters.json')
    (M/'README.md').write_text(f'''# Splicing-BMI numerical display revision

Started {params['started']}; completed {validation['completed']}.

The requested correction removes machine-precision decimals from two methodological scaling constants in the manuscript: approximately 1.48 and approximately 1.35. Both are explicitly display approximations. No calculations, thresholds, associations, tables, figures, references or other prose changed. Original constants remain in the source-authored-content.json snapshot and predecessor. All cohort scope and scientific limitations remain as documented in the predecessor. Human scientific review remains pending.

Predecessor: {OLD}

## Artifacts and provenance

artifacts/Splicing_BMI_revised_manuscript.pdf is the corrected {validation['pages']}-page draft. artifacts/manuscript.html and manuscript-draft.txt are reading/source copies. Native renderer and review outputs are under artifacts/native-render; the full-precision participant CSV and four source figures are unchanged. metadata/editorial-changes.json records the complete attributed change. The prior source and scientific validation records are snapshotted; input-provenance.json identifies every direct input and its SHA-256. No participant records, provider calls, new statistical fitting or network transfers occurred.

The current software template 2026-11-14.6 explicitly extends the existing concise display rule to prose and equations. Native review flags excessive decimal literals without silently changing exact cutoffs or identifiers. This is not an automatic scientific precision decision or a live-provider test.

## Commands and verification

Working directory: {B}

The immutable scripts/revise_precision.py ran with argument generate, using {sys.executable}; exact Java, Node, browser and Poppler commands, outputs, status and elapsed times are in logs/commands.json. After inspection it ran with arguments seal --visual-qa-complete. Script/helper/CSS/JAR/prompt and changed software source snapshots are in scripts/. Reproduce into a new bundle, never this sealed directory.

PDF text is exactly the predecessor text after the two approved replacements and whitespace normalization. Parsed tables, figures and all other authored fields are identical. All 55 equations render successfully; no missing images, remote requests or layout overflow were detected. Changed rendered pages {validation['pages_requiring_visual_review']} were inspected; other pages {validation['pixel_identical_to_reviewed_predecessor']} are pixel-identical to previously inspected PNGs. Inputs were rehashed unchanged. The captured pypdf source-reader trailer-size warning does not prevent final parsing, text comparison or Poppler rendering.

## Software

{versions['os']} {versions['architecture']}; Python {versions['python'].split()[0]}; {versions['java'].splitlines()[0]}; {versions['javac']}; OmicsMatic 0.3.7 / template 2026-11-14.6; KaTeX 0.19.0; Node {versions['node']}; Playwright {versions['playwright']}; Edge {versions['browser']}; pypdf {versions['pypdf']}; Pillow {versions['Pillow']}; {versions['poppler'].splitlines()[0]}; runtime bundle 26.1112.11011. Exact hashes and executable paths: software_versions.json. Focused regression/build logs are retained under logs/.

Manifest status describes complete artifact provenance, not submission approval. The project output index and session history link this revision; predecessor files remain intact.
''',encoding='utf-8')
    save(M/'index_record.json',{'bundle':B.name,'purpose':'Splicing-BMI manuscript with concise numerical display; two Methods constants rounded explicitly as approximations.',
        'generated':validation['completed'],'status':'complete','status_reason':'Display correction and artifact QA complete; scientific author review pending.',
        'tags':['Splicing-BMI','manuscript','PDF','precision','template 2026-11-14.6'],
        'primary_artifacts':['artifacts/Splicing_BMI_revised_manuscript.pdf','artifacts/manuscript.html']})
    args=[sys.executable,S/'update_index_scoped_v3.py','--outputs',P/'outputs','--timestamp',validation['completed']]
    r=subprocess.run([str(x) for x in args],cwd=P,capture_output=True,text=True,encoding='utf-8',errors='replace')
    save(L/'index-update.json',{'command':[str(x) for x in args],'cwd':str(P),'stdout':r.stdout,'stderr':r.stderr,'exit_code':r.returncode}); assert r.returncode==0,r.stderr
    with (P/'SESSION_HISTORY.md').open('a',encoding='utf-8') as f:
        f.write(f'\n\n## {validation["completed"]} - Manuscript decimal display correction\n\nCreated outputs/{B.name}/. Two full-precision Methods constants are now approximately 1.48 and approximately 1.35 in the PDF; calculations and all other evidence unchanged. Template 2026-11-14.6 adds prose/equation precision instructions and native review cues. Focused regression/build evidence, exact scripts, input hashes, versions and commands are retained. PDF text differs only by those replacements; changed pages inspected and remaining pages pixel-identical. Updated outputs/index.html. Scientific author inputs remain pending. See output metadata/README.md.\n')
    files=[]
    for path in sorted(B.rglob('*')):
        if path.is_file() and path!=M/'manifest.json':
            st=path.stat();files.append({'path':path.relative_to(B).as_posix(),'role':path.relative_to(B).parts[0],'bytes':st.st_size,
                'modified':datetime.fromtimestamp(st.st_mtime,timezone.utc).isoformat(),'sha256':sha(path)})
    save(M/'manifest.json',{'created':now(),'bundle':B.name,'inputs':inputs,'files':files,'status':'complete artifact; scientific review pending','self_hash':'omitted to avoid recursion'})
    assert all(sha(B/x['path'])==x['sha256'] for x in files)
    print(json.dumps({'pdf':str(A/'Splicing_BMI_revised_manuscript.pdf'),'pages':validation['pages'],'manifest_files':len(files),'status':'sealed'},indent=2))

if __name__=='__main__':
    if sys.argv[1]=='generate': generate()
    elif sys.argv[1]=='seal': seal()
    else: raise ValueError('Use generate or seal --visual-qa-complete')
