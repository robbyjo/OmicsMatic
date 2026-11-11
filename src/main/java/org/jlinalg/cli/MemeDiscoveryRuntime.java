/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Stable original payload plus current helper/ELF/configuration closure; live scientific health is separate. */
final class MemeDiscoveryRuntime {
    private MemeDiscoveryRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    private static final Set<Path> SYSTEM = Set.of(Path.of("/usr/lib"), Path.of("/usr/lib64"), Path.of("/usr/libx32"), Path.of("/lib"),
        Path.of("/lib64"), Path.of("/libx32"), Path.of("/usr/bin"), Path.of("/bin"), Path.of("/etc"), Path.of("/usr/share"),
        Path.of("/usr/local/lib"), Path.of("/usr/local/share"), Path.of("/run/systemd/resolve"), Path.of("/run/resolvconf"), Path.of("/mnt/wsl"),
        Path.of("/var/lib/ghostscript"), Path.of("/var/cache/fontconfig"));
    private static final List<String> CONFIG = List.of("/etc/ld.so.cache", "/etc/ld.so.conf", "/etc/ld.so.preload", "/etc/nsswitch.conf",
        "/etc/hosts", "/etc/resolv.conf", "/etc/gai.conf", "/etc/host.conf", "/etc/localtime", "/etc/os-release", "/usr/share/zoneinfo/UTC",
        "/lib/ld-linux.so.2", "/lib64/ld-linux-x86-64.so.2", "/libx32/ld-linux-x32.so.2");
    private static final RepeatHostFiles.Limits HOST = new RepeatHostFiles.Limits(268435456, 8589934592L, 100000, 128, 67108864);
    private static final RsemProcess.Limits PROBE = new RsemProcess.Limits(120000, 2147483648L, 120, 67108864, 1024, 1073741824, 10000, 32, 256, 1073741824, 50);
    private static final String PERL = """
        use strict; use warnings;
        use Cwd qw(abs_path); use Fcntl qw(O_RDONLY SEEK_SET);
        use File::Basename qw(fileparse); use File::Spec::Functions qw(tmpdir);
        use File::Temp qw(tempfile); use Getopt::Long; use Pod::Usage;
        use XML::Parser::Expat;
        BEGIN { require lib; lib->import($ARGV[0]); }
        unshift(@INC, $ARGV[1]);
        require HtmlMonolithWr; require TomtomSAX; require MemeSAX; require DynaLoader;
        print "VERSION\\t$^V\\n";
        for my $path (@INC) { die "Non-path module hook" if ref($path); print "INC\\t$path\\n"; }
        for my $name (sort keys %INC) { print "MODULE\\t$name\\t$INC{$name}\\n"; }
        for my $path (@DynaLoader::dl_shared_objects) { print "DYNAMIC\\t$path\\n"; }
        """;

    static final class Context {
        final Path root, home;
        final MemeDiscoveryBuild.Context build;
        final Map<String, String> platform, environment;
        final Set<Path> allowed = new HashSet<>(SYSTEM), roots = new HashSet<>();
        final TreeSet<Path> files = new TreeSet<>(), nodes = new TreeSet<>();
        final Map<String, RsemElf.Image> images = new TreeMap<>();
        final Map<String, RsemElf.Report> bindings = new TreeMap<>();
        final List<Path> completed = new ArrayList<>();
        RepeatHostFiles.Snapshot host;
        Set<Path> treeTargets = Set.of();
        MemePerl.Receipt perl;
        boolean healthy;
        final long started = System.nanoTime();
        private Context(String profile, Path home, Path evidence, Path root, Runnable check) throws IOException {
            this.root = root; this.home = home;
            Runnable bounded = bounded(check);
            try { platform = MemeNativeHost.read(); }
            catch (TomtomRuntime.Unavailable failure) { throw new Unavailable("MEME discovery native host identity is unavailable: " + failure.getMessage(), failure); }
            if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(evidence.resolve("COMPLETED.json"), LinkOption.NOFOLLOW_LINKS))
                throw new Unavailable("Exact original MEME home/build evidence is unavailable. Complete permitted explicit setup or skip dependent analysis.");
            NextflowFiles.directory(home); require((NextflowFiles.mode(home) & 0022) == 0, "Original MEME home must not be group/world writable");
            build = MemeDiscoveryBuild.read(profile, home, evidence, bounded); allowed.add(home);
            environment = Map.of("PATH", home.resolve("bin") + ":" + home.resolve("libexec/meme-" + build.version()) + ":/usr/bin:/bin", "LC_ALL", "C", "LANG", "C", "TZ", "UTC");
            for (String part : List.of("metadata", "health", "probes")) Files.createDirectory(root.resolve(part));
            build.capture(root.resolve("metadata/build"), bounded);
            addRoot(home); addRoot(Path.of("/etc/ld.so.conf.d"));
            for (String name : List.of("/usr/share/ghostscript", "/usr/lib/ghostscript", "/etc/ghostscript", "/var/lib/ghostscript", "/etc/fonts", "/usr/share/fontconfig", "/usr/share/fonts", "/usr/share/color", "/usr/share/icc", "/var/cache/fontconfig")) addRoot(Path.of(name));
            for (String name : CONFIG) files.add(Path.of(name));
            for (String name : List.of("/bin", "/lib", "/lib64", "/usr/bin", "/usr/lib", "/usr/lib64", "/usr/lib/x86_64-linux-gnu")) nodes.add(Path.of(name));
            var executables = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/ldd", "/bin/bash", "/bin/sh", "/usr/bin/gs"));
            if (build.version().equals("4.9.1")) executables.addAll(List.of("/bin/csh", "/usr/bin/tty", "/bin/cat"));
            else executables.add("/usr/bin/perl");
            for (String name : executables) executable(Path.of(name));
            executable(home.resolve("bin/meme"));
            if (build.version().equals("4.9.1")) { executable(home.resolve("bin/meme.bin")); executable(home.resolve("bin/meme-xml-html")); }
            else executable(home.resolve("libexec/meme-" + build.version() + "/meme_xml_to_html"));
            treeTargets = linkedTrees(bounded); host = capture(bounded);
        }
        void discover(Runnable check) throws IOException {
            var pending = new TreeSet<Path>();
            for (Path file : List.copyOf(files)) if (Files.isRegularFile(file) && Files.isExecutable(file)) {
                Path real = file.toRealPath(); if (RsemElf.image(real, HOST.fileBytes(), check) != null) pending.add(real);
            }
            discoverImages(pending, check);
            if (!build.version().equals("4.9.1")) {
                RsemProcess.Result imported;
                try { imported = probe(perlArguments(), "perl-imports", check); }
                catch (IOException failure) {
                    Path stderr = root.resolve("probes/perl-imports/process.stderr");
                    if (Files.isRegularFile(stderr, LinkOption.NOFOLLOW_LINKS) && MemePerl.unavailableImport(failure, NextflowFiles.bytes(stderr, 2097152)))
                        throw new Unavailable("Required original MEME Perl/XML::Parser::Expat/report module is unavailable. Supply it only on a permitted host or skip full-report discovery.", failure);
                    throw failure;
                }
                perl = MemePerl.parse(NextflowFiles.bytes(imported.stdout(), 2097152), NextflowFiles.bytes(imported.stderr(), 2097152));
                require(perl.modules().containsKey("MemeSAX.pm"), "Original MEME report capability omitted MemeSAX");
                for (String name : perl.roots()) addRoot(Path.of(name));
                for (String name : perl.modules().values()) { Path path = Path.of(name); allow(path); files.add(path); files.add(path.toRealPath()); }
                for (String name : perl.dynamicImages()) { Path path = Path.of(name); allow(path); files.add(path); files.add(path.toRealPath()); pending.add(path.toRealPath()); }
                expand(check); discoverImages(pending, check);
                var repeated = probe(perlArguments(), "perl-imports-recheck", check);
                require(perl.equals(MemePerl.parse(NextflowFiles.bytes(repeated.stdout(), 2097152), NextflowFiles.bytes(repeated.stderr(), 2097152))), "Original MEME Perl imports changed after complete dependency closure");
            }
            var version = probe(List.of(home.resolve("bin/meme").toString(), "-version"), "version", check);
            require(NextflowFiles.utf8(NextflowFiles.bytes(version.stdout(), 1048576)).strip().equals(build.version()) && Files.size(version.stderr()) == 0, "Fresh original MEME launcher/version differs");
            verify(check);
        }
        private List<String> perlArguments() { return List.of("/usr/bin/perl", "-e", PERL, home.resolve("lib/meme-" + build.version() + "/perl").toString(), home.resolve("libexec/meme-" + build.version()).toString()); }
        private void discoverImages(TreeSet<Path> pending, Runnable check) throws IOException {
            while (!pending.isEmpty()) {
                Path path = pending.pollFirst().toRealPath(); if (images.containsKey(path.toString())) continue;
                require(images.size() < 256, "Complete MEME ELF dependency closure exceeds capacity");
                var image = RsemElf.image(path, HOST.fileBytes(), check); require(image != null, "Non-ELF image in original MEME dependency closure");
                images.put(path.toString(), image); files.add(path);
                for (Path directory : RsemElf.searchDirectories(path, image)) addRoot(directory);
                expand(check); Path process = root.resolve("probes/libraries-" + bindings.size());
                RsemProcess.Result result = RsemProcess.inspectLibraries(path, environment, process, PROBE, () -> verify(check), bounded(check), accounting()); completed.add(process);
                byte[] stdout = NextflowFiles.bytes(result.stdout(), 2097152), stderr = NextflowFiles.bytes(result.stderr(), 2097152);
                if ((NextflowFiles.utf8(stdout) + NextflowFiles.utf8(stderr)).contains("not found")) throw new Unavailable("Required original MEME ELF library is unavailable; inspect " + process + " and supply it on a permitted host, or skip dependent analysis");
                var report = RsemElf.bindings(image, result.exitCode(), stdout, stderr); bindings.put(path.toString(), report);
                for (var binding : report.bindings()) {
                    Path reported = Path.of(binding.path()).normalize(); allow(reported); Path real = reported.toRealPath(); allow(real);
                    if (!Files.isReadable(real)) throw new Unavailable("Original MEME library is unreadable or forbidden: " + real);
                    require(RsemElf.image(real, HOST.fileBytes(), check) != null, "Reported original MEME library is not ELF"); files.add(reported); files.add(real);
                    if (!images.containsKey(real.toString())) pending.add(real);
                    if (binding.loader() && image.interpreter() != null) require(Path.of(image.interpreter()).toRealPath().equals(real), "Original MEME interpreter and reported loader differ");
                }
                expand(check);
            }
        }
        void verify(Runnable check) throws IOException {
            if (!healthy && java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > 1200000)
                throw new IOException("Complete original MEME runtime admission exceeded its 20-minute wall capacity; no runtime or analysis is admitted");
            Runnable bounded = bounded(check); bounded.run(); build.verify(bounded);
            try { require(platform.equals(MemeNativeHost.read()), "Native MEME CPU/process/kernel identity changed"); }
            catch (TomtomRuntime.Unavailable failure) { throw new Unavailable("Readable native MEME host identity became unavailable", failure); }
            RepeatHostFiles.verify(host, treeTargets, allowed, HOST, bounded);
        }
        RsemProcess.Result nativeRun(List<String> arguments, Path process, RsemProcess.Limits limits, RsemProcess.Check inputs,
                                    List<Path> accounted, Runnable check) throws IOException {
            try { return RsemProcess.executeOwned(arguments, environment, process, limits, () -> { verify(check); inputs.verify(); }, bounded(check), accounted); }
            catch (RsemProcess.UnavailableDependency missing) { throw new Unavailable("Original MEME process supervision helper is missing or forbidden: " + missing.getMessage(), missing); }
        }
        RsemProcess.Result execute(MemeDiscoveryRequest.Request request, MemeDiscoveryInputs.Captured inputs, Path process, List<Path> accounted, Runnable check) throws IOException {
            require(healthy, "Fresh complete original MEME scientific/report health is required before user execution");
            require(request.profile().equals(build.profile()), "Admitted MEME edition differs from the user's exact requested profile");
            var args = request.arguments(home, inputs.primary(), inputs.backgroundFile(), process.resolve("result"), inputs.population().records().size());
            return nativeRun(args, process, request.processLimits(), () -> inputs.verify(true, check), accounted, check);
        }
        List<String> htmlArguments(Path xml, Path html) {
            if (build.version().equals("4.9.1")) return List.of(home.resolve("bin/meme-xml-html").toString(), "-xml", xml.toString(), "-xsl", home.resolve("etc/meme-to-html.xsl").toString(), "-html", html.toString());
            return List.of(home.resolve("libexec/meme-" + build.version() + "/meme_xml_to_html").toString(), xml.toString(), html.toString());
        }
        private RsemProcess.Result probe(List<String> args, String name, Runnable check) throws IOException {
            Path process = root.resolve("probes").resolve(name);
            var result = RsemProcess.executeOwned(args, environment, process, PROBE, () -> verify(check), bounded(check), accounting()); completed.add(process); return result;
        }
        List<Path> accounting() { var result = new ArrayList<>(List.of(root.resolve("metadata"), root.resolve("health"))); result.addAll(completed); return List.copyOf(result); }
        Map<String, Object> document() throws IOException {
            var result = new TreeMap<String, Object>(); result.put("profile", build.profile()); result.put("version", build.version()); result.put("home", home.toString());
            result.put("platform", platform); result.put("environment_without_owned_HOME_TMPDIR", environment);
            result.put("host_files", SimpleJson.parse(NextflowFiles.utf8(RepeatHostFiles.bytes(host, HOST))));
            result.put("resolved_tree_link_targets", treeTargets.stream().map(Path::toString).sorted().toList());
            result.put("ELF_images", SimpleJson.parse(RsemJson.json(images))); result.put("ELF_bindings", SimpleJson.parse(RsemJson.json(bindings)));
            result.put("perl", perl == null ? null : perl.document()); result.put("fresh_scientific_health_passed", healthy);
            result.put("scope", "Exact original source/build/payload plus current declared executable/ELF/library/configuration, report-module, font and Ghostscript roots. Health is fresh and separate. Integrity/provenance only; no signature, arbitrary-plugin policy, execution authentication or biological certification."); return result;
        }
        private Runnable bounded(Runnable check) {
            return () -> {
                check.run();
                if (!healthy && java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > 1200000)
                    throw new java.util.concurrent.CancellationException("Complete original MEME runtime admission exceeded its 20-minute wall capacity; no runtime or analysis is admitted");
            };
        }
        private RepeatHostFiles.Snapshot capture(Runnable check) throws IOException { return RepeatHostFiles.capture(roots.stream().sorted().toList(), List.copyOf(files), List.copyOf(nodes), treeTargets, allowed, HOST, bounded(check)); }
        private void expand(Runnable check) throws IOException { verify(check); treeTargets = linkedTrees(check); var expanded = capture(check); verify(check); host = expanded; }
        private Set<Path> linkedTrees(Runnable check) throws IOException {
            // Linked members are allowed only when reached from a declared
            // consumed tree and resolved within the reviewed host prefixes.
            // Exact file targets need no unrelated parent-directory traversal.
            var targets = new HashSet<>(roots); var pending = new java.util.ArrayDeque<Path>(roots); var walked = new HashSet<Path>(); int[] members = {0};
            while (!pending.isEmpty()) {
                Path requested = pending.removeFirst(); check.run();
                if (!Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) continue;
                Path real = optionalLinkTarget(requested); allow(real); targets.add(real);
                if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS) || !walked.add(real)) continue;
                require(walked.size() <= HOST.roots(), "Complete linked MEME dependency tree population exceeds capacity");
                Files.walkFileTree(real, new java.nio.file.SimpleFileVisitor<>() {
                    private void inspect(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                        check.run(); require(++members[0] <= HOST.members(), "Complete linked MEME dependency member population exceeds capacity");
                        if (attributes.isSymbolicLink()) {
                            Path target = optionalLinkTarget(path); allow(target); targets.add(target);
                            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) pending.add(target);
                        }
                    }
                    @Override public java.nio.file.FileVisitResult preVisitDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException { inspect(path, attributes); return java.nio.file.FileVisitResult.CONTINUE; }
                    @Override public java.nio.file.FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException { inspect(path, attributes); return java.nio.file.FileVisitResult.CONTINUE; }
                });
            }
            return Set.copyOf(targets);
        }
        private Path optionalLinkTarget(Path path) throws IOException {
            try { return path.toRealPath(); }
            catch (java.nio.file.NoSuchFileException absent) {
                if (!Files.isSymbolicLink(path)) throw absent;
                Path target = Files.readSymbolicLink(path);
                // Match the census's conservative dangling-link identity. Missing
                // optional font fallbacks remain recorded absence, not a reason
                // to install unrelated software or infer a working capability.
                return (target.isAbsolute() ? target : path.getParent().resolve(target)).toAbsolutePath().normalize();
            }
        }
        private void allow(Path path) throws IOException { if (allowed.stream().noneMatch(path::startsWith)) throw new Unavailable("Unreviewed original MEME dependency/search root: " + path + ". Use a supported permitted host or skip analysis; no automatic host change was made."); }
        private void addRoot(Path path) throws IOException {
            allow(path); roots.add(path); nodes.add(path);
            if (Files.exists(path)) { Path real = path.toRealPath(); allow(real); require(Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS), "Declared MEME search root is not a directory"); roots.add(real); nodes.add(real); }
        }
        private void executable(Path path) throws IOException {
            if (!Files.isRegularFile(path) || !Files.isReadable(path) || !Files.isExecutable(path)) throw new Unavailable("Required original MEME executable/helper is unavailable or forbidden: " + path);
            Path real = path.toRealPath(); allow(real); files.add(path); files.add(real);
        }
    }

    static Context prepare(String profile, Path home, Path evidence, Path newWork, Runnable check) throws IOException {
        MemeDiscoveryRequest.nativePath(home); MemeDiscoveryRequest.nativePath(newWork);
        require(!Files.exists(newWork, LinkOption.NOFOLLOW_LINKS) && newWork.getParent().toRealPath().equals(newWork.getParent()), "Require a NEW canonical native MEME runtime-admission directory");
        if (!System.getProperty("os.name").equals("Linux")) throw new Unavailable("Original MEME execution requires Linux x86_64 Java; use a permitted WSL/Linux host or skip analysis. Captured verification is portable.");
        Files.createDirectory(newWork, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var context = new Context(profile, home, evidence, newWork, check); context.discover(check); return context;
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
