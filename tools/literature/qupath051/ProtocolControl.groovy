// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import java.nio.file.*
import java.nio.charset.StandardCharsets
import qupath.lib.io.GsonTools
import org.slf4j.LoggerFactory

def root=Paths.get(args[0])
def config=GsonTools.getInstance().fromJson(Files.readString(root.resolve('inputs/control.json'),StandardCharsets.UTF_8),Map.class)
def logger=LoggerFactory.getLogger('qupath.ExplicitProtocolControl')
if(config.mode=='throw')throw new IllegalArgumentException('Explicit original exception control')
if(config.mode=='delay')Thread.sleep(10000)
if(config.mode=='warning')logger.warn('Explicit original source warning requiring review')
if(config.mode=='logged-error')logger.error('Explicit original ERROR with process exit0 and a return marker')
if(config.mode=='mutate-captured-input')Files.writeString(root.resolve('inputs/control.json'),'explicitly changed captured input',StandardCharsets.UTF_8)
Files.writeString(root.resolve('results/complete.txt'),'Literal fixture ID: 00017.2_α\n',StandardCharsets.UTF_8)
if(config.mode=='workspace-link')Files.createSymbolicLink(root.resolve('unqualified-alias'),Paths.get('results/complete.txt'))
if(config.mode=='unsafe-cache-link') {
    def cache=root.resolve('owned-home/.javacpp/cache')
    Files.createDirectories(cache)
    Files.createSymbolicLink(cache.resolve('unqualified-alias'),root.resolve('results/complete.txt'))
}
if(config.mode=='internal-cache-link'||config.mode=='internal-cache-absolute-link') {
    def cache=root.resolve('owned-home/.javacpp/cache')
    Files.createDirectories(cache)
    Files.writeString(cache.resolve('explicit-fixture-library.dat'),'Explicit native-cache alias boundary control\n',StandardCharsets.UTF_8)
    Files.createSymbolicLink(cache.resolve('explicit-fixture-alias'),config.mode=='internal-cache-link'?Paths.get('explicit-fixture-library.dat'):cache.resolve('explicit-fixture-library.dat'))
}
if(config.mode=='extra-output')Files.writeString(root.resolve('results/extra.txt'),'undeclared original output\n',StandardCharsets.UTF_8)
return config.mode=='wrong-return'?'QUPATH051_WRONG_RETURN':'QUPATH051_PROTOCOL_COMPLETE'
