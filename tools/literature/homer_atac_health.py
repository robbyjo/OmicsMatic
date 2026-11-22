"""Deterministic synthetic health inputs for the separately admitted HOMER ATAC profile.

These are installation checks, not paper data or estimates of biological validity.
"""


def fixture_sam(replicate):
    if replicate not in (0,1):raise ValueError('Exactly two defined health replicates')
    rows=[];sequence='ACGT'*12+'AC'
    for cluster in [10000,40000,80000]:
        for i in range(150):
            p=cluster+i;name=f'r{replicate}-cluster{cluster}-{i}'
            for flag,position,mate,tlen in [(99,p,p+90,100),(147,p+90,p,-100)]:
                rows.append('\t'.join(map(str,[name,flag,'chr1',position,60,'50M','=',mate,tlen,sequence,'I'*50])))
    for i in range(1000):
        rows.append('\t'.join(map(str,[f'r{replicate}-background-{i}',0 if i%2==0 else 16,'chr1',150000+i*400,60,'50M','*',0,0,sequence,'I'*50])))
    return '@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:1000000\n'+'\n'.join(rows)+'\n'


def peak_rows(text):
    return '\n'.join(line for line in text.splitlines() if line and not line.startswith('#'))+'\n'
