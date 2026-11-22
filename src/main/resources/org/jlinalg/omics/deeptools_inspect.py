"""Read-only format audits for typed JLinAlg original-deepTools workflows.

Uses the same pinned dependency initialization as the original program bridge.
No pickle loading, package installation, remote inputs or scientific source edits.
"""
import argparse
import csv
import gzip
import importlib.util
import json
import math
from pathlib import Path
import sys
import traceback
import zipfile


def regions(path):
    result = {}
    with open(path, encoding='utf-8') as stream:
        for fields in csv.reader(stream, delimiter='\t'):
            if not fields or fields[0].startswith('#'):
                continue
            if len(fields) != 6 or fields[3] in result:
                raise ValueError('BED6 contract has invalid or duplicate region identities')
            result[fields[3]] = fields
    return result


def matrix(request):
    import numpy as np
    expected = regions(request['regions'])
    labels = request['labels']
    columns = request['columns']
    rows = []
    kept = []
    kept_set = set()
    with gzip.open(request['input'], 'rt', encoding='utf-8') as stream:
        first = stream.readline(1_000_001)
        if len(first) > 1_000_000 or not first.startswith('@'):
            raise ValueError('Missing or oversized original computeMatrix header')
        header = json.loads(first[1:])
        if (header.get('sample_labels') != labels or
                header.get('sample_boundaries') != list(range(0, columns*len(labels)+1, columns)) or
                header.get('group_labels') != [request['group_label']]):
            raise ValueError('Matrix sample/group labels or column boundaries changed')
        for key,value in request.get('parameters',{}).items():
            if header.get(key)!=[value]*len(labels):raise ValueError('Matrix changed declared '+key)
        maximum=min(64*1024*1024,columns*len(labels)*400+4096)
        for line in iter(lambda:stream.readline(maximum+1),''):
            if len(line)>maximum:raise ValueError('Matrix row exceeds its bounded text budget')
            if (len(rows)+1)*columns*len(labels) > request['max_cells']:
                raise ValueError('Matrix exceeds max-cells')
            fields = line.rstrip('\n').split('\t')
            if len(fields) != 6+columns*len(labels) or fields[3] not in expected or fields[3] in kept_set:
                raise ValueError('Matrix has unexpected columns,region identities or duplicate rows')
            original = expected[fields[3]]
            if fields[:4] != original[:4] or fields[5] != original[5] or float(fields[4]) != float(original[4]):
                raise ValueError('Matrix changed BED6 coordinates,score,strand or region identity')
            values = [float(value) for value in fields[6:]]
            if any(math.isinf(value) for value in values):
                raise ValueError('Matrix contains infinite signal values')
            if request['missing'] != 'retain' and any(math.isnan(value) for value in values):
                raise ValueError('Matrix has missing signal or out-of-reference flank bins; supply represented signal,change flanks or explicitly choose missing retain')
            kept.append(fields[3])
            kept_set.add(fields[3])
            rows.append(values)
    if not rows:
        raise ValueError('No regions survived original matrix generation; inspect missingness,regions and zero-row policy')
    if kept != [identity for identity in expected if identity in kept_set]:
        raise ValueError('Original keep-order matrix changed input region order')
    if header.get('group_boundaries') != [0, len(rows)]:
        raise ValueError('Matrix group row boundaries changed')
    values = np.asarray(rows, dtype=float)
    if request.get('sorted_regions'):
        ordered=[]
        ordered_set=set()
        with open(request['sorted_regions'],encoding='utf-8') as source:
            for fields in csv.reader(source,delimiter='\t'):
                if not fields or fields[0].startswith('#'):continue
                if len(fields)<6 or fields[3] not in kept_set or fields[3] in ordered_set:
                    raise ValueError('Plotted/exported BED changed matrix region identities')
                original=expected[fields[3]]
                if fields[:4]!=original[:4] or fields[5]!=original[5]:
                    raise ValueError('Plotted/exported BED changed matrix coordinates/strand')
                ordered.append(fields[3])
                ordered_set.add(fields[3])
        if set(ordered)!=kept_set or request.get('sorted_keep') and ordered!=kept:
            raise ValueError('Plotted/exported BED omitted regions or violated keep-order policy')
    with open(request['status_table'], 'w', encoding='utf-8', newline='') as output:
        writer=csv.writer(output, delimiter='\t', lineterminator='\n')
        writer.writerow(['region_id','status','matrix_row'])
        positions={identity:index+1 for index,identity in enumerate(kept)}
        for identity in expected:
            writer.writerow([identity,'kept' if identity in positions else 'omitted-by-original',positions.get(identity,'NA')])
    if request.get('profile_data'):
        with open(request['profile_data'], encoding='utf-8') as source:
            lines=list(csv.reader(source, delimiter='\t'))
        data=lines[2:]
        if len(data)!=len(labels):
            raise ValueError('Profile data has unexpected sample/group rows')
        average=request['average']
        functions={'mean':np.nanmean,'median':np.nanmedian,'min':np.nanmin,'max':np.nanmax,'std':np.nanstd,'sum':np.nansum}
        for sample,fields in enumerate(data):
            if fields[:2]!=[labels[sample],request['group_label']] or len(fields)!=columns+2:
                raise ValueError('Profile data changed sample/group labels or bin count')
            block=values[:,sample*columns:(sample+1)*columns]
            truth=functions[average](block,axis=0)
            # Masked original reductions keep all-missing columns missing,including sum.
            truth[np.isnan(block).all(axis=0)]=np.nan
            actual=np.asarray([float(value) for value in fields[2:]])
            if not np.allclose(actual,truth,rtol=6e-6,atol=1e-6,equal_nan=True):
                raise ValueError('Original exported profile data differs from its declared reduction over the sealed matrix')
    return dict(rows=len(rows),columns=columns*len(labels),sample_labels=labels,
                missing_cells=int(np.isnan(values).sum()),omitted_regions=len(expected)-len(rows),header=header)


def npz_data(request):
    import numpy as np
    path=request['input']
    with zipfile.ZipFile(path) as archive:
        if set(archive.namelist()) != {'matrix.npy','labels.npy'}:
            raise ValueError('Summary NPZ must contain exactly matrix and labels arrays')
        for name in archive.namelist():
            if archive.getinfo(name).file_size > request['max_cells']*8+1_000_000:
                raise ValueError('NPZ expanded member exceeds the explicit resource budget')
            with archive.open(name) as source:
                version=np.lib.format.read_magic(source)
                if version==(1,0):shape,unused,dtype=np.lib.format.read_array_header_1_0(source)
                elif version==(2,0):shape,unused,dtype=np.lib.format.read_array_header_2_0(source)
                else:raise ValueError('Unsupported NPY header version')
            if dtype.hasobject or dtype.kind not in ('f','i','u','S','U'):
                raise ValueError('Object/pickled or unsupported summary arrays are forbidden')
            if name=='matrix.npy' and (len(shape)!=2 or math.prod(shape)>request['max_cells'] or dtype.kind not in ('f','i','u')):
                raise ValueError('Summary matrix shape/dtype exceeds the declared contract')
            if name=='labels.npy' and (len(shape)!=1 or shape[0]>1000 or dtype.kind not in ('S','U')):
                raise ValueError('Summary labels have unexpected shape/dtype')
    with np.load(path,allow_pickle=False) as archive:
        values=archive['matrix']
        labels=[value.decode('utf-8') if isinstance(value,bytes) else str(value) for value in archive['labels']]
    if (labels!=request['labels'] or values.shape[1]!=len(labels) or values.shape[0]<1 or
            not np.isfinite(values).all() or (values<0).any()):
        raise ValueError('BAM summary requires exact labels and finite nonnegative counts')
    return values,labels


def summary(request):
    import numpy as np
    values,labels=npz_data(request)
    with open(request['raw'],encoding='utf-8') as stream, open(request['table'], 'w',encoding='utf-8',newline='') as output:
        first=stream.readline().rstrip('\n')
        if first!="#'chr'\t'start'\t'end'\t'"+"'\t'".join(labels)+"'":
            raise ValueError('Original raw summary header differs from exact sample labels')
        writer=csv.writer(output,delimiter='\t',lineterminator='\n')
        writer.writerow(['chromosome','start','end',*labels])
        count=0
        previous=(-1,-1)
        reference=request['reference'];order={chrom:index for index,chrom in enumerate(reference)}
        expected_windows=None
        if request.get('regions'):
            from collections import Counter
            expected_windows=Counter((row[0],int(row[1]),int(row[2])) for row in regions(request['regions']).values())
        for line in stream:
            fields=line.rstrip('\n').split('\t')
            if len(fields)!=3+len(labels) or count>=len(values):
                raise ValueError('Raw summary has unexpected row or column count')
            chrom,start,stop=fields[0],int(fields[1]),int(fields[2])
            if chrom not in reference or not 0<=start<stop<=reference[chrom]:
                raise ValueError('Raw summary window lies outside the explicit reference')
            key=(order[chrom],start)
            if key<previous:
                raise ValueError('Raw summary windows are not in reference/coordinate order')
            previous=key
            if expected_windows is not None:
                window=(chrom,start,stop)
                if expected_windows[window]<1:raise ValueError('Summary changed supplied region coordinates/multiplicity')
                expected_windows[window]-=1
            elif start%request['step'] or stop!=min(start+request['bin_size'],reference[chrom]):
                raise ValueError('Summary output differs from declared bin/step grid')
            actual=np.asarray([float(value) for value in fields[3:]])
            if not np.array_equal(actual,values[count]):
                raise ValueError('Raw summary and NPZ count rows differ')
            writer.writerow(fields);count+=1
        if count!=len(values) or expected_windows is not None and any(expected_windows.values()):
            raise ValueError('Summary omitted rows or regions without an explicit policy')
        if expected_windows is None and count!=sum((length-1)//request['step']+1 for length in reference.values()):
            raise ValueError('Summary omitted bins from the explicit reference grid')
    return dict(rows=len(values),columns=len(labels),sample_labels=labels,zero_rows=int((values.sum(axis=1)==0).sum()))


def rank(values):
    order=sorted(range(len(values)),key=values.__getitem__);output=[0.]*len(values);start=0
    while start<len(order):
        stop=start+1
        while stop<len(order) and values[order[stop]]==values[order[start]]:stop+=1
        for index in order[start:stop]:output[index]=(start+1+stop)/2
        start=stop
    return output


def correlation(request):
    values,labels=npz_data(request)
    if len(labels)<2:raise ValueError('Correlation requires at least two samples')
    original=len(values)
    if request['skip_zeros']:values=values[values.sum(axis=1)!=0]
    if len(values)<2:raise ValueError('Fewer than two bins remain after the explicit zero-row policy')
    columns=[list(map(float,values[:,index])) for index in range(len(labels))]
    for label,column in zip(labels,columns):
        if min(column)==max(column):raise ValueError('Undefined correlation for constant sample '+label+'; inspect summary,filters and zero-row policy')
    if request['method']=='spearman':columns=[rank(column) for column in columns]
    centered=[[value-math.fsum(column)/len(column) for value in column] for column in columns]
    sums=[math.fsum(value*value for value in column) for column in centered]
    truth=[[math.fsum(x*y for x,y in zip(a,b))/math.sqrt(sums[i]*sums[j]) for j,b in enumerate(centered)] for i,a in enumerate(centered)]
    if request.get('result'):
        with open(request['result'],encoding='utf-8') as stream:lines=[line.rstrip('\n').split('\t') for line in stream if line.strip() and not line.startswith('#')]
        actual_labels=[label.strip("'") for label in lines[0][1:]]
        row_labels=[fields[0].strip("'") for fields in lines[1:]]
        if len(lines)!=len(labels)+1 or set(actual_labels)!=set(labels) or len(set(actual_labels))!=len(labels) or set(row_labels)!=set(labels) or len(set(row_labels))!=len(labels):
            raise ValueError('Original correlation output changed sample labels')
        for fields in lines[1:]:
            label=fields[0].strip("'")
            if label not in labels or len(fields)!=len(labels)+1:raise ValueError('Invalid correlation output row')
            for other,actual in zip(actual_labels,fields[1:]):
                if not math.isfinite(float(actual)) or abs(float(actual)-truth[labels.index(label)][labels.index(other)])>5.1e-5:
                    raise ValueError('Original correlation differs from independently centered dot products/tied ranks')
        with open(request['table'],'w',encoding='utf-8',newline='') as output:
            writer=csv.writer(output,delimiter='\t',lineterminator='\n');writer.writerow(['sample1','sample2','correlation','independent_reference'])
            for fields in lines[1:]:
                label=fields[0].strip("'")
                for other,actual in zip(actual_labels,fields[1:]):writer.writerow([label,other,actual,truth[labels.index(label)][labels.index(other)]])
    return dict(input_rows=original,retained_rows=len(values),omitted_zero_rows=original-len(values),sample_labels=labels)


def figure(request):
    path=Path(request['input']);size=path.stat().st_size
    if not 256<size<=request['max_bytes']:raise ValueError('Original figure is empty or exceeds max-bytes')
    kind=request['format']
    if kind=='png':
        from PIL import Image
        with Image.open(path) as image:
            image.load()
            if image.format!='PNG' or image.width<16 or image.height<16 or image.width*image.height>100_000_000:
                raise ValueError('Invalid original PNG dimensions/format')
            if all(left==right for left,right in image.convert('RGB').getextrema()):raise ValueError('Original figure is a single constant color')
            return dict(format=kind,bytes=size,width=image.width,height=image.height)
    if kind=='pdf':
        with path.open('rb') as source:
            if source.read(5)!=b'%PDF-':raise ValueError('Original PDF lacks a PDF header')
            source.seek(max(0,size-1024))
            if b'%%EOF' not in source.read():raise ValueError('Original PDF lacks its completion trailer')
    elif kind=='svg':
        import xml.etree.ElementTree as ET
        root=ET.parse(path).getroot()
        if root.tag!='{http://www.w3.org/2000/svg}svg' or not root.findall('.//{http://www.w3.org/2000/svg}path'):
            raise ValueError('Original SVG lacks its vector drawing')
    else:raise ValueError('Unsupported figure format')
    return dict(format=kind,bytes=size,validation='structural; rendered-export checks are separate')


def bam(request):
    import pysam
    reference = request['reference']
    counts = dict(records=0, mapped=0, unmapped=0, proper_pairs=0,
                  paired_records=0, sequence_records=0, quality_records=0,
                  tagged_records=0)
    with pysam.AlignmentFile(request['input'], 'rb') as source:
        if list(source.references) != list(reference) or dict(zip(source.references, source.lengths)) != reference:
            raise ValueError('BAM dictionary differs from the entire ordered explicit reference')
        if request.get('indexed', True) and not source.check_index():
            raise ValueError('BAM requires a readable existing BAI/CSI index')
        previous = (-1, -1)
        unplaced = False
        for read in source.fetch(until_eof=True):
            counts['records'] += 1
            if counts['records'] > request['max_records']:
                raise ValueError('BAM exceeds max-records; choose an explicit audited resource limit')
            counts['paired_records'] += int(read.is_paired)
            counts['proper_pairs'] += int(read.is_proper_pair)
            counts['sequence_records'] += int(read.query_sequence is not None)
            counts['quality_records'] += int(read.query_qualities is not None)
            counts['tagged_records'] += int(bool(read.get_tags()))
            if read.reference_id < 0:
                unplaced = True
            else:
                key = (read.reference_id, read.reference_start)
                if request.get('sorted', True) and (unplaced or key < previous):
                    raise ValueError('BAM is not coordinate sorted; sort and index the original input')
                previous = key
            if read.is_unmapped:
                counts['unmapped'] += 1
                continue
            counts['mapped'] += 1
            if (read.reference_start < 0 or read.reference_end is None or
                    read.reference_end > reference[read.reference_name]):
                raise ValueError('Mapped BAM alignment is outside its reference bounds')
            if request.get('match_only') and (len(read.cigartuples or []) != 1 or read.cigartuples[0][0] != 0):
                raise ValueError('This ATAC adapter requires one M CIGAR operation; retain the original BAM and use a separately reviewed complex-CIGAR workflow')
            if request.get('shifted'):
                if not read.is_proper_pair or not read.is_paired:
                    raise ValueError('ATAC output contains an unexpected non-proper pair')
                if read.query_sequence is not None or read.query_qualities is not None or read.get_tags():
                    raise ValueError('ATAC output differs from the documented original SEQ/QUAL/tag-loss contract')
                if read.next_reference_id != read.reference_id or not 0 <= read.next_reference_start < reference[read.reference_name] or abs(read.template_length) < 1:
                    raise ValueError('ATAC output has an invalid mate position/template length; review original pairs and fragment-length filters')
        if request.get('indexed', True) and source.mapped != counts['mapped']:
            raise ValueError('BAM index mapped count differs from the physical record scan; rebuild the index')
    if request.get('shifted') and counts['mapped'] == 0:
        raise ValueError('No ATAC alignments survived; inspect filters/proper-pair input and metrics')
    return counts


def bigwig(request):
    import pyBigWig
    reference = request['reference']
    covered = 0
    with pyBigWig.open(request['input']) as source:
        if not source.isBigWig() or list(source.chroms()) != list(reference) or source.chroms() != reference:
            raise ValueError('Require BigWig with the entire exact ordered explicit reference dictionary')
        for chrom, length in reference.items():
            # Bounded queries avoid an unbounded whole-chromosome interval tuple.
            for start in range(0, length, 1_000_000):
                stop = min(start + 1_000_000, length)
                previous = start
                for left, right, value in source.intervals(chrom, start, stop) or []:
                    left, right = max(left, start), min(right, stop)
                    if left < previous or right <= left or not math.isfinite(value):
                        raise ValueError('BigWig contains overlapping, invalid or nonfinite signal intervals')
                    covered += right - left
                    previous = right
    missing = sum(reference.values()) - covered
    if request['missing'] == 'error' and missing:
        raise ValueError('BigWig contains unrepresented bases; supply complete tracks or explicitly choose missing zero/retain')
    return dict(covered_bases=covered, unrepresented_bases=missing)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--bootstrap', type=Path, required=True)
    parser.add_argument('--request', type=Path, required=True)
    args = parser.parse_args()
    report = dict(status='initializing-audit')
    save = lambda: args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    save()
    try:
        spec = importlib.util.spec_from_file_location('jlinalg_deeptools_bootstrap', args.bootstrap)
        bootstrap = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bootstrap)
        initial = args.report.with_name(args.report.stem + '-bootstrap.json')
        sys.argv = [str(args.bootstrap), '--report', str(initial)]
        code = bootstrap.main()
        identity = json.loads(initial.read_text(encoding='utf-8'))
        if code or identity.get('status') != 'probe-completed':
            raise RuntimeError('Audit dependency initialization did not complete; inspect bootstrap marker')
        report.update(identity)
        report['status'] = 'auditing-input'
        save()
        request = json.loads(args.request.read_text(encoding='utf-8'))
        if request.get('schema') != 'jlinalg-deeptools-audit-v1':
            raise ValueError('Unrecognized audit schema')
        audit = {'bam': bam, 'bigwig': bigwig, 'matrix':matrix, 'summary':summary,
                 'correlation':correlation, 'figure':figure}[request['kind']](request)
        report.update(status='audit-completed', audit=audit)
        save()
        print('audit-completed', flush=True)
        return 0
    except BaseException:
        report.update(status='failed', error=traceback.format_exc())
        save()
        print(report['error'], file=sys.stderr, flush=True)
        return 2


if __name__ == '__main__':
    sys.exit(main())
