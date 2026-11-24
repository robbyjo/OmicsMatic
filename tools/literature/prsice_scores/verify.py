# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify native PRSice scoring evidence, optionally computing a fresh fixture."""
from pathlib import Path
import argparse,csv,hashlib,json,math,os,shutil,subprocess,sys

ROOT=Path(__file__).resolve().parents[3]
PACK='tools/literature/prsice_scores'
CHECKPOINT='docs/literature/prsice-scores-checkpoint'
MODELS={'ADDITIVE':'add','DOMINANT':'dom','RECESSIVE':'rec','HETEROZYGOUS':'het'}
SCALES={'SUM':'sum','AVERAGE':'avg','STANDARDIZE':'std','CONTROL_STANDARDIZE':'con-std'}

class Unavailable(Exception):pass
def require(value,message):
    if not value:raise ValueError(message)
def pin(path):
    digest=hashlib.sha256()
    with path.open('rb')as f:
        while block:=f.read(1048576):digest.update(block)
    return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def closure(root,files):
    require(files and len(files)<50000,'Empty/excessive source closure')
    for name,expected in files.items():
        path=Path(name);require(not path.is_absolute()and'..'not in path.parts and'\\'not in name and':'not in name,'Unsafe source member');target=root/path
        require(target.resolve()==target.absolute()and target.is_file()and pin(target)==expected,'Changed source/evidence: '+name)
def close_score(value,token):
    target=float(token);bound=(.5*10**(math.floor(math.log10(abs(target)))-8)if target else 0)+2e-13
    require(math.isfinite(value)and math.isfinite(target)and abs(value-target)<=bound,'Native/upstream score differs beyond nine-significant-digit output rounding');return abs(value-target)
def run(command,folder,label):
    save(folder/(label+'.COMMAND.json'),command)
    try:
        with(folder/(label+'.stdout')).open('xb')as out,(folder/(label+'.stderr')).open('xb')as err:result=subprocess.run(command,cwd=folder,stdout=out,stderr=err,timeout=180)
    except FileNotFoundError as e:raise Unavailable('Required executable is absent; install/select a permitted JDK or exact upstream runtime: '+str(e))from e
    require(result.returncode==0,label+' failed; preserve this attempt and inspect its stdout/stderr');return result.returncode
def tool(declared,name):
    value=declared or shutil.which(name)
    if not value or not Path(value).is_file():raise Unavailable('Required '+name+' is unavailable; select a permitted JDK with --java and --javac, or retain the skip')
    return str(Path(value).absolute())

def public(root):
    checkpoint=root/CHECKPOINT;state=read(checkpoint/'STATUS.json');require(state['status']=='PASS_NATIVE_PRSICE_HARD_CALL_SCORING_COMPONENT'and not state['catalog_admitted']and not state['whole_goal_complete']and(state['new_family_credit'],state['new_review_credit'])==(0,0),'Expanded component/catalog claim')
    require(pin(checkpoint/'FILES.json')==state['public_manifest'],'Changed public closure');files=read(checkpoint/'FILES.json');closure(root,files)
    validation=read(checkpoint/'VALIDATION.json');require(validation['Windows_Java_tests']==validation['Linux_Java_tests']==18 and validation['original_runs']==96 and validation['native_original_values_compared']==1152 and validation['primary_XML_checked']==250,'Incomplete actual component validation')
    reference=read(root/PACK/'reference-scores.json');profile=read(root/PACK/'source-profile.json');require(reference['original_runs']==96 and reference['source_output_significant_digits']==9 and reference['Windows_Linux_outputs_equal']and len(reference['cases'])==48,'Changed original reference population');require(reference['source_commit']==profile['source_commit']=='9dbf55e3f7f3ede2c1b1b6a334c676163e0cbe34'and len(profile['source_files'])==47,'Changed selected original source')
    keys={(c['model'],c['missing'],c['scaling'])for c in reference['cases']};require(len(keys)==48 and keys=={(m,z,s)for m in MODELS for z in('MEAN_IMPUTE','SET_ZERO','CENTER')for s in SCALES},'Incomplete reference combinations');require(all(len(c['scores'])==12 for c in reference['cases']),'Incomplete reference sample rows')
    return state,files,reference,profile

def fixture(folder,data):
    folder.mkdir();n=len(data['IID']);require(n==12 and len(data['target_a1_copies'])==6,'Changed artificial fixture dimensions')
    (folder/'target.fam').write_text(''.join(f'F S{i:02} '+('S00 S01'if i==11 else'0 0')+f' {1+i%2} {1+i%2}\n'for i in range(n)),newline='\n')
    (folder/'target.bim').write_text(''.join(f'1 rs{i+1} 0 {1000+i*1000} A G\n'for i in range(6)),newline='\n');bed=bytearray(b'\x6c\x1b\x01')
    for row in data['target_a1_copies']:
        for start in range(0,n,4):bed.append(sum({2:0,-1:1,1:2,0:3}[row[j]]<<(2*(j-start))for j in range(start,start+4)))
    (folder/'target.bed').write_bytes(bed);(folder/'base.tsv').write_text('SNP CHR BP A1 A2 BETA P\n'+''.join(f'rs{i+1} 1 {1000+i*1000} '+('G A'if data['flipped'][i]else'A G')+f' {data["effects"][i]} 0.01\n'for i in range(6)),newline='\n');(folder/'phenotype.tsv').write_text('FID IID PHENO\n'+''.join(f'F S{i:02} {1+i%2}\n'for i in range(n)),newline='\n');return{p.name:pin(p)for p in folder.iterdir()if p.is_file()}

def verify(args):
    require(__debug__,'Run without -O');root=args.root.absolute();require(root.resolve()==root,'Use a physical source root');seal=pin(root/CHECKPOINT/'STATUS.json');state,files,reference,profile=public(root)
    result=dict(status='PASS_PUBLIC_NATIVE_PRSICE_SOURCE_EVIDENCE_ONLY',public_files_checked=len(files),fresh_native_values=0,fresh_upstream_values=0,source_roles=5,source_anchors=17,primary_XML_checked_now=0,catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False)
    if args.cache is not None:
        sys.path.insert(0,str(root/'tools/literature'));from prsice_scores.primary import audit
        try:fresh=audit((args.papers or root/'docs/literature/omics-2023-2026/papers.json').read_bytes(),args.cache)
        except FileNotFoundError as e:raise Unavailable('Exact primary source prerequisite missing; restore the frozen XML/papers metadata by an allowed method or retain the skip: '+str(e))from e
        result['primary_XML_checked_now']=fresh['primary_XML_checked']
    if args.jar is None:
        require(args.out is None and args.upstream_home is None,'Fresh computation needs --jar and --out');closure(root,files);require(pin(root/CHECKPOINT/'STATUS.json')==seal and pin(root/CHECKPOINT/'FILES.json')==state['public_manifest'],'Checkpoint changed during verification');return result
    jar=args.jar.absolute()
    if not jar.is_file():raise Unavailable('Restore the exact qualified source-built JAR; the existing release asset predates this component: '+str(jar))
    require(pin(jar)==state['producer'],'Changed whole producer JAR; restore the exact qualified file or qualify a new build, never refresh old expected hashes')
    require(args.out is not None,'Fresh numerical verification requires explicit new --out');d=args.out.absolute();require(not d.exists()and d.resolve()==d and d.parent.is_dir(),'Preserve existing/aliased output; choose a new physical child of an existing permitted directory')
    java=tool(args.java,'java');javac=tool(args.javac,'javac');exe=None;platform='windows'if os.name=='nt'else'linux'if sys.platform.startswith('linux')else'unsupported'
    if args.upstream_home is not None:
        if platform=='unsupported':raise Unavailable('Only the pinned Windows/Linux original runtimes are qualified; omit upstream execution or supply a qualified platform')
        exe=args.upstream_home.absolute()/('PRSice_win64.exe'if platform=='windows'else'PRSice_linux')
        if not exe.is_file():raise Unavailable('Exact original upstream executable missing; install/stage the permitted 2.3.5 runtime or retain the skip: '+str(exe))
        require(pin(exe)==profile['runtime_executables'][platform],'Changed upstream executable; restore the exact pinned original binary')
    d.mkdir();args._owned_output=(d.stat().st_dev,d.stat().st_ino);(d/'classes').mkdir();save(d/'STARTED.json',dict(status='RUNNING_NO_RESULT_ADMITTED',producer=state['producer'],upstream_requested=exe is not None));run([javac,'--release','17','-encoding','UTF-8','-cp',str(jar),'-d',str(d/'classes'),str(root/PACK/'ScoreProbe.java')],d,'compile')
    run([java,'-cp',str(jar)+os.pathsep+str(d/'classes'),'ScoreProbe'],d,'native');rows=list(csv.DictReader((d/'native.stdout').read_text().splitlines(),delimiter='\t'));require(len(rows)==576,'Incomplete fresh native sample rows');keyed={}
    for row in rows:
        key=(row['model'],row['missing'],row['scaling'],row['sample']);require(key not in keyed and row['status']=='COMPLETE','Duplicate/unadmitted native score row');keyed[key]=float(row['score'])
    errors=[]
    for case in reference['cases']:
        for i,token in enumerate(case['scores']):errors.append(close_score(keyed[(case['model'],case['missing'],case['scaling'],f'S{i:02}')],token))
    result.update(status='PASS_FRESH_PACKAGED_NATIVE_PRSICE_FIXTURE',fresh_native_values=576,maximum_absolute_error=max(errors),producer=state['producer'],original_output_significant_digits=9,internal_full_precision_identity_claimed=False)
    if exe is not None:
        f=d/'fixture';inputs=fixture(f,read(root/PACK/'fixture.json'));run([str(exe),'--version'],d,'upstream-version');require('2.3.5 (2021-09-20)'in(d/'upstream-version.stderr').read_text(),'Unexpected original runtime version')
        for case in reference['cases']:
            model,missing,scaling=case['model'],case['missing'],case['scaling'];o=d/(model+'-'+missing+'-'+scaling);o.mkdir();command=[str(exe),'--base',str(f/'base.tsv'),'--target',str(f/'target'),'--type','bed','--beta','--snp','SNP','--chr','CHR','--bp','BP','--a1','A1','--a2','A2','--pvalue','P','--stat','BETA','--no-default','--no-clump','--fastscore','--bar-levels','1','--no-full','--no-regress','--all-score','--score',SCALES[scaling],'--missing',missing,'--model',MODELS[model],'--thread','1','--seed','42','--out',str(o/'original'),'--binary-target','T','--pheno',str(f/'phenotype.tsv'),'--pheno-col','PHENO','--nonfounders','--print-snp'];run(command,o,'upstream')
            product=o/'original.all_score';require(product.is_file(),'Original process returned no score product');lines=[r.split()for r in product.read_text().splitlines()];require(lines[0]==['FID','IID','Pt_1']and len(lines)==13,'Changed original score schema/population')
            for i,row in enumerate(lines[1:]):require(row[:2]==['F',f'S{i:02}'],'Changed original sample order/identity');close_score(keyed[(model,missing,scaling,f'S{i:02}')],row[2])
        require(inputs=={p.name:pin(p)for p in f.iterdir()if p.is_file()}and pin(exe)==profile['runtime_executables'][platform],'Original inputs/runtime changed');result.update(fresh_upstream_values=576,fresh_upstream_runs=48)
    require(pin(jar)==state['producer'],'Producer changed during computation');closure(root,files);require(pin(root/CHECKPOINT/'STATUS.json')==seal and pin(root/CHECKPOINT/'FILES.json')==state['public_manifest'],'Checkpoint changed during computation');save(d/'COMPLETED.json',result);return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in('root','cache','papers','jar','out','upstream-home'):parser.add_argument('--'+name,type=Path,default=ROOT if name=='root'else None)
    parser.add_argument('--java');parser.add_argument('--javac');args=parser.parse_args()
    try:print(json.dumps(verify(args),indent=2));return 0
    except(Unavailable,PermissionError)as error:code=3;message='SKIP3: DEPENDENT VERIFICATION SKIPPED. '+str(error)+'. Download/install only where permitted; otherwise retain this skip.'
    except Exception as error:code=2;message='FAIL2: NO VERIFIED RESULT. '+repr(error)+'. Preserve the attempt and original values; resolve the named cause and retry new output. Never refresh old expected hashes.'
    if getattr(args,'_owned_output',None)is not None and args.out.is_dir()and(args.out.stat().st_dev,args.out.stat().st_ino)==args._owned_output and not(args.out/'COMPLETED.json').exists():
        try:
            with(args.out/'FAILURE.md').open('x',encoding='utf-8')as f:f.write(message+'\n')
        except OSError:pass
    print(message,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
