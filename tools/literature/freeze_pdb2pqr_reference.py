#!/usr/bin/env python3
"""Freeze actual independent original preparation outputs and numerical fingerprints."""
from pathlib import Path
import argparse,datetime as dt,hashlib,json,math,struct,zipfile

def sha(data):return hashlib.sha256(data).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--references',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False);document=json.loads((args.references/'verification.json').read_text());assert document['status']=='completed-original-preparation-matrix' and document['case_count']==14;properties={};rows=[];total=0
    with zipfile.ZipFile(args.out/'original-preparations.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for case in document['cases']:
            assert case['exit_code']==0;name=case['case'];folder=args.references/name;data=(folder/'prepared.pqr').read_bytes();assert sha(data)==case['pqr_sha256'];assert sha((folder/'prepared.pdb').read_bytes())==case['pdb_sha256'];assert sha((folder/'source.pdb').read_bytes())==case['source_sha256'];numerical=hashlib.sha256();identities=hashlib.sha256();charges=[];count=0;zero=0
            for line in data.decode('ascii').splitlines():
                f=line.split()
                if not f or f[0] not in {'ATOM','HETATM'}:continue
                assert len(f) in {10,11};offset=1 if len(f)==11 else 0;chain=f[4] if offset else '';residue=f[4+offset];values=[float(x) for x in f[5+offset:]];assert len(values)==5 and all(map(math.isfinite,values));numerical.update(struct.pack('<i5d',int(f[1]),*values));identities.update(('|'.join([f[0],chain,residue,f[3],f[2]])+'\n').encode());charges.append(values[3]);zero+=values[4]==0;count+=1
            assert count==case['atoms'];total+=count;properties.update({name+'.atoms':str(count),name+'.charge':repr(math.fsum(charges)),name+'.zero_radius':str(zero),name+'.numerical_sha256':numerical.hexdigest(),name+'.identity_sha256':identities.hexdigest()})
            for filename in ['source.pdb','prepared.pdb','prepared.pqr']:
                raw=(folder/filename).read_bytes();info=zipfile.ZipInfo(name+'/'+filename,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
            rows.append({key:case[key] for key in ['case','flags','source_sha256','pqr_sha256','pdb_sha256','atoms','net_charge_e','zero_radius_atoms']})
    properties['archive.sha256']=sha((args.out/'original-preparations.zip').read_bytes());properties['cases']='14';properties['total.atom_rows']=str(total);(args.out/'reference.properties').write_text(''.join(name+'='+value+'\n' for name,value in sorted(properties.items())),encoding='ascii',newline='\n')
    provenance=dict(schema_version=1,status='frozen-original-reference-data',program='Original PDB2PQR3.8.0;PROPKA3.5.1;numpy1.26.4;CPython3.12.14 Windowsx64',references_verification_sha256=sha((args.references/'verification.json').read_bytes()),freezer_sha256=sha(Path(__file__).read_bytes()),source_pdb=dict(accession='1CRN',sha256=document['source_sha256'],url='https://files.rcsb.org/download/1CRN.pdb',usage='https://www.wwpdb.org/about/usage-policies',license='CC0'),cases=rows,atom_rows=total,scope='Actual original invocations retained before the typed project implementation. Independently parsed complete per-atom coordinates,charges,radii,serials and identities. Numeric fingerprint int32+five float64 little-endian per PQR atom;identity fingerprint UTF8 record|chain|residueID|residueName|atomName newline. No experimental pKa/chemical validity or paper-parameter reconstruction is implied.')
    (args.out/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');(args.out/'SHA256SUMS').write_text(''.join(sha(p.read_bytes())+'  '+p.name+'\n' for p in sorted(args.out.iterdir()) if p.name!='SHA256SUMS'));print(json.dumps(dict(status=provenance['status'],cases=14,atom_rows=total)))

if __name__=='__main__':main()
