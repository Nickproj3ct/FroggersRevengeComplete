import java.awt.*;

class Projectile {
    double x, y;          // top left
    double vx, vy;        // velocity per tick
    int w = 10, h = 10;   // bullet size
    boolean alive = true;

    Projectile(double x, double y, double vx, double vy) {
        this.x = x; this.y = y;
        this.vx = vx; this.vy = vy;
    }

    void update() {
        x += vx;
        y += vy;
    }

    boolean offscreen(int width, int height) {
        return (x < -w || y < GamePanel.TILE - h || x > width + w || y > height + h);
    }

    Rectangle bounds() { return new Rectangle((int)x, (int)y, w, h); }

    void draw(Graphics2D g) {
        g.setColor(new Color(255, 240, 120));
        g.fillOval((int)x, (int)y, w, h);
        g.setColor(new Color(180, 120, 30));
        g.drawOval((int)x, (int)y, w, h);
    }
}
