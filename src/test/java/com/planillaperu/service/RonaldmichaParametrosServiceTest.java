package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaAfpComision;
import com.planillaperu.domain.RonaldmichaEstadoParametros;
import com.planillaperu.domain.RonaldmichaParametrosVersion;
import com.planillaperu.domain.RonaldmichaRegistroAuditoria;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaParametrosRepository;
import com.planillaperu.repository.RonaldmichaPeriodoRepository;
import com.planillaperu.repository.RonaldmichaTrabajadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaParametrosServiceTest {

    private static final RonaldmichaUsuario ADMIN = new RonaldmichaUsuario(1L, "admin", "-", RonaldmichaRol.ADMIN, null, true);
    private static final RonaldmichaUsuario TRABAJADOR = new RonaldmichaUsuario(2L, "40000002", "-", RonaldmichaRol.TRABAJADOR, 2L, true);

    @TempDir
    Path carpeta;

    private RonaldmichaDatabase db;
    private RonaldmichaParametrosService servicio;
    private RonaldmichaAuditoriaService auditoria;

    @BeforeEach
    void preparar() {
        db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        Clock reloj = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Lima"));
        auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), reloj);
        servicio = new RonaldmichaParametrosService(new RonaldmichaParametrosRepository(db), new RonaldmichaPeriodoRepository(db),
                new RonaldmichaTrabajadorRepository(db), auditoria, reloj);
    }

    @Test
    void septiembre2026UsaRmv1130YOctubre2026UsaRmv1230() {
        assertThat(servicio.vigentes(YearMonth.of(2026, 9)).rmv()).isEqualByComparingTo("1130");
        assertThat(servicio.vigentes(YearMonth.of(2026, 10)).rmv()).isEqualByComparingTo("1230");
    }

    @Test
    void usaLaVersionConMayorVigenteDesdeHastaElPeriodo() {
        RonaldmichaParametrosVersion v2025 = servicio.vigentes(YearMonth.of(2025, 12));
        assertThat(v2025.vigenteDesde()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(v2025.uit()).isEqualByComparingTo("5350");

        RonaldmichaParametrosVersion enero = servicio.vigentes(YearMonth.of(2026, 1));
        assertThat(enero.uit()).isEqualByComparingTo("5500");
        assertThat(enero.rmv()).isEqualByComparingTo("1130");
    }

    @Test
    void conservaPrecisionYComisiones() {
        RonaldmichaParametrosVersion v = servicio.vigentes(YearMonth.of(2026, 10));
        assertThat(v.afpPrimaPct()).isEqualTo(new BigDecimal("1.37"));
        assertThat(v.rmaPrima()).isEqualTo(new BigDecimal("12732.70"));
        assertThat(v.comision("AFP Integra")).hasValueSatisfying(c -> {
            assertThat(c.comisionFlujoPct()).isEqualTo(new BigDecimal("1.55"));
            assertThat(c.comisionMixtaPct()).isEqualTo(new BigDecimal("0.78"));
        });
    }

    @Test
    void fallaAntesDeLaPrimeraVersion() {
        assertThatThrownBy(() -> servicio.vigentes(YearMonth.of(2024, 12)))
                .isInstanceOf(RonaldmichaParametroNoEncontradoException.class)
                .hasMessageContaining("12/2024");
    }

    @Test
    void unCambioFuturoSoloAplicaDesdeSuMes() {
        servicio.registrar(version(YearMonth.of(2027, 1), "1300"), ADMIN);

        assertThat(servicio.vigentes(YearMonth.of(2026, 12)).rmv()).isEqualByComparingTo("1230");
        assertThat(servicio.vigentes(YearMonth.of(2027, 1)).rmv()).isEqualByComparingTo("1300");
        assertThat(servicio.vigentes(YearMonth.of(2027, 6)).rmv()).isEqualByComparingTo("1300");
        assertThat(servicio.vigentes(YearMonth.of(2027, 1)).comisiones()).hasSize(4);
        assertThat(auditoria.recientes(5)).extracting(RonaldmichaRegistroAuditoria::accion).contains("PARAMETROS_REGISTRADOS");
    }

    @Test
    void rechazaDosVersionesVigentesEnElMismoMes() {
        assertThatThrownBy(() -> servicio.registrar(version(YearMonth.of(2026, 10), "1300"), ADMIN))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("Ya existe una versión vigente desde 10/2026");
    }

    @Test
    void noPermiteCambiarParametrosDeUnPeriodoCerrado() throws SQLException {
        ejecutar("INSERT INTO periodo (periodo, estado, cerrado_por) VALUES ('202611', 'Cerrado', 'admin')");

        assertThatThrownBy(() -> servicio.registrar(version(YearMonth.of(2026, 11), "1300"), ADMIN))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("11/2026 está cerrado");
    }

    @Test
    void validaTasasYComisiones() {
        RonaldmichaParametrosVersion base = version(YearMonth.of(2027, 1), "1300");
        RonaldmichaParametrosVersion invalida = new RonaldmichaParametrosVersion(null, base.vigenteDesde(), null, base.rmv(), base.uit(),
                new BigDecimal("150"), base.essaludPct(), base.epsPct(), base.onpPct(), base.afpAportePct(),
                base.afpPrimaPct(), base.rmaPrima(), base.bonifExtPct(), base.bonifExtEpsPct(), base.vidaLeyPct(),
                base.sisMonto(), null, null, null, base.comisiones().subList(0, 3), null, null, null);

        assertThat(servicio.validar(invalida))
                .anyMatch(e -> e.startsWith("Asignación familiar"))
                .anyMatch(e -> e.contains("cuatro AFP"));
    }

    @Test
    void soloElAdministradorRegistraYAnula() {
        assertThatThrownBy(() -> servicio.registrar(version(YearMonth.of(2027, 1), "1300"), TRABAJADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
        long id = servicio.vigentes(YearMonth.of(2026, 10)).id();
        assertThatThrownBy(() -> servicio.anular(id, "motivo", TRABAJADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void anularNoBorraYElPeriodoVuelveALaVersionAnterior() {
        RonaldmichaParametrosVersion octubre = servicio.vigentes(YearMonth.of(2026, 10));

        servicio.anular(octubre.id(), "Registrada por error", ADMIN);

        assertThat(servicio.vigentes(YearMonth.of(2026, 10)).rmv()).isEqualByComparingTo("1130");
        assertThat(servicio.listar()).hasSize(3)
                .filteredOn(v -> v.id().equals(octubre.id()))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.estado()).isEqualTo(RonaldmichaEstadoParametros.ANULADA);
                    assertThat(v.motivoAnulacion()).isEqualTo("Registrada por error");
                    assertThat(v.anuladoPor()).isEqualTo("admin");
                });
        assertThat(auditoria.recientes(5))
                .anySatisfy(r -> {
                    assertThat(r.accion()).isEqualTo("PARAMETROS_ANULADOS");
                    assertThat(r.detalle()).contains("Registrada por error");
                });
    }

    @Test
    void anularExigeMotivo() {
        long id = servicio.vigentes(YearMonth.of(2026, 10)).id();
        assertThatThrownBy(() -> servicio.anular(id, "  ", ADMIN))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void vistaPreviaDelImpactoDeUnaNuevaRmv() {
        var impacto = servicio.impacto(version(YearMonth.of(2027, 1), "1600"));

        assertThat(impacto.bajoNuevaRmv()).extracting(t -> t.trabajador().numDoc())
                .containsExactlyInAnyOrder("40000006", "40000007");
        assertThat(impacto.anterior().rmv()).isEqualByComparingTo("1230");
        assertThat(impacto.asignacionAnterior()).isEqualTo(new BigDecimal("123.00"));
        assertThat(impacto.asignacionNueva()).isEqualTo(new BigDecimal("160.00"));
        assertThat(impacto.conAsignacion()).isEqualTo(3);
        assertThat(impacto.diferenciaMensual()).isEqualTo(new BigDecimal("111.00"));
    }

    private RonaldmichaParametrosVersion version(YearMonth desde, String rmv) {
        RonaldmichaParametrosVersion b = servicio.vigentes(YearMonth.of(2026, 10));
        List<RonaldmichaAfpComision> comisiones = b.comisiones();
        return new RonaldmichaParametrosVersion(null, desde, "Prueba", new BigDecimal(rmv), b.uit(), b.asigFamiliarPct(),
                b.essaludPct(), b.epsPct(), b.onpPct(), b.afpAportePct(), b.afpPrimaPct(), b.rmaPrima(),
                b.bonifExtPct(), b.bonifExtEpsPct(), b.vidaLeyPct(), b.sisMonto(), null, null, null, comisiones,
                null, null, null);
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = db.conexion(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
