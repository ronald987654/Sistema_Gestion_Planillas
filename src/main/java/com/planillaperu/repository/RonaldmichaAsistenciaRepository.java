package com.planillaperu.repository;

import com.planillaperu.common.RonaldmichaDinero;
import com.planillaperu.domain.RonaldmichaMarcacion;
import com.planillaperu.domain.RonaldmichaNovedad;
import com.planillaperu.domain.RonaldmichaOrigenAsistencia;
import com.planillaperu.domain.RonaldmichaTratamientoHe;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class RonaldmichaAsistenciaRepository {

    public record NovedadMes(int inasistencias, int vacaciones, int tardanzaMin, int licencia,
                             int feriadosTrabajados, int diasSubsidiados, BigDecimal comisiones,
                             BigDecimal bonificacion, BigDecimal adelanto, BigDecimal otrosDescuentos) {

        public static final NovedadMes NINGUNA = new NovedadMes(0, 0, 0, 0, 0, 0, RonaldmichaDinero.CERO, RonaldmichaDinero.CERO,
                RonaldmichaDinero.CERO, RonaldmichaDinero.CERO);
    }

    public record HoraExtraManual(LocalDate fecha, BigDecimal horas, RonaldmichaTratamientoHe tratamiento, boolean autorizada) {
    }

    private static final String COLUMNAS = "trabajador_id, fecha, hora_ingreso, hora_salida, novedad, he_autorizada,"
            + " he_tratamiento, origen";

    private final RonaldmichaDatabase db;

    public RonaldmichaAsistenciaRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Map<LocalDate, RonaldmichaMarcacion> delMes(long trabajadorId, YearMonth mes) {
        String sql = "SELECT " + COLUMNAS + " FROM asistencia WHERE trabajador_id = ? AND fecha BETWEEN ? AND ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            ps.setString(2, mes.atDay(1).toString());
            ps.setString(3, mes.atEndOfMonth().toString());
            Map<LocalDate, RonaldmichaMarcacion> mapa = new TreeMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RonaldmichaMarcacion m = leer(rs);
                    mapa.put(m.fecha(), m);
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la asistencia", e);
        }
    }

    public Map<Long, Map<LocalDate, RonaldmichaMarcacion>> delMes(YearMonth mes) {
        String sql = "SELECT " + COLUMNAS + " FROM asistencia WHERE fecha BETWEEN ? AND ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, mes.atDay(1).toString());
            ps.setString(2, mes.atEndOfMonth().toString());
            Map<Long, Map<LocalDate, RonaldmichaMarcacion>> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RonaldmichaMarcacion m = leer(rs);
                    mapa.computeIfAbsent(m.trabajadorId(), k -> new TreeMap<>()).put(m.fecha(), m);
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer la asistencia del mes", e);
        }
    }

    public void guardar(Collection<RonaldmichaMarcacion> marcaciones) {
        String upsert = "INSERT INTO asistencia (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (trabajador_id, fecha) DO UPDATE SET hora_ingreso = excluded.hora_ingreso,"
                + " hora_salida = excluded.hora_salida, novedad = excluded.novedad,"
                + " he_autorizada = excluded.he_autorizada, he_tratamiento = excluded.he_tratamiento,"
                + " origen = excluded.origen";
        String borrar = "DELETE FROM asistencia WHERE trabajador_id = ? AND fecha = ?";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement ins = c.prepareStatement(upsert); PreparedStatement del = c.prepareStatement(borrar)) {
                for (RonaldmichaMarcacion m : marcaciones) {
                    if (m.vacia()) {
                        del.setLong(1, m.trabajadorId());
                        del.setString(2, m.fecha().toString());
                        del.executeUpdate();
                        continue;
                    }
                    ins.setLong(1, m.trabajadorId());
                    ins.setString(2, m.fecha().toString());
                    ins.setString(3, hora(m.ingreso()));
                    ins.setString(4, hora(m.salida()));
                    ins.setString(5, m.novedad().codigo());
                    ins.setInt(6, m.heAutorizada() ? 1 : 0);
                    ins.setString(7, m.heTratamiento().codigo());
                    ins.setString(8, m.origen().etiqueta());
                    ins.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar la asistencia", e);
        }
    }

    public int borrarMes(long trabajadorId, YearMonth mes) {
        String sql = "DELETE FROM asistencia WHERE trabajador_id = ? AND fecha BETWEEN ? AND ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            ps.setString(2, mes.atDay(1).toString());
            ps.setString(3, mes.atEndOfMonth().toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo borrar la asistencia", e);
        }
    }

    public Map<LocalDate, String> feriadosExtra(int anio) {
        String sql = "SELECT fecha, motivo FROM feriado_extra WHERE fecha BETWEEN ? AND ? ORDER BY fecha";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, anio + "-01-01");
            ps.setString(2, anio + "-12-31");
            Map<LocalDate, String> mapa = new TreeMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(LocalDate.parse(rs.getString("fecha")), rs.getString("motivo"));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los días no laborables", e);
        }
    }

    public boolean agregarFeriado(LocalDate fecha, String motivo) {
        String sql = "INSERT INTO feriado_extra (fecha, motivo) VALUES (?, ?) ON CONFLICT (fecha) DO NOTHING";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fecha.toString());
            ps.setString(2, motivo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo agregar el día no laborable", e);
        }
    }

    public boolean quitarFeriado(LocalDate fecha) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement("DELETE FROM feriado_extra WHERE fecha = ?")) {
            ps.setString(1, fecha.toString());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo quitar el día no laborable", e);
        }
    }

    public Optional<NovedadMes> novedadMes(long trabajadorId, YearMonth mes) {
        String sql = "SELECT inasistencias, vacaciones, tardanza_min, licencia, feriados_trabajados,"
                + " dias_subsidiados, comisiones, bonificacion, adelanto, otros_descuentos FROM novedad_mes"
                + " WHERE trabajador_id = ? AND periodo = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            ps.setString(2, String.format("%04d%02d", mes.getYear(), mes.getMonthValue()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new NovedadMes(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), RonaldmichaDinero.de(rs.getString(7)), RonaldmichaDinero.de(rs.getString(8)),
                        RonaldmichaDinero.de(rs.getString(9)), RonaldmichaDinero.de(rs.getString(10))))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las novedades del mes", e);
        }
    }

    public Map<Long, NovedadMes> novedadesDelMes(YearMonth mes) {
        String sql = "SELECT trabajador_id, inasistencias, vacaciones, tardanza_min, licencia, feriados_trabajados,"
                + " dias_subsidiados, comisiones, bonificacion, adelanto, otros_descuentos FROM novedad_mes"
                + " WHERE periodo = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, String.format("%04d%02d", mes.getYear(), mes.getMonthValue()));
            Map<Long, NovedadMes> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getLong(1), new NovedadMes(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                            rs.getInt(6), rs.getInt(7), RonaldmichaDinero.de(rs.getString(8)), RonaldmichaDinero.de(rs.getString(9)),
                            RonaldmichaDinero.de(rs.getString(10)), RonaldmichaDinero.de(rs.getString(11))));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las novedades del mes", e);
        }
    }

    public Map<Long, List<HoraExtraManual>> horasExtraManualesDelMes(YearMonth mes) {
        String sql = "SELECT trabajador_id, fecha, horas, tratamiento, autorizada FROM horas_extra_manual"
                + " WHERE fecha BETWEEN ? AND ? ORDER BY trabajador_id, fecha";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, mes.atDay(1).toString());
            ps.setString(2, mes.atEndOfMonth().toString());
            Map<Long, List<HoraExtraManual>> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new HoraExtraManual(
                            LocalDate.parse(rs.getString(2)), new BigDecimal(rs.getString(3)),
                            RonaldmichaTratamientoHe.desdeCodigo(rs.getString(4)), rs.getInt(5) == 1));
                }
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las horas extra manuales", e);
        }
    }

    public List<HoraExtraManual> horasExtraManuales(long trabajadorId, YearMonth mes) {
        String sql = "SELECT fecha, horas, tratamiento, autorizada FROM horas_extra_manual"
                + " WHERE trabajador_id = ? AND fecha BETWEEN ? AND ? ORDER BY fecha";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            ps.setString(2, mes.atDay(1).toString());
            ps.setString(3, mes.atEndOfMonth().toString());
            List<HoraExtraManual> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new HoraExtraManual(LocalDate.parse(rs.getString(1)), new BigDecimal(rs.getString(2)),
                            RonaldmichaTratamientoHe.desdeCodigo(rs.getString(3)), rs.getInt(4) == 1));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer las horas extra manuales", e);
        }
    }

    private static RonaldmichaMarcacion leer(ResultSet rs) throws SQLException {
        String tratamiento = rs.getString("he_tratamiento");
        return new RonaldmichaMarcacion(
                rs.getLong("trabajador_id"),
                LocalDate.parse(rs.getString("fecha")),
                horaDe(rs.getString("hora_ingreso")),
                horaDe(rs.getString("hora_salida")),
                RonaldmichaNovedad.desdeCodigo(rs.getString("novedad")),
                rs.getInt("he_autorizada") == 1,
                tratamiento == null ? RonaldmichaTratamientoHe.PAGAR : RonaldmichaTratamientoHe.desdeCodigo(tratamiento),
                RonaldmichaOrigenAsistencia.desdeEtiqueta(rs.getString("origen")));
    }

    private static String hora(LocalTime h) {
        return h == null ? null : h.toString();
    }

    private static LocalTime horaDe(String texto) {
        return texto == null || texto.isBlank() ? null : LocalTime.parse(texto);
    }
}
