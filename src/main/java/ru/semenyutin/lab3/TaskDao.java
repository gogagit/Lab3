package ru.semenyutin.lab3;
import java.io.IOException;
import java.util.*;
public interface TaskDao {
    List<Task> all() throws IOException;
    void upsert(Task task) throws IOException;
    void replaceAll(Collection<Task> tasks) throws IOException;
    default Optional<Task> find(UUID id)throws IOException{return all().stream().filter(t->t.id().equals(id)).findFirst();}
}
