import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.text.AttributedString;
import javax.imageio.ImageIO;

/**
 * Renders a 20-second vertical Reel (1080x1920, 30fps) for the Lock Screen app and
 * pipes the raw frames straight into ffmpeg (H.264 MP4).
 *
 * Usage: java Reel.java <screenshots dir> <out.mp4> <ffmpeg.exe> <cover.jpg>
 */
public class Reel {
    static final int W = 1080, H = 1920, FPS = 30;
    static final double DUR = 20.0;
    static final double S = 1.12;                       // screenshot scale inside the phone
    static final int SW = (int) (640 * S), SH = (int) (1136 * S);

    static Font black, bold, medium, light, regular;
    static BufferedImage[] shot = new BufferedImage[5];  // lock, styles, notif, perms, pattern
    static BufferedImage screenBuf, screenMask;

    static final Color ACCENT = new Color(0xA8C0FF);
    static final Color BLUE = new Color(0x3B5BDB);

    public static void main(String[] a) throws Exception {
        String dir = a[0], out = a[1], ffmpeg = a[2], cover = a[3];
        black = font("Heebo-Black.ttf");
        bold = font("Heebo-Bold.ttf");
        medium = font("Heebo-Medium.ttf");
        light = font("Heebo-Light.ttf");
        regular = font("Heebo-Regular.ttf");
        String[] names = {"000756", "000808", "000813", "000817", "000821"};
        for (int i = 0; i < 5; i++) {
            BufferedImage src = ImageIO.read(new File(dir, "Screenshot_20261115-" + names[i] + ".png"));
            BufferedImage s = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = s.createGraphics();
            hq(g);
            g.drawImage(src, 0, 0, SW, SH, null);
            g.dispose();
            shot[i] = s;
        }
        screenBuf = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_ARGB);
        screenMask = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = screenMask.createGraphics();
        hq(mg);
        mg.setColor(Color.WHITE);
        mg.fill(new RoundRectangle2D.Double(0, 0, SW, SH, 132, 132));
        mg.dispose();

        ProcessBuilder pb = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", W + "x" + H, "-r", String.valueOf(FPS), "-i", "-",
                "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p",
                "-movflags", "+faststart", out);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        OutputStream os = new BufferedOutputStream(p.getOutputStream(), 1 << 22);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        int frames = (int) (DUR * FPS);
        int coverFrame = (int) (4.6 * FPS);
        for (int i = 0; i < frames; i++) {
            double t = i / (double) FPS;
            Graphics2D g = img.createGraphics();
            hq(g);
            render(g, t);
            g.dispose();
            os.write(data);
            if (i == coverFrame) ImageIO.write(img, "jpg", new File(cover));
            if (i % 60 == 0) System.out.println("frame " + i + "/" + frames);
        }
        os.close();
        System.out.println("ffmpeg exit " + p.waitFor());
    }

    // ------------------------------------------------------------------ timeline

    static void render(Graphics2D g, double t) {
        background(g, t);
        hook(g, t);

        headline(g, t, 3.0, 6.6, "מסך נעילה חדש", "נקי. מודרני. עם הרקע שלך.");
        headline(g, t, 6.6, 10.2, "8 סגנונות שעון", "8 צבעים · 3 גדלים · תצוגה חיה");
        headline(g, t, 10.2, 13.9, "קו, קוד או סיסמה", "ציור חלק עם טבעות עדינות");
        headline(g, t, 13.9, 17.0, "ההתראות שלך, על המסך", "החלק הצידה למחיקה · הקש לפתיחה");

        if (t >= 2.7 && t < 17.7) {
            double in = easeOutBack(clamp01((t - 2.75) / 0.95));
            double out = easeInCubic(clamp01((t - 16.95) / 0.7));
            double cy = 1175 + (1 - in) * 1500 - out * 160 + Math.sin(t * 1.3) * 7;
            double sc = 1 - 0.38 * out;
            phone(g, t, 540, cy, sc, (float) (1 - out));
        }
        notificationCard(g, t);
        endCard(g, t);
    }

    static void background(Graphics2D g, double t) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0x070A14));
        g.fillRect(0, 0, W, H);
        blob(g, 220 + Math.sin(t * 0.30) * 130, 420 + Math.cos(t * 0.25) * 160, 950, new Color(0x3B5BDB), 0.55f);
        blob(g, 880 + Math.sin(t * 0.22) * 110, 640 + Math.cos(t * 0.29) * 130, 720, new Color(0x8B5CF6), 0.38f);
        blob(g, 900 + Math.cos(t * 0.27) * 150, 1560 + Math.sin(t * 0.31) * 170, 860, new Color(0x14B8A6), 0.34f);
        blob(g, 150 + Math.cos(t * 0.2) * 90, 1750, 600, new Color(0x2563EB), 0.25f);
        // vignette
        g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, H / 2.0), 1250f,
                new float[]{0.55f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g.fillRect(0, 0, W, H);
    }

    static void blob(Graphics2D g, double x, double y, float r, Color c, float a) {
        Color c0 = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * a));
        Color c1 = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[]{0f, 1f}, new Color[]{c0, c1}));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /** 0–3s: "Bored of the stock lock screen?" word lines rising in. */
    static void hook(Graphics2D g, double t) {
        if (t > 3.1) return;
        double exit = easeInCubic(clamp01((t - 2.45) / 0.55));
        double dy = -exit * 140;
        float fade = (float) (1 - exit);
        String[] lines = {"נמאס לך", "ממסך הנעילה", "המשעמם?"};
        Font[] fonts = {light.deriveFont(104f), black.deriveFont(150f), black.deriveFont(150f)};
        double[] ys = {800, 980, 1150};
        for (int i = 0; i < 3; i++) {
            double p = easeOutCubic(clamp01((t - 0.15 - i * 0.38) / 0.65));
            float al = (float) (p * fade);
            if (al <= 0) continue;
            double y = ys[i] + (1 - p) * 70 + dy;
            Paint paint = Color.WHITE;
            if (i == 2) paint = new LinearGradientPaint(260, 0, 820, 0, new float[]{0, 0.5f, 1},
                    new Color[]{new Color(0x99F6E4), ACCENT, new Color(0xC4B5FD)});
            text(g, lines[i], fonts[i], paint, 540, y, al);
        }
    }

    static void headline(Graphics2D g, double t, double start, double end, String title, String sub) {
        if (t < start || t > end) return;
        double in = easeOutCubic(clamp01((t - start - 0.05) / 0.5));
        double out = easeInCubic(clamp01((t - (end - 0.35)) / 0.35));
        float al = (float) (in * (1 - out));
        double dy = (1 - in) * 50 - out * 30;
        text(g, title, black.deriveFont(104f), Color.WHITE, 540, 300 + dy, al);
        double sin = easeOutCubic(clamp01((t - start - 0.2) / 0.5));
        text(g, sub, light.deriveFont(54f), ACCENT, 540, 395 + dy + (1 - sin) * 30, (float) (sin * (1 - out)));
    }

    // ------------------------------------------------------------------ phone

    static void phone(Graphics2D g, double t, double cx, double cy, double scale, float alpha) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.scale(scale, scale);
        double bez = 20, bw = SW + 2 * bez, bh = SH + 2 * bez;
        double x0 = -bw / 2, y0 = -bh / 2;

        // soft shadow
        for (int k = 14; k >= 1; k--) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.035f * alpha));
            g.setColor(Color.BLACK);
            double e = k * 5;
            g.fill(new RoundRectangle2D.Double(x0 - e, y0 - e + 40, bw + 2 * e, bh + 2 * e, 170 + e, 170 + e));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        // body + side buttons
        g.setColor(new Color(0x2A2F3A));
        g.fill(new RoundRectangle2D.Double(bw / 2 - 4, y0 + 260, 12, 110, 8, 8));
        g.fill(new RoundRectangle2D.Double(bw / 2 - 4, y0 + 400, 12, 170, 8, 8));
        g.setPaint(new LinearGradientPaint((float) x0, (float) y0, (float) (x0 + bw), (float) (y0 + bh),
                new float[]{0, 1}, new Color[]{new Color(0x22262F), new Color(0x0A0C10)}));
        RoundRectangle2D body = new RoundRectangle2D.Double(x0, y0, bw, bh, 170, 170);
        g.fill(body);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(0x4A5163));
        g.draw(body);

        // screen content (rendered off-screen, then masked for smooth rounded corners)
        drawScreen(t);
        g.drawImage(screenBuf, (int) (x0 + bez), (int) (y0 + bez), null);
        g.setTransform(old);
        g.setComposite(AlphaComposite.SrcOver);
    }

    static final double[] STARTS = {2.7, 6.6, 10.2, 13.9};
    static final int[] SHOTS = {0, 1, 4, 2};

    static void drawScreen(double t) {
        Graphics2D s = screenBuf.createGraphics();
        hq(s);
        s.setComposite(AlphaComposite.Clear);
        s.fillRect(0, 0, SW, SH);
        s.setComposite(AlphaComposite.SrcOver);

        int k = 0;
        for (int i = 0; i < STARTS.length; i++) if (t >= STARTS[i]) k = i;
        double tr = t - STARTS[k];
        if (k > 0 && tr < 0.6) {
            double p = easeInOutCubic(tr / 0.6);
            shotAt(s, SHOTS[k - 1], -p * SH * 0.16, (float) (1 - p));
            overlays(s, SHOTS[k - 1], t, -p * SH * 0.16, (float) (1 - p));
            shotAt(s, SHOTS[k], (1 - p) * SH * 0.12, (float) p);
            overlays(s, SHOTS[k], t, (1 - p) * SH * 0.12, (float) p);
        } else {
            shotAt(s, SHOTS[k], 0, 1f);
            overlays(s, SHOTS[k], t, 0, 1f);
        }
        s.setComposite(AlphaComposite.DstIn);
        s.drawImage(screenMask, 0, 0, null);
        s.dispose();
    }

    static void shotAt(Graphics2D s, int idx, double dy, float alpha) {
        if (alpha <= 0) return;
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
        s.drawImage(shot[idx], 0, (int) dy, null);
        s.setComposite(AlphaComposite.SrcOver);
    }

    /** Animated touches drawn on top of each screenshot (coordinates are screenshot pixels * S). */
    static void overlays(Graphics2D s, int idx, double t, double dy, float alpha) {
        if (alpha <= 0.02f) return;
        AffineTransform old = s.getTransform();
        s.translate(0, dy);
        s.scale(S, S);
        if (idx == 0) {
            // a swipe up on the lock screen
            double p = clamp01((t - 5.2) / 1.1);
            if (p > 0 && p < 1) {
                double e = easeInOutCubic(p);
                float fa = (float) (Math.sin(Math.PI * p)) * alpha;
                finger(s, 320, 960 - e * 520, fa);
            }
        } else if (idx == 1) {
            tap(s, 125, 735, t - 7.9, alpha);   // "analog" style chip
            tap(s, 125, 878, t - 8.9, alpha);   // "pink" colour chip
        } else if (idx == 4) {
            pattern(s, t, alpha);
        }
        s.setTransform(old);
    }

    static void tap(Graphics2D s, double x, double y, double lt, float alpha) {
        if (lt < 0 || lt > 0.8) return;
        double p = lt / 0.8;
        float a = (float) (1 - p) * alpha;
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * 0.35f));
        s.setColor(Color.BLACK);
        double r = 30 + p * 60;
        s.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        finger(s, x, y, (float) (lt < 0.5 ? 1 : (0.8 - lt) / 0.3) * alpha);
    }

    static void finger(Graphics2D s, double x, double y, float a) {
        if (a <= 0) return;
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a) * 0.5f));
        s.setColor(Color.WHITE);
        s.fill(new Ellipse2D.Double(x - 30, y - 30, 60, 60));
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a) * 0.9f));
        s.setStroke(new BasicStroke(3f));
        s.setColor(new Color(0x9AA3B2));
        s.draw(new Ellipse2D.Double(x - 30, y - 30, 60, 60));
        s.setComposite(AlphaComposite.SrcOver);
    }

    /** Draws the unlock pattern Z (0-1-2-4-6-7-8) with small rings, like the app does. */
    static void pattern(Graphics2D s, double t, float alpha) {
        double[] dx = {132, 319, 507}, dyy = {460, 648, 836};
        int[] seq = {0, 1, 2, 4, 6, 7, 8};
        double start = 10.95, dur = 1.8;
        double p = clamp01((t - start) / dur);
        if (p <= 0) return;
        boolean done = t > start + dur + 0.15;
        Color col = done ? BLUE : new Color(0x1B1D22);
        double segs = seq.length - 1;
        double pos = easeInOutCubic(p) * segs;
        int reached = (int) Math.floor(pos);

        Path2D path = new Path2D.Double();
        path.moveTo(dx[seq[0] % 3], dyy[seq[0] / 3]);
        for (int i = 1; i <= reached && i < seq.length; i++) path.lineTo(dx[seq[i] % 3], dyy[seq[i] / 3]);
        double tipX = dx[seq[Math.min(reached, seq.length - 1)] % 3], tipY = dyy[seq[Math.min(reached, seq.length - 1)] / 3];
        if (reached < seq.length - 1) {
            double f = pos - reached;
            int a0 = seq[reached], a1 = seq[reached + 1];
            tipX = dx[a0 % 3] + (dx[a1 % 3] - dx[a0 % 3]) * f;
            tipY = dyy[a0 / 3] + (dyy[a1 / 3] - dyy[a0 / 3]) * f;
            path.lineTo(tipX, tipY);
        }
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f * alpha));
        s.setColor(col);
        s.setStroke(new BasicStroke(5.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        s.draw(path);

        for (int i = 0; i <= Math.min(reached, seq.length - 1); i++) {
            double reachT = start + dur * invEaseApprox(i / segs);
            double rp = smooth(clamp01((t - reachT) / 0.3));
            double x = dx[seq[i] % 3], y = dyy[seq[i] / 3];
            double r = 12 + (23 - 12) * rp;
            s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) rp * alpha));
            s.setStroke(new BasicStroke(3.6f));
            s.setColor(col);
            s.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            s.fill(new Ellipse2D.Double(x - 12, y - 12, 24, 24));
        }
        if (!done) finger(s, tipX, tipY, alpha * (float) Math.min(1, (1 - p) * 8 + 0.0001));
        s.setComposite(AlphaComposite.SrcOver);
    }

    /** Rough inverse of easeInOutCubic, to know when the animation reaches each dot. */
    static double invEaseApprox(double y) {
        double lo = 0, hi = 1;
        for (int i = 0; i < 30; i++) {
            double m = (lo + hi) / 2;
            if (easeInOutCubic(m) < y) lo = m; else hi = m;
        }
        return lo;
    }

    // ------------------------------------------------------------------ floating notification card

    static void notificationCard(Graphics2D g, double t) {
        double start = 14.3, swipe = 15.95;
        if (t < start || t > swipe + 0.6) return;
        double in = easeOutBack(clamp01((t - start) / 0.65));
        double away = easeInCubic(clamp01((t - swipe) / 0.45));
        double off = (1 - in) * 1150 - away * 1250;
        float al = (float) (clamp01((t - start) / 0.25) * (1 - away));
        double cw = 900, ch = 196, x = 540 - cw / 2 + off, y = 1430;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al * 0.35f));
        g.setColor(Color.BLACK);
        g.fill(new RoundRectangle2D.Double(x + 6, y + 18, cw, ch, 96, 96));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al * 0.88f));
        g.setColor(new Color(0x12161D));
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, cw, ch, 96, 96);
        g.fill(card);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al * 0.18f));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(card);

        // icon on the right (RTL)
        double ix = x + cw - 40 - 92, iy = y + 34;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al));
        appIcon(g, ix, iy, 92);
        double right = ix - 28;
        text(g, "הודעות  ·  עכשיו", regular.deriveFont(33f), new Color(255, 255, 255, 180), right, y + 66, al, true);
        text(g, "דנה", bold.deriveFont(44f), Color.WHITE, right, y + 120, al, true);
        text(g, "נפגשים ב־8 בקפה?", regular.deriveFont(39f), new Color(255, 255, 255, 220), right, y + 168, al, true);
        // count badge (left)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al * 0.25f));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x + 40, y + 36, 54, 40, 40, 40));
        text(g, "2", bold.deriveFont(28f), Color.WHITE, x + 67, y + 66, al);

        if (t > swipe - 0.25) {
            double fp = clamp01((t - (swipe - 0.25)) / 0.7);
            float fa = (float) Math.sin(Math.PI * fp);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fa * 0.5f));
            g.setColor(Color.WHITE);
            double fx = 640 + off, fy = y + ch / 2;
            g.fill(new Ellipse2D.Double(fx - 38, fy - 38, 76, 76));
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    static void appIcon(Graphics2D g, double x, double y, double size) {
        Composite c = g.getComposite();
        g.setPaint(new LinearGradientPaint((float) x, (float) y, (float) (x + size), (float) (y + size),
                new float[]{0, 1}, new Color[]{new Color(0x3B5BDB), new Color(0x1C2647)}));
        g.fill(new RoundRectangle2D.Double(x, y, size, size, size * 0.46, size * 0.46));
        g.setColor(Color.WHITE);
        double u = size / 24.0;
        // lock body
        g.fill(new RoundRectangle2D.Double(x + 6.5 * u, y + 10.5 * u, 11 * u, 8.5 * u, 2.4 * u, 2.4 * u));
        // shackle
        g.setStroke(new BasicStroke((float) (1.7 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D sh = new Path2D.Double();
        sh.moveTo(x + 8.8 * u, y + 10.6 * u);
        sh.lineTo(x + 8.8 * u, y + 8.4 * u);
        sh.curveTo(x + 8.8 * u, y + 4.6 * u, x + 15.2 * u, y + 4.6 * u, x + 15.2 * u, y + 8.4 * u);
        sh.lineTo(x + 15.2 * u, y + 10.6 * u);
        g.draw(sh);
        g.setColor(new Color(0x26346A));
        g.fill(new Ellipse2D.Double(x + 11 * u, y + 13.4 * u, 2 * u, 2 * u));
        g.setComposite(c);
    }

    // ------------------------------------------------------------------ end card

    static void endCard(Graphics2D g, double t) {
        double start = 17.25;
        if (t < start) return;
        double ip = easeOutBack(clamp01((t - start) / 0.7));
        float ia = (float) clamp01((t - start) / 0.3);
        double size = 300 * (0.6 + 0.4 * ip);
        // glow
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ia));
        blob(g, 540, 800, 420, new Color(0x3B5BDB), 0.45f);
        appIcon(g, 540 - size / 2, 800 - size / 2, size);

        double np = easeOutCubic(clamp01((t - start - 0.3) / 0.55));
        text(g, "מסך נעילה", black.deriveFont(150f), Color.WHITE, 540, 1150 + (1 - np) * 50, (float) np);
        double tp = easeOutCubic(clamp01((t - start - 0.6) / 0.55));
        text(g, "החלק למעלה.", light.deriveFont(76f), ACCENT, 540, 1260 + (1 - tp) * 40, (float) tp);
        double sp = easeOutCubic(clamp01((t - start - 0.9) / 0.55));
        text(g, "לאנדרואיד 12 ומעלה", regular.deriveFont(44f), new Color(255, 255, 255, 150), 540, 1360, (float) sp);

        // bobbing chevron
        double cp = clamp01((t - start - 1.0) / 0.4);
        if (cp > 0) {
            double by = 1520 - Math.abs(Math.sin((t - start) * 2.6)) * 22;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) cp * 0.85f));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D ch = new Path2D.Double();
            ch.moveTo(500, by + 22);
            ch.lineTo(540, by - 16);
            ch.lineTo(580, by + 22);
            g.draw(ch);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    // ------------------------------------------------------------------ text & helpers

    static void text(Graphics2D g, String s, Font f, Paint p, double cx, double baseline, float alpha) {
        text(g, s, f, p, cx, baseline, alpha, false);
    }

    /** Hebrew-aware text (RTL run direction). Centered on x, or right-aligned at x when alignRight. */
    static void text(Graphics2D g, String s, Font f, Paint p, double x, double baseline, float alpha, boolean alignRight) {
        if (alpha <= 0.003f) return;
        AttributedString as = new AttributedString(s);
        as.addAttribute(TextAttribute.FONT, f);
        as.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        TextLayout tl = new TextLayout(as.getIterator(), g.getFontRenderContext());
        double maxW = W - 110;
        if (!alignRight && tl.getAdvance() > maxW) { // shrink to fit the frame width
            Font smaller = f.deriveFont((float) (f.getSize2D() * maxW / tl.getAdvance()));
            as.addAttribute(TextAttribute.FONT, smaller);
            tl = new TextLayout(as.getIterator(), g.getFontRenderContext());
        }
        double left = alignRight ? x - tl.getAdvance() : x - tl.getAdvance() / 2;
        Shape shape = tl.getOutline(AffineTransform.getTranslateInstance(left, baseline));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
        g.setPaint(p);
        g.fill(shape);
        g.setComposite(AlphaComposite.SrcOver);
    }

    static Font font(String file) throws Exception {
        String[] dirs = {"C:\\Windows\\Fonts", System.getenv("LOCALAPPDATA") + "\\Microsoft\\Windows\\Fonts"};
        for (String d : dirs) {
            File f = new File(d, file);
            if (f.exists()) return Font.createFont(Font.TRUETYPE_FONT, f);
        }
        System.out.println("font not found, falling back: " + file);
        return new Font("Arial", Font.PLAIN, 12);
    }

    static void hq(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
    static double smooth(double x) { return x * x * (3 - 2 * x); }
    static double easeOutCubic(double x) { return 1 - Math.pow(1 - x, 3); }
    static double easeInCubic(double x) { return x * x * x; }
    static double easeInOutCubic(double x) { return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; }
    static double easeOutBack(double x) {
        double c1 = 1.3, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }
}
