"""Frozen comparison cases from original MIT deepTools getRatio; no JLinAlg formula."""
import argparse,hashlib,importlib.util,json,math,random,sys
from pathlib import Path

EXPECTED='636cc1f54a15fc6bf47852e6f22c3c452edb86742f0867fbf64b96dcdc761f98'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def write_utf8(path,body):
    with path.open('w',encoding='utf-8',newline='\n') as f:f.write(body)
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args();source=a.source/'deeptools/getRatio.py';assert sha(source)==EXPECTED,'Original3.5.6 source differs';out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    import numpy as np
    spec=importlib.util.spec_from_file_location('original_deeptools_ratio',source);original=importlib.util.module_from_spec(spec);spec.loader.exec_module(original)
    rng=random.Random(128871);operations=['ratio','log2','reciprocal_ratio','subtract','add','first','second','mean'];pairs=[(0,0),(0,1),(1,0),(1,1),(-1,1),(1,-1),(-1,-1),(math.nan,0),(0,math.nan),(math.nan,math.nan),(1e308,1e308),(1e-300,1e-300),(9,19),(1,2),(2,1)]
    pairs += [(rng.uniform(-100,100),rng.uniform(-100,100)) for _ in range(493)]
    cases=[]
    for i,(first,second) in enumerate(pairs):
        for op in operations:
            scale1,scale2=rng.choice([(1,1),(1,.5),(2,.25),(0,1),(-1,2)]);p1,p2=rng.choice([(0,0),(1,1),(2,.5),(0,1)]);args=dict(valueType=op,scaleFactors=(scale1,scale2),pseudocount=(p1,p2));value=float(original.getRatio([np.float64(first),np.float64(second)],args));status='MISSING_INPUT' if math.isnan(first) or math.isnan(second) else 'FINITE' if math.isfinite(value) else 'NONFINITE_RESULT';cases.append([str(len(cases)),op,*map(lambda x:format(x,'.17g'),[first,second,scale1,scale2,p1,p2,value]),status])
    dest=out/'comparison-reference.tsv';write_utf8(dest,'case_id\toperation\tfirst\tsecond\tfirst_scale\tsecond_scale\tfirst_pseudo\tsecond_pseudo\texpected\tstatus\n'+''.join('\t'.join(row)+'\n' for row in cases));record=dict(source='Original unmodified deepTools3.5.6 getRatio.py invoked with NumPy scalar signals; no JLinAlg formula or I/O stub',source_sha256=EXPECTED,original_wheel_sha256='7adbb7f27ba45d7dfc19687ffb3944b00687c6f2219c9e80d3b08df464a2375f',reference_sha256=sha(dest),cases=len(cases),seed=128871,numpy_version=np.__version__,python_version=sys.version,generator_sha256=sha(Path(__file__)),scope='Eight supplied-signal comparison operations,explicit scale/pseudocount order,missing inputs and computed nonfinite values. No full BigWig/BAM/runtime,normalization estimation,feature matrix or plotting parity.');write_utf8(out/'comparison-provenance.json',json.dumps(record,indent=2)+'\n');print(json.dumps(dict(status='passed',cases=len(cases),reference_sha256=sha(dest))))
if __name__=='__main__':main()
