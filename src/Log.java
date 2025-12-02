import java.awt.*;
import java.awt.image.BufferedImage;

class Log {
    double x; final int y, w, h; final double speed;
    Log(int x, int y, int w, int h, double speed){ this.x=x; this.y=y; this.w=w; this.h=h; this.speed=speed; }
    void update(){ x += speed; }
    Rectangle bounds(){ return new Rectangle((int)x, y, w, h); }
    void draw(Graphics2D g){
        BufferedImage spr = Assets.log();
        if (spr != null) g.drawImage(spr, (int)x, y, w, h, null);
        else { int xi=(int)x; g.setColor(new Color(128,88,48)); g.fillRect(xi,y,w,h); g.setColor(Color.BLACK); g.drawRect(xi,y,w,h); }
    }
}
