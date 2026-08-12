import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the Modrinth cover image.
 *
 * <p>The pitch of this fork is one specific thing — you can finally tell a command that failed
 * from one that quietly did nothing — so the banner shows that as three real lines of output
 * rather than describing it in adjectives.
 */
public final class MakeBanner {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private static final Color BACKGROUND = new Color(0x16, 0x18, 0x1D);
    private static final Color PANEL = new Color(0x1E, 0x21, 0x28);
    private static final Color BORDER = new Color(0x2A, 0x2F, 0x38);
    private static final Color GREEN = new Color(0x6A, 0xCB, 0x5A);
    private static final Color AMBER = new Color(0xF2, 0xC1, 0x4E);
    private static final Color RED = new Color(0xE0, 0x6C, 0x5A);
    private static final Color TEXT = new Color(0xE8, 0xEA, 0xED);
    private static final Color MUTED = new Color(0x8B, 0x93, 0xA1);

    /** Same pixel grid as the icon, so the two read as one identity. */
    private static final int[][] CHEVRON_ROWS = {
        {5, 2, 4}, {6, 4, 6}, {7, 6, 8},
        {8, 7, 9},
        {9, 6, 8}, {10, 4, 6}, {11, 2, 4},
    };
    private static final int[][] CARET_ROWS = {
        {10, 10, 13}, {11, 10, 13},
    };

    private record Line(String command, String verdict, Color verdictColor, String note) {
    }

    private static final Line[] LINES = {
        new Line("time set day", "success=true  result=1000", GREEN,
            "it worked, and here is what it returned"),
        new Line("setblock ~ ~ ~ minecraft:not_a_block", "errorType=parse  cursor=15", RED,
            "a syntax error, with the exact position"),
        new Line("execute if block ~ ~ ~ bedrock run say", "success=false  error=null", AMBER,
            "it ran and did nothing - the silent failure"),
    };

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        drawMark(g, 80, 74, 11);
        drawHeading(g);
        drawTerminal(g);
        drawFooter(g);

        g.dispose();

        String path = args.length > 0 ? args[0] : "banner.png";
        ImageIO.write(image, "png", new File(path));
        System.out.println("wrote " + new File(path).getAbsolutePath() + " (" + WIDTH + "x" + HEIGHT + ")");
    }

    /** Renders the icon glyph at {@code cell} pixels per grid square. */
    private static void drawMark(Graphics2D g, int x, int y, int cell) {
        g.setColor(GREEN);
        fill(g, CHEVRON_ROWS, x, y, cell);
        g.setColor(AMBER);
        fill(g, CARET_ROWS, x, y, cell);
    }

    private static void fill(Graphics2D g, int[][] rows, int x, int y, int cell) {
        for (int[] row : rows) {
            g.fillRect(x + row[1] * cell, y + row[0] * cell, (row[2] - row[1] + 1) * cell, cell);
        }
    }

    private static void drawHeading(Graphics2D g) {
        g.setColor(TEXT);
        g.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 64));
        g.drawString("MCP-rogal", 260, 148);

        g.setColor(GREEN);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 30));
        g.drawString("An MCP server for building Minecraft mods and datapacks", 262, 192);

        g.setColor(MUTED);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 25));
        g.drawString("Your assistant runs a command and sees exactly what the game did with it", 262, 240);
    }

    private static void drawTerminal(Graphics2D g) {
        int x = 80;
        int y = 306;
        int w = WIDTH - 160;
        int h = 258;

        g.setColor(PANEL);
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(BORDER);
        g.drawRoundRect(x, y, w, h, 14, 14);

        Font mono = monospace(21);
        Font monoSmall = monospace(15);

        int lineY = y + 52;
        for (Line line : LINES) {
            g.setFont(mono);
            g.setColor(GREEN);
            g.drawString(">", x + 28, lineY);
            g.setColor(TEXT);
            g.drawString(line.command(), x + 52, lineY);

            g.setColor(line.verdictColor());
            g.drawString(line.verdict(), x + 640, lineY);

            g.setFont(monoSmall);
            g.setColor(MUTED);
            g.drawString(line.note(), x + 52, lineY + 24);

            lineY += 78;
        }
    }

    private static void drawFooter(Graphics2D g) {
        g.setFont(new Font("Segoe UI", Font.PLAIN, 24));

        String[] parts = {
            "26 tools", "live logs", "keyboard & mouse", "datapack files", "fake players",
        };

        int x = 80;
        int y = 630;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                g.setColor(BORDER);
                g.drawString("/", x, y);
                x += g.getFontMetrics().stringWidth("/") + 18;
            }
            g.setColor(i == 0 ? GREEN : MUTED);
            g.drawString(parts[i], x, y);
            x += g.getFontMetrics().stringWidth(parts[i]) + 18;
        }

        g.setColor(MUTED);
        g.setFont(monospace(19));
        String version = "Fabric  ·  Minecraft 26.2";
        int width = g.getFontMetrics().stringWidth(version);
        g.drawString(version, WIDTH - 80 - width, y);
    }

    /** Consolas where it exists, otherwise whatever monospace font the platform provides. */
    private static Font monospace(int size) {
        Font consolas = new Font("Consolas", Font.PLAIN, size);
        return "Consolas".equalsIgnoreCase(consolas.getFamily())
            ? consolas
            : new Font(Font.MONOSPACED, Font.PLAIN, size);
    }
}
