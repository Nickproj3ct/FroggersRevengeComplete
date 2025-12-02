import java.awt.*;
import java.awt.image.BufferedImage;

public class Snake {
    public double x, y;   // double so subpixel moves work
    public int w, h;
    public double speed;

    private final BufferedImage img;

    public Snake(double x, double y, int w, int h, double speed) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.speed = speed;
        this.img = Assets.snake(); 
    }

    public void update() {
        x += speed;
    }

    public void draw(Graphics2D g) {
        int ix = (int)Math.round(x);
        int iy = (int)Math.round(y);

        if (img != null) {
            if (speed >= 0) {
                // facing right
                g.drawImage(img, ix, iy, w, h, null);
            } else {
                // facing left (flip horizontally)
                g.drawImage(img, ix + w, iy, -w, h, null);
            }
        } else {
            g.setColor(new Color(80, 200, 120));
            g.fillRoundRect(ix, iy, w, h, 8, 8);
            g.setColor(Color.BLACK);
            g.drawRoundRect(ix, iy, w, h, 8, 8);
        }
    }

    public Rectangle bounds() {
        return new Rectangle((int)Math.round(x), (int)Math.round(y), w, h);
    }
}
