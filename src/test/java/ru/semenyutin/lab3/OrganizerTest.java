package ru.semenyutin.lab3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizerTest {
    @TempDir Path dir;
    private final Clock clock=Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"),ZoneOffset.UTC);
    private final Logger logger=Logger.getAnonymousLogger();
    @Test void validatesBeforeTouchingDao() {
        var dao=mock(TaskDao.class);var service=new Organizer(dao,clock,logger);
        assertThrows(IllegalArgumentException.class,()->service.save(null," ","","Илья",LocalDate.now(),Task.Status.TODO,Task.Category.STUDY));
        verifyNoInteractions(dao);
    }
    @Test void delegatesAndFilters()throws Exception {
        var dao=mock(TaskDao.class);var service=new Organizer(dao,clock,logger);
        var t=service.save(null,"Сдать отчёт","","Илья",LocalDate.now(),Task.Status.TODO,Task.Category.STUDY);
        verify(dao).upsert(t);when(dao.all()).thenReturn(List.of(t));
        assertEquals(List.of(t),service.search("отчёт",Task.Status.TODO,Task.Category.STUDY));
        assertTrue(service.search("отчёт",Task.Status.DONE,null).isEmpty());
    }
    @Test void synchronizesMemoryFileAndDatabaseIncludingDeletion()throws Exception {
        TaskDao a=new StoreFactory.MemoryFactory().createTasks(),b=new StoreFactory.FileFactory(dir.resolve("tasks.properties")).createTasks(),c=new StoreFactory.DatabaseFactory(dir.resolve("tasks")).createTasks();
        var service=new Organizer(a,clock,logger);
        var t=service.save(null,"Сдать отчёт","Текст","Илья",LocalDate.now(),Task.Status.TODO,Task.Category.STUDY);
        assertEquals(1,Organizer.synchronize(List.of(a,b,c),logger));
        assertEquals(List.of(t),b.all());assertEquals(List.of(t),c.all());
        var updated=new Organizer(c,Clock.offset(clock,Duration.ofSeconds(1)),logger).save(t.id(),"Проверить отчёт","Текст","Илья",t.due(),Task.Status.DONE,t.category());
        assertEquals(1,Organizer.synchronize(List.of(a,b,c),logger));assertEquals(List.of(updated),b.all());
        new Organizer(b,Clock.offset(clock,Duration.ofSeconds(2)),logger).remove(t.id());
        assertEquals(0,Organizer.synchronize(List.of(a,b,c),logger));
        assertTrue(c.find(t.id()).orElseThrow().deleted());assertTrue(service.search("",null,null).isEmpty());
    }
}
