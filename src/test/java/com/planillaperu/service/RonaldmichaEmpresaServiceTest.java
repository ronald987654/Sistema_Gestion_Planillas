package com.planillaperu.service;

import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.domain.RonaldmichaUsuario;
import com.planillaperu.repository.RonaldmichaAuditoriaRepository;
import com.planillaperu.repository.RonaldmichaBaseDatosPrueba;
import com.planillaperu.repository.RonaldmichaDatabase;
import com.planillaperu.repository.RonaldmichaEmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RonaldmichaEmpresaServiceTest {

    private static final RonaldmichaUsuario ADMIN = new RonaldmichaUsuario(1L, "admin", "-", RonaldmichaRol.ADMIN, null, true);
    private static final RonaldmichaUsuario APROBADOR = new RonaldmichaUsuario(3L, "aprob", "-", RonaldmichaRol.APROBADOR, null, true);
    private static final byte[] IMAGEN = "imagen".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path carpeta;

    private RonaldmichaEmpresaService servicio;

    @BeforeEach
    void preparar() {
        RonaldmichaDatabase db = RonaldmichaBaseDatosPrueba.crear(carpeta);
        RonaldmichaAuditoriaService auditoria = new RonaldmichaAuditoriaService(new RonaldmichaAuditoriaRepository(db), Clock.systemDefaultZone());
        servicio = new RonaldmichaEmpresaService(new RonaldmichaEmpresaRepository(db), auditoria);
    }

    @Test
    void sinDatosFaltaTodoYHayAviso() {
        assertThat(servicio.obtener().id()).isNull();
        assertThat(servicio.faltantes()).contains("RUC", "razón social", "dirección", "representante legal");
        assertThat(servicio.avisoDatosIncompletos()).hasValueSatisfying(a -> assertThat(a).startsWith("Faltan"));
    }

    @Test
    void guardaYLeeTodosLosDatosIncluidasImagenes() {
        RonaldmichaEmpresa guardada = servicio.guardar(completa(RonaldmichaRegimenLaboral.GENERAL, null), ADMIN);

        RonaldmichaEmpresa leida = servicio.obtener();
        assertThat(leida.id()).isEqualTo(guardada.id());
        assertThat(leida.ruc()).isEqualTo("20600000005");
        assertThat(leida.inicioActividades()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(leida.diaPago()).isEqualTo(30);
        assertThat(leida.sctr()).isTrue();
        assertThat(leida.logo()).isEqualTo(IMAGEN);
        assertThat(servicio.faltantes()).isEmpty();
        assertThat(servicio.avisoDatosIncompletos()).isEmpty();
    }

    @Test
    void actualizaLaMismaFila() {
        RonaldmichaEmpresa primera = servicio.guardar(completa(RonaldmichaRegimenLaboral.GENERAL, null), ADMIN);
        RonaldmichaEmpresa cambio = new RonaldmichaEmpresa(primera.id(), primera.ruc(), "Otra Razón S.A.C.", primera.nombreComercial(),
                primera.regimenTributario(), primera.ciiu(), primera.inicioActividades(), primera.direccion(),
                primera.departamento(), primera.provincia(), primera.distrito(), primera.ubigeo(), primera.ciudad(),
                primera.regimenLaboral(), primera.registroRemype(), primera.eps(), primera.periodicidad(),
                primera.diaPago(), primera.sctr(), primera.repNombre(), primera.repTipoDoc(), primera.repDoc(),
                primera.repCargo(), primera.partidaRegistral(), primera.telefono(), primera.correo(),
                primera.correoBoletas(), primera.logo(), primera.firma());

        servicio.guardar(cambio, ADMIN);

        assertThat(servicio.obtener().razonSocial()).isEqualTo("Otra Razón S.A.C.");
        assertThat(servicio.obtener().id()).isEqualTo(primera.id());
    }

    @Test
    void remypeObligatorioParaPequenaYMicroempresa() {
        assertThat(servicio.validar(completa(RonaldmichaRegimenLaboral.PEQUENA, null)))
                .anyMatch(e -> e.contains("REMYPE"));
        assertThat(servicio.validar(completa(RonaldmichaRegimenLaboral.MICRO, " ")))
                .anyMatch(e -> e.contains("REMYPE"));
        assertThat(servicio.validar(completa(RonaldmichaRegimenLaboral.MICRO, "0001234567-2026"))).isEmpty();
        assertThat(servicio.validar(completa(RonaldmichaRegimenLaboral.GENERAL, null))).isEmpty();
    }

    @Test
    void rechazaRucInvalidoAlGuardar() {
        RonaldmichaEmpresa base = completa(RonaldmichaRegimenLaboral.GENERAL, null);
        RonaldmichaEmpresa mala = new RonaldmichaEmpresa(null, "20600000006", base.razonSocial(), null, null, null, null, null, null,
                null, null, null, null, RonaldmichaRegimenLaboral.GENERAL, null, null, null, null, false, null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> servicio.guardar(mala, ADMIN))
                .isInstanceOf(RonaldmichaValidacionException.class)
                .hasMessageContaining("dígito verificador");
    }

    @Test
    void soloElAdministradorModifica() {
        assertThatThrownBy(() -> servicio.guardar(completa(RonaldmichaRegimenLaboral.GENERAL, null), APROBADOR))
                .isInstanceOf(RonaldmichaAccesoDenegadoException.class);
    }

    @Test
    void vistaPreviaPdfGeneraUnPdf() {
        byte[] pdf = servicio.vistaPreviaPdf(RonaldmichaEmpresa.vacia());
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    private static RonaldmichaEmpresa completa(RonaldmichaRegimenLaboral regimen, String remype) {
        return new RonaldmichaEmpresa(null, "20600000005", "Empresa de Ejemplo S.A.C.", "Ejemplo", "Régimen General",
                "4711", LocalDate.of(2020, 3, 1), "Av. Ejemplo 123", "Lima", "Lima", "Miraflores", "150122", "Lima",
                regimen, remype, null, "Mensual", 30, true, "Rosa Quispe Mamani", "DNI", "40000001",
                "Gerente general", "12345678", "014000000", "contacto@ejemplo.pe", "boletas@ejemplo.pe",
                IMAGEN, IMAGEN);
    }
}
