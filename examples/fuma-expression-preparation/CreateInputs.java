import java.nio.file.Files;
import java.nio.file.Path;

/** Creates NEW synthetic sample-level expression and independent DEG resources. */
public final class CreateInputs {
    private CreateInputs() { }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Supply a NEW work directory");
        Path work=Path.of(args[0]).toAbsolutePath().normalize();Files.createDirectory(work);Path input=Files.createDirectory(work.resolve("input"));
        String body="ENSG1.7\t0\t3\t7\t100\t9999\nENSG2\t1\t1\t1\t1\t100\nENSG3\t0\t0\t0\t2\t0\nENSG4\t0\t0\t0\t0\t100\n";
        Files.writeString(input.resolve("raw.tsv"),"gene\ts1\ts2\ts3\ts4\ts5\n"+body);
        var gct=new StringBuilder("#1.2\n4\t5\nName\tDescription\ts1\ts2\ts3\ts4\ts5\n");for(String row:body.lines().toList())gct.append(row.replaceFirst("\t","\tsynthetic gene\t")).append('\n');Files.writeString(input.resolve("raw.gct"),gct);
        Files.writeString(input.resolve("samples.tsv"),"sample\tlabel\ns4\tB\ns3\tA\ns1\tA\ns2\tA\n");Files.writeString(input.resolve("labels.tsv"),"label\nB\nA\n");
        Files.writeString(input.resolve("symbols.tsv"),"ensg\tsymbol\nENSG1.7\tONE\nENSG3\tTHREE\n");Files.writeString(input.resolve("query.txt"),"ENSG1.7\nENSG3\n");Files.writeString(input.resolve("background.txt"),"ENSG1.7\nENSG3\n");
        Files.writeString(input.resolve("deg.tsv"),"all\t2\tENSG1.7:ENSG3\nA.up\t1\tENSG1.7\nA.down\t1\tENSG3\nA.twoside\t2\tENSG1.7:ENSG3\n");
        System.out.println("Created wholly synthetic inputs: "+input);
    }
}
