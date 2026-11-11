/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Real controlled Linux processes; no user-selected command or shared process is killed. */
public final class SnifflesProcessControls {
    private SnifflesProcessControls() { }
    private static final Map<String, String> ENV = Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8");
    private static final List<Object> controls = new ArrayList<>();
    private static SnifflesProcess.Limits limits(long wall, long bytes) { return new SnifflesProcess.Limits(wall, 1073741824, 30, 8388608, 256, bytes, 512, 16, 64, 536870912, 10); }
    private static List<String> python(String source) { return List.of("/usr/bin/python3.12", "-B", "-P", "-s", "-S", "-c", source); }
    private static void pass(Path root, String name, String source) throws Exception {
        Path work = root.resolve(name); var result = SnifflesProcess.executeOwned(python(source), ENV, work, limits(10000,16777216), () -> {}, () -> {});
        require(result.exitCode() == 0 && result.ownedLiveAfterCleanup().isEmpty(), "Positive owned process failed");
        require(!Files.exists(work.resolve("COMPLETED.json")), "Process primitive must not issue scientific admission");
        controls.add(Map.of("name", name, "status", "PASS_POSITIVE_PROCESS_ONLY", "ledger", pin(result.ledger(), 16777216, () -> {})));
    }
    private static void fail(Path root, String name, String source, SnifflesProcess.Limits limit, Runnable cancel) throws Exception {
        Path work = root.resolve(name); IOException failure = null;
        try { SnifflesProcess.executeOwned(python(source), ENV, work, limit, () -> {}, cancel); } catch (IOException expected) { failure = expected; }
        require(failure != null, "Expected real process failure: " + name); var ledger = read(work.resolve("process.json"), 16777216);
        require(list(ledger.get("owned_live_after_cleanup")).isEmpty() && text(ledger.get("status")).equals("FAIL2-retained-no-result"), "Failure left live owned workers or admitted execution");
        require(!Files.exists(work.resolve("COMPLETED.json")), "Failed process issued scientific admission");
        controls.add(Map.of("name", name, "status", "PASS_EXPECTED_PROCESS_FAILURE_NO_LIVE_OWNED_WORKERS", "error", failure.toString(), "ledger", pin(work.resolve("process.json"), 16777216, () -> {})));
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Supply NEW absolute control directory"); Path root = Path.of(args[0]); canonical(root.getParent()); Files.createDirectory(root);
        Path sentinelHome = Files.createDirectory(root.resolve("unrelated-sentinel"));
        var sentinel = new ProcessBuilder(python("from pathlib import Path;import time;Path('started').write_text('owned sentinel');time.sleep(90)"))
                .directory(sentinelHome.toFile()).redirectOutput(sentinelHome.resolve("stdout").toFile()).redirectError(sentinelHome.resolve("stderr").toFile()).start();
        var identity = sentinel.toHandle().info().startInstant(); sentinel.getOutputStream().close();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(sentinelHome.resolve("started"))) { require(System.nanoTime() < deadline && sentinel.isAlive(), "Sentinel failed to start"); Thread.sleep(10); }
            pass(root, "success", "print('complete helper output')");
            fail(root, "nonzero", "import sys;sys.exit(7)", limits(10000,16777216), () -> {});
            fail(root, "timeout", "import time;time.sleep(60)", limits(300,16777216), () -> {});
            fail(root, "workspace-cap", "from pathlib import Path;import time;Path('flood').write_bytes(b'x'*1048576);time.sleep(60)", limits(10000,131072), () -> {});
            Path cancelled = root.resolve("cancelled");
            fail(root, "cancelled", "from pathlib import Path;import time;Path('started').write_text('yes');time.sleep(60)", limits(10000,16777216),
                () -> { if (Files.exists(cancelled.resolve("started"))) throw new java.io.UncheckedIOException(new IOException("Controlled cancellation requested")); });
            fail(root, "escaped-orphan", "import os,time;from pathlib import Path\npid=os.fork()\nif pid==0:\n os.setsid();Path('child.pid').write_text(str(os.getpid()));time.sleep(60)\nelse:\n time.sleep(.25)\n", limits(10000,16777216), () -> {});
            var orphan = read(root.resolve("escaped-orphan/process.json"), 16777216);
            long child = Long.parseLong(Files.readString(root.resolve("escaped-orphan/child.pid")));
            require(list(orphan.get("owned_processes")).stream().anyMatch(value -> { try { return integer(object(value).get("pid"),1,Long.MAX_VALUE)==child; } catch(IOException failure) { throw new java.io.UncheckedIOException(failure); } }), "Escaped orphan identity was not retained");
            fail(root, "symlink", "import os,time;os.symlink('/etc/passwd','foreign-link');time.sleep(60)", limits(10000,16777216), () -> {});
            fail(root, "fifo", "import os,time,sys\ntry:os.mkfifo('never-read-fifo')\nexcept OSError as error:\n print('FIFO_CREATION_UNAVAILABLE:'+str(error.errno),file=sys.stderr);sys.exit(77)\ntime.sleep(60)\n", limits(10000,16777216), () -> {});
            var fifo = read(root.resolve("fifo/process.json"), 16777216);
            if(integer(fifo.get("exit_code"),0,255)==77) {
                require(!Files.exists(root.resolve("fifo/never-read-fifo"),LinkOption.NOFOLLOW_LINKS)
                    && Files.readString(root.resolve("fifo/process.stderr")).startsWith("FIFO_CREATION_UNAVAILABLE:"), "Unexplained FIFO control omission");
                controls.set(controls.size()-1,Map.of("name","fifo","status","SKIP3_FIFO_CREATION_UNAVAILABLE_ON_WORKSPACE_FILESYSTEM",
                    "reason",Files.readString(root.resolve("fifo/process.stderr")).strip(),
                    "required_action","FIFO rejection has not been exercised. Run these controls in a permitted Linux workspace filesystem that supports FIFO creation before claiming that coverage; do not bypass workspace permissions.",
                    "ledger",pin(root.resolve("fifo/process.json"),16777216,()->{})));
            } else require(Files.exists(root.resolve("fifo/never-read-fifo"),LinkOption.NOFOLLOW_LINKS)
                && text(fifo.get("error")).contains("nonregular"),"FIFO control did not exercise a real nonregular node");
            pass(root, "temporary-files", "from pathlib import Path;import time\nfor i in range(150):\n p=Path('temporary-'+str(i));p.write_bytes(b'x'*1024);time.sleep(.001);p.unlink()\n");
            Path refused = root.resolve("integrity-refused");
            try { SnifflesProcess.executeOwned(python("raise RuntimeError('must never launch')"),ENV,refused,limits(10000,16777216),()->{throw new IOException("Controlled original source mutation");},()->{}); throw new AssertionError("Integrity refusal ignored"); }
            catch(IOException expected) { require(!Files.exists(refused,LinkOption.NOFOLLOW_LINKS), "Integrity failure still created a process directory"); controls.add(Map.of("name","prelaunch-integrity","status","PASS_REFUSED_BEFORE_LAUNCH")); }
            Path interrupted = root.resolve("interrupted"); var error = new AtomicReference<Throwable>(); var interruptedFlag = new AtomicBoolean();
            Thread thread = new Thread(() -> {
                try { SnifflesProcess.executeOwned(python("from pathlib import Path;import time;Path('started').write_text('yes');time.sleep(60)"),ENV,interrupted,limits(10000,16777216),()->{},()->{}); }
                catch(Throwable failure) { error.set(failure); interruptedFlag.set(Thread.currentThread().isInterrupted()); }
            }); thread.start(); deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(interrupted.resolve("started"))) { require(thread.isAlive()&&System.nanoTime()<deadline,"Interrupted helper did not start"); Thread.sleep(10); }
            thread.interrupt(); thread.join(10000); require(!thread.isAlive()&&error.get()!=null&&interruptedFlag.get(),"Interruption did not propagate after cleanup");
            require(list(read(interrupted.resolve("process.json"),16777216).get("owned_live_after_cleanup")).isEmpty(),"Interrupted owned worker remains live");
            controls.add(Map.of("name","interruption","status","PASS_INTERRUPTION_PRESERVED_AND_OWNED_WORKERS_TERMINAL"));
            long self=ProcessHandle.current().pid();
            try { SnifflesProcess.sample(self,(path,maximum)->{if(path.getFileName().toString().equals("status"))throw new IOException("Controlled live proc permission failure");return Files.readString(path);});throw new AssertionError("Unreadable live metrics were silently omitted"); }
            catch(IOException expected) { controls.add(Map.of("name","live-proc-read-error","status","PASS_LIVE_UNREADABLE_PROCESS_NOT_SILENTLY_OMITTED")); }
            require(sentinel.isAlive()&&sentinel.toHandle().info().startInstant().equals(identity),"An unrelated process was killed or changed identity");
            long skipped=controls.stream().filter(value->{try{return text(object(value).get("status")).startsWith("SKIP3_");}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}).count();
            write(root.resolve("COMPLETED.json"),Map.of("status","PASS_SNIFFLES_PROCESS_CONTROLS_WITH_DECLARED_SKIPS_ONLY","controls",controls,"controls_count",controls.size(),"passed_controls",controls.size()-skipped,"skipped_controls",skipped,"unrelated_sentinel_preserved",true,"scientific_analysis_admitted",false));
            require(controls.size()==12,"Unexpected process-control population"); System.out.println("PASS "+(controls.size()-skipped)+" real process controls; "+skipped+" explicitly skipped; unrelated sentinel preserved");
        } finally {
            if (sentinel.isAlive()&&sentinel.toHandle().info().startInstant().equals(identity)) sentinel.destroyForcibly();
            require(sentinel.waitFor(5,TimeUnit.SECONDS),"Owned sentinel cleanup failed");
            write(sentinelHome.resolve("CLEANUP.json"),Map.of("owned_pid",sentinel.pid(),"owned_terminal",!sentinel.isAlive()));
        }
    }
}
