package dev.rtpbuddy.region;

/**
 * DonutSMP splits its overworld between six server regions, and "region" on
 * that server means one of those - NA East, EU Central, Asia and so on - not a
 * dimension. Each region owns whole 50 000 block cells of a nine by nine grid
 * covering the playable area.
 *
 * <p>Whether a plain {@code /rtp} scatters across the whole world or stays in
 * the caller's own region is not asserted here - that is the question the map is
 * there to answer, and the answer is whatever the recorded landings show.
 *
 * <p>The grid below is the published layout (donut.auction/regions, which is
 * also what the DonutRegion mod reads). It is a property of the server, not of
 * a Minecraft world, so it is applied only to samples recorded on a matching
 * host - see {@code RTPBuddyConfig.serverRegion}. The nether and the end are
 * unmapped: both are hosted on NA East as a whole.
 */
public final class ServerRegions {

    /** Edge length of one grid cell, in blocks. */
    public static final int CELL_SIZE = 50_000;

    /** Cells per axis. */
    public static final int COLUMNS = 9;

    /** West and north edge of the grid; the playable area is +/- this. */
    public static final int MIN_COORD = -225_000;
    public static final int MAX_COORD = 225_000;

    /** One of the six server locations a player can be routed to. */
    public enum Zone {
        NA_EAST("na_east", "NA East", "Ashburn, Virginia", 0xFFD85B24),
        NA_WEST("na_west", "NA West", "Hillsboro, Oregon", 0xFFDDA932),
        EU_CENTRAL("eu_central", "EU Central", "Frankfurt", 0xFF3478D4),
        EU_WEST("eu_west", "EU West", "", 0xFF3FA64D),
        ASIA("asia", "Asia", "Singapore", 0xFF7547D8),
        OCEANIA("oceania", "Oceania", "Sydney", 0xFFCC625B);

        private final String id;
        private final String label;
        private final String site;
        private final int color;

        Zone(String id, String label, String site, int color) {
            this.id = id;
            this.label = label;
            this.site = site;
            this.color = color;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        /** Where the region is hosted, for the tooltip. May be empty. */
        public String site() {
            return site;
        }

        public int color() {
            return color;
        }
    }

    /**
     * One cell of the grid.
     *
     * @param number the server number the community counts by, 1 to 81, running
     *               west to east then north to south
     */
    public record Cell(int number, int column, int row, Zone zone) {

        public int minX() {
            return MIN_COORD + column * CELL_SIZE;
        }

        public int minZ() {
            return MIN_COORD + row * CELL_SIZE;
        }

        public int maxX() {
            return minX() + CELL_SIZE;
        }

        public int maxZ() {
            return minZ() + CELL_SIZE;
        }
    }

    /**
     * Row by row from z = -225 000, column by column from x = -225 000.
     * O oceania, W NA west, E NA east, A asia, U EU west, C EU central.
     */
    private static final String[] LAYOUT = {
            "OWWWEEEEE",
            "OWWWEEEEE",
            "OWWWEEEEE",
            "OOOWEEEEE",
            "AAAAEEEEE",
            "AUUCCCCCE",
            "AUUCCCCCE",
            "CUCCCCCCC",
            "CUUUUUUCC",
    };

    private static final Cell[] CELLS = new Cell[COLUMNS * COLUMNS];

    static {
        for (int row = 0; row < COLUMNS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                Zone zone = zoneForLetter(LAYOUT[row].charAt(column));
                CELLS[row * COLUMNS + column] = new Cell(row * COLUMNS + column + 1, column, row, zone);
            }
        }
    }

    private ServerRegions() {
    }

    private static Zone zoneForLetter(char letter) {
        return switch (letter) {
            case 'O' -> Zone.OCEANIA;
            case 'W' -> Zone.NA_WEST;
            case 'E' -> Zone.NA_EAST;
            case 'A' -> Zone.ASIA;
            case 'U' -> Zone.EU_WEST;
            case 'C' -> Zone.EU_CENTRAL;
            default -> throw new IllegalStateException("unknown region letter " + letter);
        };
    }

    /** The cell holding a position, or null when it lies outside the grid. */
    public static Cell cellAt(double x, double z) {
        int column = (int) Math.floor((x - MIN_COORD) / (double) CELL_SIZE);
        int row = (int) Math.floor((z - MIN_COORD) / (double) CELL_SIZE);
        if (column < 0 || row < 0 || column >= COLUMNS || row >= COLUMNS) {
            return null;
        }
        return CELLS[row * COLUMNS + column];
    }

    /** All 81 cells, in server-number order. */
    public static Cell[] cells() {
        return CELLS.clone();
    }

    /** The zone with this id, or null - so a hand-configured region wins over it. */
    public static Zone zone(String id) {
        if (id == null) {
            return null;
        }
        for (Zone zone : Zone.values()) {
            if (zone.id.equalsIgnoreCase(id)) {
                return zone;
            }
        }
        return null;
    }
}
