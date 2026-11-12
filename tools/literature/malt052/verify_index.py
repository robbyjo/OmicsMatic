#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rebuild and replay complete owned MALT0.5.2 index/alignment evidence without refitting upstream."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,gzip,hashlib,json,lzma,os,re,shutil,subprocess,sys,traceback,zipfile

ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/'docs/literature/malt052-build-development'
MAX_FILE=128*1024*1024;MAX_UNIQUE=512*1024*1024;MAX_RESTORED=3*1024*1024*1024
ACTION='Required action: retain all failed replay outputs and diagnostics. Supply this checkout\'s complete controls.zip.xz and matching sources, a permitted JDK21 or JDK25, the exact MALT0.5.2 reader ROOT containing jars/, the exact HTSJDK5.0.0 jar or permission to download it, and a NEW output directory with at least5GiB free space. Obtain a reader with tools/literature/malt052/setup.py --reader-only only where download/install is permitted. If a required compiler/runtime/download/install is unavailable or forbidden, skip dependent analysis. Resolve the named integrity, complete-input, format or resource issue before retrying. No partial population, repaired record or original accession-map claim is substituted.'

def pin(path):
    digest=hashlib.sha256();count=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):digest.update(block);count+=len(block)
    return dict(bytes=count,sha256=digest.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def member(name):
    path=PurePosixPath(name)
    if not name or path.is_absolute() or '\\' in name or ':' in name or any(ord(c)<32 or ord(c)==127 for c in name) or any(part in ['', '.', '..'] for part in name.split('/')):raise ValueError('Unsafe complete evidence member')
    return name
def safe_zip_names(z):
    names=set()
    for info in z.infolist():
        name=member(info.filename)
        if name in names or info.is_dir() or info.file_size>MAX_FILE or (info.external_attr>>16)&0o170000 not in [0,0o100000]:raise ValueError('Duplicate/nonregular/over-capacity evidence member')
        names.add(name)
        if len(names)>10000:raise ValueError('Evidence member count capacity exceeded')
    return names
def sam(path):
    data=gzip.decompress(path.read_bytes())
    if len(data)>MAX_FILE:raise ValueError('Complete decoded SAM exceeds capacity')
    return Counter(row for row in data.decode('utf8').splitlines() if not row.startswith('@'))

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--java-bin',required=True,type=Path);parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);dependency=parser.add_mutually_exclusive_group();dependency.add_argument('--htsjdk-jar',type=Path);dependency.add_argument('--download-htsjdk',action='store_true');args=parser.parse_args()
    out=args.out.absolute();owned=False;state=dict(status='FAIL2_MALT052_OWNED_INDEX_PORTABLE_REPLAY',original_fitting_repeated=False,producer_authenticated=False,whole_goal_complete=False)
    try:
        if out!=out.resolve():raise OSError('Canonical NEW output required')
        out.mkdir(parents=True,exist_ok=False);owned=True;validation=json.loads((DATA/'VALIDATION.json').read_bytes());archive=DATA/'controls.zip.xz';before=validation['sources']
        if pin(archive)!=validation['archive']:raise ValueError('Complete evidence archive SHA256/bytes differ')
        for name,value in before.items():
            if pin(ROOT/member(name))!=value:raise ValueError('Current build/replay source differs: '+name)
        raw=out/'controls.zip';expanded=0
        with lzma.open(archive,'rb') as source,raw.open('xb') as target:
            for block in iter(lambda:source.read(1048576),b''):
                expanded+=len(block)
                if expanded>MAX_UNIQUE:raise ValueError('Complete evidence container exceeds capacity')
                target.write(block)
        with zipfile.ZipFile(raw) as z:
            names=safe_zip_names(z)
            if 'MANIFEST.json' not in names or z.getinfo('MANIFEST.json').file_size>16777216:raise ValueError('Missing/oversized complete evidence manifest')
            manifest=json.loads(z.read('MANIFEST.json'))
            if manifest.get('schema')!='MALT052_OWNED_BUILD_ALIGNMENT_EVIDENCE_V1':raise ValueError('Unsupported complete evidence schema')
            files={member(name):value for name,value in manifest['files'].items()};objects={}
            for value in files.values():
                if set(value)!=set(['bytes','sha256']) or type(value['bytes']) is not int or not 0<=value['bytes']<=MAX_FILE or not re.fullmatch('[0-9a-f]{64}',value['sha256']):raise ValueError('Invalid complete evidence fingerprint')
                if value['sha256'] in objects and objects[value['sha256']]!=value:raise ValueError('Inconsistent shared evidence object')
                objects[value['sha256']]=value
            if names!=set(['MANIFEST.json'])|{'objects/'+digest for digest in objects} or sum(value['bytes'] for value in objects.values())>MAX_UNIQUE:raise ValueError('Missing/extra evidence objects or complete capacity exceeded')
            for digest,value in objects.items():
                h=hashlib.sha256();count=0
                with z.open('objects/'+digest) as source:
                    for block in iter(lambda:source.read(1048576),b''):h.update(block);count+=len(block)
                if dict(bytes=count,sha256=h.hexdigest())!=value:raise ValueError('Complete evidence object differs')
            restored=0
            for name,value in files.items():
                if name.startswith(('bundles/','negative/','source-rma/','baseline-sam/','fixtures/')):
                    restored+=value['bytes']
                    if restored>MAX_RESTORED:raise ValueError('Complete restored population exceeds capacity')
                    target=out/name;target.parent.mkdir(parents=True,exist_ok=True)
                    with z.open('objects/'+value['sha256']) as source,target.open('xb') as destination:shutil.copyfileobj(source,destination,1048576)
        java=args.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';runtime=args.runtime.resolve();env={key:value for key,value in os.environ.items() if key.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
        def run(name,command,expected=0,cause=None):
            result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=360)
            (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=result.returncode))
            if result.returncode==3 and expected==0:raise FileNotFoundError('Required replay/compiler/runtime prerequisite unavailable at '+name)
            if result.returncode!=expected or cause is not None and cause.encode() not in result.stderr:raise ValueError('Replay failed to meet its declared disposition at '+name+'; inspect saved diagnostics')
            return result
        run('build-index',[sys.executable,str(ROOT/'tools/literature/malt052/build_index.py'),'--java-bin',str(java),'--out',str(out/'index-build')])
        indexjar=out/'index-build/malt052-index-development.jar'
        seeds=manifest['seed_bundles']
        if seeds!=['seed-single','seed-multi']:raise ValueError('Complete two-build population required')
        for name in seeds:
            bundle=out/'bundles'/name;run(name+'-replay',[str(java/('java'+suffix)),'-Xmx3g','-jar',str(indexjar),'--verify',str(bundle)])
            audit=json.loads((bundle/'audit.json').read_bytes());science=audit['fasta_audit']
            if science['references']!=3 or science['reference_bases']!=52244 or audit['mapping_audit']['taxon_by_accession']!={'AB177354.1':8574,'NC_007596.2':37349,'NC_012920.1':9606}:raise ValueError('Complete reference/taxonomy population differs')
        differences={}
        for directory in ['native/index','derived/index']:
            first=out/'bundles/seed-single'/directory;second=out/'bundles/seed-multi'/directory
            names={p.name for p in first.iterdir()};different=[name for name in sorted(names) if pin(first/name)!=pin(second/name)]
            if different!=['table0.db','table0.idx']:raise ValueError('Retained upstream seed-table difference population differs')
            differences[directory]=different
        build=[sys.executable,str(ROOT/'tools/literature/malt052/build.py'),'--java-bin',str(java),'--out',str(out/'alignment-build')]
        if args.htsjdk_jar:build+=['--htsjdk-jar',str(args.htsjdk_jar.resolve())]
        elif args.download_htsjdk:build+=['--download-htsjdk']
        else:raise FileNotFoundError('Supply exact --htsjdk-jar or permitted --download-htsjdk')
        run('build-alignment',build);alignjar=out/'alignment-build/malt052-alignment-development.jar';classes=out/'probe-classes';classes.mkdir();cp=os.pathsep.join(map(str,[classes,indexjar,alignjar]))
        run('compile-probes',[str(java/('javac'+suffix)),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',os.pathsep.join(map(str,[indexjar,alignjar])),'-d',str(classes),str(ROOT/'tools/literature/java/Malt052FastaNativeProbe.java'),str(ROOT/'tools/literature/java/Malt052RmaNativeProbe.java')])
        alignments=manifest['alignment_bundles'];expected_names=[group+'-'+case for group in ['single','multi'] for case in ['A','B','dna5','support59']]
        if [row['name'] for row in alignments]!=expected_names:raise ValueError('Complete eight alignment populations required')
        observations=[]
        expected={'A':(8642,59,60,59,1),'B':(8208,47,57,47,1),'dna5':(6,0,0,0,1),'support59':(6,5,5,6,3)}
        for row in alignments:
            name=row['name'];case=row['case'];bundle=out/member(row['member'])
            run(name+'-alignment-replay',[str(java/('java'+suffix)),'-Xmx3g','-jar',str(alignjar),'--verify',str(bundle),'--runtime',str(runtime)])
            science=json.loads((bundle/'audit.json').read_bytes())['scientific'];actual=tuple(science[key] for key in ['input_queries','aligned_queries','alignments','rma_stored_reads','selected_minimum_support'])
            if actual!=expected[case]:raise ValueError('Complete source alignment population differs: '+name)
            if sam(bundle/'native/sam/reads.blastn.sam.gz')!=sam(out/member(row['baseline_sam'])):raise ValueError('Complete source SAM rows differ: '+name)
            rawout=out/(name+'-raw-fields');run(name+'-raw-fields',[str(java/('java'+suffix)),'-Xmx2g','-cp',cp,'Malt052RmaNativeProbe',str(runtime),str(bundle/'native/rma/reads.rma6'),str(rawout)])
            for filename in ['reads.tsv','matches.tsv','classes.tsv','counts.tsv','identities.tsv']:
                if (rawout/filename).read_bytes()!=(out/member(row['source_rma'])/filename).read_bytes():raise ValueError('Complete original Java14 RMA fields differ: '+name+'/'+filename)
            observations.append(dict(name=name,queries=actual[0],aligned_queries=actual[1],alignments=actual[2],stored_reads=actual[3],minimum_support=actual[4]));print(name,'complete saved alignment/raw fields passed',flush=True)
        fixture=out/'fixtures/malt052-fasta-controls.zip';fixture_root=out/'fasta-source-controls';fixture_root.mkdir();fixture_total=0
        with zipfile.ZipFile(fixture) as z:
            names=safe_zip_names(z)
            for name in names:
                info=z.getinfo(name);fixture_total+=info.file_size
                if fixture_total>16777216:raise ValueError('Complete FASTA controls exceed capacity')
                target=fixture_root/name;target.parent.mkdir(parents=True,exist_ok=True)
                with z.open(name) as source,target.open('xb') as destination:shutil.copyfileobj(source,destination)
        rows=(fixture_root/'INDEX.tsv').read_text().splitlines();assert rows[0]=='case\tnative_disposition\tsource_exit';assert len(rows)==17;positive=0;refused=0
        for line in rows[1:]:
            case,disposition,source_exit=line.split('\t');directory=fixture_root/member(case);paths=sorted([p for p in directory.iterdir() if p.name.endswith(('.fa','.fa.gz'))]);listing=out/(case+'-fasta-inputs.txt');listing.write_text(''.join(str(path)+'\n' for path in paths),encoding='utf8',newline='\n');target=out/(case+'-fasta-native')
            command=[str(java/('java'+suffix)),'-Xmx1g','-cp',cp,'Malt052FastaNativeProbe',str(listing),str(target)]
            run(case+'-fasta',command,expected=0 if disposition=='equal' else 1,cause=None if disposition=='equal' else 'Required action:')
            if (target/'dna5.tsv').read_bytes()!=(directory/'source/dna5.tsv').read_bytes():raise ValueError('Complete127-value source DNA5 table differs')
            if disposition=='equal':
                for name in ['references.tsv','counts.tsv']:
                    if (target/name).read_bytes()!=(directory/'source'/name).read_bytes():raise ValueError('Complete source FASTA fields differ: '+case+'/'+name)
                positive+=1
            else:refused+=1
        if (positive,refused)!=(12,4):raise ValueError('Complete FASTA source/control disposition family differs')
        negatives=manifest['negative_bundles']
        if [row['name'] for row in negatives]!=['changed-worker-request','insufficient-total-input-capacity','false-tool-credit','failure-marker']:raise ValueError('Complete rehashed metadata refusal family required')
        for row in negatives:run(row['name']+'-refusal',[str(java/('java'+suffix)),'-Xmx3g','-jar',str(indexjar),'--verify',str(out/member(row['member']))],expected=2,cause=row['required_error'])
        for name,value in files.items():
            if name.startswith(('bundles/','negative/','source-rma/','baseline-sam/','fixtures/')) and pin(out/name)!=value:raise ValueError('Complete restored evidence changed: '+name)
        if pin(archive)!=validation['archive'] or any(pin(ROOT/name)!=value for name,value in before.items()):raise ValueError('Source/evidence changed during complete replay')
        state.update(status='PASS_COMPLETE_OWNED_MALT052_INDEX_ALIGNMENT_AND_SOURCE_ORACLE_REPLAY_ONLY',archive=validation['archive'],logical_files=len(files),unique_objects=len(objects),restored_bytes=restored,seed_builds=2,alignment_populations=observations,complete_queries=sum(row['queries'] for row in observations),alignments=sum(row['alignments'] for row in observations),source_fasta_positive_cases=positive,source_fasta_declared_refusals=refused,source_dna5_values=127,metadata_refusals=4,retained_seed_table_differences=differences,new_tool_credit=0,original_accession_map_qualified=False)
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Complete saved software-profile replay only\n\nOriginal fitting is not repeated; producer authentication and biological paper reproduction are not established. Original accession mapping remains unqualified; the mapped indices are explicitly selected native alternatives.\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,OSError) else 2;state.update(status='SKIP3_REQUIRED_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_MALT052_OWNED_INDEX_PORTABLE_REPLAY',cause=repr(error),traceback=traceback.format_exc(),dependent_analysis_skipped=True,required_action=ACTION)
        if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
        print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence/source checks')
    raise SystemExit(main())
