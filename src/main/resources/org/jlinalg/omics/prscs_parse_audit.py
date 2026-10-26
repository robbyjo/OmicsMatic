"""Call unmodified original PRS-CS parsers; Java independently checks every result."""
import json
from pathlib import Path
import sys


def main():
    if len(sys.argv)!=3:
        raise ValueError('Use prscs_parse_audit.py REQUEST_JSON OUTPUT_TSV')
    request_path,output_path=map(Path,sys.argv[1:])
    if request_path.stat().st_size>1048576:
        raise ValueError('Oversized original-parser audit request')
    request=json.loads(request_path.read_text(encoding='utf-8'))
    if set(request)!={'source','reference_info','target_prefix','summary','sample_size','chromosomes'}:
        raise ValueError('Unknown/missing original-parser audit request keys')
    sys.path.insert(0,request['source'])
    import parse_genet
    with output_path.open('x',encoding='utf-8',newline='\n') as stream:
        stream.write('CHR\tSNP\tBP\tA1\tA2\tMAF\tBETA\tFLP\n')
        for chromosome in request['chromosomes']:
            reference=parse_genet.parse_ref(request['reference_info'],chromosome)
            target=parse_genet.parse_bim(request['target_prefix'],chromosome)
            result=parse_genet.parse_sumstats(reference,target,request['summary'],request['sample_size'])
            keys=['CHR','SNP','BP','A1','A2','MAF','BETA','FLP']
            if any(len(result[key])!=len(result['SNP']) for key in keys):
                raise ValueError('Original parser returned inconsistent result lengths')
            for values in zip(*(result[key] for key in keys)):
                stream.write('\t'.join(map(str,values))+'\n')


if __name__=='__main__':
    main()
