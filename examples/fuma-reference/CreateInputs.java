import java.nio.file.Files;
import java.nio.file.Path;

/** Creates a new, wholly synthetic autosomal hardcall example; never overwrites. */
public final class CreateInputs {
    private CreateInputs() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Supply a NEW work directory");
        Path work=Path.of(args[0]).toAbsolutePath().normalize();Files.createDirectory(work);Path input=Files.createDirectory(work.resolve("input"));
        Files.writeString(input.resolve("panel.fam"),"F A 0 0 1 -9\nF B 0 0 2 -9\nF C 0 0 0 -9\nF D 0 0 1 -9\nF E A B 2 -9\n");
        Files.writeString(input.resolve("panel.bim"),"1 rs0 0 1101 G A\n1 rs1 0 100 G A\n1 rs2 0 1100 G A\n1 zero 0 2000 G A\n1 hetero 0 2100 G A\n1 missing 0 2200 G A\n");
        try(var bed=Files.newOutputStream(input.resolve("panel.bed"))){bed.write(new byte[]{0x6c,0x1b,1});for(int i=0;i<3;i++)bed.write(pack(0,1,2,1,2));bed.write(pack(0,0,0,0,2));bed.write(pack(1,1,1,1,0));bed.write(pack(-1,-1,-1,-1,1));}
        Files.writeString(input.resolve("variants.tsv"),"id\nrs0\nrs1\nrs2\nzero\nhetero\nmissing\n");
        Files.writeString(input.resolve("samples.tsv"),"fid\tiid\nF\tE\nF\tD\nF\tC\nF\tB\nF\tA\n");
        Files.writeString(input.resolve("gwas.tsv"),"chr\tpos\tnon_effect_allele\teffect_allele\trsID\tp\n1\t100\tA\tG\trs1\t1e-10\n1\t1101\tA\tG\trs0\t2e-10\n");
        System.out.println("Created synthetic inputs: "+input);
    }
    private static byte[] pack(int... calls){byte[] r=new byte[(calls.length+3)/4];for(int i=0;i<calls.length;i++){int code=switch(calls[i]){case -1->1;case 0->3;case 1->2;case 2->0;default->throw new IllegalArgumentException();};r[i/4]|=(byte)(code<<(2*(i%4)));}return r;}
}
