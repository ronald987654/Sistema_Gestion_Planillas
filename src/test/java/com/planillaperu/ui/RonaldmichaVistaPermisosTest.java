package com.planillaperu.ui;

import com.planillaperu.domain.RonaldmichaRol;
import com.planillaperu.service.RonaldmichaelMatrizPermisos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RonaldmichaVistaPermisosTest {

    @Test
    void elMenuCoincideConLaMatriz() {
        for (RonaldmichaRol rol : RonaldmichaRol.values()) {
            for (RonaldmichaVista.Grupo g : RonaldmichaVista.menu(rol)) {
                for (RonaldmichaVista v : g.vistas()) {
                    assertThat(v.permisos()).as("%s para %s", v, rol)
                            .anyMatch(p -> RonaldmichaelMatrizPermisos.tiene(rol, p));
                }
            }
        }
        assertThat(RonaldmichaVista.USUARIOS.permisos()).noneMatch(p -> RonaldmichaelMatrizPermisos.tiene(RonaldmichaRol.TRABAJADOR, p));
        assertThat(RonaldmichaVista.MIS_DATOS.permisos()).noneMatch(p -> RonaldmichaelMatrizPermisos.tiene(RonaldmichaRol.ADMIN, p));
    }
}
