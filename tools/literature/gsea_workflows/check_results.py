"""Independent decoder/comparator for native workflow binary objects and original API exports."""
from pathlib import Path
import math,struct

class Decoder:
    def __init__(self,raw):self.raw=raw;self.offset=0
    def take(self,format):
        count=struct.calcsize(format)
        if self.offset+count>len(self.raw):raise ValueError('Truncated BGS1 object')
        result=struct.unpack_from(format,self.raw,self.offset);self.offset+=count;return result[0] if len(result)==1 else result
    def integer(self):return self.take('>i')
    def text(self):
        count=self.integer()
        if count<0 or count>len(self.raw)-self.offset:raise ValueError('Invalid BGS1 string length')
        b=self.raw[self.offset:self.offset+count];self.offset+=count;return b.decode('utf-8','strict')
    def floats(self):return [self.take('>f') for _ in range(self.integer())]
    def strings(self):return [self.text() for _ in range(self.integer())]

def decode(path):
    return decode_bytes(Path(path).read_bytes())

def decode_bytes(raw):
    d=Decoder(raw)
    if (d.integer(),d.integer())!=(0x42475331,1):raise ValueError('Unknown BGS1 binary format')
    result=dict(edition=d.text(),null_model=d.text(),work=d.take('>q'))
    result['ranks']=[dict(name=d.text(),score=d.take('>f'),row=d.integer(),metric=d.take('>d')) for _ in range(d.integer())]
    result['dispositions']=[dict(name=d.text(),input_members=d.integer(),matched=d.strings(),absent=d.strings(),status=d.text(),filter_disabled=d.take('>?')) for _ in range(d.integer())]
    result['sets']=[]
    for _ in range(d.integer()):
        s=dict(name=d.text(),members=d.strings(),es=d.take('>f'),rank_at_es=d.integer(),rank_score_at_es=d.take('>f'))
        s['hits']=[d.integer() for _ in range(d.integer())];s['hit_profile']=d.floats();s['full_profile']=d.floats();s['null_es']=d.floats();s['normalized_null']=d.floats()
        s.update(nes=d.take('>f'),nominal_p=d.take('>f'),fdr=d.take('>f'),fwer=d.take('>f'));result['sets'].append(s)
    if d.offset!=len(d.raw):raise ValueError('Trailing BGS1 binary payload')
    return result

def f32(value):return struct.pack('>f',float(value))
def compare(path,original,raw_metrics=False):
    return compare_bytes(Path(path).read_bytes(),Path(original).read_bytes(),raw_metrics=raw_metrics,label=str(path))

def compare_bytes(raw,original,raw_metrics=False,label='saved original counterpart'):
    path=label;actual=decode_bytes(raw);rows=[line.split('\t') for line in original.decode('utf-8').splitlines()]
    rank=[r for r in rows if r[0]=='RANK'];count=0
    if len(rank)!=len(actual['ranks']):raise AssertionError('Different complete ranking size')
    def equal(expected,got,label):
        nonlocal count
        count+=1
        if f32(expected)!=f32(got):raise AssertionError(f'{path}/{label}: {expected} != {got}')
    def array(expected,got,label):
        if len(expected)!=len(got):raise AssertionError(label+' dimensions differ')
        for i,(a,b) in enumerate(zip(expected,got)):equal(a,b,label+'/'+str(i))
    for i,(row,got) in enumerate(zip(rank,actual['ranks'])):
        if row[2]!=got['name']:raise AssertionError('Literal rank identity/order differs')
        equal(row[3],got['score'],'rank/'+str(i))
        if raw_metrics:
            count+=1
            if struct.pack('>d',float(row[4]))!=struct.pack('>d',got['metric']):raise AssertionError('Raw double ranking metric differs')
    maps={key:{r[1]:r[2:] for r in rows if r[0]==key} for key in ['SCORE','MEMBERS','NULL','HITS','HIT_PROFILE','FULL_PROFILE']}
    if list(maps['SCORE'])!=[s['name'] for s in actual['sets']]:raise AssertionError('Complete family/order differs')
    for s in actual['sets']:
        name=s['name'];score=maps['SCORE'][name]
        if s['members']!=maps['MEMBERS'][name]:raise AssertionError('Literal gene-set member order differs')
        equal(score[0],s['es'],name+'/es');equal(score[5],s['rank_at_es'],name+'/rankAtEs');equal(score[6],s['rank_score_at_es'],name+'/rankScoreAtEs')
        for index,key in enumerate(['nes','nominal_p','fdr','fwer'],1):equal(score[index],s[key],name+'/'+key)
        for key,tag in [('null_es','NULL'),('hit_profile','HIT_PROFILE'),('full_profile','FULL_PROFILE')]:array(maps[tag][name],s[key],name+'/'+key)
        if [int(x) for x in maps['HITS'][name]]!=s['hits']:raise AssertionError('Hit positions differ')
        if len(s['normalized_null'])!=len(s['null_es']) or not all(math.isfinite(x) for x in s['normalized_null']):raise AssertionError('Incomplete/nonfinite normalized-null transport')
    return count
