#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the owned MALT0.5.2 seed-build/native-mapping controller using only a JDK."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, traceback

ROOT=Path(__file__).resolve().parents[3]
SOURCES=['src/main/java/org/jlinalg/metagenomics/'+n+'.java' for n in ['MaltTaxonomy','MaltIndexTaxonomy','Malt052FastaIndexAudit']]+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['Malt052BuildCli','NextflowFiles','NextflowJson','NextflowParameters','SimpleJson','NfcoreProcess','NextflowProcess']]+['src/main/java/org/jlinalg/workflow/LinuxProcessSnapshot.java']
ACTION='Required action: supply a permitted JDK21 or JDK25 compiler with --java-bin and a NEW --out directory. Retain diagnostics. If a compiler/download/install is unavailable or forbidden, skip dependent building and analysis. This component jar does not bundle original MALT, Java14, taxonomy or reference data. Running original seed fitting requires the separately pinned Linux runtime and an explicitly selected native-accession-alternative; read --help and WARNINGS.md.'

def pin(path):
    digest=hashlib.sha256();count=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):digest.update(block);count+=len(block)
    return dict(bytes=count,sha256=digest.hexdigest())

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java-bin',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();owned=False;state=dict(status='FAIL2_MALT052_INDEX_COMPONENT_BUILD',whole_goal_complete=False)
    try:
        if out!=out.resolve():raise OSError('Canonical output path required')
        out.mkdir(parents=True,exist_ok=False);owned=True;java=args.java_bin.resolve();suffix='.exe' if os.name=='nt' else ''
        env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
        def run(name,command):
            result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=result.returncode))
            if result.returncode:raise ValueError('Component build failed at '+name+'; retain full diagnostics')
            return (result.stdout+result.stderr).decode(errors='replace')
        versions={}
        for name in ['java','javac','jar']:
            versions[name]=run(name+'-version',[str(java/(name+suffix)),'-version' if name!='jar' else '--version'])
            if not re.search(r'\b(?:21|25)(?:\.|\s|"|$)',versions[name]):raise OSError('JDK21 or JDK25 required: '+name)
        sources={name:pin(ROOT/name) for name in SOURCES};classes=out/'classes';classes.mkdir()
        run('javac',[str(java/('javac'+suffix)),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-d',str(classes),*[str(ROOT/name) for name in SOURCES]])
        artifact=out/'malt052-index-development.jar'
        run('jar',[str(java/('jar'+suffix)),'--create','--file',str(artifact),'--main-class','org.jlinalg.cli.Malt052BuildCli','-C',str(classes),'.'])
        run('help',[str(java/('java'+suffix)),'-jar',str(artifact),'--help'])
        if b'Required action:' not in (out/'help.stdout').read_bytes():raise ValueError('Required recovery instructions missing from packaged entry point')
        if any(pin(ROOT/name)!=value for name,value in sources.items()):raise ValueError('Source changed during complete build')
        shutil.copyfile(ROOT/'LICENSE',out/'LICENSE-project.txt')
        state.update(status='READY_MALT052_OWNED_INDEX_BUILD_COMPONENT',sources=sources,versions=versions,artifact=pin(artifact),bytecode_target=17,original_seed_fitting_qualified_by_compilation=False,upstream_programs_bundled=False)
        save(out/'BUILD.json',state);(out/'WARNINGS.md').write_text('# Development build only\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],str(artifact),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,OSError) else 2;state.update(status='SKIP3_REQUIRED_COMPILER_UNAVAILABLE' if code==3 else 'FAIL2_MALT052_INDEX_COMPONENT_BUILD',cause=repr(error),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
        if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
        print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source checks')
    raise SystemExit(main())
