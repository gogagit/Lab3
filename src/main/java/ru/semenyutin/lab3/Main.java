package ru.semenyutin.lab3;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

public final class Main extends Application {
    private record Store(StoreFactory.StoreInfo info,TaskDao dao){public String toString(){return info.title();}}
    private final ComboBox<Store> store=new ComboBox<>();
    private final TableView<Task> table=new TableView<>();
    private final TextField search=new TextField(),title=new TextField(),owner=new TextField();
    private final TextArea description=new TextArea();
    private final DatePicker due=new DatePicker(LocalDate.now().plusDays(1));
    private final ComboBox<Task.Status> status=new ComboBox<>(),statusFilter=new ComboBox<>();
    private final ComboBox<Task.Category> category=new ComboBox<>(),categoryFilter=new ComboBox<>();
    private final Label banner=new Label();
    private final Logger log=Logger.getLogger("ru.semenyutin.lab3");
    private FileHandler logFile;
    private UUID edited;
    public static void main(String[] args){launch(args);}
    @Override public void start(Stage stage)throws Exception {
        Properties cfg=new Properties();
        try(var r=new InputStreamReader(getClass().getResourceAsStream("/application.properties"),StandardCharsets.UTF_8)){cfg.load(r);}
        Path local=Path.of("config.local.properties");
        if(Files.exists(local))try(var r=Files.newBufferedReader(local,StandardCharsets.UTF_8)){cfg.load(r);}
        Path logDir=Path.of(cfg.getProperty("logs.path"));Files.createDirectories(logDir);
        logFile=new FileHandler(logDir.resolve("organizer-%g.log").toString(),200000,3,true);
        logFile.setEncoding("UTF-8");logFile.setFormatter(new SimpleFormatter());log.addHandler(logFile);log.setUseParentHandlers(false);
        for(StoreFactory f:List.of(new StoreFactory.MemoryFactory(),new StoreFactory.FileFactory(Path.of(cfg.getProperty("file.path"))),new StoreFactory.DatabaseFactory(Path.of(cfg.getProperty("database.path")))))
            store.getItems().add(new Store(f.info(),f.createTasks()));
        store.setValue(store.getItems().stream().filter(s->s.info().key().equals(cfg.getProperty("datasource"))).findFirst().orElseThrow(()->new IllegalArgumentException("datasource: memory, file или database")));
        title.setPromptText("Название");owner.setPromptText("Исполнитель");description.setPromptText("Описание");description.setPrefRowCount(3);
        search.setPromptText("Поиск по задаче и исполнителю");
        status.getItems().setAll(Task.Status.values());statusFilter.getItems().setAll(Task.Status.values());
        category.getItems().setAll(Task.Category.values());categoryFilter.getItems().setAll(Task.Category.values());
        statusFilter.setPromptText("Все статусы");categoryFilter.setPromptText("Все категории");
        table.getColumns().addAll(col("Задача",Task::title),col("Исполнитель",Task::owner),col("Срок",t->t.due().toString()),col("Статус",t->t.status().name()),col("Категория",t->t.category().name()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(v->new TableRow<>(){protected void updateItem(Task t,boolean empty){super.updateItem(t,empty);setStyle(empty||t==null||t.status()==Task.Status.DONE||!t.due().isBefore(LocalDate.now())?"":"-fx-background-color:#ffede6;");}});
        table.getSelectionModel().selectedItemProperty().addListener((o,a,t)->{if(t!=null){edited=t.id();title.setText(t.title());description.setText(t.description());owner.setText(t.owner());due.setValue(t.due());status.setValue(t.status());category.setValue(t.category());}});
        store.valueProperty().addListener((o,a,b)->{clear();act(this::refresh);});
        search.textProperty().addListener((o,a,b)->act(this::refresh));
        statusFilter.valueProperty().addListener((o,a,b)->act(this::refresh));
        categoryFilter.valueProperty().addListener((o,a,b)->act(this::refresh));
        var top=new HBox(8,new Label("Хранилище"),store,Fx.button("Синхронизировать все",()->act(()->{
            int n=Organizer.synchronize(store.getItems().stream().map(Store::dao).toList(),log);clear();refresh();banner.setText("Синхронизировано активных задач: "+n);
        })));
        var filters=new HBox(7,search,statusFilter,categoryFilter,Fx.button("Сброс",()->{search.clear();statusFilter.setValue(null);categoryFilter.setValue(null);}));
        var form=new VBox(9,new Label("Карточка задачи"),title,owner,description,new Label("Срок"),due,status,category,
            new HBox(8,Fx.button("Сохранить",()->act(()->save(status.getValue()))),Fx.button("Новая",this::clear)),
            new HBox(8,Fx.button("Выполнено",()->act(()->{if(edited==null)throw new IllegalArgumentException("Выберите задачу.");save(Task.Status.DONE);})),
            Fx.button("Удалить",()->act(()->{if(edited==null)throw new IllegalArgumentException("Выберите задачу.");organizer().remove(edited);clear();refresh();}))));
        form.getStyleClass().add("panel");
        var split=new SplitPane(table,form);split.setDividerPositions(.68);
        var layout=new VBox(12,Fx.title("Органайзер «План дня»"),top,filters,split,banner);layout.setPadding(new Insets(18));VBox.setVgrow(split,Priority.ALWAYS);
        clear();refresh();Fx.show(stage,"Лабораторная 3 · План дня",layout,1130,680);
    }
    private TableColumn<Task,String> col(String title,java.util.function.Function<Task,String> value){var c=new TableColumn<Task,String>(title);c.setCellValueFactory(e->new ReadOnlyStringWrapper(value.apply(e.getValue())));return c;}
    private Organizer organizer(){return new Organizer(store.getValue().dao(),Clock.systemUTC(),log);}
    private void save(Task.Status chosen)throws IOException {organizer().save(edited,title.getText(),description.getText(),owner.getText(),due.getValue(),chosen,category.getValue());clear();refresh();}
    private void clear(){table.getSelectionModel().clearSelection();edited=null;title.clear();description.clear();owner.clear();due.setValue(LocalDate.now().plusDays(1));status.setValue(Task.Status.TODO);category.setValue(Task.Category.STUDY);}
    private void refresh()throws IOException {var sort=List.copyOf(table.getSortOrder());table.setItems(FXCollections.observableArrayList(organizer().search(search.getText(),statusFilter.getValue(),categoryFilter.getValue())));table.getSortOrder().setAll(sort);table.sort();banner.setText("Задач в списке: "+table.getItems().size());}
    private interface Job{void run()throws Exception;}
    private void act(Job j){try{j.run();}catch(Exception e){log.log(Level.WARNING,e.getMessage(),e);Fx.error(e);}}
    @Override public void stop(){if(logFile!=null){log.removeHandler(logFile);logFile.close();}}
}
