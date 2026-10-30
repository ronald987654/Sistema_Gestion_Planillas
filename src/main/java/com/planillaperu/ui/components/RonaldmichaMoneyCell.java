package com.planillaperu.ui.components;

import com.planillaperu.common.RonaldmichaFormatos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

import java.math.BigDecimal;

public class RonaldmichaMoneyCell<S> extends TableCell<S, BigDecimal> {

    public RonaldmichaMoneyCell() {
        getStyleClass().add("num");
    }

    public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> fabrica() {
        return columna -> new RonaldmichaMoneyCell<>();
    }

    @Override
    protected void updateItem(BigDecimal monto, boolean vacio) {
        super.updateItem(monto, vacio);
        setText(vacio || monto == null ? null : RonaldmichaFormatos.moneda(monto));
        getStyleClass().remove("monto-negativo");
        if (!vacio && monto != null && monto.signum() < 0) {
            getStyleClass().add("monto-negativo");
        }
    }
}
