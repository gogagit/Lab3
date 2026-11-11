package ru.semenyutin.lab3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public interface StoreFactory {
    TaskDao createTasks() throws IOException;
    StoreInfo info();
    record StoreInfo(String key,String title,boolean persistent){}
    record MemoryFactory() implements StoreFactory {
        public TaskDao createTasks(){return new MemoryDao();}
        public StoreInfo info(){return new StoreInfo("memory","Память",false);}
    }
    record FileFactory(Path file) implements StoreFactory {
        public TaskDao createTasks(){return new FileDao(file.toAbsolutePath());}
        public StoreInfo info(){return new StoreInfo("file","Файл",true);}
    }
    record DatabaseFactory(Path file) implements StoreFactory {
        public TaskDao createTasks()throws IOException{return new JdbcDao(file.toAbsolutePath());}
        public StoreInfo info(){return new StoreInfo("database","База H2",true);}
    }
}

final class MemoryDao implements TaskDao {
    private final Map<UUID,Task> records=new LinkedHashMap<>();
    public List<Task> all(){return List.copyOf(records.values());}
    public void upsert(Task t){records.put(t.id(),t);}
    public void replaceAll(Collection<Task> tasks){records.clear();tasks.forEach(this::upsert);}
}

final class FileDao implements TaskDao {
    private final Path file;
    FileDao(Path file){this.file=file;}
    public List<Task> all()throws IOException {
        if(Files.notExists(file))return List.of();
        Properties p=new Properties();
        try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}
        List<Task> result=new ArrayList<>();
        try{
            if(!"1".equals(p.getProperty("format")))throw new IllegalArgumentException("Версия файла");
            for(String id:p.stringPropertyNames().stream().filter(k->k.endsWith(".title")).sorted().toList()){
                String k=id.substring(0,id.length()-6),prefix=k+".";
                result.add(new Task(UUID.fromString(k),p.getProperty(id),p.getProperty(prefix+"description"),p.getProperty(prefix+"owner"),
                    LocalDate.parse(p.getProperty(prefix+"due")),Task.Status.valueOf(p.getProperty(prefix+"status")),Task.Category.valueOf(p.getProperty(prefix+"category")),
                    Instant.parse(p.getProperty(prefix+"updatedAt")),Boolean.parseBoolean(p.getProperty(prefix+"deleted"))));
            }
        }catch(RuntimeException e){throw new IOException("Файл задач повреждён: "+file.getFileName(),e);}
        return result;
    }
    public void upsert(Task task)throws IOException {
        Map<UUID,Task> rows=new LinkedHashMap<>();for(Task t:all())rows.put(t.id(),t);rows.put(task.id(),task);replaceAll(rows.values());
    }
    public void replaceAll(Collection<Task> tasks)throws IOException {
        Files.createDirectories(file.getParent());Properties p=new Properties();p.setProperty("format","1");
        for(Task t:tasks){
            String k=t.id()+".";
            p.setProperty(k+"title",t.title());p.setProperty(k+"description",t.description());p.setProperty(k+"owner",t.owner());
            p.setProperty(k+"due",t.due().toString());p.setProperty(k+"status",t.status().name());p.setProperty(k+"category",t.category().name());
            p.setProperty(k+"updatedAt",t.updatedAt().toString());p.setProperty(k+"deleted",""+t.deleted());
        }
        Path tmp=Files.createTempFile(file.getParent(),"organizer-",".tmp");
        try{
            try(var out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){p.store(out,"Organizer tasks");}
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}

final class JdbcDao implements TaskDao {
    private static final String UPSERT="MERGE INTO tasks(id,title,description,owner,due,status,category,updated_at,deleted) KEY(id) VALUES(?,?,?,?,?,?,?,?,?)";
    private final String url;
    JdbcDao(Path file)throws IOException {
        Files.createDirectories(file.getParent());
        url="jdbc:h2:file:"+file.toString().replace('\\','/');
        try(var c=open();var s=c.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS tasks(id UUID PRIMARY KEY,title VARCHAR(120),description VARCHAR(1500),owner VARCHAR(80),due DATE,status VARCHAR(20),category VARCHAR(20),updated_at VARCHAR(40),deleted BOOLEAN)");
        }catch(SQLException e){throw fail(e);}
    }
    private Connection open()throws SQLException{return DriverManager.getConnection(url,"sa","");}
    private IOException fail(SQLException e){return new IOException("Ошибка доступа к локальной базе H2: "+e.getMessage(),e);}
    public List<Task> all()throws IOException {
        try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM tasks ORDER BY id")){
            List<Task> tasks=new ArrayList<>();
            while(r.next())tasks.add(new Task(r.getObject("id",UUID.class),r.getString("title"),r.getString("description"),r.getString("owner"),r.getObject("due",LocalDate.class),
              Task.Status.valueOf(r.getString("status")),Task.Category.valueOf(r.getString("category")),Instant.parse(r.getString("updated_at")),r.getBoolean("deleted")));
            return tasks;
        }catch(SQLException e){throw fail(e);}
    }
    private void bind(PreparedStatement s,Task t)throws SQLException {
        s.setObject(1,t.id());s.setString(2,t.title());s.setString(3,t.description());s.setString(4,t.owner());
        s.setObject(5,t.due());s.setString(6,t.status().name());s.setString(7,t.category().name());s.setString(8,t.updatedAt().toString());s.setBoolean(9,t.deleted());
    }
    public void upsert(Task task)throws IOException {
        try(var c=open();var s=c.prepareStatement(UPSERT)){bind(s,task);s.executeUpdate();}catch(SQLException e){throw fail(e);}
    }
    public void replaceAll(Collection<Task> tasks)throws IOException {
        try(var c=open()){
            c.setAutoCommit(false);
            try(var del=c.createStatement();var s=c.prepareStatement(UPSERT)){
                del.executeUpdate("DELETE FROM tasks");for(Task t:tasks){bind(s,t);s.addBatch();}s.executeBatch();c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }catch(SQLException e){throw fail(e);}
    }
}
