import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Writes the app icon as a 512x512 PNG (same design as the launcher / Reel icon). Usage: java MakeIcon.java out.png */
public class MakeIcon {
    public static void main(String[] a) throws Exception {
        int n = 512;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setPaint(new LinearGradientPaint(0, 0, n, n, new float[]{0, 1},
                new Color[]{new Color(0x3B5BDB), new Color(0x1C2647)}));
        g.fill(new RoundRectangle2D.Double(0, 0, n, n, n * 0.46, n * 0.46));
        double u = n / 24.0;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(6.5 * u, 10.5 * u, 11 * u, 8.5 * u, 2.4 * u, 2.4 * u));
        g.setStroke(new BasicStroke((float) (1.7 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D sh = new Path2D.Double();
        sh.moveTo(8.8 * u, 10.6 * u);
        sh.lineTo(8.8 * u, 8.4 * u);
        sh.curveTo(8.8 * u, 4.6 * u, 15.2 * u, 4.6 * u, 15.2 * u, 8.4 * u);
        sh.lineTo(15.2 * u, 10.6 * u);
        g.draw(sh);
        g.setColor(new Color(0x26346A));
        g.fill(new Ellipse2D.Double(11 * u, 13.4 * u, 2 * u, 2 * u));
        g.dispose();
        ImageIO.write(img, "png", new File(a[0]));
    }
}
