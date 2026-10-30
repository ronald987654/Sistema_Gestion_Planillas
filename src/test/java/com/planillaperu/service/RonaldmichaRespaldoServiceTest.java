package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaRespaldoServiceTest {

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;
    private RonaldmichaRespaldoService servicio;
    private RonaldmichaAuditoriaService auditoria;
    private final RonaldmichaUsuario admin = new RonaldmichaUsuario(1L, "admin", "", RonaldmichaRol.ADMIN, null, true);

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-20T20:30:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        servicio = new RonaldmichaRespaldoService(db, auditoria, reloj);
    }

    @Test
    void respaldaYRestauraLaBase() throws SQLException {
        assertThat(servicio.nombreSugerido()).isEqualTo("planilla_2026-10-20_1530.db");
        Path copia = carpeta.resolve("copias").resolve(servicio.nombreSugerido());
        copia.getParent().toFile().mkdirs();
        servicio.respaldar(admin, copia);
        assertThat(copia).exists();
        assertThat(auditoria.recientes(1).getFirst().accion()).isEqualTo("BASE_RESPALDADA");

        ejecutar("DELETE FROM horario WHERE trabajador_id IN (SELECT id FROM trabajador WHERE num_doc = '40000007')");
        ejecutar("UPDATE trabajador SET nombres = 'CAMBIADO' WHERE num_doc = '40000007'");
        assertThat(nombre("40000007")).isEqualTo("CAMBIADO");

        Path previo = servicio.restaurar(admin, copia);

        assertThat(nombre("40000007")).isNotEqualTo("CAMBIADO");
        assertThat(previo).exists().hasFileName("planilla_antes_de_restaurar_2026-10-20_1530.db");
        assertThat(auditoria.recientes(1).getFirst().accion()).isEqualTo("BASE_RESTAURADA");
    }

    @Test
    void rechazaArchivosQueNoSonCopiasYUsuariosSinPermiso() throws IOException {
        Path texto = Files.writeString(carpeta.resolve("otro.db"), "no soy una base");
        assertThatThrownBy(() -> servicio.restaurar(admin, texto)).isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("no es una base de datos SQLite válida");
        assertThatThrownBy(() -> servicio.restaurar(admin, carpeta.resolve("no-existe.db")))
                .isInstanceOf(RonaldmichaValidacionException.class).hasMessageContaining("no existe");
        assertThatThrownBy(() -> servicio.restaurar(admin, db.archivo())).isInstanceOf(RonaldmichaValidacionException.class);
        assertThatThrownBy(() -> servicio.respaldar(admin, db.archivo())).isInstanceOf(RonaldmichaValidacionException.class);

        RonaldmichaUsuario aprobador = new RonaldmichaUsuario(3L, "aprobador", "", RonaldmichaRol.APROBADOR, null, true);
        assertThatThrownBy(() -> servicio.respaldar(aprobador, carpeta.resolve("x.db")))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        assertThat(RonaldmichaRespaldoService.comparar("7", "7")).isZero();
        assertThat(RonaldmichaRespaldoService.comparar("8", "7")).isPositive();
        assertThat(RonaldmichaRespaldoService.comparar("7.1", "7")).isPositive();
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private String nombre(String dni) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombres FROM trabajador WHERE num_doc = '" + dni + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
