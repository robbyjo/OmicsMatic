# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extract compact exact quantification fixtures from the immutable original-run checkpoint."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def sha(raw):return hashlib.sha256(raw).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve()
    assert out==ROOT/'src/test/resources/org/jlinalg/sequence/salmon-quantification' and not out.exists()
    source=ROOT/'docs/literature/salmon-native-development';proof=json.loads((source/'VALIDATION.json').read_bytes());archive=source/'controls.zip';assert sha(archive.read_bytes())==proof['archive']['sha256']
    entries={};case_rows=['path\tversion\tmode\tassigned_fragments\tprecision\ttargets\tprocess_exit_code']
    with zipfile.ZipFile(archive) as z:
        def original(name):
            raw=z.read(name);row=proof['archive_files'][name];assert len(raw)==row['bytes'] and sha(raw)==row['sha256'];return raw
        fasta=original('evidence/reference-protocols-3/inputs/transcripts.fa');targets=[];name=None;sequence=[]
        for line in fasta.decode().splitlines():
            if line.startswith('>'):
                if name is not None:targets.append((name,len(''.join(sequence))))
                name=line[1:].split()[0];sequence=[]
            else:sequence.append(line)
        if name is not None:targets.append((name,len(''.join(sequence))))
        assert len(targets)==40 and len({n for n,l in targets})==40
        entries['targets.tsv']=('name\tlength\n'+''.join(n+'\t'+str(l)+'\n' for n,l in targets)).encode()
        entries['original-input/transcripts.fa']=fasta
        for family in proof['original_reference_validation']['runs']:
            for fit in family['quantifications']:
                relative=family['version']+'/'+fit['mode'];prefix='evidence/reference-protocols-3/'+relative+'/'
                for name in ['quant.sf','aux_info/meta_info.json','cmd_info.json']:
                    entries[relative+'/'+name]=original(prefix+name)
                meta=json.loads(entries[relative+'/aux_info/meta_info.json']);assert fit['call']['exit_code']==0 and meta['num_mapped']==8520
                assert [(r.split('\t')[0],int(r.split('\t')[1])) for r in entries[relative+'/quant.sf'].decode().splitlines()[1:]]==targets
                case_rows.append('\t'.join(map(str,[relative,family['version'],fit['mode'],meta['num_mapped'],3,40,0])))
        entries['cases.tsv']=('\n'.join(case_rows)+'\n').encode()
        # A complete arithmetic table can precede an original process crash.
        # This is retained explicitly so no arithmetic receipt becomes a run certificate.
        failure='evidence/reference-protocols-2/1.8.0/'
        entries['failed-control/quant.sf']=original(failure+'paired-em-bias-50-FAILED/quant.sf')
        receipt=json.loads(original(failure+'paired-em-bias-50.json'));assert receipt['exit_code']==-11
        entries['failed-control/process.json']=json.dumps(receipt,indent=2).encode()+b'\n'
        entries['failed-control/status.properties']=b'original_exit_code=-11\nassigned_fragments=6000\nprecision=3\ncomplete_quantification_admitted=false\n'
        entries['failed-control/targets.tsv']=('name\tlength\n'+''.join(n+'\t'+str(l)+'\n' for n,l in targets[:4])).encode()
    assert len(case_rows)==26
    out.mkdir(parents=True);(out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    target=out/'original-quantification.zip';census={}
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(entries.items()):
            item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw);census[name]=dict(bytes=len(raw),sha256=sha(raw))
    with zipfile.ZipFile(target) as z:
        assert len(z.infolist())==len(entries)==len(set(z.namelist())) and z.testzip() is None
        assert all(z.read(name)==raw for name,raw in entries.items())
    provenance=dict(status='frozen-complete-original-Salmon-quantification-tables',source_checkpoint='docs/literature/salmon-native-development/VALIDATION.json',source_checkpoint_sha256=sha((source/'VALIDATION.json').read_bytes()),
        source_archive_sha256=proof['archive']['sha256'],producer_artifact=proof['artifact'],original_successes=25,complete_successful_rows=1000,retained_failed_processes=1,
        archive=dict(bytes=target.stat().st_size,sha256=sha(target.read_bytes())),files=census,scope='Exact original tables and metadata plus separately protected complete target identities/lengths; a failed process has a complete compatible arithmetic prefix and remains unadmitted. One synthetic population is reused; no biological or whole-paper reproduction certificate.')
    (out/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(files=len(entries),archive_bytes=target.stat().st_size,archive_sha256=provenance['archive']['sha256'])))


if __name__=='__main__':main()
