package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaCategoria;
import com.planillaperu.domain.RonaldmichaHorario;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;
import com.planillaperu.domain.RonaldmichaSalud;
import com.planillaperu.domain.RonaldmichaSistemaPension;
import com.planillaperu.domain.RonaldmichaTipoComision;
import com.planillaperu.domain.RonaldmichaTipoDocumento;
import com.planillaperu.domain.RonaldmichaTipoPago;
import com.planillaperu.domain.RonaldmichaTrabajador;
import com.planillaperu.domain.RonaldmichaTrabajadorRemuneracion;
import com.planillaperu.domain.RonaldmichaTrabajadorResumen;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RonaldmichaTrabajadorRepository {

    private static final List<String> COLUMNAS = List.of(
            "codigo", "tipo_doc", "num_doc", "ap_paterno", "ap_materno", "nombres", "celular", "correo",
            "fecha_ingreso", "fecha_cese", "area", "cargo", "tipo_contrato", "regimen_laboral", "centro_costo",
            "sucursal", "sueldo", "movilidad", "salud", "sistema_pension", "cuspp", "tipo_comision",
            "asig_familiar", "vida_ley", "tipo_pago", "banco_sueldo", "cta_sueldo", "banco_cts", "cta_cts",
            "fecha_nacimiento", "hijos", "direccion", "categoria");

    private final RonaldmichaDatabase db;

    public RonaldmichaTrabajadorRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    private static final String SQL_ACTIVOS = "SELECT id, codigo, tipo_doc, num_doc, ap_paterno, ap_materno,"
            + " nombres, cargo, area, regimen_laboral, sueldo, asig_familiar FROM trabajador"
            + " WHERE fecha_cese IS NULL OR fecha_cese >= ? ORDER BY ap_paterno, ap_materno, nombres";

    public List<RonaldmichaTrabajadorResumen> listarActivos(LocalDate fecha) {
        return listarRemuneraciones(fecha).stream().map(RonaldmichaTrabajadorRemuneracion::trabajador).toList();
    }

    public List<RonaldmichaTrabajadorRemuneracion> listarRemuneraciones(LocalDate fecha) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(SQL_ACTIVOS)) {
            ps.setString(1, fecha.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<RonaldmichaTrabajadorRemuneracion> lista = new ArrayList<>();
                while (rs.next()) {
                    RonaldmichaTrabajadorResumen t = new RonaldmichaTrabajadorResumen(
                            rs.getLong("id"),
                            rs.getString("codigo"),
                            rs.getString("tipo_doc"),
                            rs.getString("num_doc"),
                            rs.getString("ap_paterno"),
                            rs.getString("ap_materno"),
                            rs.getString("nombres"),
                            rs.getString("cargo"),
                            rs.getString("area"),
                            RonaldmichaRegimenLaboral.desdeCodigo(rs.getString("regimen_laboral")));
                    lista.add(new RonaldmichaTrabajadorRemuneracion(t, new BigDecimal(rs.getString("sueldo")),
                            rs.getInt("asig_familiar") == 1));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo listar los trabajadores", e);
        }
    }

    public List<RonaldmichaTrabajador> listarTodos() {
        String sql = "SELECT * FROM trabajador ORDER BY ap_paterno, ap_materno, nombres";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<RonaldmichaTrabajador> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(leer(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo listar los trabajadores", e);
        }
    }

    public Optional<RonaldmichaTrabajador> buscarPorId(long id) {
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM trabajador WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el trabajador", e);
        }
    }

    public boolean existeDocumento(String numDoc, Long excluirId) {
        return existe("SELECT 1 FROM trabajador WHERE num_doc = ? AND id <> ?", numDoc, excluirId);
    }

    public boolean existeCodigo(String codigo, Long excluirId) {
        return existe("SELECT 1 FROM trabajador WHERE codigo = ? AND id <> ?", codigo, excluirId);
    }

    private boolean existe(String sql, String valor, Long excluirId) {
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, valor);
            ps.setLong(2, excluirId == null ? -1 : excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo verificar los datos del trabajador", e);
        }
    }

    public String siguienteCodigo() {
        String sql = "SELECT MAX(CAST(substr(codigo, 2) AS INTEGER)) FROM trabajador WHERE codigo GLOB 'T[0-9]*'";
        try (Connection c = db.conexion(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int ultimo = rs.next() ? rs.getInt(1) : 0;
            return String.format("T%04d", ultimo + 1);
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo generar el código del trabajador", e);
        }
    }

    public Optional<RonaldmichaHorario> horario(long trabajadorId) {
        String sql = "SELECT * FROM horario WHERE trabajador_id = ?";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, trabajadorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leerHorario(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer el horario", e);
        }
    }

    public Map<Long, RonaldmichaHorario> horarios() {
        try (Connection c = db.conexion(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM horario")) {
            Map<Long, RonaldmichaHorario> mapa = new HashMap<>();
            while (rs.next()) {
                mapa.put(rs.getLong("trabajador_id"), leerHorario(rs));
            }
            return mapa;
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los horarios", e);
        }
    }

    public RonaldmichaTrabajador insertar(RonaldmichaTrabajador t, RonaldmichaHorario h) {
        String sql = "INSERT INTO trabajador (" + String.join(", ", COLUMNAS) + ") VALUES ("
                + "?, ".repeat(COLUMNAS.size() - 1) + "?)";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                asignar(ps, t);
                ps.executeUpdate();
                long id;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getLong(1);
                }
                guardarHorario(c, id, h);
                c.commit();
                return t.conIdYCodigo(id, t.codigo());
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo registrar el trabajador", e);
        }
    }

    public void actualizar(RonaldmichaTrabajador t, RonaldmichaHorario h) {
        String sql = "UPDATE trabajador SET " + String.join(" = ?, ", COLUMNAS) + " = ? WHERE id = ?";
        try (Connection c = db.conexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                asignar(ps, t);
                ps.setLong(COLUMNAS.size() + 1, t.id());
                ps.executeUpdate();
                guardarHorario(c, t.id(), h);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo actualizar el trabajador", e);
        }
    }

    public void actualizarContacto(long id, String celular, String correo) {
        try (Connection c = db.conexion()) {
            Map<String, String> valores = new java.util.LinkedHashMap<>();
            valores.put("celular", celular);
            valores.put("correo", correo);
            actualizarColumnas(c, id, valores);
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo actualizar los datos de contacto", e);
        }
    }

    static void actualizarColumnas(Connection c, long id, Map<String, String> valores) throws SQLException {
        for (Map.Entry<String, String> e : valores.entrySet()) {
            if (!COLUMNAS.contains(e.getKey())) {
                throw new IllegalArgumentException("Columna no permitida: " + e.getKey());
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE trabajador SET " + e.getKey() + " = ? WHERE id = ?")) {
                ps.setString(1, e.getValue() == null || e.getValue().isBlank() ? null : e.getValue());
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        }
    }

    public void actualizarHorario(long trabajadorId, RonaldmichaHorario h) {
        try (Connection c = db.conexion()) {
            guardarHorario(c, trabajadorId, h);
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo guardar el horario", e);
        }
    }

    private static void guardarHorario(Connection c, long id, RonaldmichaHorario h) throws SQLException {
        String sql = "INSERT INTO horario (trabajador_id, hora_ingreso, hora_salida, refrigerio_min, tolerancia_min,"
                + " dias_laborables, no_fiscalizado) VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (trabajador_id) DO UPDATE SET hora_ingreso = excluded.hora_ingreso,"
                + " hora_salida = excluded.hora_salida, refrigerio_min = excluded.refrigerio_min,"
                + " tolerancia_min = excluded.tolerancia_min, dias_laborables = excluded.dias_laborables,"
                + " no_fiscalizado = excluded.no_fiscalizado";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, h.horaIngreso().toString());
            ps.setString(3, h.horaSalida().toString());
            ps.setInt(4, h.refrigerioMin());
            ps.setInt(5, h.toleranciaMin());
            ps.setString(6, h.diasComoTexto());
            ps.setInt(7, h.noFiscalizado() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private static void asignar(PreparedStatement ps, RonaldmichaTrabajador t) throws SQLException {
        int i = 1;
        ps.setString(i++, t.codigo());
        ps.setString(i++, t.tipoDoc().codigo());
        ps.setString(i++, t.numDoc());
        ps.setString(i++, t.apPaterno());
        ps.setString(i++, t.apMaterno());
        ps.setString(i++, t.nombres());
        ps.setString(i++, t.celular());
        ps.setString(i++, t.correo());
        ps.setString(i++, t.fechaIngreso().toString());
        ps.setString(i++, t.fechaCese() == null ? null : t.fechaCese().toString());
        ps.setString(i++, t.area());
        ps.setString(i++, t.cargo());
        ps.setString(i++, t.tipoContrato());
        ps.setString(i++, t.regimen().codigo());
        ps.setString(i++, t.centroCosto());
        ps.setString(i++, t.sucursal());
        ps.setString(i++, t.sueldo().toPlainString());
        ps.setString(i++, t.movilidad().toPlainString());
        ps.setString(i++, t.salud().nombre());
        ps.setString(i++, t.sistemaPension().nombre());
        ps.setString(i++, t.cuspp());
        ps.setString(i++, t.tipoComision() == null ? null : t.tipoComision().codigo());
        ps.setInt(i++, t.asigFamiliar() ? 1 : 0);
        ps.setInt(i++, t.vidaLey() ? 1 : 0);
        ps.setString(i++, t.tipoPago().nombre());
        ps.setString(i++, t.bancoSueldo());
        ps.setString(i++, t.ctaSueldo());
        ps.setString(i++, t.bancoCts());
        ps.setString(i++, t.ctaCts());
        ps.setString(i++, t.fechaNacimiento() == null ? null : t.fechaNacimiento().toString());
        ps.setInt(i++, t.hijos());
        ps.setString(i++, t.direccion());
        ps.setString(i, t.categoria().nombre());
    }

    private static RonaldmichaTrabajador leer(ResultSet rs) throws SQLException {
        String cese = rs.getString("fecha_cese");
        String comision = rs.getString("tipo_comision");
        String nacimiento = rs.getString("fecha_nacimiento");
        return new RonaldmichaTrabajador(
                rs.getLong("id"),
                rs.getString("codigo"),
                RonaldmichaTipoDocumento.desdeCodigo(rs.getString("tipo_doc")),
                rs.getString("num_doc"),
                rs.getString("ap_paterno"),
                rs.getString("ap_materno"),
                rs.getString("nombres"),
                rs.getString("celular"),
                rs.getString("correo"),
                LocalDate.parse(rs.getString("fecha_ingreso")),
                cese == null ? null : LocalDate.parse(cese),
                rs.getString("area"),
                rs.getString("cargo"),
                rs.getString("tipo_contrato"),
                RonaldmichaRegimenLaboral.desdeCodigo(rs.getString("regimen_laboral")),
                rs.getString("centro_costo"),
                rs.getString("sucursal"),
                new BigDecimal(rs.getString("sueldo")),
                new BigDecimal(rs.getString("movilidad")),
                RonaldmichaSalud.desdeNombre(rs.getString("salud")),
                RonaldmichaSistemaPension.desdeNombre(rs.getString("sistema_pension")),
                rs.getString("cuspp"),
                comision == null ? null : RonaldmichaTipoComision.desdeCodigo(comision),
                rs.getInt("asig_familiar") == 1,
                rs.getInt("vida_ley") == 1,
                RonaldmichaTipoPago.desdeNombre(rs.getString("tipo_pago")),
                rs.getString("banco_sueldo"),
                rs.getString("cta_sueldo"),
                rs.getString("banco_cts"),
                rs.getString("cta_cts"),
                nacimiento == null ? null : LocalDate.parse(nacimiento),
                rs.getInt("hijos"),
                rs.getString("direccion"),
                RonaldmichaCategoria.desdeNombre(rs.getString("categoria")));
    }

    private static RonaldmichaHorario leerHorario(ResultSet rs) throws SQLException {
        return new RonaldmichaHorario(
                LocalTime.parse(rs.getString("hora_ingreso")),
                LocalTime.parse(rs.getString("hora_salida")),
                rs.getInt("refrigerio_min"),
                rs.getInt("tolerancia_min"),
                RonaldmichaHorario.leerDias(rs.getString("dias_laborables")),
                rs.getInt("no_fiscalizado") == 1);
    }
}
