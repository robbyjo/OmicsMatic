package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
public final class ManuscriptLocalBridge {
  @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
    Path b=Path.of(args[0]);
    String prose=Files.readString(b.resolve("artifacts/manuscript-draft.txt"));
    List<Object> tables=(List<Object>)new Yaml().load(Files.readString(b.resolve("metadata/native-population-tables.json")));
    Path out=b.resolve("artifacts/native-render"); Files.createDirectories(out);
    StudyManuscriptWriting.writePolicy(out);
    Files.writeString(out.resolve("manuscript.html"),StudyAssistance.manuscript(prose,tables));
    Files.writeString(out.resolve("participant-characteristics.html"),"<!doctype html><meta charset=\"utf-8\"><h1>Participant characteristics</h1>"+StudyPublication.table1(tables)+StudyPublication.detailedTable1(tables,false));
    Files.writeString(out.resolve("participant-characteristics.csv"),StudyPublication.table1Csv(tables));
    Files.writeString(out.resolve("review.json"),StudyAssistance.json(StudyManuscriptWriting.assessDraft(prose,tables)));
    System.out.println("Current native manuscript renderer, participant table, abstract and writing checks completed.");
  }
}
