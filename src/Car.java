import java.awt.*;
import java.awt.image.BufferedImage;

class Car {
    enum Kind { NORMAL, RED }

    double x;
    final int y, w, h;
    final double speed;
    final Kind kind;

    Car(int x, int y, int w, int h, double speed) {
        this(x, y, w, h, speed, Kind.NORMAL);
    }

    Car(int x, int y, int w, int h, double speed, Kind kind){
        this.x=x; this.y=y; this.w=w; this.h=h; this.speed=speed; this.kind = kind;
    }

    void update(){ x += speed; }

    Rectangle bounds(){ return new Rectangle((int)x, y, w, h); }

    void draw(Graphics2D g){
        BufferedImage spr = (kind == Kind.RED) ? Assets.carRed() : Assets.car();
        int xi = (int)x;
        if (spr != null) {
            // Base art: right; flip when moving left
            if (speed >= 0) g.drawImage(spr, xi, y, w, h, null);   // moving right: no flip
else            g.drawImage(spr, xi + w, y, -w, h, null); // moving left: flip

        } else {
            g.setColor(kind==Kind.RED ? new Color(210,48,48) : new Color(184,80,80));
            g.fillRect(xi,y,w,h);
            g.setColor(Color.BLACK); g.drawRect(xi,y,w,h);
        }
    }
}
