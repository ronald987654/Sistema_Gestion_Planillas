module com.planillaperu {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;

    requires org.xerial.sqlitejdbc;
    requires flyway.core;
    requires org.apache.poi.ooxml;
    requires com.github.librepdf.openpdf;
    requires jbcrypt;
    requires org.slf4j;
    requires ch.qos.logback.classic;

    exports com.planillaperu;
    opens com.planillaperu.ui to javafx.fxml;
    opens com.planillaperu.ui.components to javafx.fxml;
    opens db.migration;
}
