/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NewickReaderTest {
    @TempDir Path temporary;
    @Test void readsEveryRecordWithQuotedDelimitersNestedCommentsAndExactDocumentText() throws Exception {
        String first=" [note;[don't split]] ('a'';[b]':1,B:2)99;";
        String second="\n[&R](C:3,D:4);";String tail="\n [tail[';]] \t";
        var reader=new NewickReader(new StringReader(first+second+tail));
        try (reader) {
            assertEquals(first,reader.nextTree().originalText());assertFalse(reader.isComplete());
            assertEquals(second,reader.nextTree().originalText());assertFalse(reader.isComplete());
            assertNull(reader.nextTree());assertFalse(reader.isComplete());assertNull(reader.nextTree());
        }
        assertTrue(reader.isComplete());assertEquals(tail,reader.trailingText());assertEquals(first.length()+second.length()+tail.length(),reader.charactersRead());assertEquals(2,reader.treesRead());
    }
    @Test void earlyCloseCannotCertifyACollection() throws Exception {
        var reader=new NewickReader(new StringReader("(A,B);(C,D);"));
        assertEquals(Set.of("A","B"),reader.nextTree().taxa());reader.close();reader.close();
        assertFalse(reader.isComplete());assertThrows(IllegalStateException.class,reader::trailingText);assertThrows(IllegalStateException.class,reader::nextTree);
    }
    @ParameterizedTest @ValueSource(strings={"(C,D", "'unclosed;", "[unclosed;", "[tail] junk", "(C,C);", "(C,D);;", "(C,D); ]", "(C,D);\u0000", "(C,D);\u001c"})
    void laterBadRecordsPermanentlyInvalidateTheCollection(String bad) throws Exception {
        try (var reader=new NewickReader(new StringReader("(A,B);"+bad))) {
            assertNotNull(reader.nextTree());
            assertThrows(Exception.class,()->{while(reader.nextTree()!=null) { /* Audit all, not only the first. */ }});
            assertFalse(reader.isComplete());assertThrows(IllegalStateException.class,reader::nextTree);assertThrows(IllegalStateException.class,reader::trailingText);
        }
    }
    @Test void balancedCommentOnlyDocumentsAndEmptyDocumentsHaveVerifiedZeroTrees() throws Exception {
        for (String text:new String[]{""," \n[doc[';]]\t"}) {
            var reader=new NewickReader(new StringReader(text));
            try (reader) { assertNull(reader.nextTree());assertEquals(0,reader.treesRead()); }
            assertTrue(reader.isComplete());assertEquals(text,reader.trailingText());
        }
    }
    @Test void exactlyDeclaredTreeAndCharacterCapacitiesCanReachEof() throws Exception {
        String text="(A,B);(C,D); [tail]";
        var reader=new NewickReader(new StringReader(text),new NewickReader.Limits(NewickTree.Limits.defaults(),text.length(),2));
        try (reader) {
            assertNotNull(reader.nextTree());assertNotNull(reader.nextTree());assertNull(reader.nextTree());assertFalse(reader.isComplete());
        }
        assertTrue(reader.isComplete());
    }
    @Test void anExtraTreeOrTrailingCharacterCannotBeSilentlyIgnoredAtACapacity() throws Exception {
        var per=NewickTree.Limits.defaults();
        try (var reader=new NewickReader(new StringReader("(A,B);(C,D);"),new NewickReader.Limits(per,100,1))) {
            assertNotNull(reader.nextTree());assertThrows(IllegalArgumentException.class,reader::nextTree);assertFalse(reader.isComplete());
        }
        try (var reader=new NewickReader(new StringReader("(A,B); "),new NewickReader.Limits(per,6,1))) {
            assertNotNull(reader.nextTree());assertThrows(IllegalArgumentException.class,reader::nextTree);assertFalse(reader.isComplete());
        }
        var tiny=new NewickTree.Limits(5,10,10,10,10);
        try (var reader=new NewickReader(new StringReader("(A,B);"),new NewickReader.Limits(tiny,100,10))) {
            assertThrows(IllegalArgumentException.class,reader::nextTree);assertFalse(reader.isComplete());
        }
    }
    @Test void malformedUtf8AndTrailingTruncatedUtf8FailInsteadOfBeingReplaced() throws Exception {
        Path path=temporary.resolve("trees.nwk");
        Files.write(path,new byte[]{'(','A',',','B',')',';', (byte)0xc3});
        try (var reader=NewickReader.open(path,NewickReader.Limits.defaults())) {
            assertThrows(IOException.class,()->{while(reader.nextTree()!=null) { }});assertFalse(reader.isComplete());
        }
    }
    @Test void successfulEofFollowedByACloseFailureBecomesInvalid() throws Exception {
        Reader source=new StringReader("(A,B);") { @Override public void close() { throw new IllegalStateException("close failure"); } };
        var reader=new NewickReader(source);assertNotNull(reader.nextTree());assertNull(reader.nextTree());assertFalse(reader.isComplete());
        assertThrows(IllegalStateException.class,reader::close);assertFalse(reader.isComplete());assertThrows(IllegalStateException.class,reader::trailingText);
    }
    @Test void aLaterIoFailureRetainsOnlyProvisionalRecords() throws Exception {
        Reader source=new Reader() {
            private final String text="(A,B);";private int at;
            @Override public int read(char[] data,int offset,int length) throws IOException {
                if(at==text.length()) throw new EOFException("injected read failure");
                int count=Math.min(length,text.length()-at);text.getChars(at,at+count,data,offset);at+=count;return count;
            }
            @Override public void close() { }
        };
        try (var reader=new NewickReader(source)) {
            assertNotNull(reader.nextTree());assertThrows(IOException.class,reader::nextTree);assertFalse(reader.isComplete());assertThrows(IllegalStateException.class,reader::nextTree);
        }
    }
    @Test void streamsLargeCollectionsUnderADeclaredBudgetWithoutRetainingPriorTrees() throws Exception {
        int count=10000;String text="(A:.1,B:.2,C:.3);\n".repeat(count);
        var reader=new NewickReader(new StringReader(text),new NewickReader.Limits(NewickTree.Limits.defaults(),text.length(),count));
        try (reader) {
            for(int i=0;i<count;i++) assertEquals(Set.of("A","B","C"),reader.nextTree().taxa());
            assertNull(reader.nextTree());assertEquals(count,reader.treesRead());
        }
        assertTrue(reader.isComplete());
    }
    @Test void finalDocumentCommentsUseTheDeclaredCommentBudgetToo() throws Exception {
        var per=new NewickTree.Limits(100,10,10,10,3);
        try(var reader=new NewickReader(new StringReader("(A,B);[tail]"),new NewickReader.Limits(per,100,10))) {
            assertNotNull(reader.nextTree());assertThrows(IllegalArgumentException.class,reader::nextTree);assertFalse(reader.isComplete());
        }
    }
}
