/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;

/** One verification pass hashes a physical file once,including regular hardlink aliases. */
final class RepeatFingerprint {
    private RepeatFingerprint() { }
    private record Identity(Object fileKey,long bytes,FileTime modified,FileTime created,FileTime changed) { }
    private static final class Pass {
        final Map<Identity,String> hashes=new HashMap<>();
        long physicalHashes;
    }
    private static final ThreadLocal<Pass> ACTIVE=new ThreadLocal<>();
    static final class Scope implements AutoCloseable {
        private final Pass pass;
        private final boolean owner;
        private boolean closed;
        Scope(Pass pass,boolean owner){this.pass=pass;this.owner=owner;}
        long physicalHashes(){return pass.physicalHashes;}
        void requireActive(){if(closed||ACTIVE.get()!=pass)throw new IllegalStateException("Fingerprint pass is closed or belongs to another thread");}
        @Override public void close(){
            if(closed)return;
            if(owner){if(ACTIVE.get()!=pass)throw new IllegalStateException("Fingerprint verification scopes must close in their owning thread");ACTIVE.remove();pass.hashes.clear();}
            closed=true;
        }
    }
    static Scope enter(){Pass pass=ACTIVE.get();boolean owner=pass==null;if(owner){pass=new Pass();ACTIVE.set(pass);}return new Scope(pass,owner);}
    static String hash(Path supplied,long maximum,Runnable check)throws IOException{
        check.run();Path path=supplied.toAbsolutePath().normalize();
        if(maximum<0||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Fingerprint requires a canonical regular file and nonnegative byte capacity");
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()>maximum)throw new IOException("Complete fingerprint file exceeds its byte capacity");
        Identity key=identity(path,before);Pass pass=ACTIVE.get();
        if(pass!=null&&key.fileKey()!=null&&key.changed()!=null){String known=pass.hashes.get(key);if(known!=null)return known;}
        String hash=RepeatInputs.hash(path,maximum,check);
        BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        Identity ending=identity(path,after);
        if(!java.util.Objects.equals(key.fileKey(),ending.fileKey())||key.bytes()!=ending.bytes()||!key.modified().equals(ending.modified())||!key.created().equals(ending.created()))throw new IOException("Fingerprint file changed across a complete verification read");
        if(pass!=null){pass.physicalHashes++;if(key.fileKey()!=null&&key.changed()!=null&&key.equals(ending))pass.hashes.put(key,hash);}
        return hash;
    }
    private static Identity identity(Path path,BasicFileAttributes attributes)throws IOException{
        FileTime changed=null;
        try{Object value=Files.getAttribute(path,"unix:ctime",LinkOption.NOFOLLOW_LINKS);if(value instanceof FileTime time)changed=time;}
        catch(UnsupportedOperationException|IllegalArgumentException unsupported){/* Without change time,read bytes on every call. */}
        return new Identity(attributes.fileKey(),attributes.size(),attributes.lastModifiedTime(),attributes.creationTime(),changed);
    }
}
