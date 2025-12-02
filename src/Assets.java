import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

// loader 
class Assets {
    private static final Map<String, BufferedImage> cache = new HashMap<>();

    private static BufferedImage load(String relPath) {
        if (cache.containsKey(relPath)) return cache.get(relPath);
        BufferedImage img = null;
        try {
            File f = new File("." + File.separator + relPath.replace("/", File.separator));
            if (f.exists()) img = ImageIO.read(f);
            if (img == null) {
                URL url = Assets.class.getResource("/" + relPath);
                if (url != null) img = ImageIO.read(url);
            }
        } catch (Exception ignored) {}
        cache.put(relPath, img);
        return img;
    }

    // Sprites entities
    static BufferedImage car()     { return load("assets/sprites/car.png"); }
    static BufferedImage carRed()  { return load("assets/sprites/carRed.png"); } 
    static BufferedImage truck()   { return load("assets/sprites/truck.png"); }
    static BufferedImage log()     { return load("assets/sprites/log.png"); }
    static BufferedImage bird()    { return load("assets/sprites/bird.png"); }
    static BufferedImage snake()   { return load("assets/sprites/snake.png"); }
    // Frog avatar
    static BufferedImage frog()      { return load("/assets/sprites/frog.png"); }
    static BufferedImage frogLeft()  { return load("/assets/sprites/frogLeft.png"); }
    static BufferedImage frogRight() { return load("/assets/sprites/frogRight.png"); }
    static BufferedImage frogRear()  { return load("/assets/sprites/frogRear.png"); }

    // Tiles
    static BufferedImage tileGrass()    { return load("assets/tiles/grass.png"); }
    static BufferedImage tileWater()    { return load("assets/tiles/water.png"); }
    static BufferedImage tileRoad()     { return load("assets/tiles/road.png"); }
    static BufferedImage tileGoal()     { return load("assets/tiles/goal.png"); }
    static BufferedImage tileStart()    { return load("assets/tiles/start.png"); }
    static BufferedImage tileRoadMark() { return load("assets/tiles/road_mark.png"); }
    static BufferedImage tileuglyGrass(){ return load("assets/tiles/uglyGrass.png"); }
}
