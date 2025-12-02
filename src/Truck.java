import java.awt.*;
import java.awt.image.BufferedImage;

class Truck {
    double x;
    final int y, w, h;
    final double speed;

    Truck(int x, int y, int w, int h, double speed) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.speed = speed;
    }

    void update() { x += speed; }

    Rectangle bounds() { return new Rectangle((int) x, y, w, h); }

    void draw(Graphics2D g) {
        BufferedImage spr = Assets.truck();
        int xi = (int) x;
        if (spr != null) {
            if (speed <= 0) {
                // Truck art faces left
                g.drawImage(spr, xi, y, w, h, null);
            } else {
                // Moving right: flip the art
                g.drawImage(spr, xi + w, y, -w, h, null);
            }
        } else {
            g.setColor(new Color(84, 132, 196));
            g.fillRect(xi, y, w, h);
            g.setColor(Color.BLACK);
            g.drawRect(xi, y, w, h);
        }
    }
}
