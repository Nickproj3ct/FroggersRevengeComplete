import java.awt.*;
import java.awt.image.BufferedImage;

public class Bird {
    public double x, y;   // double so subpixel moves work
    public int w, h;
    public double speed;  // horizontal px per tick

    private final BufferedImage img;

    public Bird(double x, double y, int w, int h, double speed) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.speed = speed;
        this.img = Assets.bird();
    }

    public void update() {
        x += speed;
    }

    public void draw(Graphics2D g) {
        int ix = (int)Math.round(x);
        int iy = (int)Math.round(y);

        if (img != null) {
            if (speed >= 0) {
                // facing right (normal)
                g.drawImage(img, ix, iy, w, h, null);
            } else {
                // facing left (flip horizontally)
                g.drawImage(img, ix + w, iy, -w, h, null);
            }
        } else {
            // fallback placeholder
            g.setColor(new Color(255, 220, 120));
            g.fillOval(ix, iy, w, h);
            g.setColor(Color.BLACK);
            g.drawOval(ix, iy, w, h);
        }
    }

    public Rectangle bounds() {
        return new Rectangle((int)Math.round(x), (int)Math.round(y), w, h);
    }
}
