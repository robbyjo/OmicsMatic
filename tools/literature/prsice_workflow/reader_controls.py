#!/usr/bin/env python3
"""Exercise saved-reader refusals on private artificial copies; never edit the parent."""
from pathlib import Path
import argparse,json,shutil,subprocess,sys
from check import pin,inventory,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',default='java');p.add_argument('--jar',type=Path,required=True);p.add_argument('--capture',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();root=a.out.absolute();root.mkdir();parent=a.capture.resolve();before=inventory(parent);records=[]
    def reseal(copy):
        manifest=json.loads((copy/'manifest.json').read_bytes());files=inventory(copy)
        for name in ('manifest.json','COMPLETED.json'):files.pop(name,None)
        manifest['inventory']=dict(files=dict(sorted(files.items())),directories=sorted(p.relative_to(copy).as_posix()for p in copy.rglob('*')if p.is_dir()));save(copy/'manifest.json',manifest);seal=json.loads((copy/'COMPLETED.json').read_bytes());seal['manifest']=pin(copy/'manifest.json');save(copy/'COMPLETED.json',seal)
    for kind in ('score-byte','score-resealed','sample-ledger-resealed','selected-SNP-resealed','scientific-request-resealed','extra-directory','missing-seal','missing-input'):
        copy=root/kind;shutil.copytree(parent,copy)
        if kind.startswith('score-'):
            file=copy/'worker/original.all_score';rows=file.read_text(encoding='utf-8').splitlines();row=rows[1].split();row[2]=str(float(row[2])+1);rows[1]=' '.join(row);file.write_text('\n'.join(rows)+'\n',encoding='utf-8')
        elif kind=='sample-ledger-resealed':
            file=copy/'sample-ledger.tsv';text=file.read_text(encoding='utf-8');assert 'S000'in text;file.write_text(text.replace('S000','changed-literal-ID',1),encoding='utf-8')
        elif kind=='selected-SNP-resealed':
            file=copy/'worker/original.snp';rows=file.read_text(encoding='utf-8').splitlines();del rows[1];file.write_text('\n'.join(rows)+'\n',encoding='utf-8')
        elif kind=='scientific-request-resealed':
            for name in ('canonical-request.json','source-request.json'):
                file=copy/name;request=json.loads(file.read_bytes());request['parameters']['scoring']['model']='het';save(file,request)
        elif kind=='extra-directory':(copy/'unexpected-empty-directory').mkdir()
        elif kind=='missing-seal':(copy/'COMPLETED.json').unlink()
        elif kind=='missing-input':(copy/'inputs/target.bed').unlink()
        if kind.endswith('resealed'):reseal(copy)
        frozen=inventory(copy);command=[a.java,'-jar',str(a.jar.resolve()),'prsice-tools','--inspect',str(copy)]
        with(root/(kind+'.stdout')).open('xb')as out,(root/(kind+'.stderr')).open('xb')as err:code=subprocess.run(command,stdout=out,stderr=err,timeout=120).returncode
        unchanged=frozen==inventory(copy);records.append(dict(case=kind,exit_code=code,expected=2,read_only=unchanged,resealed=kind.endswith('resealed')));print(kind,code,unchanged,flush=True)
    assert before==inventory(parent);save(root/'PRIVATE-RESULT.json',dict(records=records,parent_unchanged=True));return 0 if all(r['exit_code']==2 and r['read_only']for r in records)else 1
if __name__=='__main__':sys.exit(main())
