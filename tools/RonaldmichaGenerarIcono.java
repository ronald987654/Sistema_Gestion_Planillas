import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RonaldmichaGenerarIcono {

    public static void main(String[] args) throws IOException {
        Files.write(Path.of("src/main/resources/com/planillaperu/icono.png"), png(dibujar(256)));
        List<byte[]> imagenes = new ArrayList<>();
        int[] tamanos = {16, 24, 32, 48, 64, 128, 256};
        for (int t : tamanos) {
            imagenes.add(png(dibujar(t)));
        }
        ByteBuffer cabecera = ByteBuffer.allocate(6 + 16 * tamanos.length).order(ByteOrder.LITTLE_ENDIAN);
        cabecera.putShort((short) 0).putShort((short) 1).putShort((short) tamanos.length);
        int desplazamiento = 6 + 16 * tamanos.length;
        for (int i = 0; i < tamanos.length; i++) {
            int t = tamanos[i];
            cabecera.put((byte) (t >= 256 ? 0 : t)).put((byte) (t >= 256 ? 0 : t)).put((byte) 0).put((byte) 0);
            cabecera.putShort((short) 1).putShort((short) 32);
            cabecera.putInt(imagenes.get(i).length).putInt(desplazamiento);
            desplazamiento += imagenes.get(i).length;
        }
        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        ico.write(cabecera.array());
        for (byte[] img : imagenes) {
            ico.write(img);
        }
        Files.write(Path.of("src/main/packaging/planilla-peru.ico"), ico.toByteArray());
    }

    private static BufferedImage dibujar(int t) {
        BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double m = t * 0.04;
        g.setColor(new Color(0x0E5A47));
        g.fill(new RoundRectangle2D.Double(m, m, t - 2 * m, t - 2 * m, t * 0.22, t * 0.22));
        g.setColor(new Color(0xE6EEE9));
        g.setStroke(new BasicStroke((float) Math.max(1, t * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double x1 = t * 0.24;
        double x2 = t * 0.76;
        for (int i = 0; i < 3; i++) {
            double y = t * (0.30 + i * 0.13);
            g.drawLine((int) x1, (int) y, (int) (i == 2 ? t * 0.52 : x2), (int) y);
        }
        g.setColor(new Color(0x26A686));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (t * 0.30)));
        String s = "S/";
        int ancho = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (int) (t * 0.76 - ancho), (int) (t * 0.82));
        g.dispose();
        return img;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", salida);
        return salida.toByteArray();
    }
}
