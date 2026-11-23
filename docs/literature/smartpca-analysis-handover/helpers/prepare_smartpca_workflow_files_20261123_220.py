from pathlib import Path
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1'
source=(T/'src/main/java/org/jlinalg/cli/MrPressoWorkflowFiles.java').read_text().replace('MrPressoWorkflowFiles','SmartpcaWorkflowFiles').replace('MrPressoWorkflowContracts','SmartpcaWorkflowContracts')
source=source.replace('Supply exact permitted association input:','Supply exact permitted EIGENSTRAT input:')
start=source.index('    static Map<String,Object> capture(');end=source.index('    static Map<String,Object> inventory(',start)
source=source[:start]+'''    static Map<String,Object> capture(Path logical,Path destination,long maximum)throws IOException {
        if(!Files.exists(logical))throw new SmartpcaWorkflowContracts.Unavailable("Supply exact permitted EIGENSTRAT input: "+logical);
        Path physical=logical.toRealPath();var expected=pin(physical);require(number(expected,"bytes")<=maximum,"Complete input exceeds declared byte capacity");
        try(var in=Files.newInputStream(physical,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){
            byte[] block=new byte[65536];long total=0;
            for(int n;(n=in.read(block))>=0;){total=Math.addExact(total,n);require(total<=maximum,"Input grew beyond complete-input byte capacity");out.write(block,0,n);}
        }
        require(equal(expected,pin(destination))&&equal(expected,pin(physical))&&logical.toRealPath().equals(physical),"Input bytes or logical/physical binding changed during capture");
        return map("logical",SmartpcaWorkflowContracts.address(logical),"physical",SmartpcaWorkflowContracts.address(physical),"identity",expected);
    }
''' + source[end:]
source=source.replace('++nodes<=256','++nodes<=512').replace('number(identity,"bytes")<=67_108_864','number(identity,"bytes")<=2_147_483_648L').replace('total<=134_217_728','total<=8_589_934_592L')
target=T/'src/main/java/org/jlinalg/cli/SmartpcaWorkflowFiles.java';assert not target.exists();target.write_text(source,encoding='utf-8')
print('Created isolated regular-file/seal helpers with streaming complete input capture.')
