# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate and compare complete recorded R value trees; no RDS byte/pointer parity claim."""
from pathlib import Path
import hashlib,json,re,stat,struct

SCHEMA='omicsmatic-complete-R-values-v1'
KINDS={'NULL','S4','list','pairlist','language','expression','symbol','logical','integer','double','complex','character','raw'}
SIZES={'logical':4,'integer':4,'double':8,'complex':16,'raw':1}

def unique(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate R value index key: '+key)
        result[key]=value
    return result

def integer(value,lower=0,upper=(1<<53)-1):
    if type(value)!=int or not lower<=value<=upper:raise ValueError('Invalid whole-number R value index field')
    return value

def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for data in iter(lambda:stream.read(1048576),b''):digest.update(data);size+=len(data)
    return dict(bytes=size,sha256=digest.hexdigest())

def character_payload(path,length):
    """Validate every original string boundary/NA tag without Unicode normalization."""
    size=path.stat().st_size
    with path.open('rb') as stream:
        for _ in range(length):
            flag=stream.read(1)
            if flag==b'\0':continue
            if flag!=b'\1':raise ValueError('Incomplete or invalid R character NA tag')
            count=stream.read(4)
            if len(count)!=4:raise ValueError('Incomplete R string encoding header')
            encoding_length=struct.unpack('<i',count)[0]
            if not 0<=encoding_length<=32:raise ValueError('Invalid R encoding label size')
            encoding=stream.read(encoding_length)
            if encoding not in [b'unknown',b'UTF-8',b'latin1',b'bytes']:raise ValueError('Unknown original R string encoding label')
            count=stream.read(4)
            if len(count)!=4:raise ValueError('Incomplete R string byte header')
            byte_length=struct.unpack('<i',count)[0]
            if byte_length<0 or stream.tell()+byte_length>size:raise ValueError('Incomplete original R character bytes')
            stream.seek(byte_length,1)
        if stream.tell()!=size:raise ValueError('Unindexed R character bytes')

def load_tree(directory,*,max_index_bytes=67108864,max_nodes=1000000,max_total_bytes=1099511627776):
    root=Path(directory).absolute()
    if root!=root.resolve() or not root.is_dir():raise ValueError('Canonical complete R value directory required')
    files={}
    for path in root.rglob('*'):
        mode=path.lstat().st_mode
        if stat.S_ISLNK(mode) or not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):raise ValueError('R value evidence contains a link/special node')
        if path.is_file():files[path.relative_to(root).as_posix()]=path
    if 'INDEX.json' not in files or 'original.rds' not in files:raise ValueError('Complete original RDS and value index are required')
    if files['INDEX.json'].stat().st_size>max_index_bytes:raise ValueError('R value index capacity exceeded')
    index=json.loads(files['INDEX.json'].read_bytes(),object_pairs_hook=unique,parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Nonfinite R graph JSON token: '+value)))
    expected={'schema','status','root','nodes','node_count','original_snapshot','original_snapshot_bytes','payload_and_snapshot_bytes','byte_order','pointer_identity_or_opaque_RDS_byte_parity_claimed'}
    if set(index)!=expected or index['schema']!=SCHEMA or index['status']!='COMPLETE_SUPPORTED_R_VALUE_TREE' or index['original_snapshot']!='original.rds' or index['byte_order']!='little' or index['pointer_identity_or_opaque_RDS_byte_parity_claimed'] is not False:raise ValueError('Unsupported complete R value index/scope')
    integer(index['payload_and_snapshot_bytes'],0,max_total_bytes)
    nodes=index['nodes'];count=integer(index['node_count'],1,max_nodes)
    if not isinstance(nodes,list) or len(nodes)!=count:raise ValueError('Incomplete R value node population')
    root_id=integer(index['root'],1,count);references={};payloads={};expected_files={'INDEX.json','original.rds'}
    for ordinal,node in enumerate(nodes,1):
        base={'id','type','S4','length','attribute_names','attributes','slot_names','slots','children','payload'}
        if not isinstance(node,dict) or node.get('id')!=ordinal or node.get('type') not in KINDS or type(node.get('S4'))!=bool or not base.issubset(node):raise ValueError('Invalid ordered R value node')
        length=integer(node['length']);edges=[]
        def reference(value):edges.append(integer(value,1,count))
        for key in ['attributes','slots','children']:
            if not isinstance(node[key],list):raise ValueError('R value child population is not an ordered list')
        if node['attribute_names'] is not None:reference(node['attribute_names'])
        elif node['attributes']:raise ValueError('Attribute values have no complete original names')
        for position,attribute in enumerate(node['attributes'],1):
            if not isinstance(attribute,dict) or attribute.get('index')!=position or type(attribute.get('represented_by_slot'))!=bool:raise ValueError('Invalid R value attribute binding')
            wanted={'index','represented_by_slot'} if attribute['represented_by_slot'] else {'index','represented_by_slot','value'}
            if set(attribute)!=wanted:raise ValueError('Unexpected/missing R value attribute field')
            if not attribute['represented_by_slot']:reference(attribute['value'])
            elif not node['S4']:raise ValueError('Non-S4 value cannot omit an attribute as a slot')
        if node['slot_names'] is not None:reference(node['slot_names'])
        elif node['slots']:raise ValueError('S4 slots have no original names')
        for value in node['slots']+node['children']:reference(value)
        extra=set()
        if node['S4']:
            if node['children'] or node['payload'] is not None:raise ValueError('Unexpected S4 value payload')
        elif node['type']=='symbol':
            extra={'symbol_name'};reference(node.get('symbol_name'))
        elif node['type'] in ['list','pairlist','language','expression']:
            extra={'element_names'};reference(node.get('element_names'))
            if len(node['children'])!=length:raise ValueError('Incomplete R value list/expression population')
        elif node['type']=='NULL':
            if length:raise ValueError('Nonempty R NULL node')
        else:
            extra={'encoding'};payload=node['payload']
            if not isinstance(payload,dict) or set(payload)!={'path','bytes'}:raise ValueError('Missing exact R atomic payload')
            name=payload['path'];wanted='records/'+str(ordinal).zfill(7)+'.bin'
            if name!=wanted or not re.fullmatch(r'records/[0-9]{7}\.bin',name):raise ValueError('Invalid/traversing R payload path')
            if name not in files or files[name].stat().st_size!=integer(payload['bytes']):raise ValueError('Incomplete original R payload bytes')
            if node['type']=='character':
                character_payload(files[name],length)
                if node['encoding']!='R_character_vector_v1: flag0=NA; flag1=int32le_encoding_length,encoding_bytes,int32le_value_length,R_native_string_bytes':raise ValueError('Unknown R character representation')
            else:
                if payload['bytes']!=length*SIZES[node['type']] or node['encoding']!='R_'+node['type']+'_'+str(SIZES[node['type']])+'_bytes_little_endian; original_NA_and_NaN_bits_retained':raise ValueError('Incomplete/mismatched original atomic R representation')
            expected_files.add(name);payloads[name]=pin(files[name])
        if set(node)!=base|extra:raise ValueError('Unexpected/missing complete R node metadata')
        if not node['S4'] and node['type'] not in SIZES and node['type']!='character' and node['payload'] is not None:raise ValueError('Unexpected non-atomic R payload')
        if not node['S4'] and (node['slots'] or node['slot_names'] is not None):raise ValueError('Non-S4 value has slot metadata')
        if node['type'] not in ['list','pairlist','language','expression'] and node['children']:raise ValueError('Unexpected R child population')
        references[ordinal]=edges
    if set(files)!=expected_files:raise ValueError('Unindexed or omitted complete R evidence files')
    if integer(index['original_snapshot_bytes'])!=files['original.rds'].stat().st_size:raise ValueError('Incomplete original RDS snapshot bytes')
    total=index['original_snapshot_bytes']+sum(row['bytes'] for row in payloads.values())
    if integer(index['payload_and_snapshot_bytes'],0,max_total_bytes)!=total:raise ValueError('Complete R value byte population differs')
    reached=set();pending=[root_id]
    while pending:
        identifier=pending.pop()
        if identifier in reached:raise ValueError('R value tree contains shared/cyclic node references')
        reached.add(identifier);pending.extend(references[identifier])
    if reached!=set(references):raise ValueError('Unreachable/omitted original R value nodes')
    return dict(root=root,index=index,payloads=payloads,original_snapshot=pin(files['original.rds']))

def compare_trees(reference,actual):
    left=load_tree(reference);right=load_tree(actual)
    # These two fields account for the separately retained opaque RDS byte size.
    # All value nodes, types, attributes, slots, order and atomic bytes are compared.
    accounting={'original_snapshot_bytes','payload_and_snapshot_bytes'}
    if {k:v for k,v in left['index'].items() if k not in accounting}!={k:v for k,v in right['index'].items() if k not in accounting}:raise ValueError('Complete original R value structure/type/order differs')
    if left['payloads']!=right['payloads']:raise ValueError('Complete original R atomic values/NA/NaN/identifier bytes differ')
    return dict(status='PASS_COMPLETE_SUPPORTED_ORIGINAL_R_VALUE_TREE',nodes=left['index']['node_count'],atomic_payloads=len(left['payloads']),atomic_payload_bytes=sum(row['bytes'] for row in left['payloads'].values()),pointer_or_opaque_RDS_byte_parity_claimed=False)
