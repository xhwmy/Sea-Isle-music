package app.seashelf.music.player;

public final class FloatingBounds {
    private FloatingBounds() { }
    public static Position clamp(int x, int y, int width, int height, int screenWidth, int screenHeight, int inset) {
        int safeTop = Math.max(0, Math.min(inset, screenHeight - height));
        int maxX = Math.max(0, screenWidth - width);
        int maxY = Math.max(safeTop, screenHeight - height - Math.max(0, inset));
        return new Position(Math.max(0, Math.min(x, maxX)), Math.max(safeTop, Math.min(y, maxY)));
    }
    public static final class Position {
        public final int x, y;
        Position(int x, int y) { this.x = x; this.y = y; }
    }
}
