import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;


public class App extends JFrame implements GamePanel.GameListener {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final MenuPanel menuPanel = new MenuPanel();
    private final ScorePanel scorePanel = new ScorePanel();
    private final AudioManager audio = new AudioManager();
    private GamePanel gamePanel = null;

    private String playerName = "";

    public App() {
        super("Frogger's Revenge");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        root.add(menuPanel, "menu");
        root.add(scorePanel, "score");
        setContentPane(root);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Show menu first
        cards.show(root, "menu");

        // Start menu music
        audio.playLoop("audio/gameMusic.wav");
    }

    // GameListener callbacks
    @Override public void onGameOver(int finalScore, String player) {
        ScoreManager.get().add(player, finalScore);
        scorePanel.refresh();
        cards.show(root, "score");
        disposeGamePanel();
        audio.playLoop("audio/gameMusic.wav");
    }

    @Override public void onBackToMenu() {
        cards.show(root, "menu");
        audio.playLoop("audio/gameMusic.wav");
        disposeGamePanel();
    }

    private void disposeGamePanel() {
        if (gamePanel != null) {
            root.remove(gamePanel);
            gamePanel.stop();
            gamePanel = null;
            root.revalidate();
            root.repaint();
        }
    }

    private void startGameWithName() {
        playerName = JOptionPane.showInputDialog(
        this,
        "Enter name:",
        "",
        JOptionPane.PLAIN_MESSAGE
);

        if (playerName == null) return; // cancelled
        playerName = playerName.trim();
        if (playerName.isEmpty()) playerName = "Player";

        // Switch to gameplay music
        audio.playLoop("audio/gameMusic.wav");

        disposeGamePanel();
        gamePanel = new GamePanel(playerName, this);
        root.add(gamePanel, "game");
        cards.show(root, "game");
        gamePanel.start();
        pack();
        setLocationRelativeTo(null);
    }

    // Menu Panel
    // ===== Menu Panel =====
private class MenuPanel extends JPanel {
    private Image bg;

    MenuPanel() {
        // Load background image
        try {
            bg = new ImageIcon("assets/menu/menu_bg.png").getImage();
        } catch (Exception e) {
            System.out.println("Menu background failed to load.");
        }

        setLayout(new GridBagLayout());
        setPreferredSize(new Dimension(GamePanel.WIDTH, GamePanel.HEIGHT));
        setBackground(new Color(22,18,32,0)); // transparent so image shows

        JLabel title = new JLabel("Frogger's Revenge");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 42f));

        JButton start = new JButton("Start Game");
        JButton scores = new JButton("Scoreboard");
        JButton exit = new JButton("Exit Game");

        start.setFocusPainted(false);
        scores.setFocusPainted(false);
        exit.setFocusPainted(false);

        start.addActionListener(e -> startGameWithName());
        scores.addActionListener(e -> {
            scorePanel.refresh();
            cards.show(root, "score");
        });
        exit.addActionListener(e -> System.exit(0));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);
        c.gridx = 0; 
        c.gridy = 0; 
        add(title, c);
        c.gridy++; add(start, c);
        c.gridy++; add(scores, c);
        c.gridy++; add(exit, c);

        JLabel tip = new JLabel("ESC in-game returns to Menu");
        tip.setForeground(new Color(200,200,200));
        c.gridy++; add(tip, c);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (bg != null) {
            g.drawImage(bg, 0, 0, getWidth(), getHeight(), this);
        }
    }
}


    // Scoreboard Panel 
    private class ScorePanel extends JPanel {
        private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Player","Score","When"}, 0) {
            @Override public boolean isCellEditable(int r, int c){ return false; }
        };
        private final JTable table = new JTable(model);

        ScorePanel() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(GamePanel.WIDTH, GamePanel.HEIGHT));
            setBackground(new Color(22,18,32));

            JLabel h = new JLabel("Scoreboard", SwingConstants.CENTER);
            h.setForeground(Color.WHITE);
            h.setFont(h.getFont().deriveFont(Font.BOLD, 24f));

            JScrollPane sp = new JScrollPane(table);
            table.setFillsViewportHeight(true);

            JPanel buttons = new JPanel();
            JButton back = new JButton("Back to Menu");
            JButton clear = new JButton("Clear Scores");
            back.addActionListener(e -> cards.show(root, "menu"));
            clear.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(this, "Clear all scores?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
                if (res == JOptionPane.OK_OPTION) {
                    ScoreManager.get().clear();
                    refresh();
                }
            });
            buttons.add(back); buttons.add(clear);

            add(h, BorderLayout.NORTH);
            add(sp, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
        }

        void refresh() {
            model.setRowCount(0);
            for (Score s : ScoreManager.get().top(50)) {
                model.addRow(new Object[]{s.player, s.score, s.when});
            }
        }
    }

    //  Score storage 
    static class Score {
        final String player;
        final int score;
        final String when;
        Score(String p, int s, String w){ this.player=p; this.score=s; this.when=w; }
    }

    static class ScoreManager {
        private static final ScoreManager INSTANCE = new ScoreManager();
        public static ScoreManager get(){ return INSTANCE; }

        private final File file = new File("scores.csv");

        synchronized void add(String player, int score) {
            String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                String line = escape(player) + "," + score + "," + escape(when) + "\n";
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }

        synchronized List<Score> top(int n) {
            List<Score> list = new ArrayList<>();
            if (!file.exists()) return list;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String ln;
                while ((ln = br.readLine()) != null) {
                    String[] parts = splitCsv(ln);
                    if (parts.length >= 3) {
                        String p = unescape(parts[0]);
                        int s = Integer.parseInt(parts[1].trim());
                        String w = unescape(parts[2]);
                        list.add(new Score(p, s, w));
                    }
                }
            } catch (IOException ignored) {}
            list.sort((a,b)->Integer.compare(b.score, a.score));
            if (list.size() > n) return list.subList(0, n);
            return list;
        }

        synchronized void clear() {
            if (file.exists()) file.delete();
        }

        private static String escape(String s){
            if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"","\"\"") + "\"";
            return s;
        }
        private static String unescape(String s){
            s = s.trim();
            if (s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length()-1).replace("\"\"", "\"");
            }
            return s;
        }
        private static String[] splitCsv(String line){
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQ = false;
            for (int i=0;i<line.length();i++){
                char c = line.charAt(i);
                if (inQ){
                    if (c=='"'){
                        if (i+1<line.length() && line.charAt(i+1)=='"'){ cur.append('"'); i++; }
                        else inQ=false;
                    } else cur.append(c);
                } else {
                    if (c=='"') inQ=true;
                    else if (c==','){ out.add(cur.toString()); cur.setLength(0); }
                    else cur.append(c);
                }
            }
            out.add(cur.toString());
            return out.toArray(new String[0]);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
