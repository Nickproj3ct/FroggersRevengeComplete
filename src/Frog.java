import java.awt.*;
import java.awt.image.BufferedImage;

class Frog {
    // Match GamePanel constants
    static final int TILE = GamePanel.TILE;

    int x, y;
    int w = TILE - 4;
    int h = TILE - 4;

    boolean alive = true;

    private enum Dir { LEFT, RIGHT, UP, DOWN }
    private Dir facing = Dir.UP; // default = forward (toward goal)

    // Sprites
    private final BufferedImage sprFront; 
    private final BufferedImage sprLeft;  
    private final BufferedImage sprRight; 
    private final BufferedImage sprRear;  

    Frog(int startX, int startY) {
        this.x = startX;
        this.y = startY;

        // Load once
        sprFront = Assets.frog();       
        sprLeft  = Assets.frogLeft();    
        sprRight = Assets.frogRight();   
        sprRear  = Assets.frogRear();    
    }

    void update() {
        // no-op (kept for parity)
    }

    void nudge(int dx, int dy) {
        if (!alive) return;

        // Move by one tile step
        x += dx;
        y += dy;

        // Update facing based on last input
        if (dx < 0)      facing = Dir.LEFT;
        else if (dx > 0) facing = Dir.RIGHT;
        else if (dy > 0) facing = Dir.UP;     // forward toward goal
        else if (dy < 0) facing = Dir.DOWN;   // backwards toward player
    }

    void clampToBoard(int width) {
        if (x < 0) x = 0;
        if (x + w > width) x = width - w;
        if (y < GamePanel.TILE) y = GamePanel.TILE; // keep below HUD
        int maxY = GamePanel.HEIGHT - h;
        if (y > maxY) y = maxY;
    }

    Rectangle bounds() { return new Rectangle(x, y, w, h); }

    void draw(Graphics2D g) {
        BufferedImage use = switch (facing) {
            case LEFT  -> (sprLeft  != null ? sprLeft  : sprFront);
            case RIGHT -> (sprRight != null ? sprRight : sprFront);
            case UP    -> (sprFront  != null ? sprRear  : sprFront);
            case DOWN  -> (sprRear != null ? sprFront : sprRear);
        };

        if (use != null) {
            g.drawImage(use, x, y, w, h, null);
        } else {
            g.setColor(new Color(56, 196, 96));
            g.fillOval(x, y, w, h);
            g.setColor(Color.BLACK);
            g.drawOval(x, y, w, h);
        }
    }
public int facingDX() {
    // LEFT, RIGHT, UP, DOWN
    return switch (facing) {
        case LEFT  -> -1;
        case RIGHT ->  1;
        default    ->  0;
    };
}

public int facingDY() {
    return switch (facing) {
        case UP   -> -1;
        case DOWN ->  1;
        default   ->  0; 
    };
}

}
