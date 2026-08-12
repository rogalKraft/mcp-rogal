import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the mod icon as pixel art on a 16x16 grid, then scales it up with nearest-neighbour
 * so the pixels stay hard-edged at every size. A terminal prompt reads as "developer tool" and
 * the blocky rendering reads as Minecraft, which is the whole pitch in one glyph.
 */
public final class MakeIcon {

    private static final int GRID = 16;

    private static final Color BACKGROUND = new Color(0x16, 0x18, 0x1D);
    private static final Color BORDER = new Color(0x2A, 0x2F, 0x38);
    private static final Color CHEVRON = new Color(0x6A, 0xCB, 0x5A);
    private static final Color CHEVRON_SHADE = new Color(0x4A, 0x9B, 0x3E);
    private static final Color CARET = new Color(0xF2, 0xC1, 0x4E);

    // '>' prompt as {row, firstCol, lastCol}: two pixels thick, stepping exactly one column per
    // row so both strokes read as clean diagonals meeting at a single apex. Three pixels of
    // thickness makes the strokes overlap near the tip and the whole glyph reads as a zigzag.
    private static final int[][] CHEVRON_ROWS = {
        {5, 2, 4}, {6, 4, 6}, {7, 6, 8},
        {8, 7, 9},
        {9, 6, 8}, {10, 4, 6}, {11, 2, 4},
    };

    // '_' cursor on the chevron's baseline, kept clear of the border
    private static final int[][] CARET_ROWS = {
        {10, 10, 13}, {11, 10, 13},
    };

    public static void main(String[] args) throws Exception {
        BufferedImage source = new BufferedImage(GRID, GRID, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, GRID, GRID);

        g.setColor(BORDER);
        g.drawRect(0, 0, GRID - 1, GRID - 1);

        // Flat, no drop shadow: an offset copy blurs the silhouette at icon sizes.
        paint(g, CHEVRON_ROWS, CHEVRON, 0, 0);
        paint(g, CARET_ROWS, CARET, 0, 0);

        g.dispose();

        write(source, 512, args.length > 0 ? args[0] : "icon-512.png");
        write(source, 256, args.length > 1 ? args[1] : "icon-256.png");
    }

    private static void paint(Graphics2D g, int[][] rows, Color color, int dx, int dy) {
        g.setColor(color);
        for (int[] row : rows) {
            int y = row[0] + dy;
            int from = row[1] + dx;
            int to = row[2] + dx;
            if (y >= GRID) {
                continue;
            }
            g.fillRect(from, y, Math.min(to, GRID - 1) - from + 1, 1);
        }
    }

    private static void write(BufferedImage source, int size, String path) throws Exception {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();

        File file = new File(path);
        ImageIO.write(scaled, "png", file);
        System.out.println("wrote " + file.getAbsolutePath() + " (" + size + "x" + size + ")");
    }
}
