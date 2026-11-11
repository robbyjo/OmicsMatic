package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

final class RsemRuntimeTest {
    private List<String> fit(){return List.of("--alignments","--no-bam-output","--keep-intermediate-files","--seed","1618","--num-threads","1","/inputs/reads.sam","/reference/reference","/outputs/sample");}
    @Test void fixed_version_probe_accepts_no_replacement_arguments()throws Exception{
        RsemRuntime.validateArguments(RsemRuntime.Program.VERSION,List.of(),1);
        assertThrows(IOException.class,()->RsemRuntime.validateArguments(RsemRuntime.Program.VERSION,List.of("--help"),1));
    }
    @Test @EnabledOnOs(OS.LINUX) void original_alignment_fit_requires_its_declared_intermediates_seed_and_thread_contract()throws Exception{
        RsemRuntime.validateArguments(RsemRuntime.Program.QUANTIFY,fit(),1);
        for(String required:List.of("--alignments","--no-bam-output","--keep-intermediate-files")){
            var args=new ArrayList<>(fit());args.remove(required);assertThrows(IOException.class,()->RsemRuntime.validateArguments(RsemRuntime.Program.QUANTIFY,args,1));
        }
        var tooMany=new ArrayList<>(fit());tooMany.set(6,"2");assertThrows(IOException.class,()->RsemRuntime.validateArguments(RsemRuntime.Program.QUANTIFY,tooMany,1));
    }
    @Test @EnabledOnOs(OS.LINUX) void unsafe_shell_tokens_external_aligners_repeated_options_and_extra_positionals_are_refused()throws Exception{
        for(String addition:List.of("--star","--prep-pRSEM","--calc-pme;touch","--alignments","/extra")){
            var args=new ArrayList<>(fit());args.add(addition);assertThrows(IOException.class,()->RsemRuntime.validateArguments(RsemRuntime.Program.QUANTIFY,args,1));
        }
        for(String bad:List.of("/inputs/with space.sam","/inputs/x;touch","/inputs/$(id)","/inputs/a\n.sam")){
            var args=new ArrayList<>(fit());args.set(7,bad);assertThrows(IOException.class,()->RsemRuntime.validateArguments(RsemRuntime.Program.QUANTIFY,args,1));
        }
    }
}
