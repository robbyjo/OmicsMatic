/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.jlinalg.sequence.TpmCalculator;

/** Internal fixed worker. The public controller alone can admit a recorded operation. */
public final class TpmCalculatorWorker {
    private TpmCalculatorWorker() { }
    public static void main(String[] args) {
        boolean returned=false;
        try {
            if(args.length!=1)throw new IOException("Internal native worker requires its exact captured recipe");
            Path recipe=Path.of(args[0]).toAbsolutePath().normalize();Map<?,?> m=NextflowFiles.object(TpmCalculatorRecords.read(recipe));
            NextflowFiles.keys(m,"schema","effective","input_root","output_root","implementation");NextflowFiles.integer(m.get("schema"),1,1);
            var implementation=TpmCalculatorRecords.implementation();
            if(!NextflowJson.json(implementation.identity()).equals(NextflowJson.json(m.get("implementation"))))throw new IOException("Native worker implementation differs from recorded controller classes");
            if(htsjdk.samtools.Defaults.USE_LIBDEFLATE)throw new IOException("Native transport profile requires the explicitly selected JDK inflater");
            var request=TpmCalculatorRequest.read(m.get("effective"),true);
            Path inputs=Path.of(NextflowFiles.text(m.get("input_root"))).toAbsolutePath().normalize(),output=Path.of(NextflowFiles.text(m.get("output_root"))).toAbsolutePath().normalize();
            var samples=new ArrayList<TpmCalculator.Sample>();
            for(var s:request.samples())samples.add(new TpmCalculator.Sample(s.name(),TpmCalculatorRecords.child(inputs,s.member())));
            System.out.println("NATIVE_TPMCALCULATOR_ANALYSIS_STARTED profile="+TpmCalculatorRequest.PROFILE);System.out.flush();
            var result=TpmCalculator.readAnnotation(TpmCalculatorRecords.child(inputs,request.annotation()),request.options()).quantify(samples);returned=true;
            System.out.println("NATIVE_TPMCALCULATOR_COMPUTATION_RETURNED");System.out.flush();
            var conditions=TpmCalculatorProducts.write(result,request,output);
            TpmCalculatorRecords.json(recipe.getParent().resolve("RETURN.json"),Map.of("status","RETURNED_AND_RECORDED_NATIVE_PRODUCTS","computed",true,
                "products_recorded",true,"conditions",conditions,"libdeflate_enabled",htsjdk.samtools.Defaults.USE_LIBDEFLATE,
                "java_runtime",System.getProperty("java.runtime.version"),"java_vm",System.getProperty("java.vm.name"),"os",System.getProperty("os.name"),"architecture",System.getProperty("os.arch")));
        }catch(Throwable e) {
            System.err.println("FAIL2_NATIVE_TPMCALCULATOR_WORKER_UNADMITTED computed="+returned+"; no controller completion may be inferred from a partial return or product");
            e.printStackTrace(System.err);System.err.flush();System.exit(2);
        }
    }
}
