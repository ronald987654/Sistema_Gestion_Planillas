package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;

public class RonaldmichaGratificacionesController extends RonaldmichaBeneficiosController {

    public RonaldmichaGratificacionesController(RonaldmichaAppContext contexto) {
        super(contexto, RonaldmichaTipoBeneficio.GRATIFICACION);
    }
}
