/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Complete canonical user-file identities; byte capacities are caller declared. */
final class IqtreeFiles {
    private IqtreeFiles() { }
    record Identity(long bytes,String sha256,String fileKey,String modifiedTime) { }
    static Identity identity(Path path,long maximum,Runnable cancellation)throws IOException {
        Objects.requireNonNull(cancellation);cancellation.run();
        if(maximum<1 || path==null || !path.isAbsolute() || !path.normalize().equals(path)
                || !path.toRealPath().equals(path))throw new IOException("Require a canonical unlinked IQ-TREE input path and byte capacity");
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile() || before.size()>maximum)throw new IOException("Nonregular or oversized IQ-TREE input: "+path);
        singleLink(path);
        if(before.fileKey()==null && System.getProperty("os.name").equals("Linux"))
            throw new IOException("IQ-TREE execution input filesystem does not expose stable file identities");
        MessageDigest digest;
        try {digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        long count=0;
        try(var input=Files.newInputStream(path)) {
            byte[] buffer=new byte[65536];
            for(int n=input.read(buffer);n>=0;n=input.read(buffer)) {
                cancellation.run();count=Math.addExact(count,n);
                if(count>maximum)throw new IOException("IQ-TREE input grew beyond its complete byte capacity");
                digest.update(buffer,0,n);
            }
        }
        BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        singleLink(path);
        if(count!=before.size() || count!=after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(),after.fileKey()) || !path.toRealPath().equals(path))
            throw new IOException("IQ-TREE input changed while hashing: "+path);
        cancellation.run();
        return new Identity(count,HexFormat.of().formatHex(digest.digest()),String.valueOf(after.fileKey()),after.lastModifiedTime().toString());
    }
    private static void singleLink(Path path)throws IOException {
        if(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("unix")
                && ((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue()!=1)
            throw new IOException("Hard-linked IQ-TREE input is not admitted: "+path);
    }
}
