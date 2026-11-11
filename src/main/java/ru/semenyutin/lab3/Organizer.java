package ru.semenyutin.lab3;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;

final class Organizer {
    private final TaskDao dao;
    private final Clock clock;
    private final Logger log;
    Organizer(TaskDao dao,Clock clock,Logger log){this.dao=dao;this.clock=clock;this.log=log;}
    List<Task> search(String query,Task.Status status,Task.Category category)throws IOException {
        String q=query.strip().toLowerCase(Locale.ROOT);
        return dao.all().stream().filter(t->!t.deleted())
            .filter(t->(status==null||status==t.status())&&(category==null||category==t.category()))
            .filter(t->(t.title()+" "+t.description()+" "+t.owner()).toLowerCase(Locale.ROOT).contains(q))
            .sorted(Comparator.comparing(Task::due).thenComparing(Task::title)).toList();
    }
    Task save(UUID id,String title,String description,String owner,LocalDate due,Task.Status status,Task.Category category)throws IOException {
        Task previous=id==null?null:existing(id);
        Task task=new Task(id==null?UUID.randomUUID():id,title,description,owner,due,status,category,next(previous),false);
        dao.upsert(task);log.info((id==null?"create ":"update ")+task.id());return task;
    }
    void remove(UUID id)throws IOException {
        Task t=existing(id);dao.upsert(t.tombstone(next(t)));log.info("delete "+id);
    }
    private Task existing(UUID id)throws IOException {
        return dao.find(id).filter(t->!t.deleted()).orElseThrow(()->new IllegalArgumentException("Задача не найдена."));
    }
    private Instant next(Task old){
        Instant now=clock.instant();return old!=null&&!now.isAfter(old.updatedAt())?old.updatedAt().plusNanos(1):now;
    }
    static int synchronize(List<TaskDao> daos,Logger log)throws IOException {
        Map<UUID,Task> union=new LinkedHashMap<>();
        for(TaskDao d:daos)for(Task t:d.all())union.merge(t.id(),t,(a,b)->
            b.updatedAt().isAfter(a.updatedAt())||(b.updatedAt().equals(a.updatedAt())&&b.deleted()&&!a.deleted())?b:a);
        for(TaskDao d:daos)d.replaceAll(union.values());
        log.info("synchronize stores="+daos.size()+" records="+union.size());
        return (int)union.values().stream().filter(t->!t.deleted()).count();
    }
}
