package com.planillaperu.ui;

import com.planillaperu.RonaldmichaAppContext;
import com.planillaperu.domain.RonaldmichaTipoBeneficio;

public class RonaldmichaCtsController extends RonaldmichaBeneficiosController {

    public RonaldmichaCtsController(RonaldmichaAppContext contexto) {
        super(contexto, RonaldmichaTipoBeneficio.CTS);
    }
}
