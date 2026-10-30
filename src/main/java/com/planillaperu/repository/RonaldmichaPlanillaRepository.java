package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaConceptoExtra;
import com.planillaperu.domain.RonaldmichaPeriodo;
import com.planillaperu.domain.RonaldmichaTipoConceptoExtra;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.HoraExtraManual;
import com.planillaperu.repository.RonaldmichaAsistenciaRepository.NovedadMes;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RonaldmichaPlanillaRepository {

    private final RonaldmichaDatabase db;

    public RonaldmichaPlanillaRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public List<RonaldmichaConceptoExtra> conceptosExtra(long trabajadorId, YearMonth mes) {
        String sql = "SELECT tipo, codigo_plame, descripcion, monto FROM concepto_extra"
                + " WHERE trabajador_id = ? AND periodo = ? ORDER BY id";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            ps.setString(2, RonaldmichaPeriodo.FORMATO.format(mes));
            List<RonaldmichaConceptoExtra> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new RonaldmichaConceptoExtra(RonaldmichaTipoConceptoExtra.valueOf(rs.getString(1)), rs.getString(2),
                            rs.getString(3), new BigDecimal(rs.getString(4))));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los conceptos del periodo", e);
        }
    }

    public Map<Long, List<RonaldmichaConceptoExtra>> conceptosExtraDelMes(YearMonth mes) {
        String sql = "SELECT trabajador_id, tipo, codigo_plame, descripcion, monto FROM concepto_extra"
                + " WHERE periodo = ? ORDER BY id";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaPeriodo.FORMATO.format(mes));
            Map<Long, List<RonaldmichaConceptoExtra>> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new RonaldmichaConceptoExtra(
                            RonaldmichaTipoConceptoExtra.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                            new BigDecimal(rs.getString(5))));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los conceptos del periodo", e);
        }
    }

    public record RentaMes(long trabajadorId, YearMonth periodo, BigDecimal remuneracion, BigDecimal gratificacion,
                           BigDecimal proyectada, BigDecimal impuesto, BigDecimal retencion) {

        public BigDecimal percibido() {
            return remuneracion.add(gratificacion);
        }
    }

    public record OtroEmpleador(long trabajadorId, int anio, String ruc, String razonSocial, BigDecimal renta,
                                BigDecimal retencion) {
    }

    public Map<Long, RentaMes> rentaDelMes(YearMonth mes) {
        String sql = "SELECT trabajador_id, remuneracion, gratificacion, proyectada, impuesto, retencion"
                + " FROM renta_quinta_mes WHERE periodo = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RonaldmichaPeriodo.FORMATO.format(mes));
            Map<Long, RentaMes> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getLong(1), new RentaMes(rs.getLong(1), mes, new BigDecimal(rs.getString(2)),
                            new BigDecimal(rs.getString(3)), new BigDecimal(rs.getString(4)),
                            new BigDecimal(rs.getString(5)), new BigDecimal(rs.getString(6))));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la renta de quinta del periodo", e);
        }
    }

    public void guardarRentaDelMes(YearMonth mes, List<RentaMes> filas) {
        String periodo = RonaldmichaPeriodo.FORMATO.format(mes);
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement borrar = c.prepareStatement("DELETE FROM renta_quinta_mes WHERE periodo = ?");
                 PreparedStatement insertar = c.prepareStatement("INSERT INTO renta_quinta_mes (trabajador_id, periodo,"
                         + " remuneracion, gratificacion, proyectada, impuesto, retencion) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                borrar.setString(1, periodo);
                borrar.executeUpdate();
                for (RentaMes r : filas) {
                    insertar.setLong(1, r.trabajadorId());
                    insertar.setString(2, periodo);
                    insertar.setString(3, r.remuneracion().toPlainString());
                    insertar.setString(4, r.gratificacion().toPlainString());
                    insertar.setString(5, r.proyectada().toPlainString());
                    insertar.setString(6, r.impuesto().toPlainString());
                    insertar.setString(7, r.retencion().toPlainString());
                    insertar.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar la renta de quinta del periodo", e);
        }
    }

    public void borrarRentaDelMes(YearMonth mes) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("DELETE FROM renta_quinta_mes WHERE periodo = ?")) {
            ps.setString(1, RonaldmichaPeriodo.FORMATO.format(mes));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo borrar la renta de quinta del periodo", e);
        }
    }

    public Map<Long, OtroEmpleador> otrosEmpleadores(int anio) {
        String sql = "SELECT trabajador_id, ruc, razon_social, renta, retencion FROM renta_otro_empleador WHERE anio = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, anio);
            Map<Long, OtroEmpleador> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getLong(1), new OtroEmpleador(rs.getLong(1), anio, rs.getString(2), rs.getString(3),
                            new BigDecimal(rs.getString(4)), new BigDecimal(rs.getString(5))));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las rentas de otros empleadores", e);
        }
    }

    public void guardarOtroEmpleador(OtroEmpleador o) {
        String sql = "INSERT INTO renta_otro_empleador (trabajador_id, anio, ruc, razon_social, renta, retencion)"
                + " VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (trabajador_id, anio) DO UPDATE SET ruc = excluded.ruc,"
                + " razon_social = excluded.razon_social, renta = excluded.renta, retencion = excluded.retencion";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, o.trabajadorId());
            ps.setInt(2, o.anio());
            ps.setString(3, o.ruc());
            ps.setString(4, o.razonSocial());
            ps.setString(5, o.renta().toPlainString());
            ps.setString(6, o.retencion().toPlainString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar la renta de otro empleador", e);
        }
    }

    public void borrarOtroEmpleador(long trabajadorId, int anio) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM renta_otro_empleador WHERE trabajador_id = ? AND anio = ?")) {
            ps.setLong(1, trabajadorId);
            ps.setInt(2, anio);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo borrar la renta de otro empleador", e);
        }
    }

    public void guardarNovedades(long trabajadorId, YearMonth mes, NovedadMes n, List<HoraExtraManual> horasExtra,
                                 List<RonaldmichaConceptoExtra> conceptos) {
        String periodo = RonaldmichaPeriodo.FORMATO.format(mes);
        String upsert = "INSERT INTO novedad_mes (trabajador_id, periodo, inasistencias, vacaciones, tardanza_min,"
                + " licencia, feriados_trabajados, dias_subsidiados, comisiones, bonificacion, adelanto,"
                + " otros_descuentos) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (trabajador_id, periodo) DO UPDATE SET inasistencias = excluded.inasistencias,"
                + " vacaciones = excluded.vacaciones, tardanza_min = excluded.tardanza_min,"
                + " licencia = excluded.licencia, feriados_trabajados = excluded.feriados_trabajados,"
                + " dias_subsidiados = excluded.dias_subsidiados, comisiones = excluded.comisiones,"
                + " bonificacion = excluded.bonificacion, adelanto = excluded.adelanto,"
                + " otros_descuentos = excluded.otros_descuentos";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upsert);
                 PreparedStatement borrarHe = c.prepareStatement(
                         "DELETE FROM horas_extra_manual WHERE trabajador_id = ? AND fecha BETWEEN ? AND ?");
                 PreparedStatement insertarHe = c.prepareStatement("INSERT INTO horas_extra_manual"
                         + " (trabajador_id, fecha, horas, tratamiento, autorizada) VALUES (?, ?, ?, ?, ?)");
                 PreparedStatement borrarConceptos = c.prepareStatement(
                         "DELETE FROM concepto_extra WHERE trabajador_id = ? AND periodo = ?");
                 PreparedStatement insertarConcepto = c.prepareStatement("INSERT INTO concepto_extra"
                         + " (trabajador_id, periodo, tipo, codigo_plame, descripcion, monto) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, trabajadorId);
                ps.setString(2, periodo);
                ps.setInt(3, n.inasistencias());
                ps.setInt(4, n.vacaciones());
                ps.setInt(5, n.tardanzaMin());
                ps.setInt(6, n.licencia());
                ps.setInt(7, n.feriadosTrabajados());
                ps.setInt(8, n.diasSubsidiados());
                ps.setString(9, n.comisiones().toPlainString());
                ps.setString(10, n.bonificacion().toPlainString());
                ps.setString(11, n.adelanto().toPlainString());
                ps.setString(12, n.otrosDescuentos().toPlainString());
                ps.executeUpdate();

                borrarHe.setLong(1, trabajadorId);
                borrarHe.setString(2, mes.atDay(1).toString());
                borrarHe.setString(3, mes.atEndOfMonth().toString());
                borrarHe.executeUpdate();
                for (HoraExtraManual he : horasExtra) {
                    insertarHe.setLong(1, trabajadorId);
                    insertarHe.setString(2, he.fecha().toString());
                    insertarHe.setString(3, he.horas().toPlainString());
                    insertarHe.setString(4, he.tratamiento().codigo());
                    insertarHe.setInt(5, he.autorizada() ? 1 : 0);
                    insertarHe.executeUpdate();
                }

                borrarConceptos.setLong(1, trabajadorId);
                borrarConceptos.setString(2, periodo);
                borrarConceptos.executeUpdate();
                for (RonaldmichaConceptoExtra ce : conceptos) {
                    insertarConcepto.setLong(1, trabajadorId);
                    insertarConcepto.setString(2, periodo);
                    insertarConcepto.setString(3, ce.tipo().name());
                    insertarConcepto.setString(4, ce.codigoPlame());
                    insertarConcepto.setString(5, ce.descripcion());
                    insertarConcepto.setString(6, ce.monto().toPlainString());
                    insertarConcepto.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar las novedades de la planilla", e);
        }
    }
}
