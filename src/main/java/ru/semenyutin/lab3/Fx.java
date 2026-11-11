package ru.semenyutin.lab3;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.nio.file.Path;
final class Fx {
    private Fx() {}
    static void show(Stage stage,String title,Parent root,int w,int h) {
        Scene scene=new Scene(root,w,h);
        scene.getStylesheets().add(Fx.class.getResource("/theme.css").toExternalForm());
        stage.setTitle(title); stage.setScene(scene); stage.setMinWidth(Math.min(780,w));stage.setMinHeight(500);stage.show();
    }
    static Label title(String s) { Label l=new Label(s);l.getStyleClass().add("title");return l; }
    static Button button(String text,Runnable action) { Button b=new Button(text);b.setOnAction(e->action.run());return b; }
    static void error(Throwable error) { Alert a=new Alert(Alert.AlertType.ERROR); a.setHeaderText("Не удалось выполнить действие");a.setContentText(error.getMessage()==null?error.toString():error.getMessage());a.show(); }
    static Path file(Stage owner,boolean save,String title,String... patterns) {
        FileChooser c=new FileChooser();c.setTitle(title);c.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файлы",patterns));
        var f=save?c.showSaveDialog(owner):c.showOpenDialog(owner);return f==null?null:f.toPath();
    }
}
