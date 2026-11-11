package ru.semenyutin.lab3;
import java.time.*;
import java.util.*;

public record Task(UUID id,String title,String description,String owner,LocalDate due,Status status,Category category,Instant updatedAt,boolean deleted) {
    public enum Status { TODO, IN_PROGRESS, DONE }
    public enum Category { STUDY, PERSONAL, WORK }
    public Task {
        Objects.requireNonNull(id);Objects.requireNonNull(updatedAt);Objects.requireNonNull(due);Objects.requireNonNull(status);Objects.requireNonNull(category);
        title=check(title,120,true,"название");owner=check(owner,80,true,"исполнитель");description=check(description,1500,false,"описание");
    }
    private static String check(String v,int max,boolean required,String field){
        v=v==null?"":v.strip();
        if(v.length()>max||required&&v.isEmpty())throw new IllegalArgumentException("Проверьте поле: "+field+" (до "+max+" символов).");
        return v;
    }
    public Task tombstone(Instant at){return new Task(id,title,description,owner,due,status,category,at,true);}
}
