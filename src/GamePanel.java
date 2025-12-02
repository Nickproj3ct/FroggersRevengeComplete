import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.awt.image.BufferedImage;

class GamePanel extends JPanel implements ActionListener, KeyListener {
    // Board constants
    static final int TILE = 40;
    static final int COLS = 16;
    static final int ROWS = 18;
    static final int WIDTH = COLS * TILE;
    static final int HEIGHT = ROWS * TILE;

    // Listener back to App (menu/scoreboard)
    interface GameListener {
        void onGameOver(int finalScore, String playerName);
        void onBackToMenu();
    }

    private final String playerName;
    private final GameListener listener;

    // Actor sizes 
    private static final int CAR_W    = TILE * 2;
    private static final int CAR_H    = TILE - 10;
    private static final int TRUCK_W  = TILE * 3;
    private static final int TRUCK_H  = TILE - 8;
    private static final int LOG_W    = TILE * 3;
    private static final int LOG_H    = TILE - 12;
    private static final int BIRD_W   = TILE - 8;
    private static final int BIRD_H   = TILE - 14;
    private static final int SNAKE_W  = (int)(TILE * 1.6);
    private static final int SNAKE_H  = TILE - 12;

    // Projectiles & scoring (Level 2/3)
    private final java.util.List<Projectile> shots = new ArrayList<>();
    private long lastShotTick = -1000;
    private static final int KILL_SCORE = 25;

    // Hit effects (simple puff particles)
    private static class Puff {
        double x,y, r, vr;
        int life;
        Puff(double x,double y){ this.x=x; this.y=y; this.r=2; this.vr=0.9; this.life=16; }
        boolean update(){ r += vr; life--; return life>0; }
        void draw(Graphics2D g){
            int alpha = Math.max(0, Math.min(255, life*12));
            g.setColor(new Color(255,255,200, alpha));
            int d=(int)(r*2);
            g.fillOval((int)(x-r),(int)(y-r), d,d);
        }
    }
    private final java.util.List<Puff> puffs = new ArrayList<>();

    // Combo state 
    private int combo = 0;
    private int comboTimer = 0;
    private static final int COMBO_WINDOW_TICKS = 60; // ~1 sec at 60fps

    // Update & state
    private final javax.swing.Timer timer;
    private final Frog frog;

    // Pools
    private final java.util.List<Car> cars = new ArrayList<>();
    private final java.util.List<Truck> trucks = new ArrayList<>();
    private final java.util.List<Log> logs = new ArrayList<>();
    private final java.util.List<Bird> birds = new ArrayList<>();
    private final java.util.List<Snake> snakes = new ArrayList<>();
    private final java.util.List<Lane> roadLanes = new ArrayList<>();
    private final java.util.List<Lane> riverLanes = new ArrayList<>();
    private final java.util.List<Lane> critterLanes = new ArrayList<>();

    private final Random rng = new Random();

    // Densities (used in L1 only)
    private double trafficScale = 1.6;
    private double logDensityScale = 1.4;
    private double critterDensityScale = 1.5;

    // Spacing
    private final int VEHICLE_MIN_GAP = TILE;     // desired min gap on a road lane
    private final int CRITTER_MIN_GAP = TILE/2;

    // Levels (game scenes)
    private int level = 1;                // numeric level indicator (scene)
    private boolean levelTwo = false;     // Level 2 (critter survival, kill 30)
    private boolean levelThree = false;   // Level 3 (traffic, reach top)
    private boolean levelFour = false;    // Level 4 (river/logs)

    private int lives = 3;
    private int ticks = 0;
    private boolean paused = false;
    private boolean showHelp = true;

    private int score = 0;
    private int bestRowY;

    // Player progression (fire-mode unlocks)
    private int playerLevel = 1;                    // starts at level 1
    private static final int LEVEL2_THRESHOLD = 1000;
    private static final int LEVEL3_THRESHOLD = 3000;
    private int levelUpFlashTicks = 0;              // overlay timer
    private int justLeveledTo = 1;                  // holds new level for overlay
    private static final int LEVELUP_FLASH_FRAMES = 45;

    // L2 kill target (no timer)
    private static final int L2_KILL_TARGET = 30;
    private int l2KillCount = 0;

    // Critter population targets for L2
    private static final int CRITTER_MIN_ONSCREEN = 15;
    private static final int CRITTER_MAX_ONSCREEN = 20;

    // Level 2 spawn/motion settings
    private static final double L2_BASE_SPEED   = 2.0; // base horizontal speed for critters
    private static final int    L2_SPAWN_EVERY  = 12;  // spawn cadence base
    private static final double L2_SPAWN_CHANCE = 0.98;
    private static final double L2_DOUBLE_CHANCE= 0.20;
    private static final double V_SPEED         = 0.6; // vertical wiggle
    private static final double FLIP_CHANCE     = 0.01;// occasional horizontal flip

    // Level 3 vehicles settings
    private static final double L3_BASE_SPEED   = 1.65;
    private static final int    L3_SPAWN_EVERY  = 26;
    private static final double L3_SPAWN_CHANCE = 0.55;

    // Level 4 logs settings
    private static final int    L4_LOG_SPAWN_EVERY = 32;
    private static final double L4_LOG_BASE_SPEED  = 1.55;
    private static final double L4_SPAWN_CHANCE    = 0.60;

    // Transition overlay ("Next Level")
    private int transitionTicks = 0;
    private int nextLevelPending= 0;
    private static final int TRANSITION_FRAMES = 60; // ~1 sec

    // L2 vertical wiggle bookkeeping
    private final IdentityHashMap<Object, Lane> ownerLane = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Integer> vdir = new IdentityHashMap<>();

    // Firing modes and directional shooting
    private enum FireMode { SINGLE, SPREAD, NOVA }
    private FireMode fireMode = FireMode.SINGLE; // current selection

    // Constructor & lifecycle 
    GamePanel(String playerName, GameListener listener) {
        this.playerName = playerName;
        this.listener = listener;

        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        setBackground(new Color(22,18,32));
        addKeyListener(this);

        frog = new Frog(WIDTH/2 - TILE/2, (ROWS-1)*TILE + 4);
        bestRowY = frog.y;

        timer = new javax.swing.Timer(16, this); 
        timer.setCoalesce(true);
        timer.setInitialDelay(0);
        timer.start();
        setupLevel();
    }

    void start() { if (!timer.isRunning()) timer.start(); }
    void stop()  { if  (timer.isRunning())  timer.stop(); }
    @Override public void addNotify() { super.addNotify(); requestFocusInWindow(); }

    private double vary(double base, double factor) {
        double mult = 1.0 + (rng.nextDouble()*2 - 1) * factor;
        return base * mult;
    }
    private static double clamp(double v,double a,double b){ return Math.max(a, Math.min(b, v)); }

    // Level setup
    private void setupLevel() {
        cars.clear(); trucks.clear(); logs.clear(); birds.clear(); snakes.clear();
        roadLanes.clear(); riverLanes.clear(); critterLanes.clear();
        shots.clear(); puffs.clear();
        ownerLane.clear(); vdir.clear();
        combo = 0; comboTimer = 0;

        levelTwo   = (level == 2);
        levelThree = (level == 3);
        levelFour  = (level == 4);

        if (levelTwo) {
            // Level 2: Critter survival (kill 30, no timer)
            l2KillCount = 0;
            for (int r = 3; r <= ROWS - 3; r++) {
                boolean right = (r % 2 == 0);
                critterLanes.add(new Lane(r * TILE, right ? L2_BASE_SPEED : -L2_BASE_SPEED, L2_SPAWN_EVERY));
            }
            resetFrog(false);
            repaint();
            return;
        }

        if (levelThree) {
            // Level 3: Roads ONLY (reach top to win)
            for (int r = 3; r <= ROWS - 3; r++) {
                boolean right = ((r % 2) == 1);
                double baseSpeed = L3_BASE_SPEED + ((r % 3) * 0.12);
                int spawnEvery = Math.max(16, L3_SPAWN_EVERY - ((r % 3) * 2));
                roadLanes.add(new Lane(r * TILE, right ? baseSpeed : -baseSpeed, spawnEvery));
            }
            // Seed some vehicles present initially
            for (Lane lane : roadLanes) {
                int target = 1 + (rng.nextDouble() < 0.6 ? 1 : 0);
                int seeded = 0, attempts = 0;
                while (seeded < target && attempts++ < 40) {
                    int y = lane.y + 5;
                    boolean truck = rng.nextDouble() < 0.35;
                    int w = truck ? TRUCK_W : CAR_W;
                    int h = truck ? TRUCK_H : CAR_H;
                    int x = rng.nextInt(WIDTH - w);
                    double sp = truck
                            ? vary(Math.copySign(Math.abs(lane.speed*0.9), lane.speed), 0.25)
                            : vary(lane.speed, 0.25);
                    if (laneHasSpaceFor(lane, x, y, w, h, VEHICLE_MIN_GAP)) {
                        if (truck) trucks.add(new Truck(x, y, w, h, sp));
                        else {
                            Car.Kind kind = (rng.nextDouble() < 0.5) ? Car.Kind.RED : Car.Kind.NORMAL;
                            cars.add(new Car(x, y, w, h, sp, kind));
                        }
                        seeded++;
                    }
                }
            }
            resetFrog(false);
            repaint();
            return;
        }

        if (levelFour) {
            // Level 4: river rows with uglyGrass borders
            for (int r = 3; r <= ROWS - 3; r++) {
                boolean right = ((r % 2) == 0);
                double sp = L4_LOG_BASE_SPEED + ((r % 3) * 0.10);
                riverLanes.add(new Lane(r*TILE, right ? sp : -sp, L4_LOG_SPAWN_EVERY));
            }
            for (Lane lane : riverLanes) {
                int toPlace = (rng.nextDouble() < 0.5) ? 1 : 0;
                int attempts = 0;
                while (toPlace > 0 && attempts++ < 20) {
                    int y = lane.y + 6, x = rng.nextInt(WIDTH-LOG_W);
                    if (logLaneHasSpaceFor(lane, x, y, LOG_W, LOG_H, TILE/2)) {
                        logs.add(new Log(x, y, LOG_W, LOG_H, lane.speed));
                        toPlace--;
                    }
                }
            }
            resetFrog(false);
            repaint();
            return;
        }

        // LEVEL 1
        // River lanes: rows 2..4 (alternating dir)
        int[] riverRows = {4,3,2};
        for (int i = 0; i < riverRows.length; i++) {
            int row = riverRows[i];
            boolean right = (i % 2 == 0);
            double baseSpeed = 1.25 + 0.2 * i + (level-1) * 0.15;
            int spawnEvery = Math.max(48 - level*2 - i*2, 18);
            spawnEvery = (int)Math.round(spawnEvery * logDensityScale);
            riverLanes.add(new Lane(row*TILE, right ? baseSpeed : -baseSpeed, spawnEvery));
        }

        // Road lanes (top-bottom): rows 6..11
        // Directions fixed: lanes 1,3,5 → right (+); lanes 2,4,6 → left (-)
        int[] roadRowsTopDown = {6,7,8,9,10,11};
        for (int i = 0; i < roadRowsTopDown.length; i++) {
            int row = roadRowsTopDown[i];
            boolean dirRight = ((i % 2) == 0);
            double baseSpeed = 1.5 + 0.12 * i + (level-1) * 0.22;
            int spawnEvery = Math.max(34 - level*2 - i*2, 14);
            spawnEvery = (int)Math.round(spawnEvery * trafficScale);
            roadLanes.add(new Lane(row*TILE, dirRight ? baseSpeed : -baseSpeed, spawnEvery));
        }

        // Critter lanes (bottom): rows 14..16 (alternating dir)
        int[] critterRows = {16,15,14};
        for (int i = 0; i < critterRows.length; i++) {
            int row = critterRows[i];
            boolean right = (i % 2 == 0);
            double baseSpeed = 1.4 + 0.2 * i + (level-1) * 0.12;
            int spawnEvery = Math.max(40 - level*2 - i, 16);
            spawnEvery = (int)Math.round(clamp(critterDensityScale,0.8,2.0) * spawnEvery);
            critterLanes.add(new Lane(row*TILE, right ? baseSpeed : -baseSpeed, spawnEvery));
        }

        // Seed road vehicles (car, carRed, truck)
        for (Lane lane : roadLanes) {
            int seeded = 0, attempts = 0;
            while (seeded < 2 && attempts++ < 40) {
                int y = lane.y + 5;
                boolean truck = rng.nextDouble() < 0.33;
                int w = truck ? TRUCK_W : CAR_W;
                int h = truck ? TRUCK_H : CAR_H;
                int x = rng.nextInt(WIDTH - w);
                double sp = truck
                        ? vary(Math.copySign(Math.abs(lane.speed*0.85), lane.speed), 0.25)
                        : vary(lane.speed, 0.25);

                if (laneHasSpaceFor(lane, x, y, w, h, VEHICLE_MIN_GAP)) {
                    if (truck) trucks.add(new Truck(x, y, w, h, sp));
                    else {
                        Car.Kind kind = (rng.nextDouble() < 0.5) ? Car.Kind.RED : Car.Kind.NORMAL;
                        cars.add(new Car(x, y, w, h, sp, kind));
                    }
                    seeded++;
                }
            }
        }

        // Seed logs
        for (Lane lane : riverLanes) {
            int toPlace = 2, attempts = 0;
            while (toPlace > 0 && attempts++ < 20) {
                int y = lane.y + 6, x = rng.nextInt(WIDTH-LOG_W);
                if (logLaneHasSpaceFor(lane, x, y, LOG_W, LOG_H, TILE/2)) {
                    logs.add(new Log(x, y, LOG_W, LOG_H, lane.speed));
                    toPlace--;
                }
            }
        }

        // Seed critters (level 1 bottom)
        for (Lane lane : critterLanes) {
            int toPlace = 2, attempts = 0;
            while (toPlace > 0 && attempts++ < 20) {
                boolean bird = rng.nextBoolean();
                if (bird) {
                    int y = lane.y + 7, x = rng.nextInt(WIDTH-BIRD_W);
                    if (critterLaneHasSpaceFor(lane, x, y, BIRD_W, BIRD_H, CRITTER_MIN_GAP)) {
                        Bird b = new Bird(x, y, BIRD_W, BIRD_H, lane.speed*1.1);
                        birds.add(b);
                    }
                } else {
                    int y = lane.y + 6, x = rng.nextInt(WIDTH-SNAKE_W);
                    if (critterLaneHasSpaceFor(lane, x, y, SNAKE_W, SNAKE_H, CRITTER_MIN_GAP)) {
                        Snake s = new Snake(x, y, SNAKE_W, SNAKE_H, lane.speed*0.9);
                        snakes.add(s);
                    }
                }
                toPlace--;
            }
        }

        resetFrog(false);
    }

    private void resetFrog(boolean keepRowBonus) {
        if (!keepRowBonus) bestRowY = (ROWS-1)*TILE + 4;
        frog.x = WIDTH/2 - TILE/2;
        frog.y = (ROWS-1)*TILE + 4;
        frog.alive = true;
        repaint();
    }

    private void startTransitionTo(int nextLvl) {
        nextLevelPending = nextLvl;
        transitionTicks   = TRANSITION_FRAMES;
    }

    // Main update
    @Override public void actionPerformed(ActionEvent e) {
        if (transitionTicks > 0) {
            transitionTicks--;
            if (transitionTicks == 0 && nextLevelPending != 0) {
                level = nextLevelPending;
                nextLevelPending = 0;
                setupLevel();
            }
            repaint();
            return;
        }

        if (paused) { repaint(); return; }
        ticks++;

        // overlay tick (level-up)
        if (levelUpFlashTicks > 0) levelUpFlashTicks--;

        // combo window timer
        if (combo > 0) {
            comboTimer--;
            if (comboTimer <= 0) combo = 0;
        }

        if (!levelTwo && !levelThree && !levelFour) {
            // LEVEL 1 
            for (Lane lane : roadLanes) {
                if (ticks % lane.spawnEveryTicks == 0) {
                    boolean truck = rng.nextDouble() < 0.33;
                    int y = lane.y + 5;

                    if (truck) {
                        int w=TRUCK_W, h=TRUCK_H;
                        boolean right = lane.speed > 0;
                        int x = right ? -w - 12 : WIDTH + 12;
                        double sp = vary(Math.copySign(Math.abs(lane.speed*0.85), lane.speed), 0.25);
                        int enterX = right ? -w : WIDTH;
                        if (laneHasSpaceFor(lane, enterX, y, w, h, VEHICLE_MIN_GAP))
                            trucks.add(new Truck(x, y, w, h, sp));
                    } else {
                        int w=CAR_W, h=CAR_H;
                        boolean right = lane.speed > 0;
                        int x = right ? -w - 12 : WIDTH + 12;
                        Car.Kind kind = (rng.nextDouble()<0.5) ? Car.Kind.RED : Car.Kind.NORMAL;
                        double sp = vary(lane.speed, 0.25);
                        int enterX = right ? -w : WIDTH;
                        if (laneHasSpaceFor(lane, enterX, y, w, h, VEHICLE_MIN_GAP))
                            cars.add(new Car(x, y, w, h, sp, kind));
                    }
                }
            }

            for (Lane lane : riverLanes) {
                if (ticks % lane.spawnEveryTicks == 0 && rng.nextDouble() < 0.75) {
                    int y = lane.y + 6;
                    int x = lane.speed > 0 ? -LOG_W - 10 : WIDTH + 10;
                    if (logLaneHasSpaceFor(lane, x, y, LOG_W, LOG_H, TILE/3))
                        logs.add(new Log(x, y, LOG_W, LOG_H, lane.speed));
                }
            }

            for (Lane lane : critterLanes) {
                if (ticks % lane.spawnEveryTicks == 0 && rng.nextDouble() < 0.65) {
                    boolean bird = rng.nextBoolean();
                    if (bird) {
                        int y = lane.y + 7;
                        int x = lane.speed > 0 ? -BIRD_W - 10 : WIDTH + 10;
                        if (critterLaneHasSpaceFor(lane, x, y, BIRD_W, BIRD_H, CRITTER_MIN_GAP)) {
                            Bird b = new Bird(x, y, BIRD_W, BIRD_H, lane.speed*1.1);
                            birds.add(b);
                        }
                    } else {
                        int y = lane.y + 6;
                        int x = lane.speed > 0 ? -SNAKE_W - 10 : WIDTH + 10;
                        if (critterLaneHasSpaceFor(lane, x, y, SNAKE_W, SNAKE_H, CRITTER_MIN_GAP)) {
                            Snake s = new Snake(x, y, SNAKE_W, SNAKE_H, lane.speed*0.9);
                            snakes.add(s);
                        }
                    }
                }
            }
        } else if (levelTwo) {
            // LEVEL 2: kill 30 critters
            maintainCritterPopulation();
        } else if (levelThree) {
            // LEVEL 3: vehicles only, reach top to win
            for (Lane lane : roadLanes) {
                if (ticks % lane.spawnEveryTicks == 0 && rng.nextDouble() < L3_SPAWN_CHANCE) {
                    boolean truck = rng.nextDouble() < 0.30;
                    int y = lane.y + 5;
                    if (truck) {
                        int w=TRUCK_W,h=TRUCK_H;
                        boolean right = lane.speed > 0;
                        int x = right ? -w - 12 : WIDTH + 12;
                        double sp = vary(Math.copySign(Math.abs(lane.speed*0.9), lane.speed), 0.25);
                        int enterX = right ? -w : WIDTH;
                        if (laneHasSpaceFor(lane, enterX, y, w, h, VEHICLE_MIN_GAP))
                            trucks.add(new Truck(x, y, w, h, sp));
                    } else {
                        int w=CAR_W,h=CAR_H;
                        boolean right = lane.speed > 0;
                        int x = right ? -w - 12 : WIDTH + 12;
                        Car.Kind kind = (rng.nextDouble()<0.5) ? Car.Kind.RED : Car.Kind.NORMAL;
                        double sp = vary(lane.speed, 0.25);
                        int enterX = right ? -w : WIDTH;
                        if (laneHasSpaceFor(lane, enterX, y, w, h, VEHICLE_MIN_GAP))
                            cars.add(new Car(x, y, w, h, sp, kind));
                    }
                }
            }
        } else if (levelFour) {
            // LEVEL 4: logs spawn lighter 
            for (Lane lane : riverLanes) {
                if (ticks % lane.spawnEveryTicks == 0 && rng.nextDouble() < L4_SPAWN_CHANCE) {
                    int y = lane.y + 6;
                    int x = lane.speed > 0 ? -LOG_W - 10 : WIDTH + 10;
                    if (logLaneHasSpaceFor(lane, x, y, LOG_W, LOG_H, TILE/3))
                        logs.add(new Log(x, y, LOG_W, LOG_H, lane.speed));
                }
            }
        }

        // Move actors
        cars.forEach(Car::update);
        trucks.forEach(Truck::update);
        logs.forEach(Log::update);
        birds.forEach(Bird::update);
        snakes.forEach(Snake::update);

        // Level 2 extra motion: vertical wiggle + occasional horizontal flip
        if (levelTwo) {
            for (Bird b : birds) {
                Lane ln = ownerLane.get(b);
                if (ln != null) {
                    int dir = vdir.getOrDefault(b, 1);
                    b.y += dir * V_SPEED;
                    int minY = ln.y + 2;
                    int maxY = ln.y + TILE - b.h - 2;
                    if (b.y < minY) { b.y = minY; dir = 1; }
                    else if (b.y > maxY) { b.y = maxY; dir = -1; }
                    vdir.put(b, dir);
                    if (rng.nextDouble() < FLIP_CHANCE) b.speed = -b.speed;
                }
            }
            for (Snake s : snakes) {
                Lane ln = ownerLane.get(s);
                if (ln != null) {
                    int dir = vdir.getOrDefault(s, 1);
                    s.y += dir * (V_SPEED * 0.9);
                    int minY = ln.y + 2;
                    int maxY = ln.y + TILE - s.h - 2;
                    if (s.y < minY) { s.y = minY; dir = 1; }
                    else if (s.y > maxY) { s.y = maxY; dir = -1; }
                    vdir.put(s, dir);
                    if (rng.nextDouble() < FLIP_CHANCE) s.speed = -s.speed;
                }
            }
        }

        // Keep spacing for road vehicles
        if (!levelTwo && !levelFour) resolveVehicleGapsSingleTrack();

        // Projectiles, collisions, puffs
        if (levelTwo || levelThree) {
            for (Projectile p : shots) p.update();

            // Projectile collisions
            for (Iterator<Projectile> itP = shots.iterator(); itP.hasNext();) {
                Projectile p = itP.next();
                Rectangle pb = p.bounds();
                boolean hit = false;

                if (levelTwo) {
                    // Critters only in L2
                    for (Iterator<Bird> itB = birds.iterator(); itB.hasNext();) {
                        Bird b = itB.next();
                        if (b.bounds().intersects(pb)) {
                            itB.remove(); hit = true;
                            spawnPuff(b.x + b.w/2.0, b.y + b.h/2.0);
                            awardKillScore();
                            l2KillCount++;
                            break;
                        }
                    }
                    if (!hit) {
                        for (Iterator<Snake> itS = snakes.iterator(); itS.hasNext();) {
                            Snake s = itS.next();
                            if (s.bounds().intersects(pb)) {
                                itS.remove(); hit = true;
                                spawnPuff(s.x + s.w/2.0, s.y + s.h/2.0);
                                awardKillScore();
                                l2KillCount++;
                                break;
                            }
                        }
                    }
                } else if (levelThree) {
                    // Vehicles only in L3
                    for (Iterator<Car> itC = cars.iterator(); itC.hasNext();) {
                        Car c = itC.next();
                        if (c.bounds().intersects(pb)) {
                            itC.remove(); hit = true;
                            spawnPuff(c.x + c.w/2.0, c.y + c.h/2.0);
                            awardKillScore();
                            break;
                        }
                    }
                    if (!hit) {
                        for (Iterator<Truck> itT = trucks.iterator(); itT.hasNext();) {
                            Truck t = itT.next();
                            if (t.bounds().intersects(pb)) {
                                itT.remove(); hit = true;
                                spawnPuff(t.x + t.w/2.0, t.y + t.h/2.0);
                                awardKillScore();
                                break;
                            }
                        }
                    }
                }

                if (hit) {
                    itP.remove();
                    if (levelTwo && l2KillCount >= L2_KILL_TARGET &&
                            nextLevelPending == 0 && transitionTicks == 0) {
                        startTransitionTo(3);
                        repaint();
                        return;
                    }
                }
            }

            // remove off-screen bullets
            shots.removeIf(p -> p.offscreen(WIDTH, HEIGHT));

            // frog vs critter (dangerous) — only in L2
            if (levelTwo) {
                Rectangle fr2 = frog.bounds();
                for (Bird b : birds)   if (b.bounds().intersects(fr2)) { die(); repaint(); return; }
                for (Snake s : snakes) if (s.bounds().intersects(fr2)) { die(); repaint(); return; }
            }

            // update puffs
            puffs.removeIf(p -> !p.update());
        }

        // Trim off-screen actors
        cars.removeIf(c -> c.x < -c.w - 60 || c.x > WIDTH + 60);
        trucks.removeIf(t -> t.x < -t.w - 60 || t.x > WIDTH + 60);
        logs.removeIf(l -> l.x < -l.w - 60 || l.x > WIDTH + 60);
        birds.removeIf(b -> b.x < -b.w - 60 || b.x > WIDTH + 60);
        snakes.removeIf(s -> s.x < -s.w - 60 || s.y > HEIGHT + 60);

        frog.update();

        // Up-row bonus (only Level 1)
        if (!levelTwo && !levelThree && !levelFour && frog.y < bestRowY) {
            int rowsUp = (bestRowY - frog.y) / TILE;
            if (rowsUp > 0) { score += rowsUp * 10; bestRowY = frog.y; checkLevelUp(); }
        }

        if (!levelTwo && !levelThree && !levelFour) {
            // Level 1 collisions
            Rectangle fr = frog.bounds();
            for (Car c : cars)    if (c.bounds().intersects(fr)) { die(); repaint(); return; }
            for (Truck t : trucks) if (t.bounds().intersects(fr)) { die(); repaint(); return; }
            for (Bird b : birds)   if (b.bounds().intersects(fr)) { die(); repaint(); return; }
            for (Snake s : snakes) if (s.bounds().intersects(fr)) { die(); repaint(); return; }

            // River (must be on a log)
            boolean inRiver = frog.y >= 2*TILE && frog.y < 5*TILE;
            if (inRiver) {
                boolean onLog = false; double carry = 0;
                for (Log l : logs) if (l.bounds().intersects(fr)) { onLog = true; carry = l.speed; break; }
                if (!onLog) { die(); repaint(); return; }
                frog.x += carry; frog.clampToBoard(WIDTH);
            }

            // Reached goal > Level 2
            if (frog.y <= TILE) {
                score += 100;
                checkLevelUp();
                startTransitionTo(2);
                repaint();
                return;
            }
        } else if (levelThree) {
            // Level 3 frog vs vehicles + reach top to go to Level 4
            Rectangle fr = frog.bounds();
            for (Car c : cars)    if (c.bounds().intersects(fr)) { die(); repaint(); return; }
            for (Truck t : trucks)if (t.bounds().intersects(fr)) { die(); repaint(); return; }

            // Win by reaching top (like Level 1)
            if (frog.y <= TILE) {
                startTransitionTo(4);
                repaint();
                return;
            }
        } else if (levelFour) {
            // Level 4 river (includes bottom-most water row)
            Rectangle fr = frog.bounds();
            boolean inRiver = frog.y >= 3*TILE && frog.y < (ROWS-2)*TILE;
            if (inRiver) {
                boolean onLog = false; double carry = 0;
                for (Log l : logs) if (l.bounds().intersects(fr)) { onLog=true; carry=l.speed; break; }
                if (!onLog) { die(); repaint(); return; }
                frog.x += carry; frog.clampToBoard(WIDTH);
            }
            // L4 win at top
            if (frog.y <= TILE) { if (listener != null) listener.onGameOver(score + 250, playerName); return; }
        }

        repaint();
    }

    //Maintain 15–20 critters on-screen for L2
    private void maintainCritterPopulation() {
        int alive = birds.size() + snakes.size();

        for (Lane lane : critterLanes) {
            if (alive >= CRITTER_MAX_ONSCREEN) break;

            boolean forceSpawn = (alive < CRITTER_MIN_ONSCREEN);
            boolean tickOK = (ticks % lane.spawnEveryTicks == 0) && rng.nextDouble() < L2_SPAWN_CHANCE;

            if (forceSpawn || tickOK) {
                int spawnsThisTick = 1 + ((forceSpawn && rng.nextDouble() < L2_DOUBLE_CHANCE) ? 1 : 0);
                while (spawnsThisTick-- > 0 && alive < CRITTER_MAX_ONSCREEN) {
                    boolean birdPick = rng.nextBoolean();
                    if (birdPick) {
                        int y = lane.y + 7;
                        int x = lane.speed > 0 ? -BIRD_W - 10 : WIDTH + 10;
                        if (critterLaneHasSpaceFor(lane, x, y, BIRD_W, BIRD_H, CRITTER_MIN_GAP)) {
                            Bird b = new Bird(x, y, BIRD_W, BIRD_H, lane.speed);
                            birds.add(b);
                            ownerLane.put(b, lane);
                            vdir.put(b, rng.nextBoolean()?1:-1);
                            alive++;
                        }
                    } else {
                        int y = lane.y + 6;
                        int x = lane.speed > 0 ? -SNAKE_W - 10 : WIDTH + 10;
                        if (critterLaneHasSpaceFor(lane, x, y, SNAKE_W, SNAKE_H, CRITTER_MIN_GAP)) {
                            Snake s = new Snake(x, y, SNAKE_W, SNAKE_H, lane.speed);
                            snakes.add(s);
                            ownerLane.put(s, lane);
                            vdir.put(s, rng.nextBoolean()?1:-1);
                            alive++;
                        }
                    }
                }
            }
        }
    }

    //scoring helpers
    private void awardKillScore() {
        combo = Math.min(9, combo + 1);
        comboTimer = COMBO_WINDOW_TICKS;
        score += KILL_SCORE + combo * 5;
        checkLevelUp();
    }
    private void spawnPuff(double x, double y) { for (int i=0;i<3;i++) puffs.add(new Puff(x, y)); }

    private void checkLevelUp() {
        // Level 1  > 2 at 1000, 2 > 3 at 3000; cap at 3
        int newLevel = playerLevel;

        if (score >= LEVEL3_THRESHOLD) {
            newLevel = 3;
        } else if (score >= LEVEL2_THRESHOLD) {
            newLevel = 2;
        } else {
            newLevel = 1;
        }

        if (newLevel > 3) newLevel = 3;

        if (newLevel > playerLevel) {
            playerLevel = newLevel;
            justLeveledTo = playerLevel;
            levelUpFlashTicks = LEVELUP_FLASH_FRAMES;

            // Auto-select newly unlocked fire mode
            if (playerLevel >= 3)       fireMode = FireMode.NOVA;
            else if (playerLevel == 2)  fireMode = FireMode.SPREAD;
            else                        fireMode = FireMode.SINGLE;
        }
    }

    //death / gameover 
    private void die() {
        if (!frog.alive) return;
        frog.alive = false; lives--; score = Math.max(0, score - 25);
        combo = 0; comboTimer = 0;
        // No level down on death; keep playerLevel
        if (lives <= 0) {
            if (listener != null) listener.onGameOver(score, playerName);
            return;
        }
        setupLevel();
    }

    // paint 
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        //  HUD BAR
        g.setColor(new Color(22,18,32));
        g.fillRect(0,0,WIDTH,TILE);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD,18f));


        String timeStr = "";

        // DO NOT CHANGE HUD LINES
        String hdr = "Level: "+level+" | Lives: "+lives+" | Score: "+score+" | Player: "+playerName+ timeStr;
        if (paused) hdr += "   [PAUSED]";
        g.drawString(hdr, 10, 26);
        // =======================

        if (levelThree) {
            // Level 3: goal at top/bottom rows, roads in the middle
            for (int r = 1; r < ROWS; r++) {
                boolean topGoal = (r == 1 || r == 2);
                boolean bottomGoal = (r == ROWS - 2 || r == ROWS - 1);
                if (topGoal || bottomGoal) drawRow(g, r, Assets.tileGoal(), new Color(72,160,72));
                else                        drawRow(g, r, Assets.tileRoad(), new Color(56,56,56));
            }

            // Road markings
            BufferedImage mark = Assets.tileRoadMark();
            if (mark != null) {
                for (Lane lane : roadLanes)
                    for (int x=0;x<WIDTH;x+=TILE)
                        g.drawImage(mark, x, lane.y + TILE/2 - 2, TILE, 4, null);
            } else {
                g.setColor(new Color(236,214,96));
                for (Lane lane : roadLanes)
                    for (int x=0;x<WIDTH;x+=60) g.fillRect(x, lane.y + TILE/2 - 1, 30, 2);
            }

            // Actors (no critters)
            trucks.forEach(t -> t.draw(g));
            cars.forEach(c -> c.draw(g));
            for (Projectile p : shots) p.draw(g);
            for (Puff pf : puffs) pf.draw(g);
            frog.draw(g);

            // (Help text left)
            if (showHelp) {
                String[] lines = {
                    "Level 3 — Traffic Survival",
                    "Reach the top to advance!",
                    "WASD: move  |  Arrow keys: fire  |  1/2: fire mode",
                    "|  H: help  |  ESC: menu"
                };
                drawHelpBox(g, lines, 400, 120);
            }

            // Level-up overlay
            drawLevelUpOverlay(g);

            if (transitionTicks > 0) drawTransitionOverlay(g, (nextLevelPending != 0) ? nextLevelPending : (level+1));
            return;
        }

        if (levelTwo) {
            // Level 2 background
            for (int r = 1; r < ROWS; r++) {
                if (r <= 2 || r >= ROWS - 2) {
                    drawRow(g, r, Assets.tileuglyGrass(), new Color(120,160,80));
                } else {
                    drawRow(g, r, Assets.tileGrass(), new Color(72,160,72));
                }
            }

            birds.forEach(b -> b.draw(g));
            snakes.forEach(s -> s.draw(g));
            for (Projectile p : shots) p.draw(g);
            for (Puff pf : puffs) pf.draw(g);
            frog.draw(g);

            // (Help text left unchanged)
            if (showHelp) {
                String[] lines = {
                    "Level 2 — Critter Survival",
                    "KILL! KILL! KILL!  (30 critters to advance)",
                    "WASD: move  |  Arrow keys: fire  |  1/2: fire mode",
                    "|  H: help  |  ESC: menu"
                };
                drawHelpBox(g, lines, 400, 120);
            }

            // Level-up overlay
            drawLevelUpOverlay(g);

            if (transitionTicks > 0) drawTransitionOverlay(g, (nextLevelPending != 0) ? nextLevelPending : (level+1));
            return;
        }

        if (levelFour) {
            // Level 4: ugly grass borders + water in middle
            for (int r = 1; r < ROWS; r++) {
                if (r <= 2 || r >= ROWS - 2) {
                    drawRow(g, r, Assets.tileuglyGrass(), new Color(120,160,80));
                } else {
                    drawRow(g, r, Assets.tileWater(), new Color(40,88,152));
                }
            }
            logs.forEach(l -> l.draw(g));
            for (Projectile p : shots) p.draw(g);
            for (Puff pf : puffs) pf.draw(g);
            frog.draw(g);

            // (Help text)
            if (showHelp) {
                String[] lines = {
                    "Level 4 — River Run",
                    "Ride logs across the water to the top.",
                    "WASD: move  |  Arrow keys: fire  |  1/2: fire mode",
                    "|  H: help  |  ESC: menu"
                };
                drawHelpBox(g, lines, 400, 120);
            }

            // Level-up overlay
            drawLevelUpOverlay(g);

            if (transitionTicks > 0) drawTransitionOverlay(g, (nextLevelPending != 0) ? nextLevelPending : (level+1));
            return;
        }

        //  Level 1 drawing
        drawRow(g, 1, Assets.tileGoal(),  new Color(72,160,72));
        for (int r=2;r<=4;r++) drawRow(g, r, Assets.tileWater(), new Color(40,88,152));
        for (int r=6;r<=11;r++) drawRow(g, r, Assets.tileRoad(),  new Color(56,56,56));
        for (int r=14;r<=16;r++) drawRow(g, r, Assets.tileGrass(), new Color(72,160,72));
        drawRow(g, ROWS-1, Assets.tileStart(), new Color(72,160,72));

        // Road markings
        BufferedImage mark = Assets.tileRoadMark();
        if (mark != null) {
            for (Lane lane : roadLanes)
                for (int x=0;x<WIDTH;x+=TILE)
                    g.drawImage(mark, x, lane.y + TILE/2 - 2, TILE, 4, null);
        } else {
            g.setColor(new Color(236,214,96));
            for (Lane lane : roadLanes)
                for (int x=0;x<WIDTH;x+=60) g.fillRect(x, lane.y + TILE/2 - 1, 30, 2);
        }

        // Actors
        logs.forEach(l -> l.draw(g));
        trucks.forEach(t -> t.draw(g));
        cars.forEach(c -> c.draw(g));
        birds.forEach(b -> b.draw(g));
        snakes.forEach(s -> s.draw(g));
        frog.draw(g);

        // (Help text left unchanged)
        if (showHelp) {
            String[] lines = {"Level 1 — Why did the frog cross the road?",
                              "WASD: move  |  Arrow keys: fire  |  1/2: fire mode",  
                              "|  P: pause  |  H: help  |  ESC: menu",
                              "Reach the top or press Z to unlock Level 2"};
            drawHelpBox(g, lines, 400, 120);
        }

        // Level-up overlay
        drawLevelUpOverlay(g);

        if (transitionTicks > 0) drawTransitionOverlay(g, (nextLevelPending != 0) ? nextLevelPending : (level+1));
    }

    private void drawRow(Graphics2D g, int row, BufferedImage tile, Color fallback) {
        int y = row * TILE;
        if (tile != null) {
            for (int c=0;c<COLS;c++) g.drawImage(tile, c*TILE, y, TILE, TILE, null);
        } else {
            g.setColor(fallback);
            g.fillRect(0, y, WIDTH, TILE);
        }
    }

    private void drawHelpBox(Graphics2D g, String[] lines, int w, int h){
        int x=WIDTH/2-w/2, y=HEIGHT/2-h/2;
        g.setColor(new Color(0,0,0,180)); g.fillRect(x,y,w,h);
        g.setColor(Color.WHITE); g.drawRect(x,y,w,h);
        int yy=y+26; g.setFont(g.getFont().deriveFont(Font.PLAIN,16f));
        for (String line:lines){ g.drawString(line, x+12, yy); yy+=22; }
    }

    private void drawTransitionOverlay(Graphics2D g, int nextLvlNumber) {
        g.setColor(new Color(0,0,0,200));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 36f));
        String title = "Next Level";
        FontMetrics fm = g.getFontMetrics();
        int tx = (WIDTH - fm.stringWidth(title)) / 2;
        int ty = HEIGHT / 2 - 10;
        g.drawString(title, tx, ty);

        g.setFont(g.getFont().deriveFont(Font.PLAIN, 18f));
        String sub = "Level " + nextLvlNumber;
        int sx = (WIDTH - g.getFontMetrics().stringWidth(sub)) / 2;
        g.drawString(sub, sx, ty + 28);
    }

    private void drawLevelUpOverlay(Graphics2D g) {
        if (levelUpFlashTicks <= 0) return;
        int alpha = (int)(200 * (levelUpFlashTicks / (float)LEVELUP_FLASH_FRAMES));
        g.setColor(new Color(0, 0, 0, Math.max(80, alpha/2)));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(new Color(255, 255, 120, Math.min(255, 100 + alpha)));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 40f));
        String title = "Level Up!";
        FontMetrics fm = g.getFontMetrics();
        int tx = (WIDTH - fm.stringWidth(title)) / 2;
        int ty = HEIGHT / 2 - 10;
        g.drawString(title, tx, ty);

        g.setFont(g.getFont().deriveFont(Font.BOLD, 28f));
        String sub = "" + justLeveledTo;
        int sx = (WIDTH - g.getFontMetrics().stringWidth(sub)) / 2;
        g.drawString(sub, sx, ty + 34);
    }

    // input
    @Override public void keyPressed(KeyEvent e) {
        // Block actions during transition (allow ESC to menu)
        if (transitionTicks > 0) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE && listener != null) listener.onBackToMenu();
            return;
        }

        switch (e.getKeyCode()) {
            //Movement: WASD only (S moves down)
            case KeyEvent.VK_A -> frog.nudge(-TILE, 0);
            case KeyEvent.VK_D -> frog.nudge( TILE, 0);
            case KeyEvent.VK_W -> frog.nudge(0, -TILE);
            case KeyEvent.VK_S -> frog.nudge(0,  TILE);  // S moves down

            //  Arrow keys FIRE in their direction
            case KeyEvent.VK_LEFT  -> { if (levelTwo || levelThree) fireByCurrentMode(-1,  0); }
            case KeyEvent.VK_RIGHT -> { if (levelTwo || levelThree) fireByCurrentMode( 1,  0); }
            case KeyEvent.VK_UP    -> { if (levelTwo || levelThree) fireByCurrentMode( 0, -1); }
            case KeyEvent.VK_DOWN  -> { if (levelTwo || levelThree) fireByCurrentMode( 0,  1); }

            //Fire mode select (gated by playerLevel)
            case KeyEvent.VK_1 -> { if (playerLevel >= 1) fireMode = FireMode.SINGLE; }
            case KeyEvent.VK_2 -> { if (playerLevel >= 2) fireMode = FireMode.SPREAD; }
            case KeyEvent.VK_3 -> { if (playerLevel >= 3) fireMode = FireMode.NOVA;   }

            // Other controls preserved
            case KeyEvent.VK_P     -> paused = !paused;
            case KeyEvent.VK_H     -> showHelp = !showHelp;
            case KeyEvent.VK_Z     -> {
                // Skip: L1 -> L2, L2 -> L3, L3 -> L4
                if (!levelTwo && !levelThree && !levelFour) { startTransitionTo(2); }
                else if (levelTwo)                           { startTransitionTo(3); }
                else if (levelThree)                         { startTransitionTo(4); }
            }
            case KeyEvent.VK_ESCAPE-> { if (listener != null) listener.onBackToMenu(); }
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // fire routing by mode 
    private void fireByCurrentMode(int dirX, int dirY) {
        if (fireMode == FireMode.NOVA) fireNova();
        else fireProjectile(dirX, dirY); // SINGLE or SPREAD uses directional fire
    }

    //  shooting (SINGLE/SPREAD) 
    private void fireProjectile(int dirX, int dirY) {
        int baseCooldown = 10;
        int cooldown = baseCooldown;
        if (fireMode == FireMode.SPREAD) {
            // slowed further
            cooldown = 16;
        }
        if (ticks - lastShotTick < cooldown) return;
        lastShotTick = ticks;

        // Normalize to -1/0/1 and default to up if somehow 0,0
        if (dirX == 0 && dirY == 0) { dirX = 0; dirY = -1; }
        else { dirX = Integer.signum(dirX); dirY = Integer.signum(dirY); }

        int cx = frog.x + (frog.w / 2) - 5;
        int cy = frog.y + (frog.h / 2) - 5;
        int muzzle = 10;
        int spawnX = cx + (dirX * muzzle);
        int spawnY = cy + (dirY * muzzle);

        double speed = 8.0;
        double vx = dirX * speed;
        double vy = dirY * speed; // screen Y increases downward

        if (fireMode == FireMode.SINGLE) {
            shots.add(new Projectile(spawnX, spawnY, vx, vy));
        } else {
            // SPREAD (3 shots)
            double off = 2.0;
            if (dirX != 0 && dirY == 0) {
                // Horizontal: vary vy
                shots.add(new Projectile(spawnX, spawnY, vx, vy));
                shots.add(new Projectile(spawnX, spawnY, vx, vy - off));
                shots.add(new Projectile(spawnX, spawnY, vx, vy + off));
            } else if (dirY != 0 && dirX == 0) {
                // Vertical: vary vx
                shots.add(new Projectile(spawnX, spawnY, vx, vy));
                shots.add(new Projectile(spawnX, spawnY, vx - off, vy));
                shots.add(new Projectile(spawnX, spawnY, vx + off, vy));
            } else {
                // Diagonal fallback: small fan
                shots.add(new Projectile(spawnX, spawnY, vx, vy));
                shots.add(new Projectile(spawnX, spawnY, vx + off*0.7, vy));
                shots.add(new Projectile(spawnX, spawnY, vx, vy + off*0.7));
            }
        }
    }

    //- NOVA (radial burst around frog)
    private void fireNova() {
        int cooldown = 23;
        if (ticks - lastShotTick < cooldown) return;
        lastShotTick = ticks;

        int cx = frog.x + (frog.w / 2);
        int cy = frog.y + (frog.h / 2);

       
        int count = 6;         
        double speed = 7.0;

        for (int i = 0; i < count; i++) {
            double ang = (2*Math.PI * i) / count;
            double vx = Math.cos(ang) * speed;
            double vy = Math.sin(ang) * speed; 
            shots.add(new Projectile(cx, cy, vx, vy));
        }
    }

  
    // Overlap prevention helpers
   
    private boolean laneHasSpaceFor(Lane lane, int x, int y, int w, int h, int gap) {
        Rectangle cand = new Rectangle(x,y,w,h);
        for (Car c : cars)    if (Math.abs(c.y - y) < 2 && expand(c.bounds(), gap).intersects(cand)) return false;
        for (Truck t : trucks) if (Math.abs(t.y - y) < 2 && expand(t.bounds(), gap).intersects(cand)) return false;
        return true;
    }
    private boolean logLaneHasSpaceFor(Lane lane, int x, int y, int w, int h, int gap) {
        Rectangle cand = new Rectangle(x,y,w,h);
        for (Log l : logs) if (Math.abs(l.y - lane.y) < TILE/2 && expand(l.bounds(), gap).intersects(cand)) return false;
        return true;
    }
    private boolean critterLaneHasSpaceFor(Lane lane, int x, int y, int w, int h, int gap) {
        Rectangle cand = new Rectangle(x,y,w,h);
        for (Bird b : birds) if (Math.abs(b.y - lane.y) < TILE/2 && expand(b.bounds(), gap).intersects(cand)) return false;
        for (Snake s : snakes) if (Math.abs(s.y - lane.y) < TILE/2 && expand(s.bounds(), gap).intersects(cand)) return false;
        return true;
    }
    private Rectangle expand(Rectangle r, int gap){ return new Rectangle(r.x-gap, r.y, r.width+2*gap, r.height); }

    private void resolveVehicleGapsSingleTrack() {
        for (Lane lane : roadLanes) {
            int laneY = lane.y + 5;
            ArrayList<Object> objs = new ArrayList<>();
            for (Car c : cars)    if (Math.abs(c.y - laneY) <= 2) objs.add(c);
            for (Truck t : trucks) if (Math.abs(t.y - laneY) <= 2) objs.add(t);
            if (objs.size() < 2) continue;

            objs.sort((a,b)->Double.compare(getX(a), getX(b)));

            for (int i=0;i<objs.size()-1;i++){
                Object A = objs.get(i);
                Object B = objs.get(i+1);
                Rectangle ra = bounds(A), rb = bounds(B);

                int needed = (ra.x + ra.width + VEHICLE_MIN_GAP) - rb.x;
                if (needed > 0) {
                    double sa = getSpeed(A);
                    double sb = getSpeed(B);
                    if (Math.signum(sa) == Math.signum(sb)) {
                        if (sa > 0) setX(B, getX(B) + needed);
                        else        setX(A, getX(A) - needed);
                    } else {
                        setX(A, getX(A) - needed/2.0);
                        setX(B, getX(B) + needed/2.0);
                    }
                }
            }
        }
    }

    private double getX(Object o){ return (o instanceof Car) ? ((Car)o).x : ((Truck)o).x; }
    private void   setX(Object o, double v){ if (o instanceof Car) ((Car)o).x=v; else ((Truck)o).x=v; }
    private double getSpeed(Object o){ return (o instanceof Car) ? ((Car)o).speed : ((Truck)o).speed; }
    private Rectangle bounds(Object o){
        if (o instanceof Car c)   return c.bounds();
        if (o instanceof Truck t) return t.bounds();
        return new Rectangle();
    }

    //  Lane holder
    static class Lane {
        final int y;
        final double speed;
        final int spawnEveryTicks;
        Lane(int y, double speed, int spawnEveryTicks){
            this.y = y; this.speed = speed; this.spawnEveryTicks = spawnEveryTicks;
        }
    }
}
