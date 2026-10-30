package com.planillaperu.repository;

import com.planillaperu.domain.RonaldmichaEmpresa;
import com.planillaperu.domain.RonaldmichaRegimenLaboral;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;

public class RonaldmichaEmpresaRepository {

    private static final String COLUMNAS = "ruc, razon_social, nombre_comercial, regimen_tributario, ciiu,"
            + " inicio_actividades, direccion, departamento, provincia, distrito, ubigeo, ciudad, regimen_laboral,"
            + " registro_remype, eps, periodicidad, dia_pago, sctr, rep_nombre, rep_tipo_doc, rep_doc, rep_cargo,"
            + " partida_registral, telefono, correo, correo_boletas, logo, firma, rubro, firma_digital_activa, huella_en_boleta";

    private final RonaldmichaDatabase db;

    public RonaldmichaEmpresaRepository(RonaldmichaDatabase db) {
        this.db = db;
    }

    public Optional<RonaldmichaEmpresa> obtener() {
        String sql = "SELECT id, " + COLUMNAS + " FROM empresa ORDER BY id LIMIT 1";
        try (Connection c = db.conexion(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new RonaldmichaDatosException("No se pudo leer los datos de la empresa", e);
        }
    }

    public RonaldmichaEmpresa guardar(RonaldmichaEmpresa e) {
        String sql = e.id() == null
                ? "INSERT INTO empresa (" + COLUMNAS + ") VALUES (" + "?,".repeat(30) + "?)"
                : "UPDATE empresa SET " + COLUMNAS.replace(",", " = ?,") + " = ? WHERE id = ?";
        try (Connection c = db.conexion();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, e.ruc());
            ps.setString(i++, e.razonSocial());
            ps.setString(i++, e.nombreComercial());
            ps.setString(i++, e.regimenTributario());
            ps.setString(i++, e.ciiu());
            ps.setString(i++, e.inicioActividades() == null ? null : e.inicioActividades().toString());
            ps.setString(i++, e.direccion());
            ps.setString(i++, e.departamento());
            ps.setString(i++, e.provincia());
            ps.setString(i++, e.distrito());
            ps.setString(i++, e.ubigeo());
            ps.setString(i++, e.ciudad());
            ps.setString(i++, e.regimenLaboral().codigo());
            ps.setString(i++, e.registroRemype());
            ps.setString(i++, e.eps());
            ps.setString(i++, e.periodicidad());
            if (e.diaPago() == null) {
                ps.setNull(i++, Types.INTEGER);
            } else {
                ps.setInt(i++, e.diaPago());
            }
            ps.setInt(i++, e.sctr() ? 1 : 0);
            ps.setString(i++, e.repNombre());
            ps.setString(i++, e.repTipoDoc());
            ps.setString(i++, e.repDoc());
            ps.setString(i++, e.repCargo());
            ps.setString(i++, e.partidaRegistral());
            ps.setString(i++, e.telefono());
            ps.setString(i++, e.correo());
            ps.setString(i++, e.correoBoletas());
            ps.setBytes(i++, e.logo());
            ps.setBytes(i++, e.firma());
            ps.setString(i++, e.rubro());
            ps.setInt(i++, e.firmaDigitalActiva() ? 1 : 0);
            ps.setInt(i++, e.huellaEnBoleta() ? 1 : 0);
            if (e.id() != null) {
                ps.setLong(i, e.id());
            }
            ps.executeUpdate();
            if (e.id() != null) {
                return e;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return obtener().filter(g -> g.id() == id).orElseThrow();
            }
        } catch (SQLException ex) {
            throw new RonaldmichaDatosException("No se pudo guardar los datos de la empresa", ex);
        }
    }

    private static RonaldmichaEmpresa mapear(ResultSet rs) throws SQLException {
        String inicio = rs.getString("inicio_actividades");
        int diaPago = rs.getInt("dia_pago");
        Integer dia = rs.wasNull() ? null : diaPago;
        return new RonaldmichaEmpresa(
                rs.getLong("id"),
                rs.getString("ruc"),
                rs.getString("razon_social"),
                rs.getString("nombre_comercial"),
                rs.getString("regimen_tributario"),
                rs.getString("ciiu"),
                inicio == null ? null : LocalDate.parse(inicio),
                rs.getString("direccion"),
                rs.getString("departamento"),
                rs.getString("provincia"),
                rs.getString("distrito"),
                rs.getString("ubigeo"),
                rs.getString("ciudad"),
                RonaldmichaRegimenLaboral.desdeCodigo(rs.getString("regimen_laboral")),
                rs.getString("registro_remype"),
                rs.getString("eps"),
                rs.getString("periodicidad"),
                dia,
                rs.getInt("sctr") == 1,
                rs.getString("rep_nombre"),
                rs.getString("rep_tipo_doc"),
                rs.getString("rep_doc"),
                rs.getString("rep_cargo"),
                rs.getString("partida_registral"),
                rs.getString("telefono"),
                rs.getString("correo"),
                rs.getString("correo_boletas"),
                rs.getBytes("logo"),
                rs.getBytes("firma"),
                rs.getString("rubro"),
                rs.getInt("firma_digital_activa") == 1,
                rs.getInt("huella_en_boleta") == 1);
    }
}
