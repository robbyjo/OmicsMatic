"""Bounded read-only PRS-CS HDF5 inspection; Java validates exported LD and identities."""
import hashlib
import json
import os
from pathlib import Path
import sys
import traceback

# No HDF5 plugin or alternate module path is selected from a reference panel.
os.environ['HDF5_PLUGIN_PRELOAD'] = '::'
os.environ['HDF5_PLUGIN_PATH'] = ''
import h5py
import numpy as np


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() if hasattr(hashlib, 'file_digest') else legacy_digest(stream)


def legacy_digest(stream):
    value = hashlib.sha256()
    while block := stream.read(1048576):
        value.update(block)
    return value.hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def token(value):
    require(isinstance(value, str) and value and len(value.encode('utf-8')) <= 8192,
            'Missing or oversized HDF5 SNP identifier')
    require(not any(c.isspace() or ord(c)<32 or 127<=ord(c)<160 for c in value),
            'Whitespace/control character in HDF5 SNP identifier')
    return value


def hard_member(group, name):
    require(isinstance(group.get(name,getlink=True),h5py.HardLink),
            'Only local hard-linked HDF5 groups/datasets are admitted: '+name)
    return group[name]


def dataset(value, name):
    require(isinstance(value,h5py.Dataset), 'Expected HDF5 dataset: '+name)
    require(not value.is_virtual and not value.external,
            'Virtual/external HDF5 data storage is not admitted: '+name)
    properties = value.id.get_create_plist()
    for index in range(properties.get_nfilters()):
        filter_id = properties.get_filter(index)[0]
        require(filter_id in {1,2,3,6,32000},
                'Unsupported HDF5 filter; no external plugin is loaded: '+str(filter_id))
    return value


def main():
    require(len(sys.argv)==3, 'Use prscs_hdf5_audit.py REQUEST_JSON OUTPUT_JSON')
    request_path,output_path = map(Path,sys.argv[1:])
    require(request_path.is_file() and request_path.stat().st_size <= 128*1048576,
            'HDF5 inspection request is missing or exceeds 128 MiB')
    request = json.loads(request_path.read_text(encoding='utf-8'))
    allowed = {'reference_dir','kind','chromosomes','wanted','output_dir','maximum_source_bytes',
               'maximum_ld_cells','maximum_block_snps','maximum_blocks','maximum_variants'}
    require(set(request)==allowed, 'Unknown/missing HDF5 inspection request fields')
    source = Path(request['reference_dir']).resolve(strict=True)
    output = Path(request['output_dir']).resolve(strict=True)
    require(output==output_path.parent.resolve(strict=True), 'HDF5 inspection output directory differs')
    require(not output.is_relative_to(source) and not source.is_relative_to(output),
            'HDF5 inspection output must be separate from the source reference directory')
    require(request['kind'] in {'1kg','ukbb'}, 'Declare exact original reference kind')
    chromosomes = request['chromosomes']
    require(isinstance(chromosomes,list) and chromosomes and len(chromosomes)==len(set(chromosomes))
            and all(isinstance(c,int) and not isinstance(c,bool) and 1<=c<=22 for c in chromosomes),
            'Declare unique autosomal chromosome integers')
    wanted = request['wanted']
    require(isinstance(wanted,dict) and set(wanted)=={str(c) for c in chromosomes},
            'HDF5 inspection requires an explicit candidate SNP family for each chromosome')
    for key in ['maximum_source_bytes','maximum_ld_cells','maximum_block_snps','maximum_blocks','maximum_variants']:
        require(isinstance(request[key],int) and not isinstance(request[key],bool) and request[key]>0,
                'Invalid explicit HDF5 resource ceiling: '+key)
    total_bytes = total_cells = total_variants = total_blocks = exported_cells = 0
    results = []
    block_path = output/'hdf5-blocks.tsv'
    snp_path = output/'hdf5-selected-snps.tsv'
    with block_path.open('x',encoding='utf-8',newline='\n') as block_writer, snp_path.open('x',encoding='utf-8',newline='\n') as snp_writer:
        block_writer.write('chromosome\tblock\tsource_snps\tselected_snps\tsource_dtype\tmatrix_file\tmatrix_sha256\tmatrix_bytes\n')
        snp_writer.write('chromosome\tblock\tsource_index\tselected_index\tsnp\n')
        for chromosome in chromosomes:
            selected = wanted[str(chromosome)]
            require(isinstance(selected,list) and selected and all(isinstance(s,str) for s in selected),
                    'Each requested chromosome requires a nonempty candidate SNP list')
            require(len(selected)==len(set(selected)) and len(selected)<=request['maximum_variants'],
                    'Duplicate/oversized candidate SNP family')
            for snp in selected:
                token(snp)
            selected = set(selected)
            source_file = source/f"ldblk_{request['kind']}_chr{chromosome}.hdf5"
            require(source_file.is_file(), 'Missing original reference HDF5 file: '+str(source_file))
            total_bytes += source_file.stat().st_size
            require(total_bytes<=request['maximum_source_bytes'], 'HDF5 source-byte bound exceeded')
            before = sha(source_file)
            seen = set()
            with h5py.File(source_file,'r') as handle:
                count = len(handle)
                total_blocks += count
                require(0<count<=request['maximum_blocks'] and total_blocks<=request['maximum_blocks'],
                        'Reference HDF5 block count exceeds its bound or is empty')
                require(set(handle)=={'blk_'+str(i) for i in range(1,count+1)},
                        'Original HDF5 requires contiguous blk_1..blk_N groups with no extra root members')
                for index in range(1,count+1):
                    group = hard_member(handle,'blk_'+str(index))
                    require(isinstance(group,h5py.Group) and {'ldblk','snplist'}<=set(group),
                            'Expected ldblk/snplist in each original reference block')
                    matrix = dataset(hard_member(group,'ldblk'),'ldblk')
                    identifiers = dataset(hard_member(group,'snplist'),'snplist')
                    require(len(matrix.shape)==2 and matrix.shape[0]==matrix.shape[1],
                            'Reference LD dataset must be a square matrix')
                    size = matrix.shape[0]
                    require(size<=request['maximum_block_snps'], 'Reference block SNP bound exceeded')
                    numeric = matrix.dtype.kind in {'i','u','b'} and matrix.dtype.itemsize<=8 or matrix.dtype.kind=='f' and matrix.dtype.itemsize in {4,8}
                    require(identifiers.shape==(size,) and numeric,
                            'Reference LD requires real numeric values and a matching one-dimensional SNP list')
                    string_type = h5py.check_string_dtype(identifiers.dtype)
                    require(string_type is not None and (string_type.length is None or string_type.length<=8192),
                            'Reference SNP list must contain bounded byte/UTF-8 strings')
                    total_cells += size*size
                    total_variants += size
                    require(total_cells<=request['maximum_ld_cells'], 'Reference LD-cell work bound exceeded')
                    require(total_variants<=request['maximum_variants'], 'Reference SNP-list work bound exceeded')
                    # Read strings individually: do not materialize a whole variable-length array.
                    names,indices = [],[]
                    for offset in range(size):
                        raw = identifiers[offset]
                        require(isinstance(raw,(bytes,np.bytes_)), 'Original PRS-CS requires byte-valued HDF5 SNP identifiers')
                        require(len(raw)<=8192, 'HDF5 SNP identifier exceeds 8192 UTF-8 bytes')
                        snp = token(bytes(raw).decode('utf-8',errors='strict'))
                        require(snp not in seen, 'Duplicate SNP across reference HDF5 blocks: '+snp)
                        seen.add(snp)
                        if snp in selected:
                            names.append(snp);indices.append(offset)
                    entry = {}
                    if indices:
                        # The original program also materializes each full source block before subsetting.
                        # Resource counts above include these full matrices, including unused markers.
                        values = np.asarray(matrix)[np.ix_(indices,indices)].astype('<f8',copy=False)
                        binary = output/f'chr{chromosome}-block{index}.raw-f64le'
                        with binary.open('xb') as stream:
                            stream.write(values.tobytes(order='C'))
                        exported_cells += values.size
                        entry.update(matrix_file=binary.name,matrix_sha256=sha(binary),matrix_bytes=binary.stat().st_size)
                        del values
                    block_writer.write('\t'.join(map(str,[chromosome,index,size,len(indices),matrix.dtype.str,entry.get('matrix_file',''),entry.get('matrix_sha256',''),entry.get('matrix_bytes',0)]))+'\n')
                    for selected_index,(offset,snp) in enumerate(zip(indices,names)):
                        snp_writer.write(f'{chromosome}\t{index}\t{offset}\t{selected_index}\t{snp}\n')
            require(selected<=seen, 'Candidate SNPs are absent from the original HDF5 reference: '+','.join(sorted(selected-seen)[:5]))
            require(sha(source_file)==before, 'Original reference HDF5 changed during inspection')
            results.append(dict(chromosome=chromosome,source_file=str(source_file),source_sha256=before,blocks=count,selected_snp_entries=len(selected),source_snp_entries=len(seen)))
    result = dict(status='exported-for-native-validation',source_bytes=total_bytes,source_ld_cells=total_cells,
                  source_snp_entries=total_variants,source_blocks=total_blocks,exported_ld_cells=exported_cells,
                  chromosomes=results,block_metadata_file=block_path.name,block_metadata_sha256=sha(block_path),
                  snp_index_file=snp_path.name,snp_index_sha256=sha(snp_path),scope='Read-only local HDF5 metadata/selected raw matrices. No allele flip,LD projection,model inference or PSD certification. Java must validate numeric matrices,exact SNP/order identities and explicit model choices. Resource ceilings are not a total process RAM guarantee,including individual variable-length HDF5 strings.')
    with output_path.open('x',encoding='utf-8') as stream:
        stream.write(json.dumps(result,ensure_ascii=False,indent=2)+'\n')


if __name__=='__main__':
    try:
        main()
    except Exception as error:
        print('PRS-CS HDF5 INPUT FAILED: '+str(error),file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        raise SystemExit(2)
