# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a portable exact original-source/setup profile from admitted CellPhoneDB references; worker bindings remain separate."""
import argparse,json,shutil,subprocess,traceback,urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_WORKFLOW_PROFILE_INCOMPLETE',implementation_credit=False,whole_goal_complete=False)
    try:
        kit=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow';intake=ROOT/'build/literature-review/cellphonedb-runtime/source-intake-2';original=json.loads((intake/'COMPLETED.json').read_bytes())['versions']['5.0.1'];setup=ROOT/'build/cellphonedb-case-sensitive-1/setup-1';assert json.loads((setup/'COMPLETED.json').read_bytes())['status']=='PASS_PRIVATE_ORIGINAL_CELLPHONEDB_501_REFERENCE_IMPORT_ONLY';plan=json.loads((setup/'pip-plan.json').read_bytes());archives=[];versions={'numpy':'1.26.4','scipy':'1.11.4','scikit-learn':'1.3.2'}
        for row in plan['install']:
            name=row['metadata']['name'];version=row['metadata']['version'];url=row['download_info']['url'];sha=row['download_info']['archive_info']['hashes']['sha256']
            if name.lower()=='cellphonedb':url=original['distribution']['url']
            filename=url.rsplit('/',1)[-1];assert url.startswith('https://files.pythonhosted.org/') and Path(filename).name==filename
            if filename.endswith('.whl'):file=setup/'wheels'/filename;assert pin(file)['sha256']==sha;size=file.stat().st_size
            else:
                assert name.lower()=='fbpca' and filename=='fbpca-1.0.tar.gz';request=urllib.request.Request('https://pypi.org/pypi/fbpca/1.0/json',headers={'User-Agent':'OmicsMatic-source-review'})
                with urllib.request.urlopen(request,timeout=45) as response:raw=response.read(4194305);assert len(raw)<=4194304
                (out/'official-fbpca-metadata.json').write_bytes(raw);published=[r for r in json.loads(raw)['urls'] if r['filename']==filename];assert len(published)==1 and published[0]['url']==url and published[0]['digests']['sha256']==sha;size=published[0]['size']
            archives.append(dict(name=name,version=version,filename=filename,url=url,bytes=size,sha256=sha));versions[name]=version
        installer=ROOT/'build/literature-review/leafcutter-runtime/r-python-setup-1/micromamba';command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/mnt/d/projects/JLinAlg/build/literature-review/leafcutter-runtime/r-python-setup-1/micromamba','--version'];run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'installer-version.stdout').write_bytes(run.stdout);(out/'installer-version.stderr').write_bytes(run.stderr);assert run.returncode==0
        locks=dict(schema_version=1,python_version=[3,11,17],installer=pin(installer),installer_version=run.stdout.decode('utf8').strip(),conda_packages=json.loads((setup/'conda-dependency-locks.json').read_bytes())['packages'],python_archives=archives,python_versions=versions);assert len(locks['conda_packages'])==43 and len(archives)==len(plan['install']);save(kit/'setup/dependency-locks.json',locks);inventory=ROOT/'tools/literature/inventory_hail_runtime_linux.py';target=kit/'setup/inventory.py'
        if target.exists():assert target.read_bytes()==inventory.read_bytes()
        else:target.write_bytes(inventory.read_bytes())
        for name,want in original['members'].items():
            source=intake/'5.0.1/source'/name;assert pin(source)==want;target=kit/'original'/name;target.parent.mkdir(parents=True,exist_ok=True)
            if target.exists():assert target.read_bytes()==source.read_bytes()
            else:target.write_bytes(source.read_bytes())
        wheel=original['distribution'];source=intake/'5.0.1'/wheel['filename'];assert pin(source)=={k:wheel[k] for k in ['bytes','sha256']};target=kit/'original'/wheel['filename'];target.write_bytes(source.read_bytes());files={p.relative_to(kit).as_posix():pin(p) for p in sorted(kit.rglob('*')) if p.is_file() and (p.relative_to(kit).parts[0] in ['setup','original'])};profile=dict(schema_version=1,edition='5.0.1',distribution=wheel,original_members=original['members'],files=files,analysis_runtime='Linux x86_64 private original Python3.11.17/CellPhoneDB5.0.1; explicit source-compatible database input; no database is bundled.',installation='Separate explicit permitted private installation only. Missing/forbidden required software skips dependent analysis. RUN installs nothing.',namespace_patched=False);save(kit/'profiles.json',profile);sources={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))};save(out/'COMPLETED.json',dict(status='PASS_EXACT_CELLPHONEDB_ORIGINAL_SETUP_PROFILE_ONLY',profile=pin(kit/'profiles.json'),source_pins=sources,conda_archives=43,python_archives=len(archives),source_and_setup_members=len(files),implementation_credit=False,whole_goal_complete=False));print('Prepared',len(files),'original/setup profile members;',len(archives),'pinned Python source archives')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');save(out/'FAILED.json',state);raise


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/profile checks')
    main()
